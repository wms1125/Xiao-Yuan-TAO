#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ai-log 结构化元数据索引（P2-11）

把 `campus-tao/ai-log/NNN-*.md` 的过程留痕解析成机器可读的 `ai-log/index.json`：
编号、标题、章节结构、git 末次提交日期，以及四项元数据
（AI 生成占比 / 人工修改概述 / 人工复核签核人 / 签核日期）。

为什么需要它：v1.0.17 评估（A1/A2）指出 ai-log 只有定性描述，"AI 辅助编程的
合理应用程度"无法度量。本工具把这件事变成可校验的数字——缺元数据的篇目会被
如实统计出来，而不是靠汇报口径。

同时做三项结构校验（任一不满足退出码 1，可直接挂门禁）：
  1. 文件名编号与 H1 标题中的编号一致；
  2. 元数据块存在且四项字段齐全（值为 `未追溯` 也算齐全——缺失才是问题，
     历史篇目补不出真值，宁可如实标注也不许编造）；
  3. 至少包含 2 个 `##` 章节（旧五段式与编号五段式都兼容，不锁具体措辞）。

用法：
  python campus-tao/tools/ai_log_index.py            # 生成并校验
  python campus-tao/tools/ai_log_index.py --check    # 只校验不写文件（CI 用）
"""

import argparse
import io
import json
import os
import re
import subprocess
import sys
from datetime import date

HERE = os.path.dirname(os.path.abspath(__file__))
AI_LOG_DIR = os.path.normpath(os.path.join(HERE, "..", "ai-log"))
INDEX_PATH = os.path.join(AI_LOG_DIR, "index.json")

META_KEYS = ("ai_generated_ratio", "human_edits", "reviewer", "review_date")
META_RE = re.compile(r"<!--\s*ai-log-meta\s*(.*?)-->", re.S)
H1_RE = re.compile(r"^#\s+(.+?)\s*$", re.M)
H1_ID_RE = re.compile(r"^(?:0*)?(\d+)\s*[·•]\s*(.+)$")
H2_RE = re.compile(r"^##\s+(.+?)\s*$", re.M)
PENDING = "未追溯"


def git_last_date(path):
    try:
        out = subprocess.run(
            ["git", "log", "-1", "--format=%as", "--", path],
            capture_output=True, text=True, cwd=os.path.dirname(AI_LOG_DIR),
        ).stdout.strip()
        return out or None
    except OSError:
        return None


def parse_meta(text):
    m = META_RE.search(text)
    if not m:
        return None
    fields = {}
    for line in m.group(1).splitlines():
        if ":" in line:
            k, _, v = line.partition(":")
            k = k.strip().split("#")[0].strip()
            v = v.split("#")[0].strip()
            if k:
                fields[k] = v
    return fields


def parse_log(path, fname):
    with io.open(path, encoding="utf-8") as f:
        text = f.read()
    m = re.match(r"^(\d{3})-", fname)
    log_id = m.group(1) if m else None
    h1 = H1_RE.search(text)
    title, h1_id = (h1.group(1) if h1 else None), None
    if title:
        hm = H1_ID_RE.match(title)
        if hm:
            h1_id, title = hm.group(1), hm.group(2).strip()
    sections = [s.split("：")[0].split(":")[0].strip() for s in H2_RE.findall(text)]
    meta = parse_meta(text)

    problems = []
    if not log_id:
        problems.append("文件名缺三位编号前缀")
    if log_id and h1_id is not None and int(h1_id) != int(log_id):
        problems.append(f"H1 编号({h1_id})与文件名({log_id})不一致")
    elif h1_id is None:
        # H1 允许不带编号（如「评估复评 7 项扣分逐条治理」），只记 title
        pass
    if not sections or len(sections) < 2:
        problems.append(f"章节过少（{len(sections)} 个 ## 节）")
    missing = [k for k in META_KEYS if not meta or not meta.get(k)]
    if missing:
        problems.append(f"元数据块缺失字段：{','.join(missing)}")

    return {
        "id": log_id,
        "file": fname,
        "title": title,
        "sections": sections,
        "git_last_commit_date": git_last_date(path),
        "meta": {
            "ai_generated_ratio": (meta or {}).get("ai_generated_ratio"),
            "human_edits": (meta or {}).get("human_edits"),
            "reviewer": (meta or {}).get("reviewer"),
            "review_date": (meta or {}).get("review_date"),
        },
    }, problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="只校验并打印，不写 index.json")
    args = ap.parse_args()

    if not os.path.isdir(AI_LOG_DIR):
        print("[FAIL] 目录不存在：%s" % AI_LOG_DIR)
        return 1

    logs, failures = [], []
    for fname in sorted(os.listdir(AI_LOG_DIR)):
        if not fname.endswith(".md"):
            continue
        entry, problems = parse_log(os.path.join(AI_LOG_DIR, fname), fname)
        logs.append(entry)
        for p in problems:
            failures.append("%s: %s" % (fname, p))

    filled = sum(1 for e in logs if e["meta"]["reviewer"] and e["meta"]["reviewer"] != PENDING)
    result = {
        "generated_at": date.today().isoformat(),
        "generator": "campus-tao/tools/ai_log_index.py",
        "count": len(logs),
        "meta_reviewed": filled,
        "meta_policy": "001~025 为历史篇目，四项字段如实标注「未追溯」；"
                       "自 026 起新篇目必须填写（AI 生成占比为撰写者自估值，"
                       "签核人与日期须人工填写）。",
        "logs": logs,
    }

    if failures:
        print("[FAIL] %d 处结构问题：" % len(failures))
        for f in failures:
            print("  - " + f)
        ok = False
    else:
        ok = True

    print("[OK] 解析 %d 篇 ai-log，签核覆盖 %d/%d" % (len(logs), filled, len(logs)))
    if not args.check:
        with io.open(INDEX_PATH, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("[OK] 已写出 %s" % os.path.relpath(INDEX_PATH, os.path.join(HERE, "..")))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
