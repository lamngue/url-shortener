# Decision Log

One entry per meaningful choice. Write it when you make the decision, while the reason is fresh.
Keep each entry short: a few lines is enough.

## Template
---

## Example (delete before sharing)

### D-01: Kafka message key is the order ID
- **Date:** 2026-10-09
- **Context:** Events for one order must be processed in order, while load should spread across partitions.
- **Options considered:** single partition, random key, order ID as key
- **Decision:** Use the order ID as the key.
- **Why:** The same key always goes to the same partition, so each order stays ordered while different orders run in parallel.
- **Trade-off:** One very busy order could make a hot partition. No global ordering across orders.
- **Revisit if:** Partition count changes (keys may map to different partitions).

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
