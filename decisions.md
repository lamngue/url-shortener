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

### D-03: Redis cache in front of Postgres (cache-aside)
- **Date:** 2026-10-09 (updated 2026-10-11)
- **Context:** There are about 1,000 redirects for every link created, and a deactivated link must stop redirecting.
- **Options considered:** (1) No cache. (2) Write-through: put every new link into Redis when it is created. (3) Cache-aside: put a link into Redis the first time someone opens it.
- **Decision:** Option 3.
    * **Redirect:** check the code format first. Then look in Redis. If Redis does not have it, read Postgres and save the answer in Redis.
    * **What Redis stores:** the key `link:{code}` holds the long URL. A code that does not exist or is deactivated is stored as `NOT_FOUND`, so repeated requests for it do not hit Postgres. Both unknown and deactivated codes return 404.
    * **How long entries live:** URLs for 5 minutes plus a random extra of up to 10%. `NOT_FOUND` for 10 seconds.
    * **Create:** after saving a new link, delete its Redis key, in case an earlier request left a `NOT_FOUND` there.
    * **Deactivate (design only):** update Postgres first, then delete the Redis key.
    * **If Redis fails:** every Redis call gives up after 100 ms. A failed read counts as "not in Redis" and the app asks Postgres. A failed write is ignored. Redis is not part of the health check, so a Redis outage does not take the app out of the load balancer.
- **Why:**
    * Redis answers from memory, so most redirects never reach Postgres. Postgres stays the source of truth.
    * Write-through would fill Redis with links nobody opens. Cache-aside only stores links people actually use.
    * A popular link costs about one Postgres read every 5 minutes. The random extra stops many entries from expiring at the same moment.
    * `NOT_FOUND` lives only 10 seconds, because a code that does not exist yet will exist once the counter reaches it.
    * Updating Postgres before deleting the Redis key makes it unlikely that an old value is put back. The time limit on entries limits the damage if it happens anyway.
    * `NOT_FOUND` can never be mistaken for a real link, because create only accepts http and https URLs.
- **Trade-off:** After a deactivation, the old URL can keep working for up to about 5 minutes, if the Redis delete is lost (a request in progress puts the old value back, or Redis is down at that moment). While Redis is down, every redirect goes to Postgres, which is slower and could overload it under heavy traffic.
- **Measured:** With Redis stopped, redirects and creates work at normal speed. With Redis hanging, a redirect takes about 210 ms, because the read and the save each wait 100 ms before giving up.
- **Revisit if:** One very popular link overloads Redis (keep a small copy in each app server), many requests miss the same expired popular link at once (let only one of them reload it), fewer requests are answered from Redis, or the 210 ms during a Redis hang becomes a problem.

### D-04: Click events go to Kafka, and a consumer counts them
- **Date:** 2026-10-10 (updated 2026-10-11)
- **Context:** Count clicks per link without slowing down redirects.
- **Options considered:** (1) Add 1 to a counter in Postgres on every redirect. (2) Count in memory and save the totals every few seconds. (3) Send each click to Kafka and count it later.
- **Decision:** Option 3.
    * **What a click event holds:** a unique `eventId`, the short `code`, and `clickedAt` (when the redirect happened). The code is the Kafka key.
    * **Sending:** the redirect sends the event and does not wait for an answer. If Kafka does not take it within about 100 ms, the click is dropped.
    * **Counting:** a consumer reads the events. For each one it saves the `eventId` and adds 1 to the link's count for that day, both in one transaction. If the same event arrives twice, the `eventId` is already saved, so it is not counted again.
    * **Cleanup:** saved event IDs are deleted after 1 day.
- **Why:**
    * Option 1 is slow for popular links: every click writes to the same row.
    * Option 2 loses counts when an app instance crashes, and it can only hold counts for a short time if Postgres is down. Kafka stores events safely on disk, so nothing is lost and the consumer catches up later.
    * The code is known on every redirect (even when the URL comes from Redis), so it works as the key, and all clicks for one link stay in order on one partition.
    * `clickedAt` puts each click on the day it happened, even if the consumer reads it late.
    * Repeated events arrive within minutes, so keeping event IDs for 1 day is enough.
- **Trade-off:** Statistics are a few seconds behind. Clicks are lost while Kafka is unreachable. Counts are approximate.
- **Failure handling:** If a consumer dies, another one takes over from the last saved position. Some events are read again and skipped.
- **Revisit if:** Exact counts are required (then write each click to a Postgres table first and publish it to Kafka from there, the "outbox pattern"), or deleting old event IDs becomes slow (then split the table by day and drop whole days).

### D-05: JWT resource server, login through an OIDC provider (LEFT OUT)
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
- Built: create, and redirect with the Redis cache. Being built before hand-in: click statistics through Kafka (it stays design only if it is not working and explainable by the stop date). Design only: OIDC sign-in and JWT validation (D-05), listing and deactivating links, the load balancer and the second data center. Reason: limited time, and the brief asks for the thinking process rather than a complete solution.

## Known limitations and next steps
-
