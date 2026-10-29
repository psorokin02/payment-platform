package school.sorokin.audit.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.CommonLoggingErrorHandler;

/** Один основной топик. Ошибки consumer логируются без retry и DLT. */
@Configuration
public class KafkaConfiguration {

    @Bean
    public NewTopic confirmedTopic(@Value("${app.kafka.confirmed-topic}") String topic) {
        return new NewTopic(topic, 1, (short) 1);
    }

    /** Пропускает ошибочную запись после логирования; скрытых повторов по умолчанию нет. */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        return new CommonLoggingErrorHandler();
    }
}
