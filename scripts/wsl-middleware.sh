#!/usr/bin/env bash
# =====================================================================
# 梭子 SORTS · 本地中间件一键脚本（在 WSL Ubuntu 内执行）
#   bash scripts/wsl-middleware.sh [start|stop|status]
#
# 覆盖范围：
#   1. Redis     → 端口改为 6380（默认端口 +1），并启动
#   2. Nacos     → Docker 单机模式（8848）
#   3. RabbitMQ  → Docker（5672 / 管理台 15672）
#   4. MySQL     → 初始化 5 个微服务数据库
# 说明：Windows 侧通过 localhost 直连（WSL 端口自动映射）
# =====================================================================
set -euo pipefail

REDIS_PORT=6380
NACOS_VERSION=v2.4.3
RABBITMQ_VERSION=3.13-management
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"

# 五个微服务对应的独立库（微服务数据隔离，禁止跨库直连）
DATABASES=(sorts_user sorts_schedule sorts_ai sorts_notification sorts_mall)

log() { echo -e "\033[36m[SORTS]\033[0m $*"; }
err() { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }

check_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    err "未检测到 Docker，请先安装 Docker Desktop 并在 WSL 集成中启用 Ubuntu 发行版"
    exit 1
  fi
  docker info >/dev/null 2>&1 || { err "Docker 守护进程未运行，请先启动 Docker Desktop"; exit 1; }
}

start_redis() {
  log "配置 Redis 端口 → ${REDIS_PORT}"
  local conf="/etc/redis/redis.conf"
  if [ -f "$conf" ]; then
    sudo sed -i -E "s/^[[:space:]]*port[[:space:]]+[0-9]+/port ${REDIS_PORT}/" "$conf"
    # 允许来自 Windows 主机的连接
    sudo sed -i -E "s/^[[:space:]]*bind[[:space:]]+.*/bind 0.0.0.0 -::1/" "$conf" || true
    sudo sed -i -E "s/^[[:space:]]*protected-mode[[:space:]]+.*/protected-mode no/" "$conf" || true
  else
    log "未找到 ${conf}，尝试通过包管理器安装 redis-server"
    sudo apt-get update -y && sudo apt-get install -y redis-server
    sudo sed -i -E "s/^[[:space:]]*port[[:space:]]+[0-9]+/port ${REDIS_PORT}/" "$conf"
    sudo sed -i -E "s/^[[:space:]]*bind[[:space:]]+.*/bind 0.0.0.0 -::1/" "$conf" || true
    sudo sed -i -E "s/^[[:space:]]*protected-mode[[:space:]]+.*/protected-mode no/" "$conf" || true
  fi

  # WSL 通常没有 systemd，优先使用 service 方式启动
  if sudo service redis-server restart 2>/dev/null; then
    log "Redis 已重启"
  else
    sudo redis-server "$conf" --daemonize yes
    log "Redis 已以守护进程方式启动"
  fi
  sleep 1
  redis-cli -p "${REDIS_PORT}" ping && log "Redis 就绪：localhost:${REDIS_PORT}"
}

start_nacos() {
  log "启动 Nacos ${NACOS_VERSION}（单机模式）"
  if docker ps -a --format '{{.Names}}' | grep -q '^sorts-nacos$'; then
    docker start sorts-nacos >/dev/null
  else
    docker run -d --name sorts-nacos \
      -e MODE=standalone \
      -e NACOS_AUTH_ENABLE=false \
      -e JVM_XMS=512m -e JVM_XMX=512m -e JVM_XMN=256m \
      -p 8848:8848 -p 9848:9848 -p 9849:9849 \
      "nacos/nacos-server:${NACOS_VERSION}"
  fi
  log "Nacos 控制台：http://localhost:8848/nacos （nacos / nacos）"
}

start_rabbitmq() {
  log "启动 RabbitMQ ${RABBITMQ_VERSION}"
  if docker ps -a --format '{{.Names}}' | grep -q '^sorts-rabbitmq$'; then
    docker start sorts-rabbitmq >/dev/null
  else
    docker run -d --name sorts-rabbitmq \
      -e RABBITMQ_DEFAULT_USER=sorts \
      -e RABBITMQ_DEFAULT_PASS=sorts_dev \
      -p 5672:5672 -p 15672:15672 \
      "rabbitmq:${RABBITMQ_VERSION}"
  fi
  log "RabbitMQ 管理台：http://localhost:15672 （sorts / sorts_dev）"
}

init_mysql() {
  if ! command -v mysql >/dev/null 2>&1; then
    err "未找到 mysql 客户端，跳过建库（可先在 WSL 安装：sudo apt install -y mysql-client）"
    return 0
  fi
  log "初始化 MySQL 数据库（用户：${MYSQL_USER}）"
  for db in "${DATABASES[@]}"; do
    mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" \
      -e "CREATE DATABASE IF NOT EXISTS ${db} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" \
      && log "  数据库就绪：${db}"
  done
}

show_status() {
  log "=== 中间件状态 ==="
  (redis-cli -p "${REDIS_PORT}" ping 2>/dev/null && echo "Redis:     OK (localhost:${REDIS_PORT})") || echo "Redis:     DOWN"
  (curl -s -m 2 http://localhost:8848/nacos/ >/dev/null && echo "Nacos:     OK (localhost:8848)") || echo "Nacos:     DOWN"
  (curl -s -m 2 -u sorts:sorts_dev http://localhost:15672/api/overview >/dev/null && echo "RabbitMQ:  OK (localhost:15672)") || echo "RabbitMQ:  DOWN"
  (mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" -e "SELECT 1" >/dev/null 2>&1 && echo "MySQL:     OK (localhost:3306)") || echo "MySQL:     未验证（需确认账号密码）"
  docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -E 'sorts-|NAMES' || true
}

stop_all() {
  log "停止容器"
  docker stop sorts-nacos sorts-rabbitmq 2>/dev/null || true
  sudo service redis-server stop 2>/dev/null || true
  log "已停止（Redis/Nacos/RabbitMQ）"
}

case "${1:-start}" in
  start)
    check_docker
    start_redis
    start_nacos
    start_rabbitmq
    init_mysql
    sleep 8
    show_status
    ;;
  stop)   stop_all ;;
  status) show_status ;;
  *)      echo "用法: $0 [start|stop|status]"; exit 1 ;;
esac
