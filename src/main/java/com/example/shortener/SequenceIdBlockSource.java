package com.example.shortener;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Claims blocks from the Postgres sequence {@code link_id_seq}. One nextval returns the start
 * of a block, and the sequence's INCREMENT BY is the block size.
 */
@Component
public class SequenceIdBlockSource implements IdBlockSource {

    private static final String SEQUENCE = "link_id_seq";

    private final JdbcTemplate jdbc;
    private final long blockSize;

    public SequenceIdBlockSource(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        // Read the block size from the sequence itself, so it can never disagree with INCREMENT BY.
        Long increment = jdbc.queryForObject(
                "SELECT increment_by FROM pg_sequences WHERE schemaname = current_schema() AND sequencename = ?",
                Long.class, SEQUENCE);
        if (increment == null || increment <= 0) {
            throw new IllegalStateException("Sequence " + SEQUENCE + " must have a positive INCREMENT BY");
        }
        this.blockSize = increment;
    }

    @Override
    public IdBlock nextBlock() {
        Long start = jdbc.queryForObject("SELECT nextval('" + SEQUENCE + "')", Long.class);
        return new IdBlock(start, blockSize);
    }
}
