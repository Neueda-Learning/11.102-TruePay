package org.example.truepay.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

@Component
public class LegacyPaymentSchemaInitializer {
    private static final Logger log = LoggerFactory.getLogger(LegacyPaymentSchemaInitializer.class);

    public LegacyPaymentSchemaInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        if (!isMySql(dataSource)) {
            return;
        }
        migrate(jdbcTemplate);
    }

    private boolean isMySql(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.getDatabaseProductName() != null
                    && metaData.getDatabaseProductName().toLowerCase().contains("mysql");
        } catch (Exception ex) {
            log.warn("Skipping legacy payment schema initialization: {}", ex.getMessage());
            return false;
        }
    }

    private void migrate(JdbcTemplate jdbcTemplate) {
        log.info("Applying legacy payment schema compatibility updates");

        jdbcTemplate.execute("""
                alter table payments
                modify column method enum('UPI','BANK_TRANSFER','BANK') not null
                """);

        jdbcTemplate.execute("""
                alter table payments
                modify column status enum('PENDING','SUCCESS','FAILED','CANCELLED','COMPLETED','CREATED','SENT','VALIDATED') not null
                """);

        jdbcTemplate.execute("""
                alter table payment_status_history
                modify column status enum('PENDING','SUCCESS','FAILED','CANCELLED','COMPLETED','CREATED','SENT','VALIDATED') not null
                """);

        jdbcTemplate.update("update payments set status = 'SUCCESS' where status = 'COMPLETED'");
        jdbcTemplate.update("update payments set status = 'PENDING' where status in ('CREATED', 'SENT', 'VALIDATED')");
        jdbcTemplate.update("update payment_status_history set status = 'SUCCESS' where status = 'COMPLETED'");
        jdbcTemplate.update("update payment_status_history set status = 'PENDING' where status in ('CREATED', 'SENT', 'VALIDATED')");

        jdbcTemplate.update("""
                update payments
                set receiver_type = case
                    when destination_upi_id like '%@mobile' then 'MOBILE_NUMBER'
                    else 'UPI_ID'
                end
                where method = 'UPI'
                  and destination_upi_id is not null
                  and trim(destination_upi_id) <> ''
                  and receiver_type = 'BANK_ACCOUNT'
                """);

        jdbcTemplate.update("""
                update payments
                set transaction_id = concat('LEGACY', upper(substr(replace(uuid(), '-', ''), 1, 26)))
                where transaction_id is null or trim(transaction_id) = ''
                """);

        jdbcTemplate.update("""
                update payments
                set idempotency_key = concat('legacy-', lower(hex(id)))
                where idempotency_key is null or trim(idempotency_key) = ''
                """);

        jdbcTemplate.execute("""
                alter table payments
                modify column status enum('PENDING','SUCCESS','FAILED','CANCELLED') not null
                """);

        jdbcTemplate.execute("""
                alter table payment_status_history
                modify column status enum('PENDING','SUCCESS','FAILED','CANCELLED') not null
                """);
    }
}

