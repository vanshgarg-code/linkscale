package com.linkscale.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DatabaseMigrationIT {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcClient.sql("DELETE FROM links").update();
            jdbcClient.sql("DELETE FROM users").update();
        });
    }

    @Test
    void shouldAssertSuccessfulV1InFlywayHistory() {
        Boolean success = jdbcClient.sql("SELECT success FROM flyway_schema_history WHERE version = '1'")
                .query(Boolean.class)
                .single();

        assertThat(success).isTrue();
    }

    @Test
    void shouldInsertAndRetrieveValidLink() {
        Long ownerId = transactionTemplate.execute(status ->
                jdbcClient.sql(
                        "INSERT INTO users (email, password_hash) VALUES ('test@linkscale.io', 'hashed_pw') RETURNING id"
                ).query(Long.class).single()
        );

        transactionTemplate.executeWithoutResult(status ->
                jdbcClient.sql(
                        "INSERT INTO links (code, destination_url, owner_id, title, active) VALUES ('quick1', 'https://linkscale.io/home', ?, 'LinkScale Home', true)"
                ).param(ownerId).update()
        );

        String destinationUrl = jdbcClient.sql("SELECT destination_url FROM links WHERE code = 'quick1'")
                .query(String.class)
                .single();

        assertThat(destinationUrl).isEqualTo("https://linkscale.io/home");
    }

    @Test
    void shouldRejectDuplicateCodeWithSqlState23505() {
        Long ownerId = transactionTemplate.execute(status ->
                jdbcClient.sql(
                        "INSERT INTO users (email, password_hash) VALUES ('dup_owner@linkscale.io', 'hashed_pw') RETURNING id"
                ).query(Long.class).single()
        );

        // First insert succeeds
        transactionTemplate.executeWithoutResult(status ->
                jdbcClient.sql(
                        "INSERT INTO links (code, destination_url, owner_id) VALUES ('dupcode', 'https://example.com/one', ?)"
                ).param(ownerId).update()
        );

        // Second insert with identical code in an independent transaction must fail with SQLSTATE 23505
        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status ->
                        jdbcClient.sql(
                                "INSERT INTO links (code, destination_url, owner_id) VALUES ('dupcode', 'https://example.com/two', ?)"
                        ).param(ownerId).update()
                )
        ).satisfies(ex -> {
            assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(extractSqlState(ex)).isEqualTo("23505");
        });
    }

    @Test
    void shouldRejectInvalidOwnerWithSqlState23503() {
        Long nonExistentOwnerId = 999999L;

        // Inserting link with non-existent owner must fail with SQLSTATE 23503
        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status ->
                        jdbcClient.sql(
                                "INSERT INTO links (code, destination_url, owner_id) VALUES ('orphan1', 'https://example.com/orphan', ?)"
                        ).param(nonExistentOwnerId).update()
                )
        ).satisfies(ex -> {
            assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(extractSqlState(ex)).isEqualTo("23503");
        });
    }

    private String extractSqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlEx) {
                return sqlEx.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }
}

