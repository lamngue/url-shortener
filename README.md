# URL Shortening Service

A Java and Spring service that turns long URLs into short links, redirects visitors with low latency, and lets signed-in users manage their own links and see click statistics. It is designed to stay available through a data center outage and to absorb read spikes.

## Assumptions and scope

- Assumed scale: 1 million new links per day (about 12 per second) and 1,000 clicks per link, so about 1 billion redirects per day (about 11,600 per second on average, about 35,000 at peak). Both numbers are assumptions to confirm.
- What matters most: link mappings are never lost, redirects stay fast during spikes, and the service survives the loss of one data center.
- In scope: create a short link, redirect, list and deactivate my own links, click statistics per link, sign-in through an OIDC identity provider.
- Out of scope (on purpose): custom aliases, link expiry, abuse detection, multi-region active-active writes beyond the design below.
- Statistics may lag by a few seconds and are approximate.

## Architecture

```
Client -> Load balancer -> Spring Boot app (stateless, many instances)
                              |-> Redis (code -> long URL, cache-aside)
                              |-> Postgres (links, source of truth)
                              '-> Kafka (click events, async) -> stats consumer -> Postgres
```

- Redirect path: check the code format, check Redis, on a miss read Postgres and fill Redis (the URL, or a short-lived `NOT_FOUND` marker), answer with a 302 or 404, publish a click event without waiting.
- Create path: validate the JWT (design only), take the next ID from the Postgres sequence, scramble it, encode it in base 62, save the row, delete the Redis key for that code, return the short URL.
- Availability: two data centers, each with the same setup, behind one load balancer. Each data center has its own ID range, so codes never collide.

Key decisions are explained in [decisions.md](decisions.md).

## What is implemented and what is design only

| Area | Status |
|------|--------|
| Create and redirect (Postgres, Redis cache-aside, Redis fallback) | Implemented, run end to end with Docker Compose |
| Deactivate links | Implemented: `DELETE /api/links/{code}`, Postgres first, then the Redis key |
| List my links | Design only |
| Click statistics through Kafka (D-04) | Implemented: each successful redirect sends a click event, and a consumer counts clicks per code per day in Postgres. `GET /api/links/{code}/stats` shows the total and the clicks per day |
| JWT validation, ownership checks | Design only (D-05); links are owned by a placeholder `anonymous` |
| Two data centers, replicas, load balancer | Design only |

## Tech stack

- Language and version: Java 21
- Framework: Spring Boot 4.1 (Web MVC, Validation, Data JPA with Flyway, Data Redis, Actuator). Spring Kafka, springdoc (OpenAPI spec and Swagger UI). Design only: Spring Security OAuth2 resource server
- Database: PostgreSQL
- Cache: Redis
- Messaging: Kafka
- Build tool: Maven (wrapper included, `./mvnw`)
- Containers: Docker, Docker Compose

## How to run

Prerequisites: Docker with Docker Compose, and Java 21 (Maven comes with the wrapper). Compose starts Postgres, Redis and Kafka; the app runs on your machine.

```bash
# 1. Clone
git clone <repo-url>
cd <repo-folder>

# 2. Start Postgres, Redis and Kafka
docker compose up -d

# 3. Start the app. SHORT_CODE_K is the secret multiplier for the code shuffle
#    (odd, not a multiple of 31, below 62^7). The app refuses to start without it.
SHORT_CODE_K=<your-secret-k> ./mvnw spring-boot:run

# 4. Try it (no token needed yet: authentication is design only)
curl -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{ "url": "https://example.com/some/long/path" }'

curl -i http://localhost:8080/<code>

# 5. See the click statistics (the consumer adds clicks a moment after each redirect)
curl http://localhost:8080/api/links/<code>/stats

# 6. Deactivate the link: the next redirect returns 404
curl -i -X DELETE http://localhost:8080/api/links/<code>
```

The API can also be tried in the browser with Swagger UI at http://localhost:8080/swagger-ui/index.html (OpenAPI spec at `/v3/api-docs`). Set `SWAGGER_ENABLED=false` to turn both off where the API docs should not be public. The redirect itself is easiest to see with `curl -i`, because a browser follows it.

Stop with `docker compose down` (add `-v` to remove data).

## API

| Method | Path | Description | Success | Errors |
|--------|------|-------------|---------|--------|
| POST | /api/links | Create a short link for a long URL | 201 with code and short URL | 400 invalid URL, 401 |
| GET | /{code} | Redirect to the long URL (public) | 302 with Location | 404 unknown or deactivated |
| GET | /api/links | List my links (cursor pagination, design only) | 200 | 401 |
| DELETE | /api/links/{code} | Deactivate my link (soft delete). 204 also if it was already inactive | 204 | 401, 403 not the owner, 404 "Code not found" |
| GET | /api/links/{code}/stats | Click statistics for my link: total and clicks per day | 200 | 401, 403, 404 "Code not found" (also for a deactivated link) |

