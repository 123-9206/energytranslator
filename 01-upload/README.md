# Energy Translator · 能耗翻译官

Energy-management application source for portfolio review and local development.
The project combines a Spring Boot API, a Vue administration interface, and a
WeChat mini program. Its core bill-check workflow separates AI field extraction
from numerical validation, rule evaluation, and report generation.

**中文上手与上传说明：[docs/GITHUB_UPLOAD.zh-CN.md](docs/GITHUB_UPLOAD.zh-CN.md)**

This is a sanitized source snapshot derived from the September 2026 handover.
It is **not the separate MCP Agent package**, a running hosted service, or a
claim that all modules have been tested in production. No MCP Registry manifest
is included. See [validation and changes](docs/PUBLIC_RELEASE_NOTES.zh-CN.md).

## Components

| Directory | Contents |
| --- | --- |
| `backend/` | Java 17 / Spring Boot 3.2 API, MongoDB persistence, JWT authentication, bill processing, energy/device and VPP modules |
| `admin/` | Vue 3, Vite, Element Plus, Pinia and ECharts administration UI |
| `miniprogram/` | Native WeChat client; configure your own AppID and service endpoints |
| `deploy/` | Local-only Docker Compose configuration |
| `scripts/` | Private environment setup, backend launcher and publication checks |

## Main workflows in the source

- Electricity-bill image → AI field extraction → consistency checks → deterministic rules → report.
- Device and energy-record management, alerts and device-control modules.
- Carbon estimates, green-power records, virtual-power-plant resources and settlement modules.
- Administration and mini-program clients consuming the REST API.

Historical sample energy records are generated synthetic data. They are not
measurements from connected factories. Tariffs, carbon factors, savings estimates
and settlement examples require validation for the target use case.

## Local start

Prerequisites: Python 3.10+, Node.js 22.12+ and npm; either Docker with Compose,
or Java 17 + Maven 3.9+ and a local MongoDB instance. Dependencies and container
images require network access. Use a separate development database.

From the repository root:

```bash
python scripts/init_local_env.py
```

This creates an ignored `.env` with a random JWT secret and a random initial
administrator password. Read `BOOTSTRAP_ADMIN_USERNAME` and
`BOOTSTRAP_ADMIN_PASSWORD` from that file locally. They are never printed by the
helper and an existing `.env` is never overwritten.

Start the backend and MongoDB with Docker:

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up --build
```

Or start MongoDB separately, then use Java and Maven:

```bash
python scripts/run_backend.py
```

In another terminal:

```bash
cd admin
npm ci --legacy-peer-deps
npm run dev
```

Open `http://127.0.0.1:5173/admin/`. The frontend proxy targets your local
backend. Sign in with the credentials from `.env`. The bootstrap runner only
creates a missing account; it does not change an existing account's password
or role. Remove the bootstrap password from a deployed environment after setup.

The backend health endpoint is `http://127.0.0.1:8080/actuator/health`.
The Vue production build is `npm run build`; serving that output requires a
separate reverse proxy for `/api`. GitHub source hosting does not run the API.

## Optional functionality

| Setting | Default / purpose |
| --- | --- |
| `SEED_ENABLED` | `false`; set to `true` only to create synthetic records in a separate local database |
| `DEMO_LOGIN_ENABLED` | `false`; optional passwordless `demo` account login for the local mini-program demonstration; account has write access to demo data |
| `CONTROL_ENABLED` | `false`; device commands are disabled by default |
| `BILLCHECK_AI_API_KEY` | Empty; provide your own key to enable external AI bill reading |
| `MONGODB_URI`, `MONGODB_DATABASE` | Separate local demo database |
| `JWT_SECRET` | Required; generated locally, never committed |

The `prod` profile disables seeding and demo login; the demo login service also
rejects that profile explicitly. Protocol collectors are disabled in the base
configuration. Enable them only after configuring your own devices and broker.
An external AI provider receives the bill image when AI reading is enabled;
use synthetic documents for demonstrations.

For the mini program, start with [miniprogram/README.md](miniprogram/README.md).
The existing cloud-function mode references services not included in this snapshot;
use the HTTP mode for this repository.

## Publish this source

Extract the ZIP and publish the contents of the `energytranslator/` directory,
so this README appears at the repository root. Run:

```bash
python scripts/leak_guard.py
```

The guard checks common credential patterns. It does not establish complete
security, legal clearance, or functional correctness. A GitHub Actions workflow
runs the same source check on pushes and pull requests.

## Attribution and licensing

Project contact: **Meng Yantong** · **yantmeng2005@163.com**.
The repository host and release helper may differ from the project contact.
This source snapshot does not establish who implemented each feature; describe
individual contributions accurately in a CV or portfolio.

The supplied source snapshot had no project LICENSE file. This preparation has
not added an MIT, Apache, or other open-source license or changed existing source
attributions. Public visibility should not be presented as a new broad license
grant. Third-party dependencies remain subject to their respective licenses.

## Known limitations

- The existing administration energy view references `/analytics/*` endpoints
  that are absent from this snapshot.
- Live readings require actual device integrations; empty or zero values are
  not proof of a working hardware connection.
- Some modules and explanatory text retain prototype/demo assumptions.
- Production deployment needs additional configuration and validation. This
  preparation focuses on removing private handover material and making local
  configuration explicit; it is not a full application security audit.
