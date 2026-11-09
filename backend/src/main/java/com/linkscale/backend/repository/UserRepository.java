package com.linkscale.backend.repository;

import com.linkscale.backend.domain.UserRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class UserRepository {

    private static final RowMapper<UserRecord> USER_ROW_MAPPER = (rs, rowNum) -> new UserRecord(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getObject("created_at", OffsetDateTime.class)
    );

    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<UserRecord> findByEmail(String email) {
        return jdbcClient.sql("SELECT id, email, password_hash, created_at FROM users WHERE email = :email")
                .param("email", email)
                .query(USER_ROW_MAPPER)
                .optional();
    }

    public Optional<UserRecord> findById(Long id) {
        return jdbcClient.sql("SELECT id, email, password_hash, created_at FROM users WHERE id = :id")
                .param("id", id)
                .query(USER_ROW_MAPPER)
                .optional();
    }

    public Optional<Long> insertUserOnConflictDoNothing(String email, String passwordHash) {
        return jdbcClient.sql("""
                INSERT INTO users (email, password_hash, created_at)
                VALUES (:email, :passwordHash, CURRENT_TIMESTAMP)
                ON CONFLICT (email) DO NOTHING
                RETURNING id
                """)
                .param("email", email)
                .param("passwordHash", passwordHash)
                .query(Long.class)
                .optional();
    }
}

