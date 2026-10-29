package school.sorokin.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Объявляет единственный основной топик публикации платежей. */
@Configuration
public class KafkaConfiguration {

    /** Одна partition достаточна для показа порядка событий по paymentId. */
    @Bean
    public NewTopic confirmedTopic(@Value("${app.kafka.confirmed-topic}") String topic) {
        return new NewTopic(topic, 1, (short) 1);
    }

}
