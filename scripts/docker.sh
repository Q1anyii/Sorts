#!/usr/bin/env bash
# =====================================================================
# 梭子 SORTS · 容器统一入口（WSL 或 Git Bash 均可执行）
#
#   bash scripts/docker.sh up              # 起中间件（mysql/redis/nacos/rabbitmq），
#                                          #   首次自动准备 docker/.env 并等待就绪
#   bash scripts/docker.sh down            # 停中间件（保留数据卷）
#   bash scripts/docker.sh status          # 端口探测 + 容器状态 + 数据库清单
#   bash scripts/docker.sh logs [服务名]     # 跟踪日志（默认 mysql）
#   bash scripts/docker.sh sql             # 重放 scripts/sql/*.sql（建表脚本补执行）
#   bash scripts/docker.sh app             # 构建并启动 6 个后端服务（含网关 8080）
#   bash scripts/docker.sh app-down        # 停后端服务
#   bash scripts/docker.sh web             # 构建并启动前端站点（nginx，默认 8088）
#   bash scripts/docker.sh build           # 只构建后端镜像，不启动
#   bash scripts/docker.sh clean-legacy    # 删除历史遗留容器（Milvus 三件套 / mysql / es / seata…）
#   bash scripts/docker.sh clean           # 删除本项目容器（保留数据卷）
#   bash scripts/docker.sh reset           # 删除本项目容器 + 数据卷（数据库清空，慎用）
#   bash scripts/docker.sh shell mysql     # 进容器（mysql / redis / nacos / rabbitmq）
#
# 端口：MySQL 3307、Redis 6379、Nacos 8848(+9848/9849)、RabbitMQ 5672/15672、
#      网关 8080、前端 8088
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCKER_DIR="$PROJECT_DIR/docker"
ENV_FILE="$DOCKER_DIR/.env"
ENV_EXAMPLE="$DOCKER_DIR/.env.example"
SQL_DIR="$SCRIPT_DIR/sql"

# 历史遗留容器（早期逐个 docker run 创建的，现统一交给 compose）
LEGACY_CONTAINERS=(
  milvus-standalone milvus-minio milvus-etcd
  mysql redis-search
  es kibana seata seata-server mq
  sorts-mysql sorts-redis sorts-nacos sorts-rabbitmq
  # 注意：sorts-gateway/user/... 是 compose.app.yml 管理的，不在清理范围
)

log()  { printf '\033[36m[SORTS]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[WARN ]\033[0m %s\n' "$*"; }
err()  { printf '\033[31m[ERROR]\033[0m %s\n' "$*" >&2; }

require_docker() {
  command -v docker >/dev/null 2>&1 || { err "未找到 docker，请在 Docker Desktop 中开启 WSL 集成（或启动 WSL 内 dockerd）"; exit 1; }
  if ! docker info >/dev/null 2>&1; then
    warn "Docker 守护进程未响应，尝试在 WSL 内启动…"
    if command -v wsl.exe >/dev/null 2>&1; then
      wsl.exe -d Ubuntu -u root -- bash -lc 'systemctl start docker' || true
    fi
    sleep 5
  fi
  docker info >/dev/null 2>&1 || { err "Docker 守护进程仍未就绪：先启动 Docker Desktop，或在 WSL 内执行 systemctl start docker"; exit 1; }
}

prepare_env() {
  if [ ! -f "$ENV_FILE" ]; then
    cp "$ENV_EXAMPLE" "$ENV_FILE"
    log "已从 .env.example 生成 docker/.env（本地开发默认口令，勿提交）"
  fi
}

dc() { docker compose --project-directory "$DOCKER_DIR" --env-file "$ENV_FILE" -f "$DOCKER_DIR/compose.yml" "$@"; }
# 后端服务与前端站点在同一个 compose 文件里，靠 profile 区分：
# 不指定 profile = 只起中间件；--profile app = 起 6 个服务；--profile web = 起前端站点
APP_SERVICES=(gateway user schedule ai notification mall)

env_value() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2-; }

wait_port() {
  local port="$1" timeout="${2:-60}" i=0
  while [ "$i" -lt "$timeout" ]; do
    (echo > "/dev/tcp/127.0.0.1/$port") 2>/dev/null && return 0
    sleep 1; i=$((i + 1))
  done
  return 1
}

# 端口通 ≠ 数据库可用：MySQL 首次初始化还要跑完 initdb 才接受连接，
# 所以建库校验前必须等 healthcheck 变 healthy
wait_healthy() {
  local cname="$1" timeout="${2:-120}" i=0 st
  while [ "$i" -lt "$timeout" ]; do
    st="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cname" 2>/dev/null || echo missing)"
    [ "$st" = "healthy" ] && return 0
    sleep 2; i=$((i + 2))
  done
  warn "$cname 未在 ${timeout}s 内 healthy（当前：$st）"
  return 1
}

