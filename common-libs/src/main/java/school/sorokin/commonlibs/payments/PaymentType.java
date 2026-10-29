package school.sorokin.commonlibs.payments;

/**
 * Способ оплаты; отдельные правила риска применяются только к CARD и QR.
 */
public enum PaymentType {
    CARD,
    QR,
    INSTALLMENT,
    RECURRENT;

    public static PaymentType orDefault(PaymentType type) {
        return type == null ? CARD : type;
    }
}
