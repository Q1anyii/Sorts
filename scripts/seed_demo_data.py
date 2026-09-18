#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
梭子 SORTS · 演示种子数据脚本（不随 git 提交）

功能：
  1. 为指定用户（默认 username='user'）追加光阴砂数量（默认 +5000），并写积分流水；
  2. 以「当前日期为中轴」，前后各半年（默认 ±182 天）随机生成任务计划；
     - 每天 0~2 条，标题/描述/优先级/标签/颜色随机；
     - 颜色取自项目织历「调色板」色卡（柔和自然色系）；
     - 过去的日程按 40% 概率置为已完成（含随机实际专注秒数），其余为待开始；未来全部待开始。

用法（Windows，无需安装任何依赖）：
  python scripts/seed_demo_data.py                 # 默认：user +5000 光阴砂 + 前后半年随机计划
  python scripts/seed_demo_data.py --points 2000   # 只改积分数量
  python scripts/seed_demo_data.py --dry-run       # 只生成 SQL 不执行（输出到 scripts/seed_demo_data.sql）
  python scripts/seed_demo_data.py --days 90       # 前后各 45 天，缩小范围

说明：通过 WSL 调用 docker 内的 mysql 客户端执行；宿主无需 MySQL 驱动。
     重复运行会继续追加（不清空已有数据）；如需清空可先手动 DELETE。
