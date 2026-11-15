package com.linkscale.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record LinkAnalyticsResponse(
        @JsonProperty("link_id") Long linkId,
        @JsonProperty("total_clicks") long totalClicks,
        String timezone,
        @JsonProperty("daily_clicks") List<DailyClicks> dailyClicks
) {
    public record DailyClicks(@JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate date, long clicks) {}
}
