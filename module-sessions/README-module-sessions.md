# module-sessions — Spring Security con HTTP Sessions

> Módulo educativo del proyecto `spring-security-lab`.  
> Puerto: **8085** | Store: **PostgreSQL + Redis**

---

## ¿Qué problema resuelve este módulo?

En `module-jwt` el servidor era **stateless** — no recordaba nada entre requests, toda la identidad viajaba dentro del token. Aquí el servidor es **stateful** — crea una sesión, la guarda, y solo le da al cliente un ID para identificarla.

---

## Concepto central — Stateless vs Stateful

| | JWT (module-jwt) | HTTP Sessions (este módulo) |
|---|---|---|
| ¿Dónde vive la identidad? | Dentro del token (cliente) | En el servidor (memoria / Redis) |
| ¿Qué viaja en cada request? | `Authorization: Bearer <token>` | `Cookie: SESSION=<id en Base64>` |
| ¿Puede revocar inmediatamente? | No — el token sigue válido hasta expirar | Sí — `invalidateHttpSession(true)` |
| ¿Escala fácil? | Sí — cualquier instancia valida el token | Requiere store compartido (Redis) |
| `SessionCreationPolicy` | `STATELESS` | `IF_REQUIRED` |

**Pregunta de entrevista frecuente:** *"¿Cómo invalidas un JWT?"*  
Respuesta honesta: no puedes sin una blacklist externa. Con sessions es trivial — el servidor borra la sesión y el ID queda inútil de inmediato.

---

## Arquitectura del módulo

```
Request
  → Tomcat
  → Spring Security Filter Chain    ← aquí vive el logout, CSRF, session lookup
  → DispatcherServlet
  → @RestController                 ← aquí viven tus endpoints normales
```

Spring Security intercepta los requests **antes** de que lleguen al `DispatcherServlet`. Por eso el endpoint `/auth/logout` no necesita un método en ningún `@RestController` — Spring lo consume en la capa de filtros.

---

## Componentes clave

### `SecurityConfig.java`
El corazón del módulo. Configura:

- **`sessionManagement`** — `SessionCreationPolicy.IF_REQUIRED` + `maximumSessions`
- **`exceptionHandling`** — respuestas JSON para 401 y 403
- **`logout`** — invalida la sesión e instruye al cliente a borrar la cookie
- **`DaoAuthenticationProvider`** — configurado como objeto local dentro del `filterChain`, **no como `@Bean` global** (causa conflictos con el `AuthenticationManager`)

> ⚠️ Lección aprendida: declarar el `DaoAuthenticationProvider` como `@Bean` hace que Spring lo registre globalmente y genera el warning *"UserDetailsService beans will not be used"*, que termina en 403 inexplicables.

### `UserDetailsServiceImpl.java`
Carga el usuario para autenticación. En este módulo usa usuarios en memoria. En producción se conecta a un `UserRepository`.

### `AuthController.java`
Maneja login, logout info y sesión activa. Ver métodos:
- `POST /auth/login` — autentica, crea sesión, guarda `SecurityContext`
- `GET /auth/me` — retorna el usuario autenticado actual
- `GET /auth/sessions` — retorna metadata de la sesión activa (id, TTL, timestamps)
- `GET /auth/hash` — utilidad temporal para generar hashes BCrypt (remover en producción)

### `DemoController.java`
Endpoints protegidos para probar autorización:
- `GET /hello` — requiere autenticación
- `GET /admin/dashboard` — requiere `ROLE_ADMIN` via `@PreAuthorize`

---

## El ciclo de vida de una sesión

```
1. POST /auth/login
   → Spring autentica las credenciales
   → Crea HttpSession con UUID
   → Guarda SecurityContext en la sesión
   → Responde con: Set-Cookie: SESSION=<UUID en Base64>

2. GET /hello (con la cookie)
   → Spring decodifica Base64 → UUID
   → Busca la sesión en Redis por UUID
   → Reconstruye el SecurityContext
   → Autoriza el request ✅

3. POST /auth/logout
   → Spring llama invalidateHttpSession(true)
   → Redis borra el HASH de la sesión
   → Responde con: Set-Cookie: SESSION=""; Max-Age=0
   → El UUID queda inútil ✅

4. GET /hello (con la cookie vieja)
   → Spring busca el UUID en Redis → no existe
   → 401 Unauthorized ✅
```

---

## La cookie SESSION vs JSESSIONID

Cuando agregas Spring Session con Redis, la cookie **cambia de nombre**:

| | Sin Spring Session | Con Spring Session + Redis |
|---|---|---|
| Nombre de la cookie | `JSESSIONID` | `SESSION` |
| Formato del valor | UUID directo | UUID en **Base64** |
| Quién la gestiona | Tomcat | Spring Session |
| Dónde se guarda | Memoria JVM | Redis |

El valor de la cookie es el UUID codificado en Base64. Spring lo decodifica en cada request para buscar la sesión en Redis.

```bash
# Verificar que son el mismo valor
echo "NzJiNDJhYTYtZTA4Ni00NjFhLTg3NTYtMjJiNGVhOWY5Njdk" | base64 -d
# output: 72b42aa6-e086-461a-8756-22b4ea9f967d
```

> ⚠️ Error común: mandar el UUID directo en el header `Cookie` en lugar del valor Base64 — Spring no lo reconoce y responde 401.

---

## Spring Session + Redis

### ¿Por qué Redis?

Sin Redis las sesiones viven en la **memoria JVM**. Con múltiples instancias:

```
Login  → instancia A  (sesión en memoria de A)
Request → instancia B  (B no conoce esa sesión) → 401 ❌
```

