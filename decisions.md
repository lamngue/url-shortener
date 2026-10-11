# Decision Log

One entry per meaningful choice. Write it when you make the decision, while the reason is fresh.
Keep each entry short: a few lines is enough.

## Template
---

## Decisions

### D-01: Short codes come from a counter, encoded in base 62
- **Date:** 2026-10-09
- **Context:** Every link needs a short code that is unique and short.
- **Options considered:** Truncated hash of the URL, random string with a collision check, counter encoded in base 62.
- **Decision:** Take the next number from a counter, scramble it with a secret reversible shuffle, and encode it in base 62 (7 characters). Store the code in its own column.
- **Why:** A counter never repeats, so there is no collision check and no retry. A truncated hash collides after about 2 million links and gives the same URL the same code, which breaks per-user links. 7 characters give about 3.5 trillion codes. The code is stored, not recomputed, so published links keep working if the shuffle or secret ever changes. At about 12 creates per second a plain Postgres sequence is enough (see D-08).
- **Trade-off:** Every create asks Postgres for the next number. The shuffle only hides the order: K can be recovered from two consecutive codes, so links can be enumerated, and a random guess hits a live link about once in a thousand tries at full capacity. Private targets need access control. Stronger protection needs a keyed non-linear shuffle or longer random codes, plus rate limiting.
- **Revisit if:** More than one region writes at once, the create rate outgrows one sequence (D-08), or enumeration becomes a real risk.


### D-02: Redirect with 302
- **Date:** 2026-10-09
- **Context:** A short link sends the browser to the long URL, every click must be counted, and deactivation must work.
- **Options considered:** 301 or 302.
- **Decision:** Respond with 302 and a Location header.
- **Why:** Browsers cache a 301 and skip our server on later clicks, so those clicks would not be counted and a deactivated link would keep working for people who had already visited it. A 302 is not cached by default, so every click reaches us.
- **Trade-off:** Every click hits our server (about 11,600 per second on average), so redirects must be fast. The Redis cache (D-03) answers from memory instead of Postgres.
- **Revisit if:** Statistics and deactivation are no longer needed and the load is a problem. Then use a 301, or a 302 with a short Cache-Control max-age.

### D-03: Redis cache-aside, with eviction on deactivation (update the DB, then delete the cache entry)
- **Date:** 2026-10-09
- **Context:** Read:write ratio is 1000 to 1 (outnumber), and a deactivated link must stop redirecting.
- **Options considered:** no cache, write-through, cache-aside
- **Decision:** Redis cached-aside, on read: Check Redis, if miss Read from Postgres then fill Redis with a TTL. Implement eviction on deactivated links. Update database first then delete the cache entry.
- **Why:** Redis absorbs the reads and Postgres stays the source of truth. Write-through gonna fill links with nobody clicks. Deleting after a database update stops a concurrent read from putting the old value back to the cache.
- **Trade-off:** Serving stale data (redirected link) from Redis, a short window is possible. Redis might be unvailable, significantly slower read, reads hit Postgres and could overload.
- **Revisit if:** One hot key overloads Redis (add an in-memory copy per app server), or the hit ratio drops.

### D-04: Click events go to Kafka, and a consumer counts them
- **Date:** 2026-10-10
- **Context:** Count clicks per link without slowing down redirects.
- **Options considered:** Update a counter in Postgres on every redirect, or publish events and count them later.
- **Decision:** The app publishes a click event to Kafka without waiting, keyed by link ID, with a unique eventId. A consumer group counts them. In one transaction the consumer inserts the eventId (unique constraint) and increments the daily counter, so a redelivered event is ignored.
- **Why:** Redirects stay fast, and a statistics outage does not break them. A counter updated on every redirect would be a hot row for popular links. Keying by link ID sends all clicks for one link to one consumer.
- **Trade-off:** Statistics lag by a few seconds. Delivery is at-least-once (the eventId prevents double counting), and a click can be lost if Kafka is unreachable, so counts are approximate.
- **Failure handling:** If a consumer dies, Kafka gives its partitions to another consumer, which resumes from the last committed offset. Some events are redelivered and ignored.
- **Revisit if:** Exact real-time counts are required (use the outbox pattern so no click is lost).


