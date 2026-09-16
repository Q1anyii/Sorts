#!/usr/bin/env bash
# =====================================================================
# 梭子 SORTS · 本地中间件一键脚本（在 WSL Ubuntu 内执行）
#   bash scripts/wsl-middleware.sh [start|stop|status|logs [容器名]]
#
# 统一用 Docker 承载，避免「服务装在宿主还是 WSL」的歧义：
#   Redis      → localhost:6380（约定：默认端口 +1）
#   MySQL 8    → localhost:3307（避开宿主 3306 冲突），首次启动自动执行 scripts/sql/*.sql
#   Nacos      → localhost:8848（单机模式）
#   RabbitMQ   → localhost:5672 / 管理台 15672
#
# 可覆盖的环境变量：
#   REDIS_PORT（6380）、MYSQL_PORT（3307）、MYSQL_ROOT_PASSWORD（sorts_dev）
# =====================================================================
set -euo pipefail

REDIS_PORT="${REDIS_PORT:-6380}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-sorts_dev}"
NACOS_VERSION="v2.4.3"
RABBITMQ_VERSION="3.13-management"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SQL_DIR="$PROJECT_DIR/scripts/sql"

log()  { echo -e "\033[36m[SORTS]\033[0m $*"; }
warn() { echo -e "\033[33m[WARN ]\033[0m $*"; }
err()  { echo -e "\033[31m[ERROR]\033[0m $*" >&2; }

require_docker() {
  command -v docker >/dev/null 2>&1 || { err "未检测到 Docker，请在 Docker Desktop 中开启 WSL 集成"; exit 1; }
  docker info >/dev/null 2>&1 || { err "Docker 守护进程未运行，请先启动 Docker Desktop"; exit 1; }
}

# 通用：容器存在则启动，不存在则创建
ensure_container() {
  local name="$1"; shift
  if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
    if docker ps --format '{{.Names}}' | grep -qx "$name"; then
      log "容器 $name 已在运行"
    else
      log "启动已有容器 $name"
      docker start "$name" >/dev/null
    fi
  else
    log "创建容器 $name"
    docker "$@" >/dev/null
  fi
}

# 等待端口就绪
wait_port() {
  local port="$1" timeout="${2:-60}" i=0
  while [ "$i" -lt "$timeout" ]; do
    if (echo > "/dev/tcp/127.0.0.1/$port") 2>/dev/null; then return 0; fi
    sleep 1; i=$((i + 1))
  done
  return 1
}

start_redis() {
  log "启动 Redis（端口 ${REDIS_PORT}）"
  ensure_container sorts-redis run -d --name sorts-redis \
    -p "${REDIS_PORT}:6379" redis:7-alpine \
    redis-server --appendonly yes
  wait_port "$REDIS_PORT" 30 && log "Redis 就绪：localhost:${REDIS_PORT}" \
    || err "Redis 端口未就绪，请查看 docker logs sorts-redis"
}

start_mysql() {
  log "启动 MySQL 8（端口 ${MYSQL_PORT}，首次启动自动执行 $SQL_DIR/*.sql）"
  mkdir -p "$SQL_DIR"
  ensure_container sorts-mysql run -d --name sorts-mysql \
    -e MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD}" \
    -e TZ=Asia/Shanghai \
    -p "${MYSQL_PORT}:3306" \
    -v "$SQL_DIR":/docker-entrypoint-initdb.d:ro \
    mysql:8.0 \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_unicode_ci \
    --default-time-zone=+08:00
  log "等待 MySQL 初始化（首次启动约 20-40 秒）..."
  if wait_port "$MYSQL_PORT" 90; then
    log "MySQL 就绪：localhost:${MYSQL_PORT}（root / ${MYSQL_ROOT_PASSWORD}）"
  else
    err "MySQL 端口未就绪，最近日志："
    docker logs --tail 30 sorts-mysql || true
  fi
}

start_nacos() {
  log "启动 Nacos ${NACOS_VERSION}（单机模式）"
  ensure_container sorts-nacos run -d --name sorts-nacos \
    -e MODE=standalone \
    -e NACOS_AUTH_ENABLE=false \
    -e JVM_XMS=512m -e JVM_XMX=512m -e JVM_XMN=256m \
    -p 8848:8848 -p 9848:9848 -p 9849:9849 \
    "nacos/nacos-server:${NACOS_VERSION}"
  wait_port 8848 90 && log "Nacos 就绪：http://localhost:8848/nacos" \
    || warn "Nacos 启动较慢，可用 docker logs -f sorts-nacos 观察"
}

start_rabbitmq() {
  log "启动 RabbitMQ ${RABBITMQ_VERSION}"
  ensure_container sorts-rabbitmq run -d --name sorts-rabbitmq \
    -e RABBITMQ_DEFAULT_USER=sorts \
    -e RABBITMQ_DEFAULT_PASS=sorts_dev \
    -p 5672:5672 -p 15672:15672 \
    "rabbitmq:${RABBITMQ_VERSION}"
  wait_port 5672 60 && log "RabbitMQ 就绪：http://localhost:15672（sorts / sorts_dev）" \
    || warn "RabbitMQ 启动较慢，可用 docker logs -f sorts-rabbitmq 观察"
}

# 幂等建库：容器初始化脚本只在首次启动执行，这里补一次兜底
init_databases() {
  if ! docker ps --format '{{.Names}}' | grep -qx sorts-mysql; then
    warn "sorts-mysql 未运行，跳过建库校验"
    return 0
  fi
  log "校验数据库"
  for db in sorts_user sorts_schedule sorts_ai sorts_notification sorts_mall; do
    if docker exec sorts-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" \
        -e "CREATE DATABASE IF NOT EXISTS ${db} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" >/dev/null 2>&1; then
      echo "  ✓ ${db}"
    else
      warn "  ✗ ${db} 创建失败"
    fi
  done
}

show_status() {
  log "=== 端口探测 ==="
  for entry in "Redis:${REDIS_PORT}" "MySQL:${MYSQL_PORT}" "Nacos:8848" "RabbitMQ:5672" "RabbitMQ管理台:15672"; do
    local name="${entry%%:*}" port="${entry##*:}"
    if (echo > "/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
      printf "  \033[32m✓\033[0m %-16s localhost:%s\n" "$name" "$port"
    else
      printf "  \033[31m✗\033[0m %-16s localhost:%s\n" "$name" "$port"
    fi
  done
  log "=== 容器状态 ==="
  docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -E 'sorts-|NAMES' || echo "  无 SORTS 容器"
  log "=== 已建数据库 ==="
  docker exec sorts-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N \
    -e "SHOW DATABASES LIKE 'sorts%';" 2>/dev/null || true
}

stop_all() {
  log "停止容器"
  docker stop sorts-redis sorts-mysql sorts-nacos sorts-rabbitmq 2>/dev/null || true
  log "已停止（数据保留在 Docker 卷中）"
}

case "${1:-start}" in
  start)  require_docker; start_redis; start_mysql; start_nacos; start_rabbitmq; init_databases; show_status ;;
  stop)   stop_all ;;
  status) require_docker; show_status ;;
  logs)   docker logs --tail 50 -f "${2:-sorts-mysql}" ;;
  *)      echo "用法: $0 [start|stop|status|logs [容器名]]"; exit 1 ;;
esac