Con Redis todas las instancias comparten el mismo store:

```
Login  → instancia A  (sesión en Redis)
Request → instancia B  (busca en Redis) → 200 ✅
```

Esto elimina la necesidad de **sticky sessions** en el load balancer — cualquier instancia puede atender cualquier request.

### Estructura en Redis

Spring Session guarda cada sesión como un `HASH` en Redis:

```
HGETALL spring:session:sessions:<uuid>

sessionAttr:SPRING_SECURITY_CONTEXT  → SecurityContext serializado (Java)
creationTime                          → timestamp Long serializado
maxInactiveInterval                   → 1800 (segundos)
lastAccessedTime                      → timestamp Long serializado
```

El TTL se resetea automáticamente en cada request — esto es **sliding expiration**.

```bash
# Ver el TTL restante de una sesión
TTL spring:session:sessions:<uuid>
# Responde en segundos. -2 = ya expiró y fue eliminada por Redis
```

### ⚠️ Seguridad — el password hash en Redis

Por defecto Spring serializa el objeto `User` completo dentro del `SecurityContext`, incluyendo el **password hash**. Para evitarlo, al crear el token de autenticación en el controller el segundo argumento debe ser `null`:

```java
// Ver AuthController.java — constructor de UsernamePasswordAuthenticationToken
new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
//                                                    ↑
//                              null aquí = no guardar credentials en la sesión
```

---

## Múltiples dispositivos y sesiones concurrentes

Configurado en `SecurityConfig.java` dentro de `sessionManagement`:

```
maximumSessions(1)              → máximo de sesiones activas por usuario
maxSessionsPreventsLogin(false) → false: expira la sesión anterior (comportamiento Netflix)
                                  true:  bloquea el nuevo login
```

Con `maximumSessions(1)` y `false`: login desde Chrome invalida la sesión de Firefox automáticamente. Spring Session con Redis maneja esto via el índice de sesiones por usuario:

```bash
KEYS spring:session:index:*
# muestra el índice de sesiones activas por usuario
```

---

## Manejo de excepciones de seguridad

Configurado en `SecurityConfig.java` con `.exceptionHandling()`.

| Handler | Cuándo se dispara | HTTP Status |
|---|---|---|
| `authenticationEntryPoint` | No hay sesión válida / sesión expirada | 401 |
| `accessDeniedHandler` | Sesión válida pero sin el rol requerido | 403 |

Sin esta configuración Spring devuelve HTML por defecto — inaceptable para una API REST.

---

## CSRF — cuándo habilitarlo y cuándo no

| Escenario | CSRF |
|---|---|
| API REST consumida por mobile/Postman/curl | Deshabilitar — los clientes no son browsers |
| App con frontend HTML/Thymeleaf en el mismo dominio | Habilitar — los browsers envían cookies automáticamente |
| API REST con frontend SPA (React/Angular) en dominio diferente | Deshabilitar + configurar CORS correctamente |

Con sessions y un frontend HTML en el mismo servidor, CSRF es importante porque el browser envía la cookie `SESSION` automáticamente en cada request — un atacante podría explotarlo desde otro sitio.

---

## Endpoints disponibles

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/auth/login` | Público | Login con `username` y `password` en JSON |
| GET | `/auth/me` | Autenticado | Retorna usuario y roles actuales |
| GET | `/auth/sessions` | Autenticado | Metadata de la sesión activa |
| POST | `/auth/logout` | Autenticado | Invalida la sesión en Redis |
| GET | `/hello` | Autenticado | Endpoint de prueba |
| GET | `/admin/dashboard` | `ROLE_ADMIN` | Endpoint restringido por rol |

---

## Flujo de prueba con curl

```bash
# 1. Login
curl -v -X POST http://localhost:8085/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'
# Captura el valor de Set-Cookie: SESSION=<BASE64>

# 2. Request protegido
curl http://localhost:8085/hello \
  -H "Cookie: SESSION=<BASE64>"

# 3. Logout
curl -X POST http://localhost:8085/auth/logout \
  -H "Cookie: SESSION=<BASE64>"

# 4. Mismo request después del logout → 401
curl http://localhost:8085/hello \
  -H "Cookie: SESSION=<BASE64>"

# 5. Verificar que la sesión fue borrada de Redis
docker exec -it sessions-redis redis-cli KEYS "spring:session:*"
```

---

## Preguntas de entrevista que cubre este módulo

**Conceptuales:**
- ¿Cuál es la diferencia entre autenticación stateless y stateful?
- ¿Cómo invalidas una sesión de usuario inmediatamente?
- ¿Cómo escalas horizontalmente una aplicación que usa HTTP sessions?
- ¿Qué son las sticky sessions y por qué queremos evitarlas?
- ¿Cuándo usarías sessions sobre JWT y viceversa?
- ¿Qué es CSRF y cuándo es relevante?

**Técnicas:**
- ¿Qué hace `SessionCreationPolicy.IF_REQUIRED` vs `STATELESS`?
- ¿Qué diferencia hay entre `authenticationEntryPoint` y `accessDeniedHandler`?
- ¿Qué pasa si tienes `maximumSessions(1)` y el usuario hace login desde dos dispositivos?
- ¿Cómo funciona el sliding expiration en Spring Session?
- ¿Por qué no debes guardar el password hash en la sesión de Redis?

---

## Stack del módulo

- Spring Boot 3.3.5
- Spring Security
- Spring Session Data Redis
- Spring Data JPA
- PostgreSQL (usuarios en memoria en este lab)
- Redis 7 (store de sesiones)
- Lombok
