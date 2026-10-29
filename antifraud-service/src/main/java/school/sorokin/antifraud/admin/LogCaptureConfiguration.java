package school.sorokin.antifraud.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import school.sorokin.commonlibs.admin.logging.ServiceLogCapture;

/** Подключает копию обычных логов процесса к учебной веб-панели. */
@Configuration
public class LogCaptureConfiguration {

    /** При остановке приложения Spring отключит appender через close. */
    @Bean(destroyMethod = "close")
    public ServiceLogCapture serviceLogCapture() {
        return new ServiceLogCapture("antifraud");
    }
}
