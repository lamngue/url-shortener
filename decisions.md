# Decision Log

One entry per meaningful choice. Write it when you make the decision, while the reason is fresh.
Keep each entry short: a few lines is enough.

## Template
---

## Decisions

### D-01: Short codes come from a counter, encoded in Base 62
- **Date:** 2026-10-09
- **Context:** Every new link needs a short code that is unique, no hash collisions.
- **Options considered:** Hash the URL and truncate, random string with a collision check, counter encoded in base 62. A counter gives a unique code by construction. A truncated hash needs a collision check and retries, and it gives the same code to the same URL, which breaks per-user links.
- **Decision:** Take the next number from a counter, scramble it with a secret, reversible shuffle, encode it in base 62
- **Why:**  A counter never produce same number twice (kept incrementing), so no collision check and no retry on insert. 7 characters can give about 3.5 trillion codes. 
    * At about 12 creates per second, a plain Postgres sequence would also work fine.
    * Blocks are a cheap optimization. They also fit the two-data-center story, because each data center gets its own range and never needs to coordinate.
    * Keeps `code` stored in DB. Because if the shuffle is modified, the decoder will decode into the wrong row.
- **Trade-off:** The counter is a point of coordination. Each app server takes a block of IDs to avoid querying database every time. The time shuffle hides the order only, not the security.
- **Revisit if:** Several regions write at once (give each region unique ID range), or the write rate outgrow one counter.

### D-02: Redirect with 302
- **Date:** 2026-10-09
- **Context:** A short link must send the browser to the long URL while every click should be consistent with statistics and deactivation
- **Options considered:** Give 302 code in the response header instead of 301
- **Decision:** Response with 302 and a Location header.
- **Why:** A browser caches a 301 and then skips our server on later clicks. Those clicks would not be counted, and a deactivated link would keep working for anyone who had already visited it. A 302 is not cached by default, so every click reaches us. (the service)
- **Trade-off:** Every click hits our server, so we carry the full redirect load (about 11600 per second on average) each each click gets slightly slower. Redis cache would solve this, mapping short code -> long URL. The app now answers from memory instead of from DB.
- **Revisit if:** Statistics and deactivations are no longer needed and redirect load is a problem. Then a 301 would cut load, or a short Cache-Control max-age on the 302 as the middle part.

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
- **Context:** We want to accurately count the number of clicks for a specific link
- **Options considered:** Update a counter in the database on every redirect, Use a event streaming service that update to the database later
- **Decision:** The app publishes a click event to Kafka without waiting, keyed by link ID with a unique eventId (UUID). A consumer group counts clicks. Within the group, each partition goes to one consumer, so one consumer handles all clicks for a link. The consumer inserts the enventId (unique constraint) and increment the counter in one transaction, so a redelivered event is ignored.
- **Why:** Redirects stay fast, and a statistics outage doesn’t break redirects. A counter updated on every redirect would be a hot row for popular links.
- **Trade-off:** The cost is that statistics lag and may double count (at-least-once). A click can be lost if Kafka is unreachable at that moment, so counts are approximate. 
- **Revisit if:** Exact real-time counts are required (use the outbox pattern so no click is lost).
- **Failure handling:** If a consumer dies, Kafka reassigns its partitions to another consumer in the group, which continues from the last committed offset. Some events are redelivered, and the unique event ID prevents duplicates.

### D-05: The app is a JWT resource server, with login through an OIDC provider
- **Date:** 2026-10-10
- **Context:** We need a way to authenticate and manage users
- **Options considered:** Own user table and passwords, JWT to manage authentication - through an OIDC provider.
- **Decision:** Users log in at an OIDC provider (authorization code flow with PKCE, done by the client). The client sends the access token as a Bearer token. The API is a resource server: it checks the signature with the provider's cached public keys (JWKS), plus exp, iss, aud and a pinned algorithm. The `sub` claim identifies the owner, and every link operation checks ownership (401 for an invalid token, 403 for someone else's link). Redirects need no token.
- **Why:** No passwords to store or protect, MFA comes from the provider, and validation is stateless, so app servers scale freely.
- **Trade-off:** If the provider is down, new logins fail, though existing tokens work until they expire. A token cannot be revoked before it expires, so I keep the lifetime short.
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

### D-08: ID blocks from a Postgres sequence
- **Date:** 2026-10-11
- **Context:** Every new link needs a unique counter number (D-01). Asking the database for every number adds a round trip per create, and each data center needs its own range.
- **Options considered:** One `nextval` per link, a counter table with row locking, a sequence with `INCREMENT BY 1000`
- **Decision:** A sequence with `INCREMENT BY 1000`, `NO CYCLE` and a `MAXVALUE` below 62^7. Each app instance takes one value as the start of a block and hands out that number plus the next 999 from memory. Each data center has its own sequence with a non-overlapping `MINVALUE` and `MAXVALUE`.
- **Why:** A sequence never gives the same number to two callers, so blocks never overlap. One database call serves 1,000 links. Numbers left in a block when an instance restarts are lost, which is harmless with about 3.5 trillion codes.
- **Trade-off:** Gaps in the numbers, and numbers that are not in order across instances. Creates still need Postgres to store the link, so this reduces load rather than removing the dependency.
- **Revisit if:** The write rate outgrows one sequence (make blocks bigger first), more regions need their own ranges, or the numbers get close to `MAXVALUE`.
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
