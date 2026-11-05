# Despliegue de KleinKauf

Cómo se despliega KleinKauf en el server, qué hace falta y cómo prepararlo desde cero.

---

## Cómo funciona

El backend y el frontend se despliegan por separado, cada uno desde su rama:

| Rama | Qué despliega |
|---|---|
| `deploy/backend` | Eureka, users, products, sales, observability, gateway, Postgres, Redis y Caddy |
| `deploy/frontend` | El frontend (build de Vite servido por nginx) |

Un push a cualquiera de las dos dispara el workflow [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml), que:

1. Entra por SSH al server.
2. Deja el checkout de esa rama (`~/kleinkauf/backend` o `~/kleinkauf/frontend`) en el commit pusheado.
3. Corre [`backend/deploy/deploy.sh`](backend/deploy/deploy.sh), que compila las imágenes **en el server** (de a una, para
   no quedarse sin memoria), levanta los servicios con `docker compose up --wait` y borra las imágenes viejas.

Si algún servicio no queda healthy en 10 minutos, el run falla y se ve en la pestaña **Actions** de GitHub. Dos pushes a la
misma rama no se pisan: el segundo espera a que termine el primero.

```
                    Internet
                       │  80 / 443
                ┌──────▼──────┐
                │    Caddy    │  HTTPS (Let's Encrypt) y ruteo por host
                └──┬───────┬──┘
   <dominio>       │       │  api.<dominio>
   <tienda>.<dominio>      │
          ┌────────▼─┐   ┌─▼─────────┐
          │ frontend │   │  gateway  │──► users, products, sales, observability
          │  (nginx) │   │   :3000   │    Eureka, Postgres, Redis
          └──────────┘   └───────────┘
```

- `<dominio>` es el frontend de la plataforma (alta de tiendas y panel del superadmin).
- `api.<dominio>` es el gateway.
- `<tienda>.<dominio>` es el frontend de cada tienda. Caddy saca el certificado la primera vez que alguien entra
  (on-demand TLS), solo para subdominios con formato de identificador de tienda.

Solo Caddy se publica hacia afuera. El frontend y el gateway se publican en `127.0.0.1` y el resto queda en la red
interna de Docker.

---

## Tecnologías necesarias

**En el server**

| Qué | Para qué | Versión probada |
|---|---|---|
| Linux con acceso SSH | Correr todo | Debian 12, 8 GB de RAM, 2 CPUs |
| Docker Engine + Docker Compose | Compilar las imágenes y levantar los servicios (`up --wait` necesita Compose v2.17 o más) | Compose v5.7 |
| git | Traer el código de las ramas de deploy | 2.39 |
| openssl | Generar los secretos del `.env` | — |

La compilación de los jars (Gradle, JDK 21) y del frontend (Node) corre dentro de los contenedores de build, así que no
hace falta instalar Java ni Node en el server.

**Afuera del server**

