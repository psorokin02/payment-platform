package school.sorokin.audit.admin;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditAdminControllerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuditAdminController(
            new AuditAdminService(new KafkaListenerEndpointRegistry(), new AuditRuntimeState(true))
    )).build();

    @Test
    void removedFailureInjectionIsNotExposed() throws Exception {
        mvc.perform(post("/admin/fail-next").contentType(MediaType.APPLICATION_JSON).content("{\"count\":2}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/admin/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.retries").doesNotExist())
                .andExpect(jsonPath("$.deadLetters").doesNotExist())
                .andExpect(jsonPath("$.failuresRemaining").doesNotExist());
        mvc.perform(get("/admin/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveries").doesNotExist())
                .andExpect(jsonPath("$.duplicates").doesNotExist());
    }

    @Test
    void settingChangesAndFreshStatusReflectsBothDirections() throws Exception {
        mvc.perform(get("/admin/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotencyEnabled").value(true));
        mvc.perform(put("/admin/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyEnabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotencyEnabled").value(false));
        mvc.perform(get("/admin/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotencyEnabled").value(false));
        mvc.perform(put("/admin/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyEnabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotencyEnabled").value(true));
    }

    @Test
    void missingFlagCannotSilentlyDisableProtection() throws Exception {
        mvc.perform(put("/admin/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotencyEnabled").value(true));
    }

    @Test
    void malformedFlagCannotSilentlyDisableProtection() throws Exception {
        mvc.perform(put("/admin/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyEnabled\":\"not-a-boolean\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotencyEnabled").value(true));
    }

    @Test
    void nonBooleanJsonTokensCannotToggleProtection() throws Exception {
        for (String token : new String[]{"0", "1", "\"false\"", "\"true\"", "[]", "{}"}) {
            mvc.perform(put("/admin/settings").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idempotencyEnabled\":" + token + "}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/admin/status"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.idempotencyEnabled").value(true));
        }
    }
}
