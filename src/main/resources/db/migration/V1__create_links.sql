CREATE TABLE links (
    id         BIGINT        PRIMARY KEY,
    code       VARCHAR(7)    NOT NULL UNIQUE,
    long_url   VARCHAR(2048) NOT NULL,
    owner_sub  VARCHAR       NOT NULL,
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX links_owner_sub_idx ON links (owner_sub);

-- One nextval claims a block of INCREMENT BY ids; the app hands them out from memory.
-- MAXVALUE is the start of the last block whose ids all stay below 62^7 (7 base-62 chars).
-- This range is for one data center; another data center would get its own disjoint range.
CREATE SEQUENCE link_id_seq
    AS BIGINT
    INCREMENT BY 1000
    MINVALUE 0
    MAXVALUE 3521614605000
    START WITH 0
    NO CYCLE;
