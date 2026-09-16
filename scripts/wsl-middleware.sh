#!/usr/bin/env bash
# =====================================================================
# 【兼容层】旧的中间件脚本已并入 scripts/docker.sh 的 compose 编排。
# 这里只做命令映射，避免两套实现各自漂移：
#   start  → docker.sh up
#   stop   → docker.sh down
#   status → docker.sh status
#   sql    → docker.sh sql
#   logs   → docker.sh logs [服务名]
# 新脚本额外提供：app / app-down / web / build / clean-legacy / clean / reset / shell
# =====================================================================
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

case "${1:-start}" in
  start)  exec bash "$SCRIPT_DIR/docker.sh" up ;;
  stop)   exec bash "$SCRIPT_DIR/docker.sh" down ;;
  status) exec bash "$SCRIPT_DIR/docker.sh" status ;;
  sql)    exec bash "$SCRIPT_DIR/docker.sh" sql ;;
  logs)   shift; exec bash "$SCRIPT_DIR/docker.sh" logs "${1:-mysql}" ;;
  *)
    echo "本脚本已并入 scripts/docker.sh（compose 编排）。"
    echo "用法: bash scripts/docker.sh [up|down|status|logs|sql|app|app-down|web|build|clean-legacy|clean|reset|shell]"
    exit 1
    ;;
esac
