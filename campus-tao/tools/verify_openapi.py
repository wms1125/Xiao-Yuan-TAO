#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
校园淘 · OpenAPI 规格一致性校验（标准库实现，零第三方依赖）

解决的问题：接口契约此前"只有运行时"——springdoc 只把规格暴露在 /v3/api-docs，
          没人归档、也没人校验，代码改了接口而规格不更新时无人发现。
          本脚本把归档规格变成可核验、且能兜住漂移的单一事实来源。

归档规格：campus-tao/docs/openapi/campus-tao-openapi.json（从运行中的 /v3/api-docs 导出）
运行时来源：GET {base}/v3/api-docs

校验三件事：
  1) 归档文件本身可用：JSON 可解析，含 openapi / info / paths；
  2) 归档的版本与 application.yml 中 ruoyi.version 一致（防"版本号推进了但规格没重新导出"）；
  3) 归档与运行时一致：逐条比对 path+method 与 operationId，任一侧多/少一个接口即失败。

为什么比对 path+method+operationId 而不是整份 JSON 文本：
  整份文本比对会被键序、空白、构建期差异等无关因素打乱，产生误报；
  path+method 是契约骨架、operationId 是方法级标识，这三者变了才是真正的契约变更。

用法：
  python campus-tao/tools/verify_openapi.py                  # 归档 vs 运行时（需后端在跑）
  python campus-tao/tools/verify_openapi.py --archive-only   # 仅校验归档文件（无后端可用，CI 离线门禁）
  python campus-tao/tools/verify_openapi.py --refresh        # 人工确认契约变更后，重新导出归档
"""
import argparse
import io
import json
import os
import re
import sys
import urllib.request

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(HERE)  # campus-tao/
ARCHIVE = os.path.join(REPO_ROOT, "docs", "openapi", "campus-tao-openapi.json")
APP_YML = os.path.join(REPO_ROOT, "RuoYi-Vue-master", "ruoyi-admin",
                       "src", "main", "resources", "application.yml")

HTTP_METHODS = ("get", "post", "put", "delete", "patch", "head", "options")


def log(msg):
    print(msg, flush=True)


def read_yml_version():
    """从 application.yml 抓 ruoyi.version 的值，抓不到返回 None（不因此失败）。"""
    try:
        with io.open(APP_YML, encoding="utf-8") as f:
            text = f.read()
    except OSError:
        return None
    # 只取 ruoyi 块下的 version；用缩进判断，避免匹配到其他模块的 version
    m = re.search(r"^\s{2,}version:\s*([0-9A-Za-z._\-]+)\s*$", text, re.MULTILINE)
    return m.group(1) if m else None


def signature(spec):
    """把规格压缩成可比较的契约骨架：{‘METHOD path’: operationId}。"""
    sig = {}
    for path, item in (spec.get("paths") or {}).items():
        for method in HTTP_METHODS:
            op = item.get(method)
            if isinstance(op, dict):
                sig["%s %s" % (method.upper(), path)] = op.get("operationId") or "<no-operationId>"
    return sig


def load_json_text(text, label):
    try:
        return json.loads(text)
    except json.JSONDecodeError as e:
        log("[FAIL] %s 不是合法 JSON：%s" % (label, e))
        sys.exit(1)


def fetch_live(url):
    with urllib.request.urlopen(url, timeout=20) as r:
        raw = r.read()
    return load_json_text(raw.decode("utf-8"), "运行时 /v3/api-docs")


def check_archive_shape(spec):
    ok = True
    for key in ("openapi", "info", "paths"):
        if key not in spec:
            log("[FAIL] 归档规格缺少必填顶层字段：%s" % key)
            ok = False
    if ok:
        log("[OK] 归档规格结构完整：openapi=%s title=%s paths=%d"
            % (spec.get("openapi"), (spec.get("info") or {}).get("title"),
               len(spec.get("paths") or {})))
    return ok


def check_version(spec):
    yml_ver = read_yml_version()
    info_ver = str((spec.get("info") or {}).get("version") or "")
    if not yml_ver:
        log("[WARN] 未能从 application.yml 读到 ruoyi.version，跳过版本一致性检查")
        return True
    if yml_ver in info_ver:
        log("[OK] 规格版本与 application.yml 一致：%s ⊆ \"%s\"" % (yml_ver, info_ver))
        return True
    log("[FAIL] 规格版本与 application.yml 不一致：yml=%s，规格 info.version=\"%s\"" % (yml_ver, info_ver))
    return False


def check_runtime_sync(archive, live):
    a, b = signature(archive), signature(live)
    only_arch = sorted(set(a) - set(b))
    only_live = sorted(set(b) - set(a))
    changed = sorted(k for k in set(a) & set(b) if a[k] != b[k])

    if not (only_arch or only_live or changed):
        log("[OK] 归档规格与运行时完全一致：%d 个操作（path×method）逐一匹配" % len(a))
        return True

    log("[FAIL] 归档规格与运行时不一致——归档已过期，需人工确认契约变更后重新导出：")
    for k in only_arch:
        log("  仅归档有（运行时已删除？）：%s  operationId=%s" % (k, a[k]))
    for k in only_live:
        log("  仅运行时新增（归档未收录）：%s  operationId=%s" % (k, b[k]))
    for k in changed:
        log("  签名变更：%s  归档=%s  运行时=%s" % (k, a[k], b[k]))
    log("  修复：python campus-tao/tools/verify_openapi.py --refresh")
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default=os.environ.get("CAMPUS_BASE_URL", "http://localhost:8080"))
    ap.add_argument("--archive-only", action="store_true",
                    help="无后端时只校验归档文件本身（结构与版本），不比对运行时")
    ap.add_argument("--refresh", action="store_true",
                    help="重新导出归档规格（仅在人工确认契约变更后使用）")
    args = ap.parse_args()

    if not os.path.exists(ARCHIVE):
        log("[FAIL] 归档规格不存在：%s" % ARCHIVE)
        log("  修复：python campus-tao/tools/verify_openapi.py --refresh（需后端在跑）")
        sys.exit(1)

    with io.open(ARCHIVE, encoding="utf-8") as f:
        archive = load_json_text(f.read(), "归档规格")
    log("归档规格：%s" % ARCHIVE)

    ok = check_archive_shape(archive)

    if args.refresh:
        live = fetch_live(args.url + "/v3/api-docs")
        with io.open(ARCHIVE, "w", encoding="utf-8") as f:
            json.dump(live, f, ensure_ascii=False, indent=2)
            f.write("\n")
        log("[OK] 已重新导出归档规格：%d 个 path" % len(live.get("paths") or {}))
        sys.exit(0)

    ok = check_version(archive) and ok

    if args.archive_only:
        log("（--archive-only：跳过与运行时的比对）")
        sys.exit(0 if ok else 1)

    live = fetch_live(args.url + "/v3/api-docs")
    ok = check_runtime_sync(archive, live) and ok
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
