package school.sorokin.commonlibs.payments;

/**
 * Состояние платежа; имена сохраняют строковый формат REST, Kafka и таблицы.
 */
public enum PaymentStatus {
    NEW,
    CONFIRMED,
    REJECTED
}
