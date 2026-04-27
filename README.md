wallet-backend
==============

Java 21 / Spring Boot 3.3 / MySQL (H2 in dev). JWT auth, p2p transfers
with idempotency keys, a double-entry ledger and wallet freeze + reopen.

Listens on **10000** by default. Override with `SERVER_PORT`.


dev
---

The `local` profile runs everything on H2 and turns on a sandbox topup
endpoint so you don't need MySQL or a payment provider just to poke at
the API.

    ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

Swagger ends up at http://localhost:10000/swagger-ui.html. Register a
user, log in, top up your wallet, send money to yourself with a second
account. Auth endpoints are open; everything else wants
`Authorization: Bearer <token>`.

H2 console is at /h2-console (jdbc url `jdbc:h2:mem:walletdb`, user `sa`,
no password). Note that the security filter chain blocks `/h2-console`
by default — open it up in `SecurityConfig` if you actually need it.


prod-ish
--------

Default profile talks to MySQL. Required env:

    APP_JWT_SECRET     # 32+ ASCII chars, used as the HMAC-SHA256 key
    DB_URL             # optional, defaults to jdbc:mysql://localhost:3306/wallet_db
    DB_USERNAME
    DB_PASSWORD

Schema is owned by Flyway (`src/main/resources/db/migration`). Don't
hand-edit the DB, just write a new `V<n>__*.sql` file.


docker
------

    docker build -t wallet-backend .

    docker run --rm -p 10000:10000 \
      -e APP_JWT_SECRET=please-change-me-32-chars-minimum \
      -e DB_URL=jdbc:mysql://host.docker.internal:3306/wallet_db \
      -e DB_USERNAME=root -e DB_PASSWORD=root \
      wallet-backend

Runs as a non-root user, healthcheck hits `/actuator/health`.


api
---

    POST /api/v1/auth/register
    POST /api/v1/auth/login

    GET  /api/v1/wallet/me
    GET  /api/v1/wallet/me/all
    GET  /api/v1/wallet/me/transactions
    GET  /api/v1/wallet/me/history    ?type=&status=&from=&to=&page=&size=
    POST /api/v1/wallet/me/freeze
    POST /api/v1/wallet/me/open-new
    POST /api/v1/wallet/me/topup      (local profile only)

    POST /api/v1/transfers
    GET  /api/v1/transfers/{id}       only sender or receiver can read it

`size` on the history endpoint is capped at 100. `from`/`to` are
ISO-8601 local datetimes (e.g. `2026-01-15T00:00:00`).


tests
-----

    ./mvnw test

One Mockito unit test for the freeze/open-new logic, one MockMvc class
that drives the full auth → topup → transfer → history flow against H2.
Both finish in well under 10s.


stuff worth knowing
-------------------

Transfers grab a pessimistic write lock on both wallets, ordered by
wallet id, so two concurrent transfers between the same pair of wallets
can't deadlock each other.

Every successful transfer writes a debit/credit pair into
`ledger_entries` and one `wallet_transactions` row per side, which is
what the history endpoint reads. Failed transfers (insufficient balance,
daily limit) only write a single FAILED `wallet_transactions` row — no
ledger entry, since no money moved.

Topup is sandbox-only on purpose. In anything resembling production,
money-in comes from a payment provider, not a free POST.


todo / nice-to-have
-------------------

- pagination on `/me/transactions` (currently returns the whole list)
- per-user rate limit on `/auth/login` to avoid online password guesses
- structured logs + a request-id filter
- swap CHAR(36) UUIDs for BINARY(16) once I'm sure I won't need to read
  the DB by hand anymore
