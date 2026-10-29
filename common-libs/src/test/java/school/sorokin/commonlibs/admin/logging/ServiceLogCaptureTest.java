package school.sorokin.commonlibs.admin.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceLogCaptureTest {

    private final LoggerContext context = new LoggerContext();

    @AfterEach
    void stopContext() {
        context.stop();
    }

    @Test
    void capturesOrdinaryFormattedLogsWithTheirExceptionSignal() {
        var before = Instant.now();
        try (var capture = new ServiceLogCapture("payment", context)) {
            var logger = context.getLogger("school.sorokin.payment.PaymentService");
            logger.warn("Платёж {} отклонён", 42L, new IllegalStateException("Antifraud недоступен"));

            var snapshot = capture.snapshot(200);

            assertThat(snapshot.service()).isEqualTo("payment");
            assertThat(snapshot.entries()).singleElement().satisfies(entry -> {
                assertThat(entry.sequence()).isEqualTo(1L);
                assertThat(entry.timestamp()).isBetween(before, Instant.now());
                assertThat(entry.service()).isEqualTo("payment");
                assertThat(entry.level()).isEqualTo("WARN");
                assertThat(entry.logger()).isEqualTo("school.sorokin.payment.PaymentService");
                assertThat(entry.message()).contains("Платёж 42 отклонён", "IllegalStateException", "Antifraud недоступен");
                assertThat(entry.message()).doesNotContain("at school.sorokin");
            });
        }
    }

    @Test
    void keepsOnlyRecentEntriesAndReturnsThemInPublicationOrder() {
        try (var capture = new ServiceLogCapture("audit", context)) {
            var logger = context.getLogger("audit.events");
            for (int i = 1; i <= 600; i++) {
                logger.info("Event {}", i);
            }

            assertThat(capture.snapshot(500).entries()).hasSize(500);
            assertThat(capture.snapshot(200).entries()).extracting(ServiceLogEntry::sequence)
                    .containsExactlyElementsOf(LongStream.rangeClosed(401, 600).boxed().toList());
            assertThat(capture.snapshot(2).entries()).extracting(ServiceLogEntry::message)
                    .containsExactly("Event 599", "Event 600");
        }
    }

    @Test
    void rejectsLimitsOutsideTheBoundedPublicContract() {
        try (var capture = new ServiceLogCapture("audit", context)) {
            for (int limit : new int[]{-1, 0, 501, Integer.MAX_VALUE}) {
                assertThatThrownBy(() -> capture.snapshot(limit)).isInstanceOf(IllegalArgumentException.class);
            }
            assertThat(capture.snapshot(1).entries()).isEmpty();
            assertThat(capture.snapshot(500).entries()).isEmpty();
        }
    }

    @Test
    void boundsLongMessagesWhilePreservingTheExceptionSignal() {
        try (var capture = new ServiceLogCapture("antifraud", context)) {
            context.getLogger("antifraud.checks").error("x".repeat(20_000), new IllegalArgumentException("y".repeat(20_000)));

            var entry = capture.snapshot(1).entries().getFirst();

            assertThat(entry.message()).hasSizeLessThanOrEqualTo(4096);
            assertThat(entry.message()).contains("IllegalArgumentException");
        }
    }

    @Test
    void detachesOnCloseWithoutStoppingOtherAppenders() {
        var root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var first = new ServiceLogCapture("payment", context);
        try (var second = new ServiceLogCapture("other-view", context)) {
            first.close();
            first.close();
            root.info("После закрытия");

            assertThat(first.snapshot(200).entries()).isEmpty();
            assertThat(second.snapshot(200).entries()).extracting(ServiceLogEntry::message)
                    .containsExactly("После закрытия");
        }
        assertThat(root.iteratorForAppenders()).isExhausted();
    }

    @Test
    void snapshotsAreStableAndCannotChangeTheBuffer() {
        try (var capture = new ServiceLogCapture("audit", context)) {
            var logger = context.getLogger("audit.events");
            logger.info("Первое событие");
            var snapshot = capture.snapshot(200);
            logger.info("Второе событие");

            assertThat(snapshot.entries()).hasSize(1);
            assertThatThrownBy(snapshot.entries()::clear).isInstanceOf(UnsupportedOperationException.class);
            assertThat(capture.snapshot(200).entries()).hasSize(2);
        }
    }

    @Test
    void supportsConcurrentPublishingAndReadingWithoutLosingSequenceOrder() throws Exception {
        try (var capture = new ServiceLogCapture("payment", context);
             var executor = Executors.newFixedThreadPool(5)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int thread = 0; thread < 4; thread++) {
                tasks.add(() -> {
                    var logger = context.getLogger("payment.concurrent");
                    for (int entry = 0; entry < 1000; entry++) {
                        logger.info("Обработка {}", entry);
                    }
                    return null;
                });
            }
            tasks.add(() -> {
                for (int read = 0; read < 1000; read++) {
                    assertThat(capture.snapshot(200).entries()).hasSizeLessThanOrEqualTo(200);
                }
                return null;
            });
            for (var result : executor.invokeAll(tasks)) {
                result.get();
            }

            assertThat(capture.snapshot(500).entries()).extracting(ServiceLogEntry::sequence)
                    .containsExactlyElementsOf(LongStream.rangeClosed(3501, 4000).boxed().toList());
        }
    }

    @Test
    void respectsLoggerLevelWithoutEnablingExtraDebugOutput() {
        try (var capture = new ServiceLogCapture("payment", context)) {
            var logger = context.getLogger("payment.filtered");
            logger.setLevel(Level.INFO);
            logger.debug("Скрытое событие");
            logger.info("Видимое событие");

            assertThat(capture.snapshot(200).entries()).extracting(ServiceLogEntry::message)
                    .containsExactly("Видимое событие");
        }
    }
}
