# Deployment

How KleinKauf is deployed to the server, what it needs and how to set the server up from scratch.

The deployment files (workflow, production compose, Caddyfile, Dockerfiles, `deploy.sh`) live on the `deploy/backend`
and `deploy/frontend` branches, not on `main`.

## How it works

The backend and the frontend are deployed separately, each from its own branch:

| Branch | What it deploys |
|---|---|
| `deploy/backend` | Eureka, users, products, sales, observability, gateway, Postgres, Redis and Caddy |
| `deploy/frontend` | The frontend (Vite build served by nginx) |

A push to either one triggers the `.github/workflows/deploy.yml` workflow, which:

1. Connects to the server over SSH.
2. Moves that branch's checkout (`~/kleinkauf/backend` or `~/kleinkauf/frontend`) to the pushed commit.
3. Runs `backend/deploy/deploy.sh`, which builds the images **on the server** (one at a time, so it does not run out of
   memory), starts the services with `docker compose up --wait` and cleans up.

If a service is not healthy within 10 minutes, the run fails and shows up in GitHub's **Actions** tab. Two pushes to
the same branch do not overlap: the second waits for the first to finish.

```
                    Internet
                       │  80 / 443
                ┌──────▼──────┐
                │    Caddy    │  HTTPS (Let's Encrypt) and routing by host
                └──┬───────┬──┘
   <domain>        │       │  api.<domain>
   <store>.<domain>        │
          ┌────────▼─┐   ┌─▼─────────┐
          │ frontend │   │  gateway  │──► users, products, sales, observability
          │  (nginx) │   │   :3000   │    Eureka, Postgres, Redis
          └──────────┘   └───────────┘
```

- `<domain>` is the platform frontend (store sign-up and superadmin panel).
- `api.<domain>` is the gateway.
- `<store>.<domain>` is each store's frontend. Caddy gets the certificate the first time someone visits (on-demand
  TLS), only for subdomains shaped like a store identifier.

Only Caddy is published to the outside. The frontend and the gateway are published on `127.0.0.1` and everything else
stays on Docker's internal network.

## Requirements

**On the server**

| What | Purpose | Tested version |
|---|---|---|
| Linux with SSH access | Running everything | Debian 12, 8 GB RAM, 2 CPUs |
| Docker Engine + Docker Compose | Building the images and running the services (`up --wait` needs Compose v2.17 or later) | Compose v5.7 |
| git | Fetching the deploy branches | 2.39 |
| openssl | Generating the `.env` secrets | — |

Building the jars (Gradle, JDK 21) and the frontend (Node) happens inside build containers, so Java and Node do not
need to be installed on the server.

**Outside the server**

