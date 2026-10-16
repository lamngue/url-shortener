# URL Shortening Service

A Java/Spring service that turns long URLs into short links, redirects quickly, and lets signed-in users deactivate their links and see click stats. Designed to survive a data center outage and read spikes. The reasons behind each choice are in [decisions.md](decisions.md).

## Assumptions and scope

- 1M new links per day (about 12/s) and 1,000 clicks per link: about 11,600 redirects/s on average, 35,000 at peak. To confirm.
- Most important: link mappings are never lost, redirects stay fast, one data center can fail.
- Out of scope: custom aliases, expiry, abuse detection.
- Stats may lag by a few seconds and are approximate.

## Architecture

```
Client -> Load balancer -> Spring Boot app (stateless, many instances)
                              |-> Redis (code -> long URL, cache-aside)
                              |-> Postgres (links, source of truth)
                              '-> Kafka (click events, async) -> stats consumer -> Postgres
```

- **Redirect:** check the code format, then Redis, then Postgres on a miss. Answer 302 or 404, and send a click event without waiting.
- **Create:** check the JWT, take the next ID from a Postgres sequence, shuffle it, encode it in base 62, save, clear the Redis key.

| Area | Status |
|------|--------|
| Create, redirect, Redis cache | Built |
| Deactivate, click stats through Kafka | Built |
| Login with Auth0 (PKCE), owner checks | Built |
| Two app instances behind nginx | Built (one data center) |
| List my links | Design only |
| Second data center, Postgres replica, Kafka replication | Design only |

**Stack:** Java 21, Spring Boot 4.1, PostgreSQL, Redis, Kafka, Docker Compose, nginx, Maven wrapper.

## How to run

Needs Docker with Docker Compose.

```bash
docker compose up -d --build     # Postgres, Redis, Kafka, app1, app2, nginx
```

After about 30 seconds, open http://localhost:8080/swagger-ui/index.html, click **Authorize** and sign in. Create a link, see its stats, deactivate it. Follow a short link with curl (Swagger can't show a redirect to another site):

```bash
curl -i http://localhost:8080/<code>
```

Login uses my Auth0 tenant. Test users can be shared on request; passwords are never in the repo. Settings are in `docker-compose.yml`. `SHORT_CODE_K` must be the same on every instance, or two instances could give two links the same code.

**Failover demo** (nginx adds `X-Upstream-Addr` to show which instance answered):

```bash
for i in 1 2 3 4; do curl -s -o /dev/null -D - http://localhost:8080/actuator/health | grep -iE "^HTTP|x-upstream"; done
docker compose stop app1         # run the loop again: still all 200
docker compose logs --since 2m app2 | grep -i "partitions assigned"
docker compose start app1
```

**Without Docker for the app:** `docker compose up -d postgres redis kafka`, then run `./mvnw spring-boot:run` with `SHORT_CODE_K`, `JWT_ISSUER` and `JWT_JWK_SET_URI` set (see `docker-compose.yml` for values).

Stop with `docker compose down` (`-v` also removes the data).

## API

| Method | Path | Success | Errors |
|--------|------|---------|--------|
| POST | /api/links | 201 `{code, shortUrl}` | 400 bad URL, 401 |
| GET | /{code} (public) | 302 + Location | 404 unknown or deactivated |
| DELETE | /api/links/{code} | 204 (also if already inactive) | 401, 403, 404 |
| GET | /api/links/{code}/stats | 200 total + per day | 401, 403, 404 |
| GET | /api/links | design only | |

`/api` needs a Bearer token from Auth0. A link belongs to the token's `sub`.

**Tables:** `links` (id, code, long_url, owner_sub, active, created_at), `click_stats` (code, day, num_clicks), `processed_events` (event_id, processed_at).

## Reliability

- **Redis down:** redirects fall back to Postgres. Redis hanging: about 210 ms (two 100 ms timeouts).
- **Kafka down:** redirects still work, the click is dropped after 100 ms.
- **Postgres down:** cached links still redirect; clicks wait in Kafka and are counted afterwards.
- **One instance down:** nginx retries on the other one; Kafka moves its partitions over.
- **Duplicates:** each click has an event ID, saved in the same transaction as the count, so a redelivered event is counted once.
- **Deactivate:** Postgres first, then the Redis key, so the next redirect is a 404.

All of these were tried by hand with Docker Compose.

## Security

- Tokens: signature (Auth0's public keys), RS256 only, `exp`, `iss`, `aud`. 401 if invalid, 403 for someone else's link.
- Only http/https URLs with a host, max 2,048 characters.
- Secrets from environment variables. TLS at the load balancer and encrypted volumes in production.

## Testing

```bash
./mvnw test      # Docker must be running (Testcontainers)
```

- 78 tests, one per behaviour: unit and web tests, plus real Postgres and Redis for counting, cleanup, stats and `contextLoads`, and real signed tokens for the JWT checks.
- CI: GitHub Actions runs all tests on every pull request to `main`.
- Not covered: an automated test with a real Kafka.

## Known limitations and next steps

- No listing yet. It's the next thing to build.
- Clicks are lost while Kafka is down; an outbox would fix it.
- Tokens can't be revoked before they expire.
- Next: listing, a Kafka end-to-end test, rate limiting, the second data center.

## Use of AI tools

I used Claude Code as a pair programmer, with the VibeWise plugin in learning mode: at every build checkpoint it asked me about the concepts and my approach before any code was written. For example, before I wrote the Kafka consumer: "saving the event ID and adding to the count are two statements. What goes wrong if the first succeeds and the second fails?" (the click would be skipped on retry and lost, so both go in one transaction).

**First version.** Claude wrote most of the Spring code from my designs, step by step, and I reviewed it. I wrote the short code generator and its tests, the audience validator, the ownership checks, the OpenAPI config, the Dockerfile and nginx. I removed Claude's ID block allocator because I couldn't explain its concurrency.

**Rewrite.** I didn't want core logic I had only reviewed, so I rewrote the Redis cache, the redirect, link creation and the Kafka pipeline from scratch, each on its own branch. Claude set up empty classes and reviewed my code. The reviews caught real bugs, like a missing `@Component` and a test that didn't check the Kafka key.

**Still Claude's (reviewed):** setup code (security config, migrations, Kafka settings), some tests (JWT decoder, startup config, stats query), and the first drafts of the docs. Reviewing isn't perfect: my manual Kafka outage test found that Claude had put `max.block.ms` on the consumer instead of the producer, so redirects could hang for 60 seconds.
