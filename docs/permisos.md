# Permisos

Cómo se decide qué puede hacer cada usuario dentro de una tienda (tenant).

## Resumen

- Cada usuario tiene uno o más **roles** dentro de su tienda: `ADMINISTRADOR`, `EMPLEADO` o `CLIENTE`.
- Cada rol tiene una **máscara de permisos por área** (un número donde cada bit habilita un área).
- El **ADMINISTRADOR siempre tiene todos los permisos** y es el único que configura los de los demás roles.
- Los permisos de `EMPLEADO` y `CLIENTE` se configuran **por tienda**: cada emprendimiento decide qué puede hacer su
  personal.
- Aparte existe `SUPERADMIN`, que administra la plataforma (no una tienda) y vive en el schema `public`.
  Ver [tenants.md](tenants.md).
- **Ventas** todavía no tiene un área: el panel de ventas es solo del `ADMINISTRADOR`. Delegarlo a `EMPLEADO` (área
  `VENTAS`) está anotado para después en [ventas.md](ventas.md).

## Áreas

| Área | Bit | Qué habilita |
|---|---|---|
| `PRODUCTOS` | 1 | Crear, editar y borrar productos, sus imágenes, metadatos y destacados |
| `PRECIOS` | 2 | Ver costo y margen, editar precio y costo |
| `STOCK` | 4 | Ver y registrar movimientos de stock, ver la alerta de stock bajo |
| `COMBOS` | 8 | Crear, editar y borrar combos y cambiar su precio |
| `USUARIOS` | 16 | Gestionar los usuarios de la tienda. **No se delega**: solo `ADMINISTRADOR` |

La máscara es la suma de los bits de las áreas habilitadas:

| Máscara | Áreas |
|---|---|
| `0` | Ninguna |
| `4` | STOCK |
| `5` | PRODUCTOS + STOCK |
| `7` | PRODUCTOS + PRECIOS + STOCK |
| `15` | Todas las delegables (PRODUCTOS + PRECIOS + STOCK + COMBOS) |
| `31` | Todas (solo `ADMINISTRADOR`) |

Las áreas están definidas en `decor-common` (`com.decor.common.security.PermissionArea`).

## Permisos de cada rol

| Rol | Permisos | ¿Configurable? |
|---|---|---|
| `ADMINISTRADOR` | Siempre `31` (todas) | No: así nadie se queda afuera de su propia tienda |
| `EMPLEADO` | Por defecto `4` (STOCK) | Sí, cualquier combinación de `0` a `15` |
| `CLIENTE` | Por defecto `0` (ninguna) | Sí, cualquier combinación de `0` a `15` |
| `SUPERADMIN` | `0` en las tiendas | No (administra la plataforma, no una tienda) |

- Los valores por defecto son los permisos que tenían los roles antes de existir esta configuración, así que una
  tienda nueva (o una existente) funciona igual hasta que el administrador cambie algo.
- Si un usuario tiene **varios roles**, sus permisos son la **unión** de las máscaras de sus roles.
- `USUARIOS` (16) **no se puede dar** a `EMPLEADO` ni a `CLIENTE`. Si alguien pudiera gestionar usuarios sin ser
  administrador, podría darse a sí mismo el rol `ADMINISTRADOR`.

## Configurar los permisos

Solo un `ADMINISTRADOR` de la tienda (o sea, desde la URL de la tienda y con su token).

### Ver los permisos de cada rol

```http
GET /api/v1/users/roles/permissions
```

```json
[
  { "role": "ADMINISTRADOR", "permissions": 31 },
  { "role": "EMPLEADO", "permissions": 4 },
  { "role": "CLIENTE", "permissions": 0 }
]
```

### Cambiar los permisos de un rol

```http
PUT /api/v1/users/roles/EMPLEADO/permissions
Content-Type: application/json

{ "permissions": 7 }
```

```json
{ "role": "EMPLEADO", "permissions": 7 }
```

| Caso | Respuesta |
|---|---|
| Rol `EMPLEADO` o `CLIENTE` con máscara entre `0` y `15` | 200 |
| Máscara con `USUARIOS` (16), bits desconocidos (32, 64...) o negativa | 400 |
| Rol `ADMINISTRADOR` o `SUPERADMIN` | 400 (no se configuran) |
| Rol inexistente | 404 |
| Falta `permissions` en el body | 400 |
| Usuario que no es `ADMINISTRADOR` | 403 |

## Qué permiso pide cada endpoint

### products

