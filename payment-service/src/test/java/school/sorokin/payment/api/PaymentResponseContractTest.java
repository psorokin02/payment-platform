package school.sorokin.payment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import school.sorokin.payment.api.dto.PaymentListResponse;
import school.sorokin.payment.api.dto.PaymentReadResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentResponseContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void paymentResponseContainsBusinessDataAndItsSourceWithoutSqlDiagnostics() {
        JsonNode response = mapper.valueToTree(
                new PaymentReadResponse(null, "POSTGRESQL")
        );

        assertThat(fields(response)).containsExactly("payment", "source");
    }

    @Test
    void listResponseDoesNotExposeDatabaseQueries() {
        JsonNode response = mapper.valueToTree(
                new PaymentListResponse(List.of(), "POSTGRESQL")
        );

        assertThat(fields(response)).containsExactly("payments", "source");
    }

    private List<String> fields(JsonNode response) {
        List<String> names = new ArrayList<>();
        response.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
