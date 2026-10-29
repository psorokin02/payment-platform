package school.sorokin.audit.domain;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditSchemaTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""
    );
    private final JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    @Test
    void factsMayRepeatButProcessedEventIdsAreUnique() {
        initializeSchema();
        insertFact();
        insertFact();
        jdbc.update("INSERT INTO audit_processed_events VALUES ('event-1')");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_entries", Integer.class))
                .isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO audit_processed_events VALUES ('event-1')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repeatedInitializationDoesNotInventProcessingMarksFromAuditFacts() {
        initializeSchema();
        insertFact();

        initializeSchema();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_entries", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_processed_events", Integer.class))
                .isZero();
    }

    private void initializeSchema() {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
    }

    private void insertFact() {
        jdbc.update("""
                INSERT INTO audit_entries
                    (event_id, payment_id, amount, customer, event_type, occurred_at,
                     payment_type, status, reason)
                VALUES ('event-1', 41, 4990, 'Анна', 'PaymentCreated', CURRENT_TIMESTAMP,
                        'CARD', 'NEW', NULL)
                """);
    }
}
