# parking-system

The root `compose.yaml` runs all three independent systems for local development.
Docker Engine and Docker Compose v2.20+ are required; Java and Node are provided
by the development images.

| Profile | Services | Backend | Vite | PostgreSQL | Database |
| --- | --- | --- | --- | --- | --- |
| `bank` | `bank-db`, `bank-backend`, `bank-frontend` (+ DB forwarder) | 8081 | 5171 | 5431 | bank |
| `parking` | `parking-db`, `parking-backend`, `parking-frontend` (+ DB forwarder) | 8082 | 5172 | 5432 | parking_system |
| `payment-processor` | `payment-processor-db`, `payment-processor-backend`, `payment-processor-frontend` (+ DB forwarder) | 8083 | 5173 | 5433 | payment_processor |

Start everything, building images and waiting for health checks:

```sh
docker compose --profile all up --build --wait
```

Open http://localhost:5171, http://localhost:5172, and http://localhost:5173.
The first build downloads Maven dependencies and frontend startup installs npm
packages. Maven artifacts persist in separate cache volumes for each system;
frontends rerun `npm ci` on startup to keep dependencies in sync.

Start one system, or any combination, without requiring the other systems:

```sh
docker compose --profile bank up --build --wait
docker compose --profile parking up --build --wait
docker compose --profile payment-processor up --build --wait
docker compose --profile bank --profile payment-processor up --build --wait
```

Explicit service names work without enabling a profile. For example, start only
one database, all databases, or a frontend against a backend running on the host.
Starting a database also starts its small `*-db-access` TCP forwarder:

```sh
docker compose up -d --wait bank-db
docker compose up -d --wait bank-db parking-db payment-processor-db
BANK_API_PROXY_TARGET=http://host.docker.internal:8081 docker compose up -d --wait bank-frontend
```

The equivalent frontend overrides are `PARKING_API_PROXY_TARGET` and
`PAYMENT_PROCESSOR_API_PROXY_TARGET`. Frontends do not depend on backends being
running, so UI work is possible independently. Backends wait only for their own
database. An unavailable peer API never prevents another system from starting.

Use Compose service names rather than fixed container names to avoid global
container-name collisions. Compose project names also isolate networks and
volumes; simultaneous checkouts still need distinct published host ports.

```sh
docker compose logs -f bank-backend bank-frontend
docker compose --profile bank stop
docker compose --profile all down
```

`stop` stops only the selected profile. `down` is for shutting down the whole
project. Database data persists in named volumes across both commands.
All published ports bind to `127.0.0.1`.

Frontend `src`, `public`, and the existing configuration files are bind-mounted
read-only into the containers. Vite listens on `0.0.0.0`, uses strict ports and polling, and serves its HMR
WebSocket on the same published frontend port. Editing JSX/CSS on the host
updates the browser without rebuilding images. Each frontend owns a separate
`node_modules` volume; restart that frontend after changing `package-lock.json`
to rerun `npm ci`. Rebuild the frontend image when adding new root configuration
files that are not already mounted.

Browser API calls should use `/api/...`. Vite proxies that path, unchanged, to
its own backend, using Docker DNS in Compose and `localhost:808X` when running
Vite directly. Backend controllers should therefore use the `/api` prefix.
Browser redirects and payment URLs use the published `localhost` URLs, not
Docker service names.

Backend `src` and `pom.xml` are also bind-mounted read-only, with separate
writable Maven cache and `target` volumes. To compile and run backend changes:

```sh
docker compose restart bank-backend
```

Rebuild the backend image after changing its POM or Dockerfile. Java hot reload
is not enabled. Each backend exposes `/actuator/health`, including database
connectivity, for Compose health checks.

Networks follow the separate data ownership in
`../Sistemi elektronskog placanja.drawio.xml`:

- Each `*-data` network is `internal: true` and contains only that system's
  backend, database, and localhost access forwarder. Databases have no internet
  route or shared network.
- Each `*-web` network connects only that system's frontend and backend.
  Frontends can download npm dependencies and cannot resolve databases or peer
  systems through Docker DNS.
- `public-api` connects only the three backends and allows outbound internet
  access, simulating API communication between independently deployed systems.
  Internal API addresses are `http://bank-backend:8081`,
  `http://parking-backend:8082`, and `http://payment-processor-backend:8083`.
  This network provides connectivity; no cross-system API clients exist yet.
- Each `*-db-access` forwarder joins its own `*-data` and `*-db-admin` networks
  and publishes only `127.0.0.1:543X`. This is necessary because Docker does not
  publish ports for containers attached only to internal networks. PostgreSQL
  itself stays fully internal. The admin network contains only that forwarder;
  frontends and other systems cannot resolve it. Local IDEs and host backends can
  use the localhost port. No database is shared between systems (each uses the
  same development-only `postgres/postgres` credentials).

The diagram's future PSP plugins are separate services with their own databases;
they are not implemented or added to this stack yet.

The local database username and password are both `postgres`. Flyway runs on
backend startup from `src/main/resources/db/migration`. `V1__init.sql` creates no
application tables; Flyway initializes only its history table in `public`. Add
application tables in subsequent migrations. Hibernate validates the schema
without creating or updating it.

For host development, install Java 21 and Node 24, start the required database,
then run `./mvnw spring-boot:run` from the backend and `npm ci && npm run dev` from
the frontend. Host backend defaults use `localhost:543X`; override `DB_URL`,
`DB_USERNAME`, and `DB_PASSWORD` for other PostgreSQL instances.

Security permits all requests, with CSRF, form login, and HTTP Basic disabled.
CORS allows all three local Vite origins; override the comma-separated
`CORS_ALLOWED_ORIGINS` value when running a backend outside Compose if needed.
Each backend has custom `NotFoundException` (404), `BadRequestException` (400),
and `InternalServerErrorException` (500) types. Global handlers return JSON with
`code`, `message`, and `errors`. Internal failures are logged and return a generic
message; Spring MVC errors retain their HTTP status and headers.

Run backend tests without installing a host JDK:

```sh
docker compose run --rm --no-deps bank-backend mvn -B test
docker compose run --rm --no-deps parking-backend mvn -B test
docker compose run --rm --no-deps payment-processor-backend mvn -B test
```

Tests use an isolated H2 database and Flyway; normal startup uses PostgreSQL.

With the full stack running, `node scripts/verify-dev.mjs` verifies all published
frontend/backend URLs, Vite API proxies, and real HMR WebSocket updates. It creates
and removes a temporary source module in each frontend. On Linux, run it without
installing Node on the host:

```sh
docker run --rm --network host --mount "type=bind,src=$PWD,dst=/workspace" --workdir /workspace node:24-alpine node scripts/verify-dev.mjs
```
