#!/usr/bin/env bash
# =====================================================================
# 梭子 SORTS · Maven 构建封装（供 AI 沙箱内的 Git Bash 使用）
#
# 背景（2026-09-16 更正，此前「本机 mvn 损坏」是误诊）：
#   本机 mvn 本身是好的（mvn -v → Apache Maven 3.9.15，位于 E:\develop）。
#   真正的原因是 WorkBuddy 沙箱给 shell 注入了
#     MSYS_NO_PATHCONV=1
#     MSYS2_ARG_CONV_EXCL=*
#   这会关闭 MSYS 的路径自动转换。而 Maven 的 bin/mvn 是 POSIX sh 脚本，
#   它把 /c/Users/.../plexus-classworlds.jar 这类 POSIX 路径直接交给原生 java.exe，
#   Windows 版 java 解析不了 → 报：
#     ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher
#
#   本脚本显式用 cygpath 把路径转成 C:/... 再交给 java，因此在沙箱内始终可用。
#   在用户自己的终端 / IDEA / WSL / CI 里，直接用 mvn 或 backend/mvnw 即可，无需本脚本。
#
# 用法：
#   bash scripts/mvn.sh                 # 等价于 mvn clean install（作用于 backend 工程）
#   bash scripts/mvn.sh clean test      # 自定义阶段
#   bash scripts/mvn.sh -pl sorts-gateway clean package
# =====================================================================
set -euo pipefail

# 优先使用受管 Maven，其次允许通过 MVN_HOME 环境变量指定
MVN_HOME="${MVN_HOME:-C:/Users/Qianyi/.workbuddy/binaries/maven/apache-maven-3.9.16}"
if [ ! -d "$MVN_HOME" ]; then
  echo "[SORTS] 未找到 Maven：$MVN_HOME，请设置 MVN_HOME 环境变量" >&2
  exit 1
fi

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/backend"

# MSYS 路径（/d/xxx）Java 无法识别，统一转换为 Windows 路径（D:/xxx）
to_win_path() {
  local p="$1"
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$p"; else echo "$p"; fi
}
MVN_HOME="$(to_win_path "$MVN_HOME")"
PROJECT_DIR="$(to_win_path "$PROJECT_DIR")"
# 默认目标：整个后端工程执行 clean install；用户传参时完全以传参为准
if [ "$#" -eq 0 ]; then
  ARGS=(clean install)
else
  ARGS=("$@")
fi

# JVM 不会自动读取 HTTP_PROXY 环境变量，需显式透传（localhost 走直连）
PROXY_OPTS=()
if [ -n "${HTTP_PROXY:-${http_proxy:-}}" ]; then
  hostport="${HTTP_PROXY:-$http_proxy}"; hostport="${hostport#*://}"
  PROXY_OPTS+=("-Dhttp.proxyHost=${hostport%:*}" "-Dhttp.proxyPort=${hostport##*:}")
fi
if [ -n "${HTTPS_PROXY:-${https_proxy:-}}" ]; then
  hostport="${HTTPS_PROXY:-$https_proxy}"; hostport="${hostport#*://}"
  PROXY_OPTS+=("-Dhttps.proxyHost=${hostport%:*}" "-Dhttps.proxyPort=${hostport##*:}")
fi
if [ ${#PROXY_OPTS[@]} -gt 0 ]; then
  PROXY_OPTS+=("-Dhttp.nonProxyHosts=localhost|127.0.0.1")
  echo "[SORTS] 已启用代理：${HTTPS_PROXY:-${https_proxy:-${HTTP_PROXY:-$http_proxy}}}"
fi

CLASSWORLDS_JAR="$(ls "$MVN_HOME"/boot/plexus-classworlds-*.jar | head -1)"

java \
  "${PROXY_OPTS[@]}" \
  -classpath "$CLASSWORLDS_JAR" \
  "-Dclassworlds.conf=$MVN_HOME/bin/m2.conf" \
  "-Dmaven.home=$MVN_HOME" \
  "-Dmaven.multiModuleProjectDirectory=$PROJECT_DIR" \
  org.codehaus.plexus.classworlds.launcher.Launcher \
  -f "$PROJECT_DIR/pom.xml" -B "${ARGS[@]}"
