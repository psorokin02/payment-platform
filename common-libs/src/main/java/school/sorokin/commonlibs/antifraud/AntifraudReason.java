package school.sorokin.commonlibs.antifraud;

/**
 * Причина решения проверки; временная недоступность не является бизнес-отказом.
 */
public enum AntifraudReason {
    APPROVED,
    RISK_LIMIT,
    FORCED_REJECTION,
    TEMPORARY_UNAVAILABLE
}
