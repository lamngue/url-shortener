CREATE TABLE links (
    id         BIGINT        PRIMARY KEY,
    code       VARCHAR(7)    NOT NULL UNIQUE,
    long_url   VARCHAR(2048) NOT NULL,
    owner_sub  VARCHAR       NOT NULL,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX links_owner_sub_idx ON links (owner_sub);

-- One nextval per created link.
-- MAXVALUE is 62^7 - 1, the largest id that fits in 7 base-62 characters, so the sequence
-- runs out exactly where the short code generator's valid range ends.
-- This range is for one data center; another data center would get its own disjoint range.
CREATE SEQUENCE link_id_seq
    AS BIGINT
    INCREMENT BY 1
    MINVALUE 0
    MAXVALUE 3521614606207
    START WITH 0
    NO CYCLE;
