# module-auth0 — Spring Security con OAuth2 + Auth0

> Módulo educativo del proyecto `spring-security-lab`.  
> Puerto: **8083** | Authorization Server: **Auth0 (SaaS)**

---

## ¿Qué es Auth0?

Auth0 es Keycloak como servicio — no instalas ni operas nada. Pagas por usuarios activos mensuales. El protocolo es el mismo (OAuth2 + OpenID Connect), solo cambia quién opera el servidor y cómo estructura los claims en el token.

---

## Configuración del servicio Auth0 — resumen

Lo que se hace una sola vez en el dashboard de Auth0:

1. **Crear una API** — representa tu Resource Server. Define el `audience` (identificador único de tu API) y el algoritmo de firma (RS256).
2. **Definir Permissions** — los permisos que tu API expone, por ejemplo `read:data`, `admin:data`. En Auth0 se definen en la API, no globalmente.
3. **Habilitar RBAC** — activar "Enable RBAC" y "Add Permissions in the Access Token" en la API para que los permisos viajen dentro del JWT.
4. **Crear Roles** — agrupar permissions. El rol `admin` tiene `read:data` + `admin:data`, el rol `user` solo `read:data`.
5. **Crear Usuarios** — crear los usuarios y asignarles roles.
6. **Configurar la Application** — habilitar el grant type `password` en Advanced Settings (para pruebas con curl). En producción se usa Authorization Code Flow.
7. **Autorizar la Application a usar la API** — en la API → Application Access → autorizar el client.
8. **Configurar Default Directory** — en Tenant Settings indicar `Username-Password-Authentication` como directorio por defecto.

---

## Keycloak vs Auth0 — diferencias clave

### ¿Quién lo opera?

| | Keycloak | Auth0 |
|---|---|---|
| Instalación | Tú (Docker, servidor propio) | Nadie — es SaaS |
| Costo | Gratis (open source) | Gratis hasta cierto límite, luego por usuario |
| Operación | Tu equipo de infraestructura | Auth0 |
| Control de datos | Total — datos en tu servidor | Auth0 guarda los datos |
| Soporte | Comunidad / Red Hat pagado | Soporte dedicado Auth0 |

### Diferencias en el token JWT

| | Keycloak | Auth0 |
|---|---|---|
| Dónde van los roles | `realm_access.roles` | `permissions` |
| Formato de roles | `ROLE_ADMIN`, `ROLE_USER` | `admin:data`, `read:data` |
| `audience` requerido | No | **Sí — obligatorio** |
| `issuer` termina en | `.../realms/security-lab` | `.../` con slash final |
| Username en el token | `preferred_username` | No viene — solo `sub` |
| `sub` formato | UUID de Keycloak | `auth0|<id>` |

### Diferencias en Spring

| | Keycloak | Auth0 |
|---|---|---|
| `JwtAuthenticationConverter` | Lee `realm_access.roles` con lambda | Lee `permissions` con `setAuthoritiesClaimName` |
| `JwtDecoder` personalizado | No necesario | **Sí — para validar `audience`** |
| `@PreAuthorize` | `hasRole('ROLE_ADMIN')` | `hasAuthority('admin:data')` |
| `@AuthenticationPrincipal` | `Jwt jwt` | `Jwt jwt` — idéntico |
| `issuer-uri` | `http://localhost:8080/realms/security-lab` | `https://tenant.us.auth0.com/` |

### Lo que es idéntico en ambos

- Dependencia: `spring-boot-starter-oauth2-resource-server`
- `@EnableWebSecurity` y `@EnableMethodSecurity`
- `SecurityFilterChain` con `.oauth2ResourceServer(...)`
- `SessionCreationPolicy.STATELESS`
- El principal es `Jwt`, no `UserDetails`
- Sin `JwtAuthFilter`, sin `JwtService`, sin `UserDetailsService`

---

## La diferencia más sutil — `hasRole` vs `hasAuthority`

```
Keycloak → hasRole('ROLE_ADMIN')
           Spring agrega "ROLE_" automáticamente → busca "ROLE_ROLE_ADMIN" ← ojo
           Por eso en el converter se usa authorityPrefix = ""
           y el rol ya viene con ROLE_ desde Keycloak

Auth0    → hasAuthority('admin:data')
           No hay prefijo ROLE_ — los permisos son "resource:action"
           hasAuthority busca el valor exacto sin agregar prefijos
```

**Regla para entrevistas:**
- `hasRole('X')` → Spring busca internamente `ROLE_X`
- `hasAuthority('X')` → Spring busca exactamente `X`

---

## Por qué Auth0 requiere validar el `audience`

En Keycloak tu app es la única que usa ese realm — no hay ambigüedad.

En Auth0 un mismo tenant puede tener múltiples APIs. Sin validar el `audience`, un token emitido para otra API de tu tenant sería aceptado por tu app. El `audience` garantiza que el token fue emitido específicamente para tu API.

```
Token sin audience correcto → 401 aunque la firma sea válida
```

---

## Estructura del JWT de Auth0

```json
{
  "iss": "https://dev-xxx.us.auth0.com/",
  "sub": "auth0|6a14b7a114616cbf55e88267",
  "aud": "https://spring-lab-api",
  "permissions": ["admin:data", "read:data"],
  "exp": 1779837877,
  "azp": "YGbRS0wqghzVa4Eq3brQo6ZrkDxzypDV"
}
```

Nota: Auth0 no incluye `preferred_username` ni `email` en el access token por defecto — solo el `sub`. Para obtener datos del usuario necesitas llamar al endpoint `/userinfo` de Auth0 con el token.

---

## Obtener un token

```bash
curl -X POST "https://TU_TENANT.us.auth0.com/oauth/token" \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "password",
    "username": "admin@lab.com",
    "password": "Password1!",
    "audience": "https://spring-lab-api",
    "client_id": "TU_CLIENT_ID",
    "client_secret": "TU_CLIENT_SECRET"
  }'
```

---

## Endpoints disponibles

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/public/hello` | Público | Sin token |
| GET | `/api/hello` | Autenticado | Saludo con subject del token |
| GET | `/api/me` | Autenticado | Claims del token |
| GET | `/api/admin` | `admin:data` | Solo admins |

---

## Preguntas de entrevista que cubre este módulo

- ¿Cuándo usarías Auth0 sobre Keycloak self-hosted?
- ¿Qué es el `audience` en OAuth2 y por qué importa?
- ¿Por qué Auth0 no incluye el username en el access token?
- ¿Qué diferencia hay entre `hasRole()` y `hasAuthority()` en Spring Security?
- ¿Qué es RBAC y cómo se configura en Auth0?
- ¿Cómo cambiarías tu app de Keycloak a Auth0? (Solo cambiar `issuer-uri`, `audience` y el converter — el resto igual)

---

## Stack del módulo

- Spring Boot 3.3.5
- Spring Security OAuth2 Resource Server
- Auth0 (SaaS)
- Sin base de datos
