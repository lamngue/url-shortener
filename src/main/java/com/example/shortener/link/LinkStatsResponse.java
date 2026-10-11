package com.example.shortener.link;

import java.time.LocalDate;
import java.util.List;

/** Click statistics for one link: the total and one entry per day with clicks, oldest first. */
public record LinkStatsResponse(String code, long totalClicks, List<DailyClicks> days) {

    public record DailyClicks(LocalDate day, long clicks) {
    }
}
