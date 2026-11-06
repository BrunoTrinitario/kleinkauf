# Email (Resend)

Platform emails (password recovery and order confirmation) are sent through **Resend**, from the
`mail.kleinkauf.site` subdomain. Each email carries the store's name as sender and the store's contact address as
`Reply-To`:

```
From:     "Tienda Lucía Deco" <no-reply@mail.kleinkauf.site>
Reply-To: contacto@tiendalucia.com
```

- Account: Resend, domain `mail.kleinkauf.site` (id `1b6c1fb8-8378-44d7-b4d4-4992c9271869`), region `sa-east-1`
  (São Paulo). The region cannot be changed.
- Domain panel: <https://resend.com/domains/1b6c1fb8-8378-44d7-b4d4-4992c9271869>
- DNS for `kleinkauf.site`: **Hostmar** (`ns3.hostmar.com`, `ns4.hostmar.com`).

## DNS records

Already set up and verified. Kept here in case the domain or the DNS provider changes.

| Type | Name | Value | Priority | Purpose |
|---|---|---|---|---|
| TXT | `resend._domainkey.mail.kleinkauf.site` | `p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDDwY62jLWM0zRbMwXXBgOo/4OSK3K3jXh9M35eK52KT9sUllUv894ZNT8DLyQ/BSiXMh+DfzMN/1h2Jh9azAP3PaKU5f/OzlF6jL/sYhl/dSrESTgAZWO8jihc/CJ5O3CGIueRNtI1LecJxtW3In1AUIw01rs5GgjD3SaGaJyE9QIDAQAB` | — | **DKIM**: signs the emails |
| MX | `send.mail.kleinkauf.site` | `feedback-smtp.sa-east-1.amazonses.com` | 10 | Return-Path: where bounces go |
| TXT | `send.mail.kleinkauf.site` | `v=spf1 include:amazonses.com ~all` | — | **SPF**: authorizes Resend to send for us |
| CNAME | `rsend.mail.kleinkauf.site` | `send.forge.rmta.net` | — | Required by Resend for sending |
| TXT | `_dmarc.kleinkauf.site` | `v=DMARC1; p=none;` | — | **DMARC**: required by Gmail and Yahoo |

To check a record:

```bash
nslookup -type=TXT resend._domainkey.mail.kleinkauf.site 8.8.8.8
```

If a record does not show up, it is almost always the name: the panel may have appended the domain twice
(`send.mail.kleinkauf.site.kleinkauf.site`). See
[Resend's guide](https://resend.com/docs/knowledge-base/what-if-my-domain-is-not-verifying).

**Hardening DMARC later:** with `p=none` servers only report and block nothing. Once emails have been arriving fine for
a few weeks, switch to `v=DMARC1; p=quarantine;` so forged emails with our domain go to spam. To receive reports, add
`rua=mailto:<an address of ours>`.

## Backend

Every email is sent by **users** (`MailService`), over SMTP to `smtp.resend.com:587`:

| Email | Requested by | When |
|---|---|---|
| Password recovery | users (`POST /api/v1/auth/forgot-password`) | The user asks for it at `/recuperar` |
| Order confirmation | sales, through `POST /api/internal/mail/order-confirmation` | When an online order is created (not in-store sales) |

- **Sender** (`StoreMailIdentity`): the store name comes from its settings in products (`GET /api/v1/catalog/store`).
  If products does not answer, the name derived from the identifier is used ("mi_tienda" → "Mi tienda"). Without a
  store, `KleinKauf`. The address is always `mail.from.address`.
- **Reply-To:** the store owner's email (its first active `ADMINISTRADOR`). If the buyer replies, it reaches the
  store.
- **Order confirmation:** number, items, discount, total, delivery, transfer details, expiration (Argentina time) and
  the link `<store url>/pedido/<number>`. sales requests it in the background (it does not slow the checkout down) and
  without retries. users sends it **only once per order**: it keeps a `mail:order:<schema>:<number>` mark in Redis for
  2 days and deletes it if sending fails.
- If the mail server fails, it is logged as `ERROR` and the request carries on.

### Variables

| Variable | Value |
|---|---|
| `MAIL_PASSWORD` | Resend **send-only** key restricted to `mail.kleinkauf.site` (name "kleinkauf-backend (send only)", id `b2792571-09ce-4f00-ba9d-befad14463ab`). Without it no email goes out |
| `MAIL_FROM` | Optional. Defaults to `no-reply@mail.kleinkauf.site` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME` | Optional. Default to `smtp.resend.com`, `587`, `resend` |

In development, without `MAIL_PASSWORD` emails fail and are only logged. To really test them, set the key in the
`user-service` container's environment.

### Resend free plan limits

3,000 emails per month and 100 per day. Beyond that Resend answers with an error and the email is only logged. Usage
can be checked with `npx resend-cli@latest usage`.
