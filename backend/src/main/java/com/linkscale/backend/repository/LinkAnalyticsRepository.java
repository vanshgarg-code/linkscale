package com.linkscale.backend.repository;

import com.linkscale.backend.dto.LinkAnalyticsResponse.DailyClicks;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class LinkAnalyticsRepository {

    private final JdbcClient jdbcClient;

    public LinkAnalyticsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<DailyClicks> findDailyClicks(Long linkId, Long ownerId, LocalDate startDate, int days) {
        return jdbcClient.sql("""
                WITH dates AS (
                    SELECT CAST(:startDate AS date) + offsets.day_offset AS click_date
                    FROM generate_series(0, :days - 1) AS offsets(day_offset)
                )
                SELECT dates.click_date, COUNT(events.id) AS clicks
                FROM links
                CROSS JOIN dates
                LEFT JOIN click_events AS events
                    ON events.link_id = links.id
                    AND events.clicked_at >= (CAST(dates.click_date AS timestamp) AT TIME ZONE 'UTC')
                    AND events.clicked_at < (CAST(dates.click_date + 1 AS timestamp) AT TIME ZONE 'UTC')
                WHERE links.id = :linkId AND links.owner_id = :ownerId AND links.deleted_at IS NULL
                GROUP BY dates.click_date
                ORDER BY dates.click_date
                """)
                .param("linkId", linkId)
                .param("ownerId", ownerId)
                .param("startDate", startDate)
                .param("days", days)
                .query((rs, rowNum) -> new DailyClicks(rs.getObject("click_date", LocalDate.class), rs.getLong("clicks")))
                .list();
    }
}
