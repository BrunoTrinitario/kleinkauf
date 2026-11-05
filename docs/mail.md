# Mails (Resend)

Los mails de la plataforma (recupero de contraseña y confirmación de compra) salen por **Resend**, desde el
subdominio `mail.kleinkauf.site`. Cada mail lleva el nombre de la tienda como remitente y su mail de contacto como
`Reply-To`:

```
From:     "Tienda Lucía Deco" <no-reply@mail.kleinkauf.site>
Reply-To: contacto@tiendalucia.com
```

- Cuenta: Resend, dominio `mail.kleinkauf.site` (id `1b6c1fb8-8378-44d7-b4d4-4992c9271869`), región `sa-east-1`
  (São Paulo). La región no se puede cambiar.
- Panel del dominio: <https://resend.com/domains/1b6c1fb8-8378-44d7-b4d4-4992c9271869>
- DNS de `kleinkauf.site`: **Hostmar** (`ns3.hostmar.com`, `ns4.hostmar.com`).

## DNS en Hostmar

Se hace una sola vez. Mientras falte, Resend no deja mandar desde `mail.kleinkauf.site`.

### 1. Entrar a la zona DNS

1. Entrar al panel de Hostmar (área de clientes o cPanel).
2. Abrir **Editor de zona** (o "Zone Editor" / "Administrar DNS") del dominio `kleinkauf.site`.
3. Antes de tocar nada, fijarse que no exista ya un registro con los nombres de abajo. Si hay uno con el mismo nombre y
   tipo, editarlo en vez de agregar otro.

### 2. Cargar los registros

Hostmar pide el **nombre completo** (con `kleinkauf.site` al final). TTL: el que venga por defecto (o 3600).

| # | Tipo | Nombre | Valor | Prioridad | Para qué |
|---|---|---|---|---|---|
| 1 | TXT | `resend._domainkey.mail.kleinkauf.site` | `p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDDwY62jLWM0zRbMwXXBgOo/4OSK3K3jXh9M35eK52KT9sUllUv894ZNT8DLyQ/BSiXMh+DfzMN/1h2Jh9azAP3PaKU5f/OzlF6jL/sYhl/dSrESTgAZWO8jihc/CJ5O3CGIueRNtI1LecJxtW3In1AUIw01rs5GgjD3SaGaJyE9QIDAQAB` | — | **DKIM**: firma los mails; prueba que salen de nosotros |
| 2 | MX | `send.mail.kleinkauf.site` | `feedback-smtp.sa-east-1.amazonses.com` | 10 | Return-Path: adonde vuelven los rebotes |
| 3 | TXT | `send.mail.kleinkauf.site` | `v=spf1 include:amazonses.com ~all` | — | **SPF**: autoriza a Resend a mandar por nosotros |
| 4 | CNAME | `rsend.mail.kleinkauf.site` | `send.forge.rmta.net` | — | Lo pide Resend para el envío |
| 5 | TXT | `_dmarc.kleinkauf.site` | `v=DMARC1; p=none;` | — | **DMARC**: Gmail y Yahoo lo exigen |

Campos del formulario de Hostmar:

- **Nombre**: a qué (sub)dominio se le pega el registro. Es la "dirección" que van a consultar Gmail, Outlook, etc.
- **Tipo**: qué clase de dato es. `TXT` es texto libre (ahí van DKIM, SPF y DMARC); `MX` dice qué servidor recibe
  mails para ese nombre; `CNAME` dice "este nombre es un alias de otro".
- **Valor / Contenido / Destino**: el dato en sí (la clave, el servidor o el alias).
- **Prioridad** (solo MX): si hay varios servidores, se prueba primero el de número más bajo. Acá hay uno solo: 10.
- **TTL**: cuántos segundos pueden guardar en caché la respuesta los demás servidores. Más bajo = los cambios se ven
  antes. El de por defecto está bien.

Cuidados:

- **DKIM (1)**: el valor va en una sola línea, sin espacios, saltos ni comillas de más. Si el panel lo parte en
  pedazos entre comillas, está bien; si le agrega espacios en el medio, no.
