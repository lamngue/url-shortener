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
- Create path: validate the JWT, take the next ID from the Postgres sequence, scramble it, encode it in base 62, save the row, delete the Redis key for that code, return the short URL.
- Availability: two data centers, each with the same setup, behind one load balancer. Each data center has its own ID range, so codes never collide.

Key decisions are explained in [decisions.md](decisions.md).

## What is implemented and what is design only

| Area | Status |
|------|--------|
| Create and redirect (Postgres, Redis cache-aside, Redis fallback) | Implemented, run end to end with Docker Compose |
| Deactivate links | Implemented: `DELETE /api/links/{code}`, Postgres first, then the Redis key |
| List my links | Design only |
| Click statistics through Kafka (D-04) | Implemented: each successful redirect sends a click event, and a consumer counts clicks per code per day in Postgres. `GET /api/links/{code}/stats` shows the total and the clicks per day |
| Login and ownership (D-05) | Implemented: login through Auth0 (OIDC, authorization code + PKCE) in Swagger UI; the API checks the token and only the owner can deactivate a link or see its stats |
| Two app instances behind a load balancer (nginx) | Implemented, in one data center: stopping one instance keeps every request working |
| Second data center, Postgres replica, Kafka replication, global load balancer | Design only |

## Tech stack

- Language and version: Java 21
- Framework: Spring Boot 4.1 (Web MVC, Validation, Data JPA with Flyway, Data Redis, Actuator). Spring Kafka, springdoc (OpenAPI spec and Swagger UI). Spring Security (OAuth2 resource server)
- Database: PostgreSQL
- Cache: Redis
- Messaging: Kafka
- Build tool: Maven (wrapper included, `./mvnw`)
- Containers: Docker (multi-stage `Dockerfile`), Docker Compose, nginx as the load balancer

## How to run

Prerequisites: Docker with Docker Compose. Java is only needed to run the app outside Docker (option B).

### Option A: everything in Docker (two app instances behind nginx)

```bash
# 1. Clone
git clone <repo-url>
cd <repo-folder>

# 2. Build the app image and start Postgres, Redis, Kafka, app1, app2 and nginx
docker compose up -d --build

# 3. Wait until both apps have started (about 30 seconds), then open
#    http://localhost:8080/swagger-ui/index.html, click "Authorize" and sign in.
#    Create a link, see its stats and deactivate it from Swagger.

# 4. Follow a short link (public, no login). Use curl or the address bar:
#    Swagger's "Try it out" cannot show a redirect to another site (CORS).
curl -i http://localhost:8080/<code>
```

The app settings for both instances (database, Redis, Kafka, Auth0, `SHORT_CODE_K`) are in `docker-compose.yml`. `SHORT_CODE_K` must be the same on every instance: with two different values, two instances could turn different IDs into the same short code.

### Option B: the app on your machine (for development)

Needs Java 21 (Maven comes with the wrapper).

```bash
# Start only Postgres, Redis and Kafka
docker compose up -d postgres redis kafka

# Start the app. SHORT_CODE_K is the secret multiplier for the code shuffle
# (odd, not a multiple of 31, below 62^7). The app refuses to start without it.
# JWT_ISSUER and JWT_JWK_SET_URI point to the Auth0 tenant that issues the tokens.
SHORT_CODE_K=<your-secret-k> \
JWT_ISSUER=https://lamnguyenned.eu.auth0.com/ \
JWT_JWK_SET_URI=https://lamnguyenned.eu.auth0.com/.well-known/jwks.json \
./mvnw spring-boot:run
```

Option B uses port 8080 too, so stop option A's apps and nginx first (`docker compose stop app1 app2 nginx`).

### See the load balancing and the failover (option A)

nginx adds an `X-Upstream-Addr` header that shows which instance answered.

```bash
# Both instances take turns
for i in 1 2 3 4; do curl -s -o /dev/null -D - http://localhost:8080/actuator/health | grep -i x-upstream; done

# Stop one instance: every request still gets 200 from the other one
docker compose stop app1
for i in 1 2 3 4; do curl -s -o /dev/null -D - http://localhost:8080/actuator/health | grep -iE "^HTTP|x-upstream"; done

# Kafka gives the stopped instance's click partitions to the other instance
docker compose logs --since 2m app2 | grep -i "partitions assigned"

# Start it again (wait about 30 seconds before testing)
docker compose start app1
```

Settings for login (defaults in `application.yml`):

| Variable | Meaning | Default |
|---|---|---|
| `JWT_ISSUER` | who issues the tokens (`iss`) | placeholder, must be set |
| `JWT_JWK_SET_URI` | where the issuer's public keys are | placeholder, must be set |
| `JWT_AUDIENCE` | the API the token must be for (`aud`) | `url-shortener` |
| `AUTH0_CLIENT_ID` | the Swagger UI login client (public, not a secret) | the client ID of my Auth0 application |

Login uses my Auth0 tenant. Test users can be shared on request; passwords are never in the repository.

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

Authentication (D-05): every `/api` call needs a Bearer access token (JWT) from Auth0; without a valid token the answer is 401. The `/{code}` redirect needs no token. A link belongs to the user who created it (the token's `sub`); someone else gets 403 on deactivate and stats. Listing is design only.

## Data model

- `links` (implemented, `V1__create_links.sql`): id (from the counter), code (unique index), long_url, owner_sub (the token's `sub`), active, created_at.
- `click_stats` (implemented, `V2__create_click_stats.sql`): code, day, num_clicks, with primary key (code, day). One row per link per day; the day is the UTC date of the event's `clickedAt`.
- `processed_events` (implemented, `V2`): event_id (primary key), processed_at. Used to ignore duplicate click events; an hourly job deletes rows older than 1 day.

## Reliability

- Delivery guarantee: at-least-once from Kafka, with an idempotent consumer.
- Duplicate handling: each click event has a unique event ID. The consumer inserts it (unique constraint) and updates the counter in one transaction, so a redelivered event is ignored. Event IDs are kept for 1 day.
- Retries: if counting fails (for example Postgres is down), the consumer retries the same event every second until it succeeds, so no click is lost. Messages that can never succeed (not valid JSON, or bad data) are logged and skipped, so they do not block their partition.
- If Redis is down: reads fall back to Postgres, slower under load. Redis calls time out after 100 ms, and Redis is not part of the health check, so a cache outage does not remove instances from the load balancer.
- If Kafka is down: redirects still work. The producer waits at most about 100 ms, then drops the click, so some clicks are not counted (measured: redirects took about 107 to 137 ms while Kafka was stopped).
- If Postgres is down for the consumer: clicks wait in Kafka and are counted when Postgres is back (measured: 4 clicks during a short outage were all counted afterwards).
- If one app instance stops: nginx retries the request on the other instance, so the caller still gets an answer (measured: all requests 200, the first one tried both instances). Kafka moves the stopped instance's click partitions to the other instance.
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
- Login and ownership: tokens signed by a test key are accepted or rejected (expired, wrong issuer, wrong audience, wrong algorithm, unsigned, unknown key); the audience check on its own; 401 without a token, 403 for someone else's link, 404 for an unknown code.
- Manual end-to-end runs with Docker Compose: Redis stopped and Redis hanging; Kafka stopped; Postgres stopped while clicks were being counted; login through Swagger UI with Auth0.

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