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

        // Drop orphaned legacy columns from payments table if they exist
        dropColumnIfExists(jdbcTemplate, "payments", "payment_id");
        dropColumnIfExists(jdbcTemplate, "payments", "source_account");
        dropColumnIfExists(jdbcTemplate, "payments", "destination_ifsc_code");
        dropColumnIfExists(jdbcTemplate, "payments", "receiver_account");
        dropColumnIfExists(jdbcTemplate, "payments", "receiver_ifsc");

        // Drop orphaned legacy columns from payment_status_history
        dropColumnIfExists(jdbcTemplate, "payment_status_history", "new_status");
        dropColumnIfExists(jdbcTemplate, "payment_status_history", "old_status");
        dropColumnIfExists(jdbcTemplate, "payment_status_history", "changed_by");

        // Drop orphaned legacy columns from fraud_alerts
        dropColumnIfExists(jdbcTemplate, "fraud_alerts", "alert_type");
        dropColumnIfExists(jdbcTemplate, "fraud_alerts", "severity");

        ensureBinaryPaymentForeignKey(jdbcTemplate, "payment_status_history", "fk_status_history_payment");
        ensureBinaryPaymentForeignKey(jdbcTemplate, "fraud_alerts", "fk_fraud_alerts_payment");

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

    private void dropColumnIfExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = database() and table_name = ? and column_name = ?
                """, Integer.class, tableName, columnName);
        if (count != null && count > 0) {
            log.info("Dropping legacy column {}.{}", tableName, columnName);
            jdbcTemplate.execute("alter table `" + tableName + "` drop column `" + columnName + "`");
        }
    }

    private void ensureBinaryPaymentForeignKey(JdbcTemplate jdbcTemplate, String tableName, String expectedConstraintName) {
        Integer paymentIdColumnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = database()
                  and table_name = ?
                  and column_name = 'payment_id'
                """, Integer.class, tableName);

        if (paymentIdColumnCount == null || paymentIdColumnCount == 0) {
            return;
        }

        String existingFkName = jdbcTemplate.query("""
                select constraint_name
                from information_schema.key_column_usage
                where table_schema = database()
                  and table_name = ?
                  and column_name = 'payment_id'
                  and referenced_table_name = 'payments'
                limit 1
                """, rs -> rs.next() ? rs.getString(1) : null, tableName);

        if (existingFkName != null && !existingFkName.isBlank()) {
            jdbcTemplate.execute("alter table `" + tableName + "` drop foreign key `" + existingFkName + "`");
        }

        jdbcTemplate.execute("alter table `" + tableName + "` modify column payment_id BINARY(16) not null");

        Integer fkCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.key_column_usage
                where table_schema = database()
                  and table_name = ?
                  and column_name = 'payment_id'
                  and referenced_table_name = 'payments'
                """, Integer.class, tableName);

        if (fkCount == null || fkCount == 0) {
            jdbcTemplate.execute("alter table `" + tableName + "` add constraint `" + expectedConstraintName
                    + "` foreign key (payment_id) references payments(id)");
        }
    }
}

