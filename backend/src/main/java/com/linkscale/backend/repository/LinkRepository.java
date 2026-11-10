package com.linkscale.backend.repository;

import com.linkscale.backend.domain.LinkRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public Optional<LinkRecord> findByIdAndOwner(Long id, Long ownerId) {
        return jdbcClient.sql("""
                SELECT id, code, destination_url, owner_id, title, expires_at, active, deleted_at, created_at, updated_at
                FROM links
                WHERE id = :id AND owner_id = :ownerId AND deleted_at IS NULL
                """)
                .param("id", id)
                .param("ownerId", ownerId)
                .query(LINK_ROW_MAPPER)
                .optional();
    }

    public List<LinkRecord> findByOwnerWithFilters(Long ownerId, String search, String status, int page, int size) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, code, destination_url, owner_id, title, expires_at, active, deleted_at, created_at, updated_at
                FROM links
                """);
        Map<String, Object> params = new HashMap<>();
        appendFilters(sql, params, ownerId, search, status);

        int offset = page * size;
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset");
        params.put("limit", size);
        params.put("offset", offset);

        var spec = jdbcClient.sql(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }

        return spec.query(LINK_ROW_MAPPER).list();
    }

    public long countByOwnerWithFilters(Long ownerId, String search, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM links");
        Map<String, Object> params = new HashMap<>();
        appendFilters(sql, params, ownerId, search, status);

        var spec = jdbcClient.sql(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }

        Long count = spec.query(Long.class).single();
        return count != null ? count : 0L;
    }

    public Optional<LinkRecord> updateLink(Long id, Long ownerId,
                                          boolean titleSet, String title,
                                          boolean expiresAtSet, OffsetDateTime expiresAt,
                                          boolean activeSet, Boolean active) {
        StringBuilder sql = new StringBuilder("UPDATE links SET updated_at = CURRENT_TIMESTAMP");
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("ownerId", ownerId);

        if (titleSet) {
            sql.append(", title = :title");
            params.put("title", title);
        }
        if (expiresAtSet) {
            sql.append(", expires_at = :expiresAt");
            params.put("expiresAt", expiresAt);
        }
        if (activeSet && active != null) {
            sql.append(", active = :active");
            params.put("active", active);
        }

        sql.append("""
                 WHERE id = :id AND owner_id = :ownerId AND deleted_at IS NULL
                 RETURNING id, code, destination_url, owner_id, title, expires_at, active, deleted_at, created_at, updated_at
                """);

        var spec = jdbcClient.sql(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            spec = spec.param(entry.getKey(), entry.getValue());
        }

        return spec.query(LINK_ROW_MAPPER).optional();
    }

    public boolean softDelete(Long id, Long ownerId) {
        int rows = jdbcClient.sql("""
                UPDATE links
                SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND owner_id = :ownerId AND deleted_at IS NULL
                """)
                .param("id", id)
                .param("ownerId", ownerId)
                .update();

        return rows > 0;
    }

    private void appendFilters(StringBuilder sql, Map<String, Object> params, Long ownerId, String search, String status) {
        sql.append(" WHERE owner_id = :ownerId AND deleted_at IS NULL");
        params.put("ownerId", ownerId);

        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(destination_url) LIKE :searchPattern OR LOWER(code) LIKE :searchPattern)");
            params.put("searchPattern", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }

        if (status != null && !status.isBlank()) {
            String normalizedStatus = status.trim().toLowerCase(Locale.ROOT);
            switch (normalizedStatus) {
                case "active" -> sql.append(" AND active = TRUE AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)");
                case "inactive" -> sql.append(" AND active = FALSE");
                case "expired" -> sql.append(" AND expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP");
                case "all" -> { /* No extra status condition */ }
                default -> throw new IllegalArgumentException("Invalid status filter: '" + status + "'. Allowed values: all, active, inactive, expired");
            }
        }
    }
}
