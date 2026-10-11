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

- Redirect path: check Redis, on a miss read Postgres and fill Redis, answer with a 302, publish a click event without waiting.
- Create path: validate the JWT, take the next ID from the Postgres sequence, scramble it, encode it in base 62, save the row, return the short URL.
- Availability: two data centers, each with the same setup, behind one load balancer. Each data center has its own ID range, so codes never collide.

Key decisions are explained in [decisions.md](decisions.md).

## What is implemented and what is design only

Fill this in honestly before sending.

| Area | Status |
|------|--------|
| Create and redirect (Postgres, Redis) | TODO |
| List and deactivate links, cache eviction | TODO |
| Click statistics through Kafka | TODO |
| JWT validation | TODO |
| Two data centers, replicas, load balancer | Design only |

## Tech stack

- Language and version: Java 21
- Framework: Spring Boot (Web, Data JPA, Data Redis, Security OAuth2 resource server, Kafka, Actuator)
- Database: PostgreSQL
- Cache: Redis
- Messaging: Kafka
- Build tool: Maven (TODO: confirm)
- Containers: Docker, Docker Compose

## How to run

Prerequisites: Docker and Docker Compose (nothing else should be needed).

```bash
# 1. Clone
git clone <repo-url>
cd <repo-folder>

# 2. Start everything
docker compose up --build

# 3. Try it (TODO: confirm paths and auth once implemented)
curl -X POST http://localhost:8080/api/links \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{ "url": "https://example.com/some/long/path" }'

curl -i http://localhost:8080/<code>
```

Stop with `docker compose down` (add `-v` to remove data).

## API

| Method | Path | Description | Success | Errors |
|--------|------|-------------|---------|--------|
| POST | /api/links | Create a short link for a long URL | 201 with code and short URL | 400 invalid URL, 401 |
| GET | /{code} | Redirect to the long URL (public) | 302 with Location | 404 unknown or deactivated |
| GET | /api/links | List my links (cursor pagination) | 200 | 401 |
| DELETE | /api/links/{code} | Deactivate my link (soft delete) | 204 | 401, 403 not the owner, 404 |
| GET | /api/links/{code}/stats | Click statistics for my link | 200 | 401, 403, 404 |

Authentication: Bearer access token (JWT) from an OIDC provider. The `/{code}` redirect needs no token.

## Data model

- `links`: id (from the counter), code (unique index), long_url, owner_sub (the token's `sub`), active, created_at.
- `click_stats`: link_id, day, count. One row per link per day.
- `processed_events`: event_id (primary key). Used to ignore duplicate click events.

## Reliability

- Delivery guarantee: at-least-once from Kafka, with an idempotent consumer.
- Duplicate handling: each click event has a unique event ID. The consumer inserts it (unique constraint) and updates the counter in one transaction, so a redelivered event is ignored.
- Retries and dead letter handling: events that keep failing go to a dead letter topic so they do not block the stream.
- If Redis is down: reads fall back to Postgres, slower under load.
- If Kafka is down: redirects still work, and some clicks may not be counted.
- If the identity provider is down: new sign-ins fail, existing tokens work until they expire. Redirects are unaffected.
- Link mappings: stored in Postgres with a replica and backups. Redis is only a performance layer.
- Deactivation: update Postgres first, then delete the Redis entry, so a stale entry is not put back.

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

Planned: unit tests for the code generator (base 62, shuffle, no collisions) and integration tests with Testcontainers for Postgres, Redis and Kafka (TODO: update with what exists). CI runs build and tests on every push.

## Known limitations and next steps

- Statistics are approximate and lag by a few seconds. Exact counts would need the outbox pattern.
- No custom aliases, expiry or abuse detection yet.
- Rate limiting on link creation is a next step.
- Multi-region active-active writes would need more than disjoint ID ranges.

## Use of AI tools

AI tools were used for: (TODO: be specific, for example boilerplate and review of ideas, with the VibeWise plugin for Claude Code if that is true).
All design decisions and the code were reviewed and are understood by me.