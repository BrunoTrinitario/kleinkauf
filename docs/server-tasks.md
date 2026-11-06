# Server tasks and technical debt

What is left to do on the server and in the code. New features are in [next-features.md](next-features.md).

## E. Server

Tasks on the VPS (see [deployment.md](deployment.md)):

1. **Email**: set `MAIL_PASSWORD` (the Resend send-only key, see [mail.md](mail.md)) in `~/kleinkauf/.env` and pass it
   to the containers that send email. Without it no email goes out. Confirm `mail.kleinkauf.site` shows as verified in
   Resend.
2. Change the initial superadmin's password.
3. SSH: key-only login (`PasswordAuthentication no`) and fail2ban.
4. **Backups**: the service is ready but `deploy.sh` (in `deploy/backend`) does not start it yet. Confirm whether it
   was started by hand on the server, and copy the backups off the server from time to time.
5. Paid domain: wildcard DNS (`*.domain`) and update `PLATFORM_DOMAIN` and the rest in `~/kleinkauf/.env`.

## D. Technical debt

- **Enum CHECKs**: `@Enumerated(EnumType.STRING)` columns have a `CHECK` that Hibernate creates with the values at that
  moment and `hbm2ddl update` does not update: after adding a new value, saving it fails in schemas that already
  existed. Before adding a value, drop that `CHECK` in every schema (or store the column as text, like
  `store_settings.theme`).
- **Duplicated image detection**: `FileManager.imageExtension` in products does the same as `ImageFiles` in
  kleinkauf-common. Move products to the common one.
- **Duplicated sales settings**: `sales_settings` is "one row per schema" but has an auto-increment id; if a store's
  first two saves arrive together, two rows are left. `store_settings` solves it with a fixed id and a retry.
- **Reviews**: code performance (slow queries, N+1, caching) and server performance (CPU, memory, heap, latency).
- **CSP**: move from Report-Only to `Content-Security-Policy` once no warnings show up in the console.
