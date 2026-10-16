#!/usr/bin/env python3
"""校验 Spring YAML 配置无重复顶层键。

背景：SnakeYAML 在 Spring Boot 中开启了重复键检测，同一个 YAML 文件里出现两次
顶层键（例如新增 `spring:` 段时未并入既有 `spring:`）会导致应用**启动即失败**：
    found duplicate key spring
这类错误只在运行时暴露，静态检查可在提交前拦截。

用法：
    python tools/check_yml_dup_keys.py ruoyi-admin/src/main/resources/*.yml
退出码：0 = 通过；1 = 存在重复键。
"""
import collections
import re
import sys

TOP_KEY = re.compile(r"^([A-Za-z_][A-Za-z0-9_.-]*):(\s|$)")


def check(path: str) -> dict:
    seen = collections.Counter()
    lines = collections.defaultdict(list)
    with open(path, encoding="utf-8") as fh:
        for no, line in enumerate(fh, 1):
            m = TOP_KEY.match(line)
            if m:
                seen[m.group(1)] += 1
                lines[m.group(1)].append(no)
    return {k: v for k, v in lines.items() if len(v) > 1}


def main(argv: list) -> int:
    if not argv:
        print("用法: check_yml_dup_keys.py <file.yml> [more.yml ...]", file=sys.stderr)
        return 2
    bad = False
    for path in argv:
        dup = check(path)
        if dup:
            bad = True
            print(f"[FAIL] {path} 存在重复顶层键:")
            for key, nos in dup.items():
                print(f"        '{key}' 出现在第 {', '.join(map(str, nos))} 行")
        else:
            print(f"[ OK ] {path} 无重复顶层键")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
