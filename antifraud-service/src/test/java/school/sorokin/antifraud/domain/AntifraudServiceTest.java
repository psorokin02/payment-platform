package school.sorokin.antifraud.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import school.sorokin.antifraud.admin.AntifraudSettings;
import school.sorokin.antifraud.api.AntifraudController;
import school.sorokin.commonlibs.antifraud.AntifraudCheckRequest;
import school.sorokin.commonlibs.antifraud.AntifraudCheckResponse;
import school.sorokin.commonlibs.antifraud.AntifraudReason;
import school.sorokin.commonlibs.payments.PaymentType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:antifraud-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class AntifraudServiceTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AntifraudSettings settings;
    @Autowired private AntifraudService service;

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM antifraud_checks");
        settings.changeMode("normal");
    }

    // Ошибка > вместо >= или применение лимита ко всем типам меняет эти бизнес-решения.
    @ParameterizedTest
    @CsvSource({"CARD,100000,true", "QR,100000,true", "CARD,100000.01,false", "QR,100000.01,false",
            "INSTALLMENT,100000.01,true", "RECURRENT,100000.01,true", "INSTALLMENT,10000000,true", "RECURRENT,10000000,true"})
    void appliesLimitOnlyToCardAndQr(String type, String amount, boolean approved) throws Exception {
        var response = service.checkPayment(request(type, amount));
        assertThat(response.approved()).isEqualTo(approved);
        assertThat(response.reason()).isEqualTo(approved ? AntifraudReason.APPROVED : AntifraudReason.RISK_LIMIT);
    }

    @Test
    void readsSavedDecisionWithoutRunningAnotherCheck() throws Exception {
        var request = request("QR", "120000");
        var response = service.checkPayment(request);
        assertThat(new ObjectMapper().valueToTree(response).has("attempt")).isFalse();
        var saved = service.getCheck(response.requestId());
        settings.changeMode("error");
        assertThat(service.getCheck(response.requestId())).isEqualTo(saved);
        assertThat(saved.requestId()).isEqualTo(request.requestId());
        assertThat(saved.amount()).isEqualByComparingTo("120000");
        assertThat(saved.paymentType()).isEqualTo(PaymentType.QR);
        assertThat(saved.approved()).isFalse();
        assertThat(saved.reason()).isEqualTo(AntifraudReason.RISK_LIMIT);
        assertThat(saved.checkedAt()).isNotNull();
    }

    @Test
    void temporaryHttpFailureDoesNotBecomeCompletedBusinessCheck() throws Exception {
        settings.changeMode("error");
        var httpResponse = new AntifraudController(service, settings, 20).check(request("CARD", "1500"));
        assertThat(httpResponse.getStatusCode().value()).isEqualTo(503);
        var response = httpResponse.getBody();
        assertThat(response.reason()).isEqualTo(AntifraudReason.TEMPORARY_UNAVAILABLE);
        assertThat(new ObjectMapper().valueToTree(response).path("reason").asText()).isEqualTo("TEMPORARY_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from antifraud_checks", Integer.class)).isZero();
    }

    @Test
    void slowHttpFailureDoesNotBecomeCompletedBusinessCheck() throws Exception {
        settings.changeMode("slow");
        long start = System.nanoTime();
        var httpResponse = new AntifraudController(service, settings, 20).check(request("CARD", "1500"));
        assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(java.time.Duration.ofMillis(20).toNanos());
        assertThat(httpResponse.getStatusCode().value()).isEqualTo(503);
        assertThat(httpResponse.getBody().reason()).isEqualTo(AntifraudReason.TEMPORARY_UNAVAILABLE);
        assertThat(jdbc.queryForObject("select count(*) from antifraud_checks", Integer.class)).isZero();
    }

    @Test
    void removedTransientModeIsRejected() {
        assertThatThrownBy(() -> settings.changeMode("transient"))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void unknownReasonCannotEnterAnHttpDecision() {
        var mapper = new ObjectMapper();
        assertThatThrownBy(() -> mapper.readValue("{\"approved\":false,\"reason\":\"UNKNOWN\"}", AntifraudCheckResponse.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void forcedRejectionRemainsACompletedCheckWithTextReason() throws Exception {
        settings.changeMode("reject");
        var response = service.checkPayment(request("INSTALLMENT", "1500"));
        assertThat(response.approved()).isFalse();
        assertThat(response.reason().toString()).isEqualTo("FORCED_REJECTION");
        assertThat(service.getCheck(response.requestId()).reason()).isEqualTo(response.reason());
        assertThat(jdbc.queryForObject("select reason from antifraud_checks where request_id = ?", String.class, response.requestId()))
                .isEqualTo("FORCED_REJECTION");
    }

    private AntifraudCheckRequest request(String type, String amount) {
        return new AntifraudCheckRequest(new BigDecimal(amount), UUID.randomUUID().toString(), PaymentType.valueOf(type));
    }
}