- **MX (2)**: la prioridad va en su propio campo, no pegada al valor.
- **SPF (3)**: va en `send.mail.kleinkauf.site`, no en la raíz. No toca el SPF de `kleinkauf.site` si lo hubiera.
- **DMARC (5)**: va en la raíz (`_dmarc.kleinkauf.site`) y vale para todos los subdominios. Hoy no existe ninguno.
  Si ya hubiera uno, no agregar otro (solo puede haber uno): dejar el que está.
- **No tocar** los registros que ya están (A, `*`, MX del dominio, etc.).

### 3. Avisar y verificar

Cuando estén cargados, se verifica el dominio en Resend (desde el panel, botón **Verify**, o con
`npx resend-cli@latest domains verify 1b6c1fb8-8378-44d7-b4d4-4992c9271869`). La propagación tarda de minutos a unas
horas; se puede comprobar cada registro con:

```bash
nslookup -type=TXT resend._domainkey.mail.kleinkauf.site 8.8.8.8
```

```bash
nslookup -type=MX send.mail.kleinkauf.site 8.8.8.8
```

```bash
nslookup -type=TXT _dmarc.kleinkauf.site 8.8.8.8
```

Si después de unas horas alguno sigue sin aparecer, casi siempre es el nombre: está escrito distinto o el panel le
agregó el dominio dos veces (`send.mail.kleinkauf.site.kleinkauf.site`). Ver la
[guía de Resend](https://resend.com/docs/knowledge-base/what-if-my-domain-is-not-verifying).

### 4. Más adelante: endurecer DMARC

Con `p=none` los servidores solo informan; no bloquean nada. Cuando los mails lleguen bien durante unas semanas, se
puede pasar a `v=DMARC1; p=quarantine;` para que los mails falsos con nuestro dominio vayan a spam. Para recibir
informes, agregar `rua=mailto:<un mail nuestro>`.

## Backend

Todos los mails los manda **users** (`MailService`), por SMTP a `smtp.resend.com:587`:

| Mail | Quién lo pide | Cuándo |
|---|---|---|
| Recupero de contraseña | users (`POST /api/v1/auth/forgot-password`) | El usuario lo pide en `/recuperar` |
| Confirmación de compra | sales, por `POST /api/internal/mail/order-confirmation` | Al crear un pedido online (no las ventas presenciales) |

- **Remitente** (`StoreMailIdentity`): el nombre de la tienda sale de sus ajustes en products
  (`GET /api/v1/catalog/store`). Si products no responde, se usa el derivado del identificador ("mi_tienda" →
  "Mi tienda"). Sin tienda, `KleinKauf`. La dirección siempre es `mail.from.address`.
- **Reply-To:** el mail del dueño de la tienda (su primer `ADMINISTRADOR` activo). Así, si el comprador responde, le
  llega a la tienda.
- **Confirmación de compra:** número, ítems, descuento, total, entrega, datos para transferir, vencimiento (en hora
  argentina) y el link `<url de la tienda>/pedido/<número>`. sales la pide en segundo plano (no demora el checkout)
  y sin reintentos. users la manda **una sola vez por pedido**: guarda la marca `mail:order:<schema>:<número>` en
  Redis por 2 días y la borra si el envío falla.
- Si el servidor de mail falla, queda en el log como `ERROR` y la request sigue igual.

### Variables

| Variable | Valor |
|---|---|
| `MAIL_PASSWORD` | Clave de Resend **solo de envío** limitada a `mail.kleinkauf.site` (nombre "kleinkauf-backend (send only)", id `b2792571-09ce-4f00-ba9d-befad14463ab`). Sin ella no sale ningún mail |
| `MAIL_FROM` | Opcional. Por defecto `no-reply@mail.kleinkauf.site` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME` | Opcionales. Por defecto `smtp.resend.com`, `587`, `resend` |

En desarrollo, sin `MAIL_PASSWORD` los mails fallan y quedan en el log, como antes. Para probarlos de verdad, cargar
la clave en el entorno del contenedor `user-service`.

### Límites del plan gratis de Resend

3.000 mails por mes y 100 por día. Si se pasa, Resend responde error y el mail queda en el log. El uso se ve con
`npx resend-cli@latest usage`.
