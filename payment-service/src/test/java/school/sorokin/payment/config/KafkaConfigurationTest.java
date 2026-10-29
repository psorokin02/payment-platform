package school.sorokin.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigurationTest {

    @Test
    void startsWithOnlyTheActiveTopicConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(KafkaConfiguration.class)
                .withPropertyValues("app.kafka.confirmed-topic=payment.events")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(NewTopic.class);
                    assertThat(context.getBean(NewTopic.class).name()).isEqualTo("payment.events");
                });
    }
}
