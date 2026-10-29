#!/usr/bin/env bash
set -euo pipefail
PAYMENT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec bash "$PAYMENT_ROOT/scripts/service.sh" status all
