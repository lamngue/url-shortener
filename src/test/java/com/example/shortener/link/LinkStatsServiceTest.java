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
    void testOwnerOfLinksWithClicks() {
        String code = saveLink(true, "alice");
        addClicks(code, "2026-10-10", 5);
        addClicks(code, "2026-10-11", 3);
        // 8 clicks total, 2 days
        LinkStatsService.OwnerCheckResult result = statsService.stats(code, "alice");
        assertThat(result).isInstanceOf(LinkStatsService.Owner.class);
        LinkStatsResponse response = ((LinkStatsService.Owner) result).stats();
        assertThat(response.code()).isEqualTo(code);
        assertThat(response.totalClicks()).isEqualTo(8);
        assertThat(response.days()).containsExactly(
                new LinkStatsResponse.DailyClicks(LocalDate.parse("2026-10-10"), 5),
                new LinkStatsResponse.DailyClicks(LocalDate.parse("2026-10-11"), 3));
    }

    @Test 
    void testNotOwnerOfLink() {
        String code = saveLink(true, "alice");
        addClicks(code, "2026-10-10", 5);
        LinkStatsService.OwnerCheckResult result = statsService.stats(code, "bob");
        assertThat(result).isInstanceOf(LinkStatsService.NotOwner.class);
    }

    @Test 
    void testOwnerOfDeactivatedLink() {
        String code = saveLink(false, "alice");
        addClicks(code, "2026-10-10", 5);
        LinkStatsService.OwnerCheckResult result = statsService.stats(code, "alice");
        assertThat(result).isInstanceOf(LinkStatsService.NotFound.class);
    }

    @Test
    void testOwnerOfUnknownCode() {
        String code = uniqueCode();
        LinkStatsService.OwnerCheckResult result = statsService.stats(code, "alice");
        assertThat(result).isInstanceOf(LinkStatsService.NotFound.class);
    }

    private String saveLink(boolean active, String callerSub) {
        String code = uniqueCode();
        long id = ThreadLocalRandom.current().nextLong(1_000_000_000L, 3_000_000_000_000L);
        Link link = new Link(id, code, "https://example.com/" + code, callerSub);
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