### D-05: JWT resource server, login through an OIDC provider
- **Date:** 2026-10-10
- **Context:** Users must be authenticated, and each user may only manage their own links.
- **Options considered:** Own user table and passwords, or JWTs from an OIDC provider.
- **Decision:** Users log in at an OIDC provider (authorization code flow with PKCE, done by the client) and send the access token as a Bearer token. The API validates the signature with the provider's cached public keys (JWKS), plus exp, iss, aud and a pinned algorithm. `sub` identifies the owner. Redirects need no token.
- **Why:** No passwords to store, MFA comes from the provider, and validation is stateless, so app servers scale freely. Ownership is checked on every link operation: 401 for an invalid token, 403 for someone else's link.
- **Trade-off:** If the provider is down, new logins fail, but existing tokens work until they expire. A token cannot be revoked before it expires, so lifetimes are short.
- **Revisit if:** Immediate revocation is needed (add introspection), or the provider's cost or lock-in becomes a problem.

### D-06: Spring Boot 4.1.x
- **Date:** 2026-10-11
- **Context:** The skeleton needs a Spring Boot version, and this is a new service.
- **Options considered:** Boot 3.5.x, Boot 4.1.x
- **Decision:** Use Boot 4.1.x.
- **Why:** Boot 3.5 reached end of life on June 30, 2026, so a new service should start on a supported line.
- **Trade-off:** Renamed starters, fewer tutorials and Stack Overflow answers, and Jackson 3, all of which I need to learn.
- **Revisit if:** Boot 4 differences cost more than a few hours before the deadline. Then I fall back to 3.5 and say in the presentation that it is out of support.

### D-07: Database schema through Flyway migrations
- **Date:** 2026-10-11
- **Context:** Link mappings must never be lost, so the schema should be explicit and reproducible.
- **Options considered:** Hibernate generating tables (`ddl-auto`), a plain SQL script run by Compose, Flyway migrations
- **Decision:** Use Flyway (`V1__create_links.sql`) and set Hibernate to `validate`.
- **Why:** Versioned SQL files make the schema visible, including the unique constraint on `code`, and they run the same way in every environment. With `validate`, the app fails at startup if the entities and tables differ.
- **Trade-off:** Two extra dependencies (the Flyway starter and the Postgres module). A migration that has already run must never be edited, so every change is a new file. More typing than auto-generation.
- **Revisit if:** The schema changes so often that migration files slow me down, or I need rollback tooling. In that case I would keep auto-generation for local experiments only.

### D-08: Plain Postgres sequence per data center for IDs
- **Date:** 2026-10-11
- **Context:** Every link needs a unique number before it becomes a code.
- **Options considered:** (1) One `nextval` per create from a plain sequence. (2) Blocks of 1,000 numbers per app instance, handed out from memory.
- **Decision:** Option 1. Sequence `link_id_seq`: MINVALUE 0, START 0, INCREMENT BY 1, NO CYCLE, MAXVALUE 3,521,614,606,207 (62^7 - 1, the largest number the generator accepts). Each data center gets its own sequence with its own non-overlapping range. The ID source sits behind a small interface.
- **Why:** At about 12 creates per second, one `nextval` per create is easy for Postgres, and a plain sequence is simple to explain. The per-data-center ranges prevent collisions. Blocks would add a lock, lost numbers on restart and more tests, for a rate that does not need them.
- **Trade-off:** One extra database call per create. Gaps appear when an insert fails, because sequences are not rolled back, which does not matter with 3.5 trillion numbers. Ranges are fixed, so adding a data center needs planning.
- **Revisit if:** The `nextval` call shows up in metrics (then take blocks from the sequence behind the same interface), or there are more than two data centers.

---

## Questions for the team (ask before building if unclear)
-

## Assumptions I made (state these in the presentation)
- 1M new links per day, 1,000 clicks per link (to confirm)
- Sign-in through an OIDC identity provider (to confirm)
- Out of scope: custom aliases and expiry
- Statistics may lag by a few seconds


## Things I left out on purpose (scope)
- Custom aliases, expiry

## Known limitations and next steps
-
