// La plataforma y las tiendas comparten el build: se distinguen por el host.
// En la plataforma (sin subdominio, ej. localhost:2999) no hay tienda; en <tienda>.localhost:2999 si.
const PLATFORM_HOSTNAME = import.meta.env.VITE_PLATFORM_HOSTNAME ?? "localhost"

export const IS_PLATFORM = window.location.hostname === PLATFORM_HOSTNAME

export const SUPERADMIN_ROLE = "SUPERADMIN"
