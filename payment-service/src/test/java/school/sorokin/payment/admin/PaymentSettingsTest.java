package school.sorokin.payment.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import school.sorokin.payment.admin.dto.PaymentSettingsDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentSettingsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PaymentSettings settings = new PaymentSettings();

    @Test
    void cacheAndInvalidationAreEnabledByDefault() {
        var json = mapper.valueToTree(settings.getSettings());
        assertThat(json.path("cacheEnabled").asBoolean()).isTrue();
        assertThat(json.path("cacheInvalidationEnabled").asBoolean()).isTrue();
    }

    @Test
    void cacheFlagsCanBeChangedIndependentlyWithPartialUpdates() throws Exception {
        settings.update(mapper.readValue("{\"cacheInvalidationEnabled\":false}", PaymentSettingsDto.class));
        assertThat(settings.isCacheEnabled()).isTrue();
        assertThat(settings.isCacheInvalidationEnabled()).isFalse();

        settings.update(mapper.readValue("{\"cacheEnabled\":false}", PaymentSettingsDto.class));
        assertThat(settings.isCacheEnabled()).isFalse();
        assertThat(settings.isCacheInvalidationEnabled()).isFalse();

        settings.update(mapper.readValue("{\"cacheEnabled\":true}", PaymentSettingsDto.class));
        assertThat(settings.isCacheEnabled()).isTrue();
        assertThat(settings.isCacheInvalidationEnabled()).isFalse();
    }

    @Test
    void defaultSettingsPublishEachFactOnce() {
        assertThat(mapper.valueToTree(settings.getSettings()).path("eventCopies").asInt()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5})
    void acceptsCopyCountWithinDemoRange(int copies) throws Exception {
        settings.update(mapper.readValue("{\"eventCopies\":" + copies + "}", PaymentSettingsDto.class));
        assertThat(mapper.valueToTree(settings.getSettings()).path("eventCopies").asInt()).isEqualTo(copies);
        settings.update(mapper.readValue("{\"fetchMode\":\"nplus1\"}", PaymentSettingsDto.class));
        assertThat(mapper.valueToTree(settings.getSettings()).path("eventCopies").asInt()).isEqualTo(copies);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 6})
    void rejectsInvalidCopyCountWithoutApplyingOtherSettings(int copies) throws Exception {
        var request = mapper.readValue("{\"fetchMode\":\"nplus1\",\"eventCopies\":" + copies + "}", PaymentSettingsDto.class);
        assertThatThrownBy(() -> settings.update(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(400));
        assertThat(settings.getFetchMode()).isEqualTo("join");
    }
}
