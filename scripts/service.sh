#!/usr/bin/env bash
# Общий lifecycle только локальных процессов. Docker этой утилитой не изменяется.
set -euo pipefail

PAYMENT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PAYMENT_RUNTIME="$PAYMENT_ROOT/.runtime"
PAYMENT_ACTION="${1:-help}"
PAYMENT_SERVICE="${2:-all}"

fail() {
  printf 'Ошибка: %s\n' "$*" >&2
  exit 1
}

select_service() {
  case "$1" in
    payment|payment-service) SERVICE_NAME=payment-service; SERVICE_PORT=8081 ;;
    antifraud|antifraud-service) SERVICE_NAME=antifraud-service; SERVICE_PORT=8082 ;;
    audit|audit-service) SERVICE_NAME=audit-service; SERVICE_PORT=8083 ;;
    ui) SERVICE_NAME=ui; SERVICE_PORT=5173 ;;
    *) fail "Неизвестный сервис '$1'. Используй payment, antifraud, audit или ui." ;;
  esac
  SERVICE_PID_FILE="$PAYMENT_RUNTIME/$SERVICE_NAME.pid"
  SERVICE_LOG="$PAYMENT_RUNTIME/$SERVICE_NAME.log"
  SERVICE_JAR="$PAYMENT_ROOT/$SERVICE_NAME/build/libs/$SERVICE_NAME-1.0.0.jar"
  SERVICE_HEALTH="http://127.0.0.1:$SERVICE_PORT/actuator/health"
  SERVICE_IDENTITY="$SERVICE_JAR"
  if [ "$SERVICE_NAME" = ui ]; then
    SERVICE_IDENTITY="$PAYMENT_ROOT/ui/node_modules/vite/bin/vite.js"
    SERVICE_HEALTH="http://127.0.0.1:5173/"
  fi
}

process_alive() {
  case "${1:-}" in ''|*[!0-9]*|0|1) return 1 ;; esac
  kill -0 "$1" 2>/dev/null || return 1
  local state
  state="$(ps -p "$1" -o stat= 2>/dev/null | tr -d '[:space:]' || true)"
  case "$state" in ''|Z*) return 1 ;; esac
}

load_pid() {
  MANAGED_PID=''
  [ -f "$SERVICE_PID_FILE" ] || return 1
  read -r MANAGED_PID < "$SERVICE_PID_FILE" || true
  process_alive "$MANAGED_PID"
}

assert_ownership() {
  if load_pid; then
    local command_line process_uid expected_tail
    process_uid="$(ps -p "$MANAGED_PID" -o uid= 2>/dev/null | tr -d '[:space:]' || true)"
    [ "$process_uid" = "$(id -u)" ] || fail "$SERVICE_NAME: PID $MANAGED_PID принадлежит другому пользователю."
    command_line="$(ps -ww -p "$MANAGED_PID" -o command= 2>/dev/null || true)"
    # Совпадение части пути недостаточно: jar.old и открытый в редакторе JAR — не наш процесс.
    if [ "$SERVICE_NAME" = ui ]; then
      expected_tail=" $SERVICE_IDENTITY $PAYMENT_ROOT/ui --host 127.0.0.1 --port 5173"
      case "$command_line" in
        *"$expected_tail"|*"$expected_tail --strictPort") return 0 ;;
      esac
    else
      expected_tail=" -Xms32m -Xmx192m -jar $SERVICE_JAR"
      case "$command_line" in
        *"$expected_tail") return 0 ;;
        # Разрешаем безопасный первый restart процесса, запущенного до изменения лимита.
        *" -Xms96m -Xmx256m -jar $SERVICE_JAR") return 0 ;;
      esac
    fi
    fail "$SERVICE_NAME: PID $MANAGED_PID не принадлежит этому сервису; останавливать его не буду."
  fi
}

port_busy() {
  lsof -nP -iTCP:"$SERVICE_PORT" -sTCP:LISTEN -t >/dev/null 2>&1
}

assert_controllable() {
  assert_ownership
  if ! load_pid && port_busy; then
    fail "$SERVICE_NAME: порт $SERVICE_PORT занят процессом не из Make. Останови его в IDE/терминале самостоятельно."
  fi
}