| Qué | Para qué |
|---|---|
| GitHub Actions | Disparar el deploy en cada push a `deploy/*` |
| Un dominio con DNS comodín (`*.dominio`), o [sslip.io](https://sslip.io) mientras no haya | Que el dominio, `api.` y cada tienda resuelvan a la IP del server |
| Let's Encrypt | Certificados HTTPS (los saca Caddy solo, no hay que configurar nada) |
| Una cuenta de Gmail con contraseña de aplicación | Los mails de recupero de contraseña |

**En tu máquina**: git y, para la configuración inicial, [GitHub CLI](https://cli.github.com/) (`gh`).

---

## Desplegar

El código se trabaja en `main`. Para desplegar, se mergea `main` en la rama de deploy y se pushea:

```bash
# Backend
git checkout deploy/backend
git merge main
git push origin deploy/backend

# Frontend
git checkout deploy/frontend
git merge main
git push origin deploy/frontend
```

Cada rama despliega solo su parte: si el cambio es solo del frontend, alcanza con `deploy/frontend`.

El primer deploy tiene que ser el del backend, porque es el que levanta Caddy, Postgres y Redis.

Para seguir el run desde la terminal: `gh run watch`. También se puede volver a correr el último deploy de una rama sin
pushear, desde **Actions → Deploy → Run workflow**.

### Volver a una versión anterior

Revertir el commit en la rama de deploy y pushear. El workflow despliega lo que quede:

```bash
git checkout deploy/backend
git revert <commit>
git push origin deploy/backend
```

### Desplegar a mano en el server

Si GitHub Actions no está disponible, se puede hacer lo mismo que el workflow entrando por SSH:

```bash
cd ~/kleinkauf/backend            # o ~/kleinkauf/frontend
git fetch origin deploy/backend
git checkout --force --detach origin/deploy/backend
bash backend/deploy/deploy.sh backend    # o frontend
```

---

## Configuración inicial del server

Esto se hace una sola vez. Los ejemplos usan el usuario `bruno` y el puerto SSH `5141`.

### 1. Usuario con acceso a Docker

El usuario que despliega tiene que estar en el grupo `docker`:

```bash
sudo usermod -aG docker bruno
```

Tener en cuenta que el grupo `docker` equivale en la práctica a ser root.

### 2. Acceso de GitHub Actions al server

En tu máquina, generar una clave solo para Actions y agregar la pública al server:

```bash
ssh-keygen -t ed25519 -N "" -C "github-actions-kleinkauf" -f actions_key
ssh-copy-id -i actions_key.pub -p 5141 bruno@<ip>
```

### 3. Acceso del server al repo

El repo es privado: el server necesita una deploy key de solo lectura. En el server:

```bash
ssh-keygen -t ed25519 -N "" -C "kleinkauf-server-deploy" -f ~/.ssh/kleinkauf_deploy
printf 'Host github.com\n  IdentityFile ~/.ssh/kleinkauf_deploy\n  IdentitiesOnly yes\n' >> ~/.ssh/config
ssh-keyscan -t ed25519 github.com >> ~/.ssh/known_hosts
cat ~/.ssh/kleinkauf_deploy.pub
```

Y registrar esa clave en el repo (en tu máquina, o en **Settings → Deploy keys** sin marcar *Allow write access*):

```bash
gh repo deploy-key add kleinkauf_deploy.pub --title "server kleinkauf (solo lectura)"
```

### 4. Checkouts

Un clon con un worktree por rama. Los dos manejan el mismo proyecto de Compose (`name: kleinkauf`), así que comparten
volúmenes y red:

```bash
mkdir -p ~/kleinkauf && cd ~/kleinkauf
git clone git@github.com:BrunoTrinitario/kleinkauf.git backend
cd backend
git worktree add --detach ../frontend origin/deploy/frontend
```

### 5. El `.env`

Uno solo para los dos checkouts, en `~/kleinkauf/.env` (no se commitea). Partir del ejemplo y completar:

```bash
cp ~/kleinkauf/backend/backend/deploy/.env.example ~/kleinkauf/.env
chmod 600 ~/kleinkauf/.env
```

| Variable | Valor en el server |
|---|---|
| `POSTGRES_PASSWORD`, `JWT_SECRET`, `API_INTERNAL_KEY` | Aleatorios: `openssl rand -hex 48` |
| `PLATFORM_SCHEME` | `https` |
| `PLATFORM_DOMAIN` | El dominio, sin `https://`. Con sslip.io: `201-32-129-72.sslip.io` |
| `PLATFORM_URL` | `https://<dominio>` |
| `PLATFORM_HOSTNAME` | `<dominio>` |
| `PUBLIC_API_URL` | `https://api.<dominio>` |
| `SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD` | El primer superadmin (se crea al arrancar si no existe) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Cuenta de Gmail y su contraseña de aplicación |
| `JAVA_OPTS` | Memoria de cada JVM; con 8 GB alcanza el valor por defecto |

`PUBLIC_API_URL` y `PLATFORM_HOSTNAME` quedan fijas en el build del frontend: si cambian, hay que volver a desplegarlo.

### 6. Secrets de GitHub

En **Settings → Secrets and variables → Actions**, o con `gh`:

| Secret | Valor |
|---|---|
| `DEPLOY_HOST` | IP del server |
| `DEPLOY_PORT` | Puerto SSH (`5141`) |
| `DEPLOY_USER` | Usuario del deploy (`bruno`) |
| `DEPLOY_SSH_KEY` | La clave **privada** del paso 2 (`actions_key`) |
| `DEPLOY_KNOWN_HOSTS` | La salida de `ssh-keyscan -p 5141 <ip>` |

```bash
gh secret set DEPLOY_SSH_KEY < actions_key
ssh-keyscan -p 5141 <ip> | gh secret set DEPLOY_KNOWN_HOSTS
```

### 7. Firewall

Abrir 80 y 443 para cualquier origen (Let's Encrypt valida desde IPs que no publica) en el panel del proveedor, en ufw
y en la cadena `allowlist-docker` si existe. Ver [Firewall](#firewall).

### 8. Primer deploy

Pushear `deploy/backend`, esperar a que termine bien y después pushear `deploy/frontend`.

Para cargar la tienda demo (solo para probar):

```bash
cd ~/kleinkauf/backend
docker compose --env-file ~/kleinkauf/.env -f backend/deploy/docker-compose.prod.yml --profile seed up seed
```

---

## Operación

Los comandos van en el server, desde `~/kleinkauf/backend`. Para no repetir las opciones:

```bash
alias kk='docker compose --env-file ~/kleinkauf/.env -f ~/kleinkauf/backend/backend/deploy/docker-compose.prod.yml'
```

| Para | Comando |
|---|---|
| Ver el estado | `kk ps` |
| Ver logs de un servicio | `kk logs -f gateway-service` |
| Reiniciar un servicio | `kk restart user-service` |
| Recargar el Caddyfile | `kk exec caddy caddy reload --config /etc/caddy/Caddyfile` |
| Frenar todo (sin perder datos) | `kk --profile server stop` |

Los datos viven en volúmenes de Docker (`kleinkauf_postgres-data`, `kleinkauf_product-uploads`,
`kleinkauf_sales-uploads`, `kleinkauf_user-uploads` con las fotos de perfil, `kleinkauf_caddy-data`, etc.).
`docker compose down` los conserva; **`down -v` los borra**.
`kleinkauf_caddy-data` guarda los certificados: si se pierde, Caddy los vuelve a pedir, pero Let's Encrypt limita
cuántos se emiten por semana.

### Espacio en disco

Cada deploy compila imágenes nuevas, así que sin limpieza el disco crece varios GB por semana (con caché quedaban más
de 13 GB en dos días). Por eso **no se guarda nada de un deploy para el siguiente**:

- `deploy.sh` compila cada imagen con `--no-cache`. Dentro del mismo deploy los servicios del backend comparten la
  caché de gradle, así que las dependencias se bajan una sola vez por deploy; el de backend tarda unos minutos más
  que con caché.
- Al final borra las imágenes viejas (sin tag), **toda** la caché de build, los contenedores parados y las redes sin
  uso. El log del job de GitHub Actions muestra cuánto ocupa Docker (`docker system df`).
- Los volúmenes no se tocan nunca.

Los logs de los contenedores rotan solos: hasta 3 archivos de 10 MB por contenedor (`x-logging` en
`docker-compose.prod.yml`).

Para ver qué ocupa y liberar a mano, en el server:

```bash
df -h /                      # disco del server
docker system df             # imágenes, contenedores, volúmenes y caché de build
docker builder prune -af     # toda la caché de build (deploy.sh ya lo hace al final de cada deploy)
docker image prune -af       # todas las imágenes sin contenedor (también las de servicios frenados)
```

### Backups

> **Todavía no está activo.** El servicio está armado pero `deploy.sh` no lo levanta. Para activarlo: sumar `backup` a
> `UP` en la parte `backend` de [`deploy.sh`](backend/deploy/deploy.sh) y volver a desplegar el backend. Para probarlo a
> mano sin activarlo: `kk up -d backup` (y `kk stop backup` para frenarlo).

El servicio `backup` hace un backup por día a partir de las `BACKUP_HOUR` UTC (por
defecto 6, las 3 de Argentina) en `BACKUP_DIR` (por defecto `~/kleinkauf/backups`), una carpeta del host fuera de
los volúmenes:

| Archivo | Qué tiene |
|---|---|
| `kleinkauf-<fecha>.sql.gz` | `pg_dumpall` de todas las bases (users, products, sales, observability) |
| `kleinkauf-<fecha>-uploads.tgz` | Imágenes de productos (`products/`) y comprobantes de pago (`sales/`) |

Se guardan `BACKUP_KEEP_DAYS` días (por defecto 14). Si el contenedor se reinicia no repite el del día. Lo que hizo se
ve con `kk logs backup`.

Los backups quedan en el mismo server: si se pierde el disco, se pierden con él. Conviene copiarlos afuera de vez en
cuando, por ejemplo desde tu máquina: `scp -P <puerto> 'bruno@<ip>:kleinkauf/backups/kleinkauf-*' .`

**Restaurar** (pisa los datos actuales):

```bash
kk stop user-service product-service sales-service observability-service gateway-service
# Bases (el dump borra y vuelve a crear cada tabla)
gunzip -c ~/kleinkauf/backups/kleinkauf-<fecha>.sql.gz | kk exec -T postgres psql -U postgres -d postgres
# Imagenes y comprobantes
docker run --rm -v kleinkauf_product-uploads:/restore/products -v kleinkauf_sales-uploads:/restore/sales   -v ~/kleinkauf/backups:/backups:ro postgres:17 tar -xzf /backups/kleinkauf-<fecha>-uploads.tgz -C /restore
kk up -d user-service product-service sales-service observability-service gateway-service
```

### Headers de seguridad

Caddy agrega en todo lo que sirve por HTTPS: HSTS (1 año, con subdominios), `X-Content-Type-Options`,
`Referrer-Policy` y `Permissions-Policy`. En el frontend, además, `X-Frame-Options: DENY` y la CSP.

La CSP está en **Report-Only**: no bloquea nada, solo avisa en la consola del navegador (`Content-Security-Policy-Report-Only`)
lo que bloquearía. Después de usar un rato la tienda y el panel sin que aparezcan avisos, cambiar en el Caddyfile
`Content-Security-Policy-Report-Only` por `Content-Security-Policy`.

### Buscadores (robots.txt y sitemap.xml)

Los resuelve Caddy por host, como la vista previa de los productos compartidos:

- En cada tienda, `/robots.txt` deja indexar lo público (home, catálogo, combos, productos y textos legales) y no el
  panel, el carrito, el checkout ni la cuenta, y apunta a `https://<tienda>.<dominio>/sitemap.xml`.
- `/sitemap.xml` va al gateway (`/share/<tienda>/sitemap.xml`): lo arma products con los productos de la tienda.
  Para que Google lo use, se puede cargar en Search Console por tienda.
- La plataforma y `api.` responden `Disallow: /`.

### Firewall

Docker publica sus puertos escribiendo reglas de iptables propias, así que las reglas comunes de ufw (`ufw allow`) no
deciden qué llega a los contenedores. Por eso en el compose solo Caddy se publica en todas las interfaces (80/443); el
frontend y el gateway van en `127.0.0.1`.

En este server el tráfico hacia los contenedores pasa además por la cadena **`allowlist-docker`**, definida en
`/etc/ufw/after.rules` y `/etc/ufw/after6.rules` (`DOCKER-USER` salta a ella). Descarta todo lo que entra por `eth0`
salvo lo que se permite explícitamente, así que 80 y 443 tienen que estar ahí antes del `DROP`:

```
-A allowlist-docker -p tcp -m conntrack --ctorigdstport 80 --ctdir ORIGINAL -j RETURN
-A allowlist-docker -p tcp -m conntrack --ctorigdstport 443 --ctdir ORIGINAL -j RETURN
-A allowlist-docker -p udp -m conntrack --ctorigdstport 443 --ctdir ORIGINAL -j RETURN
-A allowlist-docker -j DROP
```

Se usa `--ctorigdstport` porque después del DNAT de Docker el puerto de destino ya no es el original. Después de
editar esos archivos: `sudo ufw reload`. En ufw, además, `ufw allow 80/tcp`, `443/tcp`, `443/udp` y el puerto SSH.

Si Caddy no consigue certificados, lo primero es mirar que Let's Encrypt llegue al puerto 80: con
`sudo tcpdump -ni eth0 'tcp port 80'` se ve si los paquetes entran y si se responden. Después de arreglarlo,
`docker restart kleinkauf-caddy-1` hace que vuelva a intentar enseguida en vez de esperar su reintento.

---

## Pasar de sslip.io a un dominio propio

1. En el DNS del dominio, crear dos registros A a la IP del server: `dominio.com` y `*.dominio.com`.
2. En `~/kleinkauf/.env`, cambiar `PLATFORM_DOMAIN`, `PLATFORM_URL`, `PLATFORM_HOSTNAME` y `PUBLIC_API_URL`.
3. Volver a desplegar el backend y el frontend (Actions → Deploy → Run workflow en cada rama).

Las tiendas ya creadas guardan su URL con el dominio viejo, así que habría que actualizarlas.

---

## Archivos

| Archivo | Qué es |
|---|---|
| [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) | Workflow de GitHub Actions |
| [`backend/deploy/deploy.sh`](backend/deploy/deploy.sh) | Script que corre en el server: build y up de una parte |
| [`backend/deploy/docker-compose.prod.yml`](backend/deploy/docker-compose.prod.yml) | Compose de producción |
| [`backend/deploy/Dockerfile.service`](backend/deploy/Dockerfile.service) | Imagen de cada servicio del backend |
| [`backend/deploy/caddy/Caddyfile`](backend/deploy/caddy/Caddyfile) | Configuración de Caddy |
| [`backend/deploy/backup/backup.sh`](backend/deploy/backup/backup.sh) | Backup diario (servicio `backup`) |
| [`backend/deploy/.env.example`](backend/deploy/.env.example) | Variables del `.env` |
| [`frontend/Dockerfile`](frontend/Dockerfile) | Imagen del frontend (build de Vite + nginx) |
