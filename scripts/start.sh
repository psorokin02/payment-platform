#!/usr/bin/env bash
# Первый запуск: обычная сборка, Docker-зависимости, затем Java и UI.
set -euo pipefail

if [ "$#" -ne 0 ]; then
  printf 'Usage: %s\nДля отдельных операций используй make help.\n' "$0" >&2
  exit 2
fi

PAYMENT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PAYMENT_ROOT"
make build
exec make up
