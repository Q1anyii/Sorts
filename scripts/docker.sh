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
#   bash scripts/docker.sh keepalive       # 占住 WSL 会话（WSL 会在会话结束后回收发行版，
#                                          #   把 dockerd 与容器一起停掉；需长时间常驻时用）
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

# ---------------------------------------------------------------- docker 通路
# 本机存在两种 Docker 守护进程来源，脚本自动选一条能用通的：
#   · local —— Docker Desktop 的命名管道（装了并在跑，Git Bash 可直连）
#   · wsl   —— WSL 发行版内的 dockerd（systemd 管理，Git Bash 够不着，需转发）
# 判定完成后所有 docker 调用都走 docker_run / dc，业务命令不必关心通路。
DOCKER_DISTRO="${SORTS_WSL_DISTRO:-Ubuntu}"
DOCKER_MODE=""
export WSL_UTF8=1   # 让 wsl.exe 自身输出 UTF-8，否则警告/中文在管道里会变成乱码

# Windows 路径 → WSL 路径（D:/a/b → /mnt/d/a/b）；已是 WSL 路径则原样返回
to_wsl_path() {
  local p="$1"
  if command -v cygpath >/dev/null 2>&1; then
    p="$(cygpath -u "$p" 2>/dev/null || printf '%s' "$p")"
  fi
  case "$p" in
    /[a-zA-Z]/*) printf '/mnt%s\n' "$p" ;;
    *)           printf '%s\n' "$p" ;;
  esac
}

# 唯一的 docker 执行入口
docker_run() {
  case "$DOCKER_MODE" in
    wsl) wsl.exe -d "$DOCKER_DISTRO" -u root -- docker "$@" ;;
    *)   docker "$@" ;;
  esac
}

# WSL 内以 root 执行任意命令（仅在 wsl 通路下有意义）
in_wsl_root() {
  wsl.exe -d "$DOCKER_DISTRO" -u root -- bash -lc "$1"
}

docker_daemon_ready() {
  case "$1" in
    local) docker info >/dev/null 2>&1 ;;
    wsl)   in_wsl_root 'docker info' >/dev/null 2>&1 ;;
    *)     return 1 ;;
  esac
}

require_docker() {
  [ -n "$DOCKER_MODE" ] && return 0
  command -v docker >/dev/null 2>&1 || { err "未找到 docker CLI：请安装 Docker Desktop，或确认 docker 在 PATH 中"; exit 1; }

  if docker_daemon_ready local; then
    DOCKER_MODE=local
    return 0
  fi

  # 宿主守护进程没响应（多为 Docker Desktop 未启动），改走 WSL 内的 dockerd
  if command -v wsl.exe >/dev/null 2>&1; then
    warn "宿主 Docker 守护进程未响应，改用 WSL($DOCKER_DISTRO) 内的 dockerd"
    in_wsl_root 'systemctl start docker' >/dev/null 2>&1 || true
    local i=0
    while [ "$i" -lt 10 ]; do
      docker_daemon_ready wsl && { DOCKER_MODE=wsl; log "通路：wsl（$DOCKER_DISTRO 内的 dockerd）"; return 0; }
      sleep 1; i=$((i + 1))
    done
  fi

  err "Docker 守护进程不可用。三选一："
  err "  1) 启动 Docker Desktop；"
  err "  2) 在 WSL 内执行 systemctl start docker；"
  err "  3) 用 SORTS_WSL_DISTRO=<发行版名> 指定正确的 WSL 发行版（当前：$DOCKER_DISTRO）"
  exit 1
}

prepare_env() {
  if [ ! -f "$ENV_FILE" ]; then
    cp "$ENV_EXAMPLE" "$ENV_FILE"
    log "已从 .env.example 生成 docker/.env（本地开发默认口令，勿提交）"
  fi
}

# compose 调用入口：wsl 通路下 --project-directory / --env-file / -f 都要先转成 WSL 路径，
# 否则容器内的 dockerd 找不到文件（compose 相对 build context 也跟着切到 /mnt/... ，与 compose.yml 的 `..` 一致）
dc() {
  if [ "$DOCKER_MODE" = wsl ]; then
    in_wsl_root "docker compose --project-directory '$(to_wsl_path "$DOCKER_DIR")' --env-file '$(to_wsl_path "$ENV_FILE")' -f '$(to_wsl_path "$DOCKER_DIR/compose.yml")' $(printf '%q ' "$@")"
  else
    docker compose --project-directory "$DOCKER_DIR" --env-file "$ENV_FILE" -f "$DOCKER_DIR/compose.yml" "$@"
  fi
}
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
    st="$(docker_run inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cname" 2>/dev/null || echo missing)"
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
  # WSL 会在最后一个会话结束后回收发行版（实测 2.7.12 如此，.wslconfig 的 vmIdleTimeout 只管 VM 不管发行版），
  # 容器本身靠 restart: unless-stopped 会在下次冷启动时自动回来，但健康检查要从头跑一遍
  warn "提示：WSL 发行版会在最后一个终端会话结束后被回收，dockerd 与容器一并停止；下次命令会冷启动自动恢复。"
  warn "      需要长时间不间断运行（远程调试 / 长任务）时，另开一个窗口执行：bash scripts/docker.sh keepalive"
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
  docker_run ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -E 'sorts-|NAMES' || echo "  无 SORTS 容器"
  # 冷启动识别：全部容器都在 90 秒内起步，说明发行版刚被回收又拉起，健康检查还没跑完
  local ages
  ages="$(docker_run ps --filter name=sorts- --format '{{.Status}}' 2>/dev/null | grep -c 'Up [0-9]* seconds' || true)"
  if [ "${ages:-0}" -ge 4 ]; then
    warn "检测到疑似冷启动恢复：$ages 个容器起步不足 1 分钟（WSL 回收发行版后自动拉起），健康检查需要再等一会儿"
  fi
  log "=== 数据库 ==="
  docker_run exec sorts-mysql mysql -uroot -p"$(env_value MYSQL_ROOT_PASSWORD)" -N \
    -e "SHOW DATABASES LIKE 'sorts%';" 2>/dev/null || echo "  （MySQL 未运行，跳过）"
}

init_databases() {
  docker_run ps --format '{{.Names}}' | grep -qx sorts-mysql || { warn "sorts-mysql 未运行，跳过建库校验"; return 0; }
  log "校验数据库（首次创建数据卷时已自动执行 scripts/sql/*.sql）"
  for db in sorts_user sorts_schedule sorts_ai sorts_notification sorts_mall; do
    if docker_run exec sorts-mysql mysql -uroot -p"$(env_value MYSQL_ROOT_PASSWORD)" \
        -e "CREATE DATABASE IF NOT EXISTS ${db} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" >/dev/null 2>&1; then
      echo "  ✓ ${db}"
    else
      warn "  ✗ ${db} 创建失败"
    fi
  done
}

apply_sql() {
  require_docker; prepare_env
  docker_run ps --format '{{.Names}}' | grep -qx sorts-mysql || { err "sorts-mysql 未运行，请先执行 bash scripts/docker.sh up"; exit 1; }
  log "按文件名顺序执行 $SQL_DIR/*.sql"
  for file in $(ls "$SQL_DIR"/*.sql | sort); do
    printf '  → %s ... ' "$(basename "$file")"
    if docker_run exec -i sorts-mysql mysql -uroot -p"$(env_value MYSQL_ROOT_PASSWORD)" < "$file" 2>/dev/null; then
      printf '\033[32mOK\033[0m\n'
    else
      printf '\033[31mFAILED\033[0m\n'; err "执行失败：$(basename "$file")"; exit 1
    fi
  done
  log "建表脚本执行完成"
}

cmd_app() {
  require_docker; prepare_env
  docker_run ps --format '{{.Names}}' | grep -qx sorts-mysql || { err "中间件未启动：先执行 bash scripts/docker.sh up"; exit 1; }
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
  # frontend 的 depends_on: gateway（app profile）——只启用 web profile 时
  # Compose 会把 gateway 排除出项目，校验报 "depends on undefined service gateway"，
  # 因此必须同时启用 app profile（gateway 已在跑时只是满足依赖，不会重建）
  dc --profile app --profile web up -d --build frontend
  wait_port "$(env_value FRONTEND_PORT)" 60 && log "前端就绪：http://localhost:$(env_value FRONTEND_PORT)" || warn "前端未就绪，用 logs frontend 查看"
}

cmd_clean_legacy() {
  require_docker
  log "删除历史遗留容器（数据卷保留，可随时 docker volume ls 查看）"
  local removed=0 skipped=0
  for name in "${LEGACY_CONTAINERS[@]}"; do
    if docker_run ps -a --format '{{.Names}}' | grep -qx "$name"; then
      # 只跳过「本项目 compose」接管的容器；其他 compose 项目（如 Agent 的 Milvus）按名单照删
      local project
      project="$(docker_run inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$name" 2>/dev/null || true)"
      if [ "$project" = "sorts" ]; then
        warn "跳过 $name（已由本项目 compose 管理）"; skipped=$((skipped + 1)); continue
      fi
      [ -n "$project" ] && warn "$name 来自其他 compose 项目（$project），按你的要求一并删除（如需恢复，回到该项目执行 compose up）"
      docker_run rm -f "$name" >/dev/null && { echo "  ✗ 已删除 $name"; removed=$((removed + 1)); }
    fi
  done
  [ "$removed" -eq 0 ] && log "没有需要清理的遗留容器" || log "共删除 $removed 个容器（跳过 $skipped 个）"

  # Agent 项目遗留的 Milvus 专用网络（容器已删，网络留着只占名字）
  if docker_run network ls --format '{{.Name}}' | grep -qx milvus; then
    docker_run network rm milvus >/dev/null 2>&1 && echo "  ✗ 已删除网络 milvus" || warn "网络 milvus 仍被占用，稍后重试"
  fi
  if docker_run network ls --format '{{.Name}}' | grep -qx hm-net; then
    docker_run network rm hm-net >/dev/null 2>&1 && echo "  ✗ 已删除网络 hm-net" || true
  fi

  # 6380 的「原生 redis-server」不再需要：密码遗留在 /etc/redis/redis.conf
  # （它跑在 WSL 里，Git Bash 侧没有 systemctl，故统一交给 WSL 执行）
  if [ "$DOCKER_MODE" = wsl ] && in_wsl_root 'command -v systemctl >/dev/null 2>&1 && systemctl list-unit-files 2>/dev/null | grep -q "^redis-server"'; then
    if in_wsl_root 'systemctl is-active --quiet redis-server'; then
      log "停用 WSL 原生 redis-server（6380，密码遗留在 /etc/redis/redis.conf，已由容器 Redis 6379 接管）"
      in_wsl_root 'systemctl disable --now redis-server' >/dev/null 2>&1 || warn "停用失败，请手动 systemctl disable --now redis-server"
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

# 保持 WSL 会话存活：WSL 2（实测 2.7.12）会在最后一个 wsl.exe 会话结束后回收发行版，
# 连带停掉 dockerd 与容器（容器靠 restart: unless-stopped 在下次冷启动时自动恢复，
# 但健康检查要从头跑一遍）。需要容器长时间不间断运行时，用这个命令占住一个会话。
cmd_keepalive() {
  require_docker
  if [ "$DOCKER_MODE" != "wsl" ]; then
    log "当前走宿主 Docker Desktop 通路，不存在发行版被回收的问题，无需 keepalive"
    return 0
  fi
  log "保持 WSL($DOCKER_DISTRO) 会话存活：此命令会一直阻塞，Ctrl-C 结束"
  log "（保持期间容器不会被连带回收；关掉本窗口后 WSL 会回收发行版）"
  in_wsl_root 'trap "exit 0" TERM INT; while true; do sleep 300; done'
}

cmd_shell() {
  require_docker; prepare_env
  case "${1:-mysql}" in
    mysql)    docker_run exec -it sorts-mysql mysql -uroot -p"$(env_value MYSQL_ROOT_PASSWORD)" ;;
    redis)    docker_run exec -it sorts-redis redis-cli -a "$(env_value REDIS_PASSWORD)" ;;
    nacos)    docker_run exec -it sorts-nacos sh ;;
    rabbitmq) docker_run exec -it sorts-rabbitmq bash ;;
    *)        docker_run exec -it "$1" sh ;;
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
  app-logs)      shift; require_docker; prepare_env; dc --profile app logs -f --tail 150 "${1:-gateway}" ;;
  build)         cmd_build ;;
  web)           cmd_web ;;
  clean-legacy)  cmd_clean_legacy ;;
  clean)         cmd_clean ;;
  reset)         cmd_reset ;;
  shell)         shift; cmd_shell "${1:-mysql}" ;;
  keepalive)     cmd_keepalive ;;
  *)             sed -n '2,30p' "$0"; exit 1 ;;
esac