| Endpoint | Permiso |
|---|---|
| `POST /api/v1/products` | PRODUCTOS (sin PRECIOS, el precio y el costo se **ignoran**) |
| `PUT /api/v1/products/{id}` | PRODUCTOS |
| `DELETE /api/v1/products/{id}` | PRODUCTOS |
| `POST /api/v1/products/{id}/images`, `DELETE /api/v1/products/images/{imageId}` | PRODUCTOS |
| `PUT /api/v1/products/{id}/metadata` | PRODUCTOS |
| `PUT /api/v1/products/{id}/featured` | PRODUCTOS |
| `GET` / `PUT /api/v1/products/{id}/pricing` | PRECIOS (incluye las unidades vendidas) |
| `GET /api/v1/products/export`, `GET /api/v1/products/import/template`, `POST /api/v1/products/import` | PRODUCTOS **y** PRECIOS (el archivo trae precio y costo) |
| `POST` / `GET /api/v1/products/{id}/stock/movements`, `GET /api/v1/products/stock/low` | STOCK |
| `POST`, `PUT`, `DELETE /api/v1/bundles/**` (incluye `PUT /{id}/price`) | COMBOS |
| `GET /api/v1/products`, `/all`, `/{id}`, `/featured`, `/search` y `GET /api/v1/bundles/**` (muestran el stock exacto) | Cualquier área del panel (PRODUCTOS, PRECIOS, STOCK o COMBOS) |
| `GET /api/v1/catalog/**`, `GET /api/v1/products/{id}/images`, `GET /api/v1/products/{id}/metadata` | Ninguno: es público |
| `/api/internal/products/**` (reservas de stock y vendidos) | Token interno de los microservicios (rol `INTERNAL`); ningún usuario |

### sales

| Endpoint | Permiso |
|---|---|
| `GET /api/v1/sales/checkout`, `POST /api/v1/sales/orders` | Ninguno: es público. Si viene logueado, el pedido queda asociado al usuario |
| `POST /api/v1/sales/orders/lookup`, `POST /api/v1/sales/orders/{number}/receipt`, `POST /api/v1/sales/orders/{number}/cancel` | Ninguno, pero piden el **email del pedido** (si no coincide, 404) |
| `GET /api/v1/sales/orders/mine` | Cualquier usuario logueado (ve solo sus pedidos) |
| `/api/v1/sales/admin/**` (pedidos, ventas del local, estados, comprobantes, reportes y configuración) | Rol `ADMINISTRADOR`. Un `EMPLEADO` no entra aunque tenga todas las áreas |

### users

| Endpoint | Permiso |
|---|---|
| `/api/v1/users/**` (gestión de usuarios, estadísticas y configuración de permisos) | Rol `ADMINISTRADOR` (área USUARIOS, no delegable) |
| `/api/v1/platform/admin/**` | Rol `SUPERADMIN` |

## Cómo funciona por dentro

```
users                                   products y sales
─────                                   ────────────────
role_permissions (por schema)
  │ RolePermissionService.maskFor(rol)
  ▼
RoleDto { type, permissions }  ──►  Redis  user:<id>:<tenant>  ──►  caché local (1 minuto)
  │                                                                   │
  ▼                                                                   ▼
HeaderAuthFilter                                              HeaderAuthFilter
  │                                                                   │
  └─► CustomUserDetails (decor-common): ROLE_<rol> + PERM_<área> ◄──┘
                     │
                     ▼
              SecurityConfig: hasRole(...) / hasAuthority("PERM_STOCK")
```

1. **Dónde se guarda:** en la tabla `role_permissions` del schema de cada tienda, una fila por rol
   (`type`, `permissions`). Si un rol no tiene fila, usa su valor por defecto.
2. **Quién la calcula:** users (`RolePermissionService.maskFor`). `ADMINISTRADOR` devuelve siempre 31.
3. **Cómo viaja:** en `RoleDto.permissions`, junto con el tipo de rol. Lo devuelven el login, `GET /api/v1/auth/me`,
   los endpoints de roles y el endpoint interno que usan products y sales (`/api/internal/users/roles/{id}`).
4. **Cómo se aplica:** `CustomUserDetails` (en `decor-common`) convierte los roles en authorities `ROLE_<rol>` y la
   unión de las máscaras en `PERM_<área>`. Los `SecurityConfig` de cada servicio piden esas authorities.
5. **Casos que no se pueden resolver solo con la URL:** el alta de producto revisa en el servicio si el usuario tiene
   PRECIOS (`SecurityUtil.hasPermission`) para decidir si usa o ignora el precio y el costo.

## Cuánto tarda en aplicarse un cambio

- **users:** al instante (lee los permisos de la base en cada request).
- **products y sales:** **menos de 1 minuto**. Al cambiar los permisos, users borra de Redis los roles cacheados de
  todos los usuarios de esa tienda (`user:*:<tenant>`, con `SCAN`), y products y sales guardan los roles en su caché
  local solo 1 minuto.

## Historia

Antes, `Role` tenía un campo `permission` (int) por usuario que nunca se cargaba ni se revisaba: todo se decidía por
rol con reglas fijas. Se reemplazó por la tabla `role_permissions`. La migración de users borra la columna vieja
(`ALTER TABLE <schema>.roles DROP COLUMN IF EXISTS permission`) porque quedaba `NOT NULL` y hacía fallar las altas de
roles; siempre valía 0, así que no se perdió ningún dato.