# 业务就绪判定：/actuator/health 返回 UP（只看 HTTP 200 会被统一响应体的 code=500 骗过去）
wait_http_up() {
  local url="$1" timeout="${2:-180}" i=0 body
  while [ "$i" -lt "$timeout" ]; do
    body="$(curl -s --max-time 3 "$url" 2>/dev/null || true)"
    echo "$body" | grep -q '"status":"UP"' && return 0
    sleep 3; i=$((i + 3))
  done
  return 1
}

cmd_up() {
  require_docker; prepare_env
  log "启动中间件（compose.yml）"
  dc up -d
  local mysql_port redis_port
  mysql_port="$(env_value MYSQL_PORT)"; redis_port="$(env_value REDIS_PORT)"
  wait_port "${mysql_port:-3307}" 90 && log "MySQL 就绪：localhost:${mysql_port:-3307}" || warn "MySQL 端口未就绪，用 logs mysql 查看"
  wait_port "${redis_port:-6379}" 40 && log "Redis 就绪：localhost:${redis_port:-6379}" || warn "Redis 端口未就绪，用 logs redis 查看"
  wait_port 8848 90 && log "Nacos 就绪：http://localhost:8848/nacos" || warn "Nacos 较慢，稍后再看 status"
  wait_port 5672 60 && log "RabbitMQ 就绪：http://localhost:15672" || warn "RabbitMQ 较慢，稍后再看 status"
  wait_healthy sorts-mysql 150 || true
  init_databases
  cmd_status
}

cmd_down()      { require_docker; dc down; log "中间件已停止（数据卷保留）"; }
cmd_app_down()  { require_docker; prepare_env; dc --profile app rm -sf "${APP_SERVICES[@]}"; log "后端服务已停止并移除容器（镜像保留）"; }

cmd_status() {
  require_docker
  log "=== 端口探测 ==="
  for entry in "MySQL:$(env_value MYSQL_PORT)" "Redis:$(env_value REDIS_PORT)" "Nacos:8848" "RabbitMQ:5672" "RabbitMQ控制台:15672" "网关:8080" "前端:8088"; do
    local name="${entry%%:*}" port="${entry##*:}"
    if (echo > "/dev/tcp/127.0.0.1/${port:-80}") 2>/dev/null; then
      printf '  \033[32m✓\033[0m %-16s localhost:%s\n' "$name" "${port:-80}"
    else
      printf '  \033[31m✗\033[0m %-16s localhost:%s\n' "$name" "${port:-80}"
    fi
  done
  log "=== 容器 ==="
  docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -E 'sorts-|NAMES' || echo "  无 SORTS 容器"
  log "=== 数据库 ==="
  docker exec sorts-mysql mysql -uroot -p"$(env_value MYSQL_ROOT_PASSWORD)" -N \
    -e "SHOW DATABASES LIKE 'sorts%';" 2>/dev/null || echo "  （MySQL 未运行，跳过）"
}

init_databases() {
  docker ps --format '{{.Names}}' | grep -qx sorts-mysql || { warn "sorts-mysql 未运行，跳过建库校验"; return 0; }
  log "校验数据库（首次创建数据卷时已自动执行 scripts/sql/*.sql）"
  for db in sorts_user sorts_schedule sorts_ai sorts_notification sorts_mall; do
    if docker exec sorts-mysql mysql -uroot -p"$(env_value MYSQL_ROOT_PASSWORD)" \
        -e "CREATE DATABASE IF NOT EXISTS ${db} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" >/dev/null 2>&1; then
      echo "  ✓ ${db}"
    else
      warn "  ✗ ${db} 创建失败"
    fi
  done
}

