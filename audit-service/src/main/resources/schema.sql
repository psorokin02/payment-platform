-- Актуальная схема Audit. Факты могут повторяться, журнал обработанных eventId — нет.
CREATE TABLE IF NOT EXISTS audit_entries (
  id BIGSERIAL PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL,
  payment_id BIGINT NOT NULL,
  amount NUMERIC(14,2) NOT NULL,
  customer VARCHAR(255) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  payment_type VARCHAR(32),
  status VARCHAR(32),
  reason VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_audit_payment_id ON audit_entries(payment_id, id);

-- Отдельный журнал обработанных событий
CREATE TABLE IF NOT EXISTS audit_processed_events (
  event_id VARCHAR(64) PRIMARY KEY
);

