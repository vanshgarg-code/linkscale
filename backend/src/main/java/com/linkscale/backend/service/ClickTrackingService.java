package com.linkscale.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ClickTrackingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClickTrackingService.class);

    private final JdbcClient jdbcClient;

    public ClickTrackingService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void recordClick(Long linkId) {
        try {
            jdbcClient.sql("INSERT INTO click_events (link_id) VALUES (:linkId)")
                    .param("linkId", linkId)
                    .update();
        } catch (DataAccessException exception) {
            LOGGER.warn("Could not record click for link {} ({})", linkId, exception.getClass().getSimpleName());
        }
    }
}
