package com.example.shortener.link;

import com.example.shortener.shortcode.ShortCodeGenerator;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Reads click statistics. The recorder package writes click_stats (from Kafka events); this
 * class only reads it. Counts lag a few seconds behind the redirects (D-04).
 */
@Service
public class LinkStatsService {

    private final LinkRepository repository;
    private final JdbcTemplate jdbc;

    public LinkStatsService(LinkRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    /** Empty for an invalid, unknown or deactivated code (the caller answers 404). */
    public Optional<LinkStatsResponse> stats(String code) {
        if (!ShortCodeGenerator.isValidCode(code)) {
            return Optional.empty();
        }
        boolean active = repository.findByCode(code).map(Link::isActive).orElse(false);
        if (!active) {
            return Optional.empty();
        }
        List<LinkStatsResponse.DailyClicks> days = jdbc.query(
                "SELECT day, num_clicks FROM click_stats WHERE code = ? ORDER BY day",
                (rs, row) -> new LinkStatsResponse.DailyClicks(rs.getObject("day", LocalDate.class), rs.getLong("num_clicks")),
                code);
        long total = days.stream().mapToLong(LinkStatsResponse.DailyClicks::clicks).sum();
        return Optional.of(new LinkStatsResponse(code, total, days));
    }
}
