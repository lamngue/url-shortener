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

    public sealed interface OwnerCheckResult permits Owner, NotOwner, NotFound {
    }

    public record Owner(LinkStatsResponse stats) implements OwnerCheckResult {
    }

    public record NotOwner() implements OwnerCheckResult {
    }

    public record NotFound() implements OwnerCheckResult {
    }

    public LinkStatsService(LinkRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    /** Empty for an invalid, unknown or deactivated code (the caller answers 404), not an owner (403) */
    public OwnerCheckResult stats(String code, String callerSub) {
        if (!ShortCodeGenerator.isValidCode(code)) {
            return new NotFound();
        }
        Optional<Link> found = repository.findByCode(code);
        if (found.isEmpty()) {
            return new NotFound();
        }
        boolean isOwner = found.filter(link -> link.getOwnerSub().equals(callerSub)).isPresent();
        // ownership check
        if (!isOwner) {
            return new NotOwner();
        }
        boolean active = found.get().isActive();
        if (!active) {
            return new NotFound();
        }

        List<LinkStatsResponse.DailyClicks> days = jdbc.query(
                "SELECT day, num_clicks FROM click_stats WHERE code = ? ORDER BY day",
                (rs, row) -> new LinkStatsResponse.DailyClicks(rs.getObject("day", LocalDate.class), rs.getLong("num_clicks")),
                code);
        long total = days.stream().mapToLong(LinkStatsResponse.DailyClicks::clicks).sum();
        return new Owner(new LinkStatsResponse(code, total, days));
    }
}