require_commands() {
  local command_name
  for command_name in "$@"; do
    command -v "$command_name" >/dev/null || fail "Не найдена команда $command_name."
  done
}

launch_service() {
  assert_controllable
  if load_pid; then
    printf '%s уже запущен (PID %s).\n' "$SERVICE_NAME" "$MANAGED_PID"
    return
  fi
  mkdir -p "$PAYMENT_RUNTIME"
  if [ "$SERVICE_NAME" = ui ]; then
    require_commands node
    [ -f "$SERVICE_IDENTITY" ] || fail 'Нет frontend-зависимостей. Выполни make build-ui.'
    NODE_OPTIONS="${NODE_OPTIONS:+$NODE_OPTIONS }--max-old-space-size=256" \
      nohup node "$SERVICE_IDENTITY" "$PAYMENT_ROOT/ui" \
      --host 127.0.0.1 --port 5173 --strictPort \
      >"$SERVICE_LOG" 2>&1 < /dev/null &
  else
    [ -s "$SERVICE_JAR" ] || fail "Нет JAR. Выполни make build-${SERVICE_NAME%-service}."
    local java_binary="${PAYMENT_JAVA:-}"
    if [ -z "$java_binary" ] && [ -x /usr/libexec/java_home ]; then
      local jdk
      jdk="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
      if [ -n "$jdk" ]; then java_binary="$jdk/bin/java"; fi
    fi
    java_binary="${java_binary:-java}"
    require_commands "$java_binary"
    nohup "$java_binary" -Xms32m -Xmx192m -jar "$SERVICE_JAR" \
      >"$SERVICE_LOG" 2>&1 < /dev/null &
  fi
  printf '%s\n' "$!" > "$SERVICE_PID_FILE"
  printf 'Запускается %s; лог: %s\n' "$SERVICE_NAME" "$SERVICE_LOG"
}

wait_ready() {
  local attempt
  for attempt in $(seq 1 60); do
    if ! load_pid; then
      tail -n 15 "$SERVICE_LOG" >&2 || true
      fail "$SERVICE_NAME завершился до готовности. См. $SERVICE_LOG"
    fi
    if curl -fsS --max-time 2 "$SERVICE_HEALTH" >/dev/null 2>&1; then
      if [ "$SERVICE_NAME" != audit-service ] || \
        curl -fsS --max-time 2 http://127.0.0.1:8083/admin/status 2>/dev/null | \
        node -e 'let s="";process.stdin.on("data",d=>s+=d);process.stdin.on("end",()=>{try{process.exit(JSON.parse(s).assigned>0?0:1)}catch{process.exit(1)}})'; then
        printf '%s готов на :%s (PID %s).\n' "$SERVICE_NAME" "$SERVICE_PORT" "$MANAGED_PID"
        return
      fi
    fi
    sleep 1
  done
  fail "$SERVICE_NAME не стал готов. См. $SERVICE_LOG"
}

stop_service() {
  assert_controllable
  if ! load_pid; then
    printf '%s уже остановлен.\n' "$SERVICE_NAME"
    return
  fi
  local previous_pid="$MANAGED_PID" attempt
  kill "$previous_pid"
  for attempt in $(seq 1 30); do
    if ! process_alive "$previous_pid"; then
      rm -f "$SERVICE_PID_FILE"
      printf '%s остановлен (PID %s).\n' "$SERVICE_NAME" "$previous_pid"
      return
    fi
    sleep 1
  done
  fail "$SERVICE_NAME не завершился за 30 секунд. SIGKILL не посылался; см. $SERVICE_LOG"
}

run_gradle() {
  (cd "$PAYMENT_ROOT" && ./gradlew "$@" --console=plain)
}

build_service() {
  if [ "$SERVICE_NAME" = ui ]; then
    require_commands node npm
    if [ ! -d "$PAYMENT_ROOT/ui/node_modules" ]; then
      (cd "$PAYMENT_ROOT/ui" && npm ci --no-audit --no-fund)
    fi
    (cd "$PAYMENT_ROOT/ui" && NODE_OPTIONS="${NODE_OPTIONS:+$NODE_OPTIONS }--max-old-space-size=256" npm run build)
  else
    run_gradle ":$SERVICE_NAME:build"
  fi
}