Authentication (design only, D-05): Bearer access token (JWT) from an OIDC provider. The `/{code}` redirect needs no token. Today create, redirect, deactivate and stats are implemented without authentication, so 401 and 403 are never returned yet, and anyone who knows a code can deactivate it or see its statistics. Listing is design only.

## Data model

- `links` (implemented, `V1__create_links.sql`): id (from the counter), code (unique index), long_url, owner_sub (the token's `sub`; `anonymous` until authentication exists), active, created_at.
- `click_stats` (implemented, `V2__create_click_stats.sql`): code, day, num_clicks, with primary key (code, day). One row per link per day; the day is the UTC date of the event's `clickedAt`.
- `processed_events` (implemented, `V2`): event_id (primary key), processed_at. Used to ignore duplicate click events; an hourly job deletes rows older than 1 day.

## Reliability

- Delivery guarantee: at-least-once from Kafka, with an idempotent consumer.
- Duplicate handling: each click event has a unique event ID. The consumer inserts it (unique constraint) and updates the counter in one transaction, so a redelivered event is ignored. Event IDs are kept for 1 day.
- Retries: if counting fails (for example Postgres is down), the consumer retries the same event every second until it succeeds, so no click is lost. Messages that can never succeed (not valid JSON, or bad data) are logged and skipped, so they do not block their partition.
- If Redis is down: reads fall back to Postgres, slower under load. Redis calls time out after 100 ms, and Redis is not part of the health check, so a cache outage does not remove instances from the load balancer.
- If Kafka is down: redirects still work. The producer waits at most about 100 ms, then drops the click, so some clicks are not counted (measured: redirects took about 107 to 137 ms while Kafka was stopped).
- If Postgres is down for the consumer: clicks wait in Kafka and are counted when Postgres is back (measured: 4 clicks during a short outage were all counted afterwards).
- If the identity provider is down: new sign-ins fail, existing tokens work until they expire. Redirects are unaffected.
- Link mappings: stored in Postgres with a replica and backups. Redis is only a performance layer.
- Deactivation: update Postgres first, then delete the Redis entry (measured: the next redirect returns 404 at once). This makes it unlikely that an old value is put back; if it happens, the cache time limit ends it within about 5 minutes.

## Security

- Authentication vs authorization: the token proves who the caller is (401 if invalid). Ownership is checked on every link operation (403 for someone else's link).
- Token validation: signature (provider's public keys), exp, iss, aud, and a pinned algorithm. Never accept `alg: none`.
- Input: only http and https URLs are accepted when creating a link.
- In transit: TLS at the load balancer. At rest: encrypted database volumes.
- Secrets: read from environment variables, never committed to Git.

## Testing

```bash
./mvnw test
```

Docker must be running: the integration tests start Postgres and Redis containers with Testcontainers.

What exists:
- Unit tests for the code generator (base 62, shuffle, boundaries, uniqueness, K validation, code format).
- Unit and web-layer tests for the cache, redirect, create, deactivate, stats and configuration (mocked Postgres and Redis).
- `contextLoads` against real Postgres and Redis containers: proves Flyway and Hibernate `validate` agree.
- Click statistics: the Kafka sender with a mocked Kafka (right key, never fails a redirect); the counting and the cleanup against a real Postgres (same-day totals, redelivered events counted once, UTC day boundaries, old event IDs deleted), and the stats query against a real Postgres (total, days in order, deactivated links hidden).
- Manual end-to-end runs with Docker Compose: Redis stopped and Redis hanging; Kafka stopped; Postgres stopped while clicks were being counted.

Not covered yet: automated end-to-end tests with a real Kafka, and a Postgres outage longer than the 30-second connection wait (where the consumer's retry takes over). A CI pipeline (build and tests on every push) is a next step.

## Known limitations and next steps

- Statistics are approximate and lag by a few seconds. Exact counts would need the outbox pattern.
- The stats consumer runs inside the app instances. At larger scale it could be its own deployment.
- No custom aliases, expiry or abuse detection yet.
- Rate limiting on link creation is a next step.
- Multi-region active-active writes would need more than disjoint ID ranges.

## Use of AI tools

AI tools were used for: Write Java/Spring code, built in small steps from my designs, README and decision-log draft I reviewed, fact checks (Boot 4, Postgres 18, Testcontainers).
I did: wrote the code generator and its tests; removed the AI's block allocator code because I couldn't explain it; chose the shuffle's threat model; chose feature packages, 404 for unknown and deactivated, idempotent 204, clicks only on 302.

All design decisions and the code were reviewed and are understood by me.