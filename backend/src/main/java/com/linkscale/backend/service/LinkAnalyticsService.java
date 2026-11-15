package com.linkscale.backend.service;

import com.linkscale.backend.dto.LinkAnalyticsResponse;
import com.linkscale.backend.dto.LinkAnalyticsResponse.DailyClicks;
import com.linkscale.backend.exception.LinkNotFoundException;
import com.linkscale.backend.repository.LinkAnalyticsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class LinkAnalyticsService {

    private final LinkAnalyticsRepository repository;

    public LinkAnalyticsService(LinkAnalyticsRepository repository) {
        this.repository = repository;
    }

    public LinkAnalyticsResponse getAnalytics(Long linkId, Long ownerId, String requestedDays) {
        int days;
        try {
            days = Integer.parseInt(requestedDays);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("days must be an integer between 1 and 90");
        }
        if (days < 1 || days > 90) {
            throw new IllegalArgumentException("days must be an integer between 1 and 90");
        }

        LocalDate startDate = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L);
        List<DailyClicks> dailyClicks = repository.findDailyClicks(linkId, ownerId, startDate, days);
        if (dailyClicks.isEmpty()) {
            throw new LinkNotFoundException("Link not found: " + linkId);
        }

        long totalClicks = dailyClicks.stream().mapToLong(DailyClicks::clicks).sum();
        return new LinkAnalyticsResponse(linkId, totalClicks, "UTC", dailyClicks);
    }
}
