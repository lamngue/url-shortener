package com.example.shortener.link;

import com.example.shortener.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Reading click_stats against a real Postgres (Testcontainers). Each test uses its own link. */
@IntegrationTest
class LinkStatsServiceTest {

    @Autowired
    private LinkStatsService statsService;

    @Autowired
    private LinkRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void returnsTotalAndDaysOldestFirst() {
        String code = saveLink(true);
        addClicks(code, "2026-10-12", 2);
        addClicks(code, "2026-10-11", 3);

        LinkStatsResponse stats = statsService.stats(code).orElseThrow();

        assertThat(stats.totalClicks()).isEqualTo(5);
        assertThat(stats.days()).containsExactly(
                new LinkStatsResponse.DailyClicks(LocalDate.parse("2026-10-11"), 3),
                new LinkStatsResponse.DailyClicks(LocalDate.parse("2026-10-12"), 2));
    }

    @Test
    void linkWithoutClicksHasZeroTotal() {
        String code = saveLink(true);

        LinkStatsResponse stats = statsService.stats(code).orElseThrow();

        assertThat(stats.totalClicks()).isZero();
        assertThat(stats.days()).isEmpty();
    }

    @Test
    void deactivatedLinkHasNoStats() {
        String code = saveLink(false);
        addClicks(code, "2026-10-11", 3);

        assertThat(statsService.stats(code)).isEmpty();
    }

    @Test
    void unknownCodeHasNoStats() {
        assertThat(statsService.stats(uniqueCode())).isEmpty();
    }

    private String saveLink(boolean active) {
        String code = uniqueCode();
        long id = ThreadLocalRandom.current().nextLong(1_000_000_000L, 3_000_000_000_000L);
        Link link = new Link(id, code, "https://example.com/" + code, "anonymous");
        if (!active) {
            link.deactivate();
        }
        repository.save(link);
        return code;
    }

    private void addClicks(String code, String day, long clicks) {
        jdbc.update("INSERT INTO click_stats (code, day, num_clicks) VALUES (?, ?, ?)", code, LocalDate.parse(day), clicks);
    }

    // 7 characters, like a real code, and different for every test.
    private static String uniqueCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 7);
    }
}
