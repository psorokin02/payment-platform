# Как устроен запуск:
# - Docker Compose запускает только PostgreSQL, Redis и Kafka.
# - scripts/service.sh запускает backend как java -jar, frontend как Node/Vite.
# - PID процессов и их stdout/stderr лежат в .runtime/<service>.pid и .log.
# Скрипт работает только во время команды Make; постоянного управляющего процесса нет.
#
# Изменил Java/YAML -> make rebuild-payment (или antifraud/audit).
# make payment только запускает готовый JAR; работающий процесс не перезапускает.
# make restart-payment перезапускает готовый JAR без компиляции исходников.

.DEFAULT_GOAL := help
SHELL := /bin/bash
CONTROL := bash scripts/service.sh
SERVICES := payment antifraud audit ui
ACTIONS := start stop restart build rebuild status logs
SERVICE_TARGETS := $(foreach action,$(ACTIONS),$(addprefix $(action)-,$(SERVICES)))

.PHONY: help up start infra-up restart rebuild build stop status logs test test-scripts smoke smoke-console smoke-audit $(SERVICES) $(SERVICE_TARGETS)

help:
	@printf '%s\n' \
	  'Payment Platform — команды из корня проекта' \
	  '' \
	  '  make up                  Поднять Docker-зависимости, затем Java + UI' \
	  '  make infra-up            Поднять только PostgreSQL, Redis и Kafka' \
	  '  make restart             Перезапустить Java + UI без сборки' \
	  '  make rebuild             Собрать, затем перезапустить Java + UI' \
	  '  make stop                Остановить Java + UI; Docker не трогать' \
	  '' \
	  '  make payment             Запустить только Payment' \
	  '  make antifraud           Запустить только Antifraud' \
	  '  make audit               Запустить только Audit' \
	  '  make ui                  Запустить только frontend' \
	  '  make restart-payment     Перезапустить Payment без сборки' \
	  '  make rebuild-payment     Собрать Payment и перезапустить только его' \
	  '  make restart-ui          Перезапустить frontend' \
	  '  make stop-ui             Остановить frontend' \
	  '  make logs-payment        Читать логи Payment; Ctrl+C — выйти из логов' \
	  '' \
	  '  Изменил Java-код или YAML -> make rebuild-payment.' \
	  '  make payment не собирает код и не перезапускает уже работающий сервис.' \
	  '  Везде payment можно заменить на antifraud, audit или ui.' \
	  '  make build               Только сборка, без перезапуска' \
	  '  make status              Docker отдельно, Java/UI отдельно' \
	  '  make logs                Все логи Java + UI' \
	  '  make test                Тесты скриптов, Java и frontend' \
	  '  make smoke               Интеграционный прогон: создаёт учебные платежи' \
	  '  make smoke-console       Проверка Swagger, Antifraud и истории платежей' \
	  '  make smoke-audit         Проверка идемпотентности Audit: включено/выключено' \
	  '' \
	  '  Обычные Docker volumes хранят данные между перезапусками.' \
	  '  Одиночные restart/rebuild управляют только выбранным Java/UI процессом.' \
	  '  Памятка: docs/local-development.md | UI: http://127.0.0.1:5173/'

# Полный запуск: сначала Docker-зависимости, затем Java-процессы и frontend.
up start: infra-up
	@$(CONTROL) start all

# Обычный docker compose up; Java-приложения здесь не запускаются.
infra-up:
	@docker info >/dev/null 2>&1 || { printf 'Запусти Docker Desktop и повтори make up.\n' >&2; exit 1; }
	@docker compose up -d --wait

# Без суффикса действие применяется ко всем Java-сервисам и UI.
# rebuild = сначала сборка с тестами, затем stop/start; при ошибке сборки старые процессы остаются.
restart rebuild build stop status logs:
	@$(CONTROL) $@ all

# Короткие имена — алиасы start: make payment == make start-payment.
$(SERVICES):
	@$(CONTROL) start $@

# Команды вида <действие>-<сервис>. Например:
# make rebuild-payment -> bash scripts/service.sh rebuild payment
# make logs-audit      -> bash scripts/service.sh logs audit
$(SERVICE_TARGETS):
	@$(CONTROL) $(firstword $(subst -, ,$@)) $(lastword $(subst -, ,$@))

test-scripts:
	@node --test scripts/tests/*.test.mjs

test: test-scripts
	@$(CONTROL) test all

smoke:
	@node scripts/smoke.mjs
	@node scripts/console-smoke.mjs
	@node scripts/features-smoke.mjs
	@node scripts/audit-idempotency-smoke.mjs

smoke-console:
	@node scripts/console-smoke.mjs

smoke-audit:
	@node scripts/audit-idempotency-smoke.mjs
