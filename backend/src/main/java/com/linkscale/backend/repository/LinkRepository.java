package com.linkscale.backend.repository;

import com.linkscale.backend.domain.LinkRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class LinkRepository {

    private static final RowMapper<LinkRecord> LINK_ROW_MAPPER = (rs, rowNum) -> new LinkRecord(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("destination_url"),
            rs.getLong("owner_id"),
            rs.getString("title"),
            rs.getObject("expires_at", OffsetDateTime.class),
            rs.getBoolean("active"),
            rs.getObject("deleted_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    private final JdbcClient jdbcClient;

    public LinkRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<LinkRecord> insertOnConflictDoNothing(String code,
                                                          String destinationUrl,
                                                          Long ownerId,
                                                          String title,
                                                          OffsetDateTime expiresAt) {
        return jdbcClient.sql("""
                INSERT INTO links (code, destination_url, owner_id, title, expires_at, active, created_at, updated_at)
                VALUES (:code, :destinationUrl, :ownerId, :title, :expiresAt, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (code) DO NOTHING
                RETURNING id, code, destination_url, owner_id, title, expires_at, active, deleted_at, created_at, updated_at
                """)
                .param("code", code)
                .param("destinationUrl", destinationUrl)
                .param("ownerId", ownerId)
                .param("title", title)
                .param("expiresAt", expiresAt)
                .query(LINK_ROW_MAPPER)
                .optional();
    }

    public Optional<LinkRecord> findByCode(String code) {
        return jdbcClient.sql("""
                SELECT id, code, destination_url, owner_id, title, expires_at, active, deleted_at, created_at, updated_at
                FROM links
                WHERE code = :code
                """)
                .param("code", code)
                .query(LINK_ROW_MAPPER)
                .optional();
    }
}

