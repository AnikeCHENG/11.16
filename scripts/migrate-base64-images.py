#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
把库里已有的 base64 图片抽成文件，并把列改写成 /uploads/... 路径（P1-12 的收尾一步）。

背景：P1-12 之前，商品图、动态图、头像都是 canvas 压成 JPEG data URL 后直接入库
（products.images / posts.images / users.avatar 是 MEDIUMTEXT，orders.order_image
是下单时的商品图快照）。接口改造后新上传的图已经是文件了，但**旧行还是 base64**——
它们照样能显示（<img src> 对 data URL 与路径一视同仁），代价是每次列表/详情响应
仍要把这些 base64 驮一遍。本脚本把它们一次性搬成文件。

用法：
    # 先干跑：只统计，不写文件、不改库
    python scripts/migrate-base64-images.py

    # 确认数字后执行；会先自动备份受影响的表
    python scripts/migrate-base64-images.py --apply

回滚：脚本在应用前会把 users / products / posts / orders 四张表 dump 到
      <工作区>\\dsh-db-backup\\ 下；文件层面删掉新落盘的图片即可。

注意：data URL 的 base64 载荷**本身不含逗号**（字母表 A-Za-z0-9+/=），
所以不能用 split(',') 拆多图——那会把一张图从中间截断。
这里用正则整体匹配每个 data URL，天然正确。
"""

import argparse
import base64
import os
import re
import sys
import time
import uuid

import pymysql

# (表, 列) —— 需要清洗的图片列；值为 NULL 或非 data URL 的行不动
TARGETS = [
    ("products", "images"),
    ("posts", "images"),
    ("users", "avatar"),
    ("orders", "order_image"),
]

DATA_URL = re.compile(r"data:image/([A-Za-z0-9.+-]+);base64,([A-Za-z0-9+/=]+)")

EXT_BY_SUBTYPE = {
    "jpeg": "jpg",
    "jpg": "jpg",
    "png": "png",
    "gif": "gif",
    "webp": "webp",
}

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
BACKEND = os.path.join(REPO, "afterend", "campussecondhand")
PROPS = os.path.join(BACKEND, "src", "main", "resources", "application-local.properties")
UPLOAD_ROOT = os.path.join(BACKEND, "uploads")
BACKUP_DIR = os.path.join(os.path.dirname(REPO), "dsh-db-backup")


def load_cfg():
    cfg = {}
    with open(PROPS, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                cfg[k.strip()] = v.strip()
    return cfg


def connect():
    cfg = load_cfg()
    return pymysql.connect(
        host="localhost", port=3306, user=cfg["DB_USERNAME"],
        password=cfg["DB_PASSWORD"], database="campus_secondhand",
        charset="utf8mb4", autocommit=False,
    )


def backup(tables):
    """导出这些表的当前内容，返回文件路径。

    **必须用独立连接**：如果复用迁移写入用的那个连接，在同一未提交事务里
    SELECT 会读到自己刚写的值，备份下来的就变成"改完之后"的状态。

    这不是假设。2026-10-12 那版把 backup() 放在 UPDATE 循环之后调用，
    生成的文件里 `/uploads/` 出现 4 次、`data:image` 出现 0 次 —— 它记录的是
    改完以后，对回滚毫无价值，直到真要回滚时才发现。所以这里既提前调用
    （见 main 里的两遍式流程），又用独立连接兜底。
    """
    conn = connect()
    cur = conn.cursor()
    os.makedirs(BACKUP_DIR, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    path = os.path.join(BACKUP_DIR, "campus_secondhand-images-PRE-migration-%s.sql" % stamp)
    with open(path, "w", encoding="utf-8") as f:
        f.write("-- 图片落盘迁移【之前】的备份 (%s)\n\n" % time.strftime("%Y-%m-%d %H:%M:%S"))
        for t in tables:
            cur.execute("SHOW CREATE TABLE `%s`" % t)
            f.write("DROP TABLE IF EXISTS `%s`;\n%s;\n\n" % (t, cur.fetchone()[1]))
            cur.execute("SELECT * FROM `%s`" % t)
            cols = [d[0] for d in cur.description]
            for row in cur.fetchall():
                vals = []
                for v in row:
                    if v is None:
                        vals.append("NULL")
                    elif isinstance(v, (int, float)):
                        vals.append(str(v))
                    else:
                        vals.append("'" + str(v).replace("\\", "\\\\").replace("'", "\\'") + "'")
                f.write("INSERT INTO `%s` (`%s`) VALUES (%s);\n"
                        % (t, "`,`".join(cols), ",".join(vals)))
            f.write("\n")
    conn.close()
    return path


def store(payload_b64, subtype, dry, counter):
    """把一段 base64 写成文件，返回 /uploads/... 路径"""
    ext = EXT_BY_SUBTYPE.get(subtype.lower())
    if ext is None:
        return None
    try:
        raw = base64.b64decode(payload_b64, validate=True)
    except Exception:
        return None
    if not raw:
        return None

    date_path = time.strftime("%Y/%m")
    name = uuid.uuid4().hex + "." + ext
    rel = "/uploads/%s/%s" % (date_path, name)
    if not dry:
        d = os.path.join(UPLOAD_ROOT, date_path)
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, name), "wb") as f:
            f.write(raw)
    counter["files"] += 1
    counter["bytes"] += len(raw)
    return rel


def rewrite(value, dry, counter):
    """把字符串里所有 data URL 换成落盘路径；返回 (新值, 替换个数)"""
    if not value or "data:image" not in value:
        return value, 0
    replaced = [0]

    def repl(m):
        rel = store(m.group(2), m.group(1), dry, counter)
        if rel is None:
            return m.group(0)          # 不认识的类型：原样保留
        replaced[0] += 1
        return rel

    return DATA_URL.sub(repl, value), replaced[0]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="真正写文件并改库（默认只干跑）")
    args = ap.parse_args()
    dry = not args.apply

    conn = connect()
    cur = conn.cursor()
    counter = {"files": 0, "bytes": 0}
    total_rows = 0

    print("模式：%s" % ("干跑（不写文件、不改库）" if dry else "执行"))
    print("-" * 62)

    # 第一遍：只读 + 算出新值，不碰数据库。
    # 写入必须推迟到备份之后，否则备份拍到的是改完的状态。
    plan = []
    for table, column in TARGETS:
        cur.execute(
            "SELECT COUNT(*) FROM `%s` WHERE `%s` LIKE '%%data:image%%'" % (table, column))
        rows = cur.fetchone()[0]
        if not rows:
            print("  %-22s 无 base64 行" % ("%s.%s" % (table, column)))
            continue
        total_rows += rows
        cur.execute("SELECT id, `%s` FROM `%s` WHERE `%s` LIKE '%%data:image%%'"
                    % (column, table, column))
        pending = []
        for rid, value in cur.fetchall():
            new_value, n = rewrite(value, dry, counter)
            if n:
                pending.append((rid, new_value))
        print("  %-22s %d 行 / 抽出 %d 张图" % ("%s.%s" % (table, column), rows,
                                              len(pending)))
        if pending:
            plan.append((table, column, pending))

    print("-" * 62)
    print("涉及 %d 行，抽出 %d 张图，合计 %.1f KB"
          % (total_rows, counter["files"], counter["bytes"] / 1024.0))

    if dry:
        print("以上为干跑。确认无误后加 --apply 执行。")
        conn.rollback()
        return

    if total_rows == 0:
        conn.rollback()
        print("没有需要迁移的数据。")
        return

    # 备份必须早于任何 UPDATE，且用独立连接（见 backup 的 docstring）
    path = backup([t for t, _ in TARGETS])
    print("已备份到 %s" % path)

    # 第二遍：真正落库
    for table, column, pending in plan:
        cur.executemany("UPDATE `%s` SET `%s` = %%s WHERE id = %%s" % (table, column),
                        [(nv, rid) for rid, nv in pending])
    conn.commit()
    print("已提交。落盘目录：%s" % UPLOAD_ROOT)

    # 复核
    print("-" * 62)
    for table, column in TARGETS:
        cur.execute(
            "SELECT COUNT(*) FROM `%s` WHERE `%s` LIKE '%%data:image%%'" % (table, column))
        left = cur.fetchone()[0]
        print("  %-22s 剩余 base64 行：%d" % ("%s.%s" % (table, column), left))
    conn.close()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(1)
