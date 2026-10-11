package com.example.shortener.shortcode;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Takes one id per call from the Postgres sequence {@code link_id_seq}.
 * The database guarantees that concurrent callers never get the same number.
 */
@Component
public class SequenceIdSource implements IdSource {

    private final JdbcTemplate jdbc;

    public SequenceIdSource(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nextId() {
        return jdbc.queryForObject("SELECT nextval('link_id_seq')", Long.class);
    }
}
