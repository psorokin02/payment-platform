#!/usr/bin/env bash
set -euo pipefail
PAYMENT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
case "${1:-}" in ''|--infra) ;; *) echo "Usage: $0 [--infra]" >&2; exit 2 ;; esac
bash "$PAYMENT_ROOT/scripts/service.sh" stop all
if [ "${1:-}" = '--infra' ]; then
  cd "$PAYMENT_ROOT"
  docker compose stop
fi
echo 'Данные не удалялись. Для остановки своих Docker-сервисов добавьте --infra.'