| What | Purpose |
|---|---|
| GitHub Actions | Triggering the deploy on every push to `deploy/*` |
| A domain with wildcard DNS (`*.domain`), or [sslip.io](https://sslip.io) until there is one | So the domain, `api.` and each store resolve to the server's IP |
| Let's Encrypt | HTTPS certificates (Caddy gets them on its own, nothing to configure) |
| Resend | Outgoing email (see [mail.md](mail.md)) |

**On your machine**: git and, for the initial setup, [GitHub CLI](https://cli.github.com/) (`gh`).

## Deploying

Work happens on `main`. To deploy, merge `main` into the deploy branch and push:

```bash
git checkout deploy/backend
git merge main
git push origin deploy/backend
```

```bash
git checkout deploy/frontend
git merge main
git push origin deploy/frontend
```

Each branch deploys only its part: if the change is frontend-only, `deploy/frontend` is enough. The very first deploy
must be the backend's, because it starts Caddy, Postgres and Redis.

To follow the run from the terminal: `gh run watch`. The last deploy of a branch can also be re-run without pushing,
from **Actions → Deploy → Run workflow**.

### Rolling back

Revert the commit on the deploy branch and push. The workflow deploys whatever is left:

```bash
git checkout deploy/backend
git revert <commit>
git push origin deploy/backend
```

### Deploying by hand on the server

If GitHub Actions is not available, do the same as the workflow over SSH:

```bash
cd ~/kleinkauf/backend            # or ~/kleinkauf/frontend
git fetch origin deploy/backend
git checkout --force --detach origin/deploy/backend
bash backend/deploy/deploy.sh backend    # or frontend
```

## Initial server setup

Done only once. The examples use the user `bruno` and SSH port `5141`.

### 1. A user with Docker access

The deploying user must be in the `docker` group (which in practice is equivalent to root):

```bash
sudo usermod -aG docker bruno
```

### 2. GitHub Actions access to the server

On your machine, generate a key just for Actions and add the public part to the server:

```bash
ssh-keygen -t ed25519 -N "" -C "github-actions-kleinkauf" -f actions_key
ssh-copy-id -i actions_key.pub -p 5141 bruno@<ip>
```

### 3. Server access to the repo

The repo is private: the server needs a read-only deploy key. On the server:

```bash
ssh-keygen -t ed25519 -N "" -C "kleinkauf-server-deploy" -f ~/.ssh/kleinkauf_deploy
printf 'Host github.com\n  IdentityFile ~/.ssh/kleinkauf_deploy\n  IdentitiesOnly yes\n' >> ~/.ssh/config
ssh-keyscan -t ed25519 github.com >> ~/.ssh/known_hosts
cat ~/.ssh/kleinkauf_deploy.pub
```

Then register that key in the repo (from your machine, or under **Settings → Deploy keys** without *Allow write
access*):

```bash
gh repo deploy-key add kleinkauf_deploy.pub --title "kleinkauf server (read only)"
```

### 4. Checkouts

One clone with a worktree per branch. Both use the same Compose project (`name: kleinkauf`), so they share volumes and
network:

```bash
mkdir -p ~/kleinkauf && cd ~/kleinkauf
git clone git@github.com:BrunoTrinitario/kleinkauf.git backend
cd backend
git worktree add --detach ../frontend origin/deploy/frontend
```

### 5. The `.env`

One file for both checkouts, at `~/kleinkauf/.env` (never committed). Start from the example and fill it in:

```bash
cp ~/kleinkauf/backend/backend/deploy/.env.example ~/kleinkauf/.env
chmod 600 ~/kleinkauf/.env
```

| Variable | Value on the server |
|---|---|
| `POSTGRES_PASSWORD`, `JWT_SECRET`, `API_INTERNAL_KEY` | Random: `openssl rand -hex 48` |
| `PLATFORM_SCHEME` | `https` |
| `PLATFORM_DOMAIN` | The domain, without `https://`. With sslip.io: `201-32-129-72.sslip.io` |
| `PLATFORM_URL` | `https://<domain>` |
| `PLATFORM_HOSTNAME` | `<domain>` |
| `PUBLIC_API_URL` | `https://api.<domain>` |
| `SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD` | The first superadmin (created on startup if it does not exist). Change the password after the first login |
| `MAIL_PASSWORD` | Resend **send-only** key ("kleinkauf-backend (send only)"). See [mail.md](mail.md) |
| `JAVA_OPTS` | Memory for each JVM; with 8 GB the default is enough |

`PUBLIC_API_URL` and `PLATFORM_HOSTNAME` are baked into the frontend build: if they change, redeploy it.

### 6. GitHub secrets

Under **Settings → Secrets and variables → Actions**, or with `gh`:

| Secret | Value |
|---|---|
| `DEPLOY_HOST` | Server IP |
| `DEPLOY_PORT` | SSH port (`5141`) |
| `DEPLOY_USER` | Deploy user (`bruno`) |
| `DEPLOY_SSH_KEY` | The **private** key from step 2 (`actions_key`) |
| `DEPLOY_KNOWN_HOSTS` | The output of `ssh-keyscan -p 5141 <ip>` |

```bash
gh secret set DEPLOY_SSH_KEY < actions_key
```

```bash
ssh-keyscan -p 5141 <ip> | gh secret set DEPLOY_KNOWN_HOSTS
```

### 7. Firewall

Open 80 and 443 to any source (Let's Encrypt validates from IPs it does not publish) in the provider's panel, in ufw
and in the `allowlist-docker` chain if it exists. See [Firewall](#firewall).

### 8. First deploy

Push `deploy/backend`, wait for it to finish successfully, then push `deploy/frontend`.

To load the demo store (for testing only):

```bash
cd ~/kleinkauf/backend
docker compose --env-file ~/kleinkauf/.env -f backend/deploy/docker-compose.prod.yml --profile seed up seed
```

## Operations

Commands run on the server, from `~/kleinkauf/backend`. To avoid repeating the options:

```bash
alias kk='docker compose --env-file ~/kleinkauf/.env -f ~/kleinkauf/backend/backend/deploy/docker-compose.prod.yml'
```

| To | Command |
|---|---|
| See the status | `kk ps` |
| Follow a service's logs | `kk logs -f gateway-service` |
| Restart a service | `kk restart user-service` |
| Reload the Caddyfile | `kk exec caddy caddy reload --config /etc/caddy/Caddyfile` |
| Stop everything (keeping data) | `kk --profile server stop` |
| Create a superadmin (asks for email, name and password) | `bash ~/kleinkauf/backend/backend/deploy/create-superadmin.sh` |

`create-superadmin.sh` inserts it straight into Postgres (`users` database, `public` schema), with the password in
bcrypt like the app. Use it when no superadmin is left to log into the panel; otherwise create it from the panel, so it
shows up in the audit log. It finds the Postgres container on its own; if it cannot, pass `PG_CONTAINER=<name>`. To
compute the hash it enables `pgcrypto` in the `postgres` database (not in `users`).

Data lives in Docker volumes (`kleinkauf_postgres-data`, `kleinkauf_product-uploads`, `kleinkauf_sales-uploads`,
`kleinkauf_user-uploads` with profile photos, `kleinkauf_caddy-data`, etc.). `docker compose down` keeps them;
**`down -v` deletes them**. `kleinkauf_caddy-data` holds the certificates: if it is lost Caddy requests them again, but
Let's Encrypt limits how many it issues per week.

### Disk space

Every deploy builds new images, so without cleanup the disk grows several GB per week (with cache it reached more than
13 GB in two days). That is why **nothing is kept from one deploy to the next**:

- `deploy.sh` builds each image with `--no-cache`. Within one deploy the backend services share the Gradle cache, so
  dependencies are downloaded once per deploy; the backend deploy takes a few minutes longer than with cache.
- At the end it removes old (untagged) images, **all** build cache, stopped containers and unused networks. The GitHub
  Actions job log shows how much Docker uses (`docker system df`).
- Volumes are never touched.

Container logs rotate on their own: up to 3 files of 10 MB per container (`x-logging` in `docker-compose.prod.yml`).

To see what takes space and free it by hand, on the server:

```bash
df -h /                      # server disk
docker system df             # images, containers, volumes and build cache
docker builder prune -af     # all build cache (deploy.sh already does this at the end of each deploy)
docker image prune -af       # every image without a container (including stopped services')
```

### Backups

The `backup` service makes one backup a day from `BACKUP_HOUR` UTC (6 by default, 3 am in Argentina) into
`BACKUP_DIR` (`~/kleinkauf/backups` by default), a host folder outside the volumes:

| File | Contents |
|---|---|
| `kleinkauf-<date>.sql.gz` | `pg_dumpall` of every database (users, products, sales, observability) |
| `kleinkauf-<date>-uploads.tgz` | Product images (`products/`) and payment receipts (`sales/`) |

Backups are kept for `BACKUP_KEEP_DAYS` days (14 by default). If the container restarts it does not repeat that day's
backup. What it did can be seen with `kk logs backup`.

To have `deploy.sh` start it, `backup` must be in `UP` in the `backend` part of `deploy.sh`. To start it by hand:
`kk up -d backup` (and `kk stop backup` to stop it).

Backups stay on the same server: if the disk is lost, they are lost with it. Copy them elsewhere from time to time,
for example from your machine: `scp -P <port> 'bruno@<ip>:kleinkauf/backups/kleinkauf-*' .`

**Restoring** (overwrites the current data):

```bash
kk stop user-service product-service sales-service observability-service gateway-service
# Databases (the dump drops and recreates each table)
gunzip -c ~/kleinkauf/backups/kleinkauf-<date>.sql.gz | kk exec -T postgres psql -U postgres -d postgres
# Images and receipts
docker run --rm -v kleinkauf_product-uploads:/restore/products -v kleinkauf_sales-uploads:/restore/sales   -v ~/kleinkauf/backups:/backups:ro postgres:17 tar -xzf /backups/kleinkauf-<date>-uploads.tgz -C /restore
kk up -d user-service product-service sales-service observability-service gateway-service
```

### Security headers

Caddy adds to everything it serves over HTTPS: HSTS (1 year, with subdomains), `X-Content-Type-Options`,
`Referrer-Policy` and `Permissions-Policy`. On the frontend it also adds `X-Frame-Options: DENY` and the CSP.

The CSP is in **Report-Only** mode: it blocks nothing and only reports in the browser console
(`Content-Security-Policy-Report-Only`) what it would block. Once the store and the panel have been used for a while
without warnings, change `Content-Security-Policy-Report-Only` to `Content-Security-Policy` in the Caddyfile.

### Search engines (robots.txt and sitemap.xml)

Caddy handles them per host, like the shared product previews:

- In each store, `/robots.txt` allows indexing the public pages (home, catalog, bundles, products and legal texts) but
  not the panel, cart, checkout or account, and points to `https://<store>.<domain>/sitemap.xml`.
- `/sitemap.xml` goes to the gateway (`/share/<store>/sitemap.xml`): products builds it with the store's products. For
  Google to use it, it can be submitted per store in Search Console.
- The platform and `api.` answer `Disallow: /`.

### Firewall

Docker publishes its ports by writing its own iptables rules, so regular ufw rules (`ufw allow`) do not decide what
reaches the containers. That is why in the compose only Caddy is published on every interface (80/443); the frontend
and the gateway are on `127.0.0.1`.

On this server, traffic to the containers also goes through the **`allowlist-docker`** chain, defined in
`/etc/ufw/after.rules` and `/etc/ufw/after6.rules` (`DOCKER-USER` jumps to it). It drops everything coming in through
`eth0` except what is explicitly allowed, so 80 and 443 must be there before the `DROP`:

```
-A allowlist-docker -p tcp -m conntrack --ctorigdstport 80 --ctdir ORIGINAL -j RETURN
-A allowlist-docker -p tcp -m conntrack --ctorigdstport 443 --ctdir ORIGINAL -j RETURN
-A allowlist-docker -p udp -m conntrack --ctorigdstport 443 --ctdir ORIGINAL -j RETURN
-A allowlist-docker -j DROP
```

`--ctorigdstport` is used because after Docker's DNAT the destination port is no longer the original one. After editing
those files: `sudo ufw reload`. In ufw, also `ufw allow 80/tcp`, `443/tcp`, `443/udp` and the SSH port.

If Caddy cannot get certificates, first check that Let's Encrypt reaches port 80: `sudo tcpdump -ni eth0 'tcp port 80'`
shows whether packets come in and get answered. After fixing it, `docker restart kleinkauf-caddy-1` makes it retry
right away instead of waiting for its next attempt.

## Moving from sslip.io to your own domain

1. In the domain's DNS, create two A records pointing to the server IP: `domain.com` and `*.domain.com`.
2. In `~/kleinkauf/.env`, change `PLATFORM_DOMAIN`, `PLATFORM_URL`, `PLATFORM_HOSTNAME` and `PUBLIC_API_URL`.
3. Redeploy the backend and the frontend (Actions → Deploy → Run workflow on each branch).

Stores created before keep their URL with the old domain, so they need updating.

## Files (on the deploy branches)

| File | What it is |
|---|---|
| `.github/workflows/deploy.yml` | GitHub Actions workflow |
| `backend/deploy/deploy.sh` | Script that runs on the server: build and start one part |
| `backend/deploy/docker-compose.prod.yml` | Production compose |
| `backend/deploy/Dockerfile.service` | Image for each backend service |
| `backend/deploy/caddy/Caddyfile` | Caddy configuration |
| `backend/deploy/backup/backup.sh` | Daily backup |
| `backend/deploy/.env.example` | Variables for the `.env` |
| `frontend/Dockerfile`, `frontend/nginx.conf` | Frontend image (Vite build + nginx) |