start_all() {
  local service
  # Сначала проверяем все порты, чтобы запуск из IDE не дал частичный второй стенд.
  for service in antifraud audit payment ui; do select_service "$service"; assert_controllable; done
  for service in antifraud audit payment; do select_service "$service"; launch_service; done
  for service in antifraud audit payment; do select_service "$service"; wait_ready; done
  select_service ui
  launch_service
  wait_ready
  printf 'UI: http://127.0.0.1:5173/ | make status | make logs\n'
}

stop_all() {
  local service
  for service in payment antifraud audit ui; do select_service "$service"; assert_controllable; done
  for service in payment antifraud audit ui; do select_service "$service"; stop_service; done
}

build_all() {
  run_gradle build
  select_service ui
  build_service
}

status_service() {
  if curl -fsS --max-time 2 "$SERVICE_HEALTH" >/dev/null 2>&1; then
    printf '%-18s :%-5s UP' "$SERVICE_NAME" "$SERVICE_PORT"
  else
    printf '%-18s :%-5s DOWN' "$SERVICE_NAME" "$SERVICE_PORT"
  fi
  if load_pid; then printf ' (сохранённый PID %s)' "$MANAGED_PID"; fi
  printf '\n'
}

show_logs() {
  [ -f "$SERVICE_LOG" ] || fail "Нет лога $SERVICE_LOG. Сначала запусти сервис."
  tail -n 80 -f "$SERVICE_LOG"
}

case "$PAYMENT_ACTION" in
  start|stop|restart|build|rebuild|status|logs|test) ;;
  help) printf 'Используй make help. CLI: bash scripts/service.sh <action> <payment|antifraud|audit|ui|all>\n'; exit 0 ;;
  *) fail "Неизвестное действие '$PAYMENT_ACTION'. Используй make help." ;;
esac
if [ "$PAYMENT_SERVICE" != all ]; then select_service "$PAYMENT_SERVICE"; fi
if [[ "$PAYMENT_ACTION" =~ ^(start|stop|restart|rebuild)$ ]]; then require_commands curl lsof ps; fi
if [ "$PAYMENT_SERVICE" = audit ] || [ "$PAYMENT_SERVICE" = all ]; then require_commands node; fi

if [ "$PAYMENT_SERVICE" = all ]; then
  case "$PAYMENT_ACTION" in
    start) start_all ;;
    stop) stop_all ;;
    restart) stop_all; start_all ;;
    rebuild) build_all; stop_all; start_all ;;
    build) build_all ;;
    test) run_gradle test; (cd "$PAYMENT_ROOT/ui" && npm test) ;;
    status)
      printf 'Docker — PostgreSQL / Redis / Kafka (отдельная инфраструктура):\n'
      if command -v docker >/dev/null && docker info >/dev/null 2>&1; then
        docker compose -f "$PAYMENT_ROOT/compose.yaml" ps
      else
        printf 'Docker недоступен.\n'
      fi
      printf '\nЛокальные Java / UI процессы:\n'
      for service in payment antifraud audit ui; do select_service "$service"; status_service; done
      ;;
    logs)
      log_files=()
      for service in payment antifraud audit ui; do
        select_service "$service"
        if [ -f "$SERVICE_LOG" ]; then log_files+=("$SERVICE_LOG"); fi
      done
      [ "${#log_files[@]}" -gt 0 ] || fail 'Логов пока нет. Сначала make up.'
      tail -n 40 -f "${log_files[@]}"
      ;;
  esac
else
  case "$PAYMENT_ACTION" in
    start) launch_service; wait_ready ;;
    stop) stop_service ;;
    restart) stop_service; launch_service; wait_ready ;;
    rebuild) build_service; stop_service; launch_service; wait_ready ;;
    build) build_service ;;
    status) status_service ;;
    logs) show_logs ;;
    test) fail 'Для тестов используй make test.' ;;
  esac
fi