"""
import argparse
import datetime as dt
import random
import subprocess
import sys
from pathlib import Path

# ---------- 连接参数（与 docker/.env 默认一致，可按需改） ----------
MYSQL_CONTAINER = "sorts-mysql"
MYSQL_ROOT_PASSWORD = "sorts_dev"

# ---------- 项目色卡（与前端织历调色板保持一致） ----------
PRIORITY_COLORS = ["#F49B8B", "#F6C177", "#8FB8DE", "#A9C3A3"]   # 珊瑚/蜜杏/淡蓝/鼠尾草
TAG_COLORS = ["#C3B091", "#A8C0C9", "#D9B8C4", "#B5C4A8",
              "#E0C7A0", "#AFC7D8", "#D4C2B8", "#9FB8AD"]        # 卡其/雾蓝/藕粉/苔绿等柔和色
PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"]
TAG_POOL = ["学习", "工作", "健康", "生活", "娱乐", "阅读"]
TITLE_A = ["晨间复盘", "专注写作", "项目推进", "技能学习", "深度阅读", "健身训练",
           "会议准备", "代码重构", "资料整理", "冥想放松", "英语学习", "周报撰写",
           "方案设计", "数据复盘", "客户沟通", "课程学习", "家务整理", "亲子时间"]
TITLE_B = ["第一节", "第二节", "第三节", "进阶", "入门", "实战", "总结",
           "初稿", "修订", "冲刺", "缓冲", "拓展"]


def pick_title():
    return f"{random.choice(TITLE_A)}·{random.choice(TITLE_B)}"


def pick_tags():
    n = random.randint(1, 3)
    return ",".join(random.sample(TAG_POOL, n))


def pick_color():
    # 颜色随机：一半取优先级色、一半取标签柔和色，贴合「调色板」观感
    return random.choice(PRIORITY_COLORS) if random.random() < 0.5 else random.choice(TAG_COLORS)


def main():
    ap = argparse.ArgumentParser(description="梭子 SORTS 演示种子数据（不提交 git）")
    ap.add_argument("--username", default="user", help="目标用户名（默认 user）")
    ap.add_argument("--points", type=int, default=5000, help="追加光阴砂数量（默认 5000）")
    ap.add_argument("--days", type=int, default=182, help="以今天为中轴，前后各 N 天（默认 182，即前后各半年）")
    ap.add_argument("--max-per-day", type=int, default=2, help="每天最多生成的日程数（默认 2）")
    ap.add_argument("--seed", type=int, default=None, help="随机种子（可复现）")
    ap.add_argument("--dry-run", action="store_true", help="只生成 SQL 到 scripts/seed_demo_data.sql，不执行")
    args = ap.parse_args()

    if args.seed is not None:
        random.seed(args.seed)

    today = dt.date.today()
    start = today - dt.timedelta(days=args.days)
    end = today + dt.timedelta(days=args.days)
    print(f"[SORTS] 中轴日期：{today}，范围：{start} ~ {end}（前后各 {args.days} 天）")

    # ---------- 组装 SQL ----------
    lines = []
    lines.append("SET NAMES utf8mb4;")
    lines.append("START TRANSACTION;")

    # 1) 定位用户并更新光阴砂
    lines.append(f"UPDATE sorts_user.t_user SET points = points + {args.points} "
                 f"WHERE username = '{args.username}' AND deleted = 0;")
    lines.append(
        "INSERT INTO sorts_user.t_points_log (user_id, change_amount, balance, reason)\n"
        "SELECT id, {p}, points, '演示种子数据：批量追加'\n"
        "FROM sorts_user.t_user WHERE username = '{u}' AND deleted = 0;"
        .format(p=args.points, u=args.username))

    # 2) 随机日程
    values = []
    per_day_total = 0
    d = start
    while d <= end:
        n = random.randint(0, args.max_per_day)
        for _ in range(n):
            per_day_total += 1
            # 时间：8:00-22:00 整点或半点
            hour = random.randint(8, 21)
            minute = random.choice([0, 30])
            planned = dt.datetime.combine(d, dt.time(hour, minute))
            duration = random.choice([15, 30, 45, 60, 90, 120])
            status = "PENDING"
            actual_dur = 0
            if d < today and random.random() < 0.4:
                status = "COMPLETED"
                actual_dur = random.randint(300, max(301, duration * 60))
            color = pick_color()
            title = pick_title().replace("'", "''")
            tags = pick_tags()
            values.append(
                "({uid}, '{title}', '{desc}', '{planned}', {dur}, {adur}, '{status}', '{pri}', '{tags}', '{color}')"
                .format(
                    uid="(SELECT id FROM sorts_user.t_user WHERE username='{}' AND deleted=0)".format(args.username),
                    title=title,
                    desc="演示种子数据（脚本生成）",
                    planned=planned.strftime("%Y-%m-%d %H:%M:%S"),
                    dur=duration,
                    adur=actual_dur,
                    status=status,
                    pri=random.choice(PRIORITIES),
                    tags=tags,
                    color=color,
                )
            )
        d += dt.timedelta(days=1)

    if values:
        lines.append(
            "INSERT INTO sorts_schedule.t_schedule\n"
            "  (user_id, title, description, planned_start_time, planned_duration, actual_duration,\n"
            "   status, priority, tags, color)\n"
            "VALUES\n" + ",\n".join(values) + ";"
        )

    lines.append("COMMIT;")
    sql = "\n".join(lines) + "\n"

    out = Path(__file__).resolve().parent / "seed_demo_data.sql"
    out.write_text(sql, encoding="utf-8")
    print(f"[SORTS] 已生成 SQL：{out}（{len(values)} 条日程，追加光阴砂 +{args.points}）")

    if args.dry_run:
        print("[SORTS] --dry-run，未执行。")
        return 0

    # ---------- 通过 WSL + docker exec 执行 ----------
    # 路径转换：Windows 盘符 → /mnt/ 形式；目录含括号等特殊字符必须加引号
    win = str(out)
    mnt = "/mnt/" + win[0].lower() + win[2:].replace("\\", "/")
    wsl_script = (
        f"docker cp '{mnt}' {MYSQL_CONTAINER}:/tmp/seed_demo_data.sql && "
        f"docker exec {MYSQL_CONTAINER} sh -c 'mysql -uroot -p{MYSQL_ROOT_PASSWORD} < /tmp/seed_demo_data.sql'"
    )
    print("[SORTS] 执行中（WSL → docker mysql）…")
    try:
        r = subprocess.run(["wsl", "-d", "Ubuntu", "--", "bash", "-lc", wsl_script],
                           capture_output=True, text=True, timeout=120)
    except FileNotFoundError:
        print("[SORTS] 错误：找不到 wsl 命令，请手动执行生成的 seed_demo_data.sql", file=sys.stderr)
        return 1
    if r.returncode != 0:
        print("[SORTS] 执行失败：", r.stderr[-800:], file=sys.stderr)
        return 1
    print("[SORTS] 执行成功：")
    print(r.stdout.strip())
    print("[SORTS] 完成。当前用户光阴砂与日程已更新，刷新页面即可看到。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
