// The platform and the stores share the build: they are told apart by the host.
// On the platform (no subdomain, e.g. localhost:2999) there is no store; on <store>.localhost:2999 there is.
const PLATFORM_HOSTNAME = import.meta.env.VITE_PLATFORM_HOSTNAME ?? "localhost"

export const IS_PLATFORM = window.location.hostname === PLATFORM_HOSTNAME

export const SUPERADMIN_ROLE = "SUPERADMIN"