apply_sql() {
  require_docker; prepare_env
  docker ps --format '{{.Names}}' | grep -qx sorts-mysql || { err "sorts-mysql 未运行，请先执行 bash scripts/docker.sh up"; exit 1; }
  log "按文件名顺序执行 $SQL_DIR/*.sql"
  for file in $(ls "$SQL_DIR"/*.sql | sort); do
    printf '  → %s ... ' "$(basename "$file")"
    if docker exec -i sorts-mysql mysql -uroot -p"$(env_value MYSQL_ROOT_PASSWORD)" < "$file" 2>/dev/null; then
      printf '\033[32mOK\033[0m\n'
    else
      printf '\033[31mFAILED\033[0m\n'; err "执行失败：$(basename "$file")"; exit 1
    fi
  done
  log "建表脚本执行完成"
}

cmd_app() {
  require_docker; prepare_env
  docker ps --format '{{.Names}}' | grep -qx sorts-mysql || { err "中间件未启动：先执行 bash scripts/docker.sh up"; exit 1; }
  log "构建并启动后端服务（首次构建需拉取 maven/JRE 基础镜像并下载依赖，约 5~15 分钟）"
  dc --profile app up -d --build "${APP_SERVICES[@]}"
  if wait_http_up "http://localhost:$(env_value GATEWAY_PORT)/actuator/health" 240; then
    log "网关就绪：http://localhost:$(env_value GATEWAY_PORT)（/actuator/health = UP）"
  else
    warn "网关未就绪，用 bash scripts/docker.sh app-logs gateway 查看"
  fi
  dc --profile app ps
}

cmd_build() {
  require_docker; prepare_env
  log "仅构建后端镜像（不启动）"
  dc --profile app build "${APP_SERVICES[@]}"
}

cmd_web() {
  require_docker; prepare_env
  log "构建并启动前端站点（nginx + /api 反代网关）"
  dc --profile web up -d --build frontend
  wait_port "$(env_value FRONTEND_PORT)" 60 && log "前端就绪：http://localhost:$(env_value FRONTEND_PORT)" || warn "前端未就绪，用 logs frontend 查看"
}

cmd_clean_legacy() {
  require_docker
  log "删除历史遗留容器（数据卷保留，可随时 docker volume ls 查看）"
  local removed=0 skipped=0
  for name in "${LEGACY_CONTAINERS[@]}"; do
    if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
      # 只跳过「本项目 compose」接管的容器；其他 compose 项目（如 Agent 的 Milvus）按名单照删
      local project
      project="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$name" 2>/dev/null || true)"
      if [ "$project" = "sorts" ]; then
        warn "跳过 $name（已由本项目 compose 管理）"; skipped=$((skipped + 1)); continue
      fi
      [ -n "$project" ] && warn "$name 来自其他 compose 项目（$project），按你的要求一并删除（如需恢复，回到该项目执行 compose up）"
      docker rm -f "$name" >/dev/null && { echo "  ✗ 已删除 $name"; removed=$((removed + 1)); }
    fi
  done
  [ "$removed" -eq 0 ] && log "没有需要清理的遗留容器" || log "共删除 $removed 个容器（跳过 $skipped 个）"

  # Agent 项目遗留的 Milvus 专用网络（容器已删，网络留着只占名字）
  if docker network ls --format '{{.Name}}' | grep -qx milvus; then
    docker network rm milvus >/dev/null 2>&1 && echo "  ✗ 已删除网络 milvus" || warn "网络 milvus 仍被占用，稍后重试"
  fi
  if docker network ls --format '{{.Name}}' | grep -qx hm-net; then
    docker network rm hm-net >/dev/null 2>&1 && echo "  ✗ 已删除网络 hm-net" || true
  fi

  # 6380 的「原生 redis-server」不再需要：密码遗留在 /etc/redis/redis.conf
  if command -v systemctl >/dev/null 2>&1 && systemctl list-unit-files 2>/dev/null | grep -q '^redis-server'; then
    if systemctl is-active --quiet redis-server 2>/dev/null; then
      log "停用 WSL 原生 redis-server（6380，密码遗留在 /etc/redis/redis.conf，已由容器 Redis 6379 接管）"
      systemctl disable --now redis-server >/dev/null 2>&1 || warn "停用失败，请手动 systemctl disable --now redis-server"
    fi
  fi
  log "清理完成，可用 bash scripts/docker.sh up 以 compose 方式重新拉起"
}

cmd_clean() {
  require_docker; prepare_env
  log "删除本项目容器（服务 + 中间件，数据卷保留）"
  dc --profile app --profile web rm -sf "${APP_SERVICES[@]}" frontend 2>/dev/null || true
  dc down --remove-orphans
}

cmd_reset() {
  require_docker; prepare_env
  warn "将删除本项目容器与数据卷（数据库/Redis 数据清空）"
  dc --profile app --profile web down -v --remove-orphans
  log "已重置"
}

cmd_logs() {
  require_docker; prepare_env
  local svc="${1:-mysql}"
  case "$svc" in
    mysql|redis|nacos|rabbitmq) dc logs -f --tail 100 "$svc" ;;
    *) dc --profile app logs -f --tail 150 "$svc" ;;
  esac
}

cmd_shell() {
  require_docker; prepare_env
  case "${1:-mysql}" in
    mysql)    docker exec -it sorts-mysql mysql -uroot -p"$(env_value MYSQL_ROOT_PASSWORD)" ;;
    redis)    docker exec -it sorts-redis redis-cli -a "$(env_value REDIS_PASSWORD)" ;;
    nacos)    docker exec -it sorts-nacos sh ;;
    rabbitmq) docker exec -it sorts-rabbitmq bash ;;
    *)        docker exec -it "$1" sh ;;
  esac
}

case "${1:-up}" in
  up)            cmd_up ;;
  down)          cmd_down ;;
  restart)       cmd_down; cmd_up ;;
  status)        cmd_status ;;
  logs)          shift; cmd_logs "${1:-mysql}" ;;
  sql)           apply_sql ;;
  app)           cmd_app ;;
  app-down)      cmd_app_down ;;
  app-logs)      shift; docker compose --project-directory "$DOCKER_DIR" --env-file "$ENV_FILE" -f "$DOCKER_DIR/compose.yml" --profile app logs -f --tail 150 "${1:-gateway}" ;;
  build)         cmd_build ;;
  web)           cmd_web ;;
  clean-legacy)  cmd_clean_legacy ;;
  clean)         cmd_clean ;;
  reset)         cmd_reset ;;
  shell)         shift; cmd_shell "${1:-mysql}" ;;
  *)             sed -n '2,30p' "$0"; exit 1 ;;
esac
