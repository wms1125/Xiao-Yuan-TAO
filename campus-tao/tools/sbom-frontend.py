"""扫描前端 node_modules，输出依赖清单（package / version / license / nested_duplicate）。

离线运行，不依赖 lock 文件：package-lock.json 不携带许可字段，直接读取每个已安装包
的 package.json 元数据更准确。含嵌套安装的重复版本时，nested_duplicate 列标记。

用法（在交付根或任意目录执行均可，路径基于脚本位置推导）：
    python campus-tao\\tools\\sbom-frontend.py
输出：
    campus-tao\\sbom\\frontend-dependencies.csv（UTF-8 BOM，可用 Excel 直接打开）
"""
import json
import os
import csv
import sys
from collections import Counter

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CAMPUS_DIR = os.path.dirname(SCRIPT_DIR)          # campus-tao/
DIST_DIR = os.path.dirname(CAMPUS_DIR)            # 交付根
UI = os.path.join(DIST_DIR, 'campus-tao-ui')
NM = os.path.join(UI, 'node_modules')
OUT_DIR = os.path.join(CAMPUS_DIR, 'sbom')

packages = {}  # (name, version) -> (license, nested)


def license_of(pj):
    lic = pj.get('license')
    if isinstance(lic, str):
        return lic
    if isinstance(lic, dict):
        return lic.get('type') or lic.get('name') or 'UNKNOWN'
    lics = pj.get('licenses')
    if isinstance(lics, list):
        out = []
        for item in lics:
            if isinstance(item, dict):
                out.append(item.get('type') or item.get('name') or 'UNKNOWN')
            elif isinstance(item, str):
                out.append(item)
        if out:
            return ' OR '.join(sorted(set(out)))
    return 'UNKNOWN'


def scan(root, nested_depth):
    if not os.path.isdir(root):
        return
    for entry in os.listdir(root):
        d = os.path.join(root, entry)
        if entry.startswith('.'):
            continue
        if entry.startswith('@'):
            if not os.path.isdir(d):
                continue
            for sub in os.listdir(d):
                scan_one(os.path.join(d, sub), entry + '/' + sub, nested_depth)
            continue
        if not os.path.isdir(d):
            continue
        scan_one(d, entry, nested_depth)


def scan_one(d, name, nested_depth):
    pj_path = os.path.join(d, 'package.json')
    if not os.path.isfile(pj_path):
        return
    try:
        with open(pj_path, encoding='utf-8') as f:
            pj = json.load(f)
    except Exception:
        return
    n = pj.get('name') or name
    v = pj.get('version') or 'UNKNOWN'
    key = (n, v)
    nested = nested_depth > 0
    prev = packages.get(key)
    if prev is None or (nested and not prev[1]):
        packages[key] = (license_of(pj), nested)
    inner = os.path.join(d, 'node_modules')
    if os.path.isdir(inner):
        scan(inner, nested_depth + 1)


def main():
    if not os.path.isdir(NM):
        print('node_modules not found: %s' % NM)
        print('请先在 campus-tao-ui 下执行 npm install')
        return 1

    os.makedirs(OUT_DIR, exist_ok=True)
    scan(NM, 0)

    rows = sorted(packages.items(), key=lambda kv: (kv[0][0].lower(), kv[0][1]))
    csv_path = os.path.join(OUT_DIR, 'frontend-dependencies.csv')
    with open(csv_path, 'w', newline='', encoding='utf-8-sig') as f:
        w = csv.writer(f)
        w.writerow(['package', 'version', 'license', 'nested_duplicate'])
        for (n, v), (lic, nested) in rows:
            w.writerow([n, v, lic, 'yes' if nested else ''])

    lic_counter = Counter(lic for (lic, _) in packages.values())
    unknown = [f'{n}@{v}' for (n, v), (lic, _) in packages.items() if lic == 'UNKNOWN']
    copyleft_kw = ('GPL', 'AGPL', 'LGPL', 'MPL', 'EUPL', 'CDDL', 'SSPL')
    copyleft = [(f'{n}@{v}', lic) for (n, v), (lic, _) in packages.items()
                if any(k in lic.upper() for k in copyleft_kw)]

    print('packages_total', len(packages))
    print('csv', csv_path)
    print('license_distribution', json.dumps(lic_counter.most_common(), ensure_ascii=False))
    print('unknown_license_count', len(unknown))
    print('copyleft_candidates', json.dumps(copyleft, ensure_ascii=False))
    sys.stdout.flush()
    return 0


if __name__ == '__main__':
    sys.exit(main())
