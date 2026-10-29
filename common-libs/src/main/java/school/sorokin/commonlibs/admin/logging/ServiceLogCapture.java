package school.sorokin.commonlibs.admin.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Дополнительный appender хранит ограниченную копию тех же логов, которые идут в консоль.
 */
public final class ServiceLogCapture implements AutoCloseable {

    private static final int CAPACITY = 500;
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final int MAX_EXCEPTION_LENGTH = 1024;

    // Один замок защищает очередь, нумерацию записей и закрытие захвата.
    private final Object lock = new Object();
    private final ArrayDeque<ServiceLogEntry> entries = new ArrayDeque<>(CAPACITY);
    private final String service;
    private final Logger rootLogger;
    private final AppenderBase<ILoggingEvent> appender;
    private long sequence;
    private boolean closed;

    public ServiceLogCapture(String service) {
        this(service, (LoggerContext) LoggerFactory.getILoggerFactory());
    }

    ServiceLogCapture(
            String service,
            LoggerContext context
    ) {
        this.service = Objects.requireNonNull(service);
        this.rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        this.appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                capture(event);
            }
        };
        appender.setName("service-log-capture-" + service);
        appender.setContext(context);
        appender.start();
        rootLogger.addAppender(appender);
    }

    /**
     * Возвращает последние limit записей от старых к новым, не меняя буфер.
     */
    public ServiceLogs snapshot(int limit) {
        if (limit < 1 || limit > CAPACITY) {
            throw new IllegalArgumentException("limit должен быть от 1 до 500");
        }
        synchronized (lock) {
            int skip = Math.max(0, entries.size() - limit);
            return new ServiceLogs(service, entries.stream().skip(skip).toList());
        }
    }

    private void capture(ILoggingEvent event) {
        String message = messageWithException(event);
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (entries.size() == CAPACITY) {
                entries.removeFirst();
            }
            entries.addLast(new ServiceLogEntry(
                    ++sequence,
                    event.getInstant(),
                    service,
                    event.getLevel().toString(),
                    truncate(event.getLoggerName(), 256),
                    message
            ));
        }
    }

    private static String messageWithException(ILoggingEvent event) {
        var failure = event.getThrowableProxy();
        if (failure == null) {
            return truncate(event.getFormattedMessage(), MAX_MESSAGE_LENGTH);
        }
        // Полный stack trace остаётся в консоли; веб-панели достаточно типа и причины ошибки.
        String exception = truncate(failure.getClassName(), 256) + ": "
                + truncate(failure.getMessage(), MAX_EXCEPTION_LENGTH);
        return truncate(event.getFormattedMessage(), MAX_MESSAGE_LENGTH - exception.length() - 3)
                + " | " + exception;
    }

    private static String truncate(
            String value,
            int maxLength
    ) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    /**
     * Spring вызывает close при уничтожении bean: чужие appenders продолжают работать.
     */
    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
        }
        rootLogger.detachAppender(appender);
        appender.stop();
    }
}
