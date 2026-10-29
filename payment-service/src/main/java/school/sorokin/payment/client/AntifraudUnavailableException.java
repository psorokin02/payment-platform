package school.sorokin.payment.client;

/** Временная ошибка, которую разрешено повторить согласно политике HTTP-клиента. */
public class AntifraudUnavailableException extends RuntimeException {
    public AntifraudUnavailableException(String message) {
        super(message);
    }

    public AntifraudUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
