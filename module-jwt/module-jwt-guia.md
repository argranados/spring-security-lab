# Autenticación JWT con Spring Boot — Guía de referencia

## Índice

1. [¿Qué es JWT?](#qué-es-jwt)
2. [Arquitectura del módulo](#arquitectura-del-módulo)
3. [Flujo de autenticación](#flujo-de-autenticación)
4. [Componentes y su interrelación](#componentes-y-su-interrelación)
5. [Código de referencia](#código-de-referencia)
6. [Pruebas con curl](#pruebas-con-curl)
7. [Preguntas frecuentes en entrevista](#preguntas-frecuentes-en-entrevista)
8. [Anotaciones más usadas de Spring Security](#anotaciones-más-usadas-de-spring-security)

---

## ¿Qué es JWT?

JWT (JSON Web Token) es un estándar abierto (RFC 7519) para transmitir información entre partes de forma segura como un objeto JSON firmado digitalmente.

Un token JWT tiene tres partes separadas por puntos:

```
eyJhbGciOiJIUzM4NCJ9        ← Header  (algoritmo de firma)
.eyJzdWIiOiJhZG1pbiJ9       ← Payload (claims: usuario, expiración, roles)
.3tWPalpY9uwBHU_UrJ-u...    ← Signature (firma con el secret key)
```

### Características clave

- **Stateless**: el servidor no guarda sesión. Toda la información está en el token.
- **Self-contained**: el token contiene todo lo necesario para verificar la identidad.
- **Firmado**: la firma garantiza que nadie alteró el contenido.
- **Expirable**: cada token tiene una fecha de expiración (`exp` claim).

### Access Token vs Refresh Token

| Aspecto | Access Token | Refresh Token |
|---|---|---|
| Duración | Corta (24h) | Larga (7 días) |
| Uso | Autenticar cada request | Obtener un nuevo access token |
| Dónde se envía | Header `Authorization` | Solo al endpoint `/auth/refresh` |
| Si es robado | Riesgo limitado (expira pronto) | Riesgo mayor (vida larga) |

---

## Arquitectura del módulo

```
module-jwt/
├── config/
│   └── SecurityConfig.java          ← Cadena de filtros de Spring Security
├── filter/
│   └── JwtAuthFilter.java           ← Intercepta y valida el token en cada request
├── service/
│   ├── JwtService.java              ← Genera y valida tokens con JJWT
│   ├── AuthService.java             ← Lógica de negocio del login
│   └── UserDetailsServiceImpl.java  ← Carga el usuario desde la fuente de datos
├── controller/
│   ├── AuthController.java          ← POST /auth/login, POST /auth/refresh
│   └── DemoController.java          ← Endpoints protegidos para pruebas
├── model/
│   ├── LoginRequest.java            ← DTO de entrada para login
│   ├── RefreshRequest.java          ← DTO de entrada para refresh
│   └── AuthResponse.java            ← DTO de respuesta con los tokens
└── ModuleJwtApplication.java        ← Entry point (puerto 8081)
```

### Dependencias Maven específicas del módulo

```xml
<!-- JJWT — librería para generar y validar tokens JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## Flujo de autenticación

### Flujo 1 — Login (obtener tokens)

```
Cliente
  │
  │  POST /auth/login
  │  { "username": "admin", "password": "password" }
  ▼
AuthController.login()
  │
  │  Llama a authService.login()
  ▼
AuthService.login()
  │
  │  authenticationManager.authenticate()
  │  Spring verifica las credenciales internamente:
  │    1. Carga el usuario → UserDetailsServiceImpl.loadUserByUsername()
  │    2. Compara el password con BCrypt
  │    3. Si no coincide → lanza BadCredentialsException → 401
  ▼
JwtService.generateToken()
JwtService.generateRefreshToken()
  │
  │  Construye los tokens con JJWT:
  │    - subject = username
  │    - issuedAt = ahora
  │    - expiration = ahora + duración
  │    - firma con HMAC-SHA384 y el secret key
  ▼
AuthController
  │
  └─▶ Responde 200 OK
      {
        "accessToken": "eyJ...",
        "refreshToken": "eyJ..."
      }
```

### Flujo 2 — Request a endpoint protegido

```
Cliente
  │
  │  GET /api/hello
  │  Authorization: Bearer eyJhbGciOiJIUzM4NCJ9...
  ▼
JwtAuthFilter.doFilterInternal()      ← OncePerRequestFilter: se ejecuta UNA vez por request
  │
  │  1. Lee el header Authorization
  │  2. Extrae el token (quita "Bearer ")
  │  3. Extrae el username del token (del claim "sub")
  │  4. Verifica que no haya autenticación activa en el contexto
  ▼
JwtService.isTokenValid()
  │
  │  - Verifica la firma con el secret key
  │  - Verifica que el username coincida
  │  - Verifica que el token no haya expirado
  ▼
SecurityContextHolder.setAuthentication()
  │
  │  Si el token es válido:
  │  Crea un UsernamePasswordAuthenticationToken
  │  y lo guarda en el SecurityContext del thread actual
  ▼
AuthorizationFilter (Spring Security)
  │
  │  Lee el SecurityContext — ve que hay una autenticación válida
  │  Verifica que el usuario tenga acceso al endpoint solicitado
  ▼
Controller protegido
  │
  └─▶ Responde 200 OK
      "Hola admin, tu token es válido."

  Si el token es inválido o falta:
  └─▶ Responde 403 Forbidden
```

### Flujo 3 — Refresh Token

```
Cliente
  │
  │  POST /auth/refresh
  │  { "refreshToken": "eyJ..." }
  ▼
AuthController.refresh()
  ▼
AuthService.refreshToken()
  │
  │  1. Extrae el username del refresh token
  │  2. Carga el usuario
  │  3. Valida el refresh token (firma + expiración)
  │  4. Si es válido → genera un nuevo accessToken
  ▼
  └─▶ Responde 200 OK
      {
        "accessToken": "eyJ...(nuevo)...",
        "refreshToken": "eyJ...(mismo)"
      }
```

---

## Componentes y su interrelación

### `SecurityConfig.java`

Es el núcleo de la configuración de seguridad. Define:

- Qué rutas son **públicas** (`/auth/**`) y cuáles requieren autenticación.
- Que la sesión sea **STATELESS** — Spring no crea ni usa `HttpSession`.
- Que el `JwtAuthFilter` se ejecute **antes** del `UsernamePasswordAuthenticationFilter` estándar de Spring.
- El `PasswordEncoder` (BCrypt) que se usará para comparar passwords.
- El `AuthenticationProvider` que conecta el `UserDetailsService` con el `PasswordEncoder`.

```java
http
    .csrf(csrf -> csrf.disable())
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/**").permitAll()
        .anyRequest().authenticated()
    )
    .sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    )
    .authenticationProvider(authenticationProvider())
    .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

**Por qué `STATELESS`?** En JWT no hay sesión del lado del servidor. El estado del usuario viaja en el token. Si usaras `IF_REQUIRED` (por defecto), Spring intentaría crear y mantener sesiones HTTP, lo cual contradice el propósito de JWT.

---

### `JwtService.java`

Es el único componente que sabe cómo crear y leer tokens. Nadie más en la aplicación toca JJWT directamente.

Responsabilidades:

- **Generar** access tokens y refresh tokens con `Jwts.builder()`.
- **Validar** que un token sea auténtico y no haya expirado con `Jwts.parser()`.
- **Extraer** claims del token (username, expiración, claims personalizados).

```java
// Generar token
private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
    return Jwts.builder()
            .claims(extraClaims)
            .subject(userDetails.getUsername())      // claim "sub"
            .issuedAt(new Date(System.currentTimeMillis()))
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey())               // HMAC-SHA384
            .compact();
}

// Validar token
public boolean isTokenValid(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
}
```

**Por qué HMAC-SHA384?** JJWT elige automáticamente el algoritmo según el tamaño de la clave. Con una clave de 256 bits usa HS256, con 384 bits usa HS384. Es simétrico — el mismo secret firma y verifica.

---

### `JwtAuthFilter.java`

Extiende `OncePerRequestFilter`, lo que garantiza que se ejecuta exactamente una vez por request (importante en arquitecturas con múltiples filtros encadenados).

Su trabajo es simple:

1. Busca el header `Authorization: Bearer <token>`.
2. Si no existe, deja pasar el request sin autenticar (Spring Security lo rechazará después si la ruta es protegida).
3. Si existe, extrae el token, lo valida y registra la autenticación en el `SecurityContextHolder`.

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    // No aplicar en rutas públicas de auth
    return request.getServletPath().startsWith("/auth/");
}
```

**Por qué `shouldNotFilter`?** Aunque `/auth/**` es `permitAll()`, el filtro se ejecutaría de todas formas. Con `shouldNotFilter` lo saltamos completamente para esas rutas — más limpio y eficiente.

**El `SecurityContextHolder`** es un almacenamiento local al thread (ThreadLocal). Cuando guardas la autenticación aquí, todos los componentes que se ejecuten en el mismo thread (el mismo request) pueden leerla. Se limpia automáticamente al terminar el request.

---

### `AuthService.java`

Contiene la lógica de negocio de autenticación. Delega la validación de credenciales al `AuthenticationManager` de Spring en vez de hacerlo manualmente.

```java
// Spring hace todo esto internamente con esta línea:
authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(username, password)
);
// Si las credenciales son incorrectas → BadCredentialsException → 401
// Si son correctas → continúa normalmente
```

**¿Por qué usar `AuthenticationManager` y no comparar el password directamente?**
Porque `AuthenticationManager` aplica toda la cadena de seguridad de Spring: bloqueo de cuentas, cuentas expiradas, credenciales expiradas, etc. Si comparas el password tú mismo te saltas esas validaciones.

---

### `UserDetailsServiceImpl.java`

Implementa la interfaz `UserDetailsService` de Spring Security. Es el puente entre Spring Security y tu fuente de datos (base de datos, LDAP, memoria, etc.).

Spring Security llama a `loadUserByUsername()` cada vez que necesita verificar quién es un usuario. Debe devolver un `UserDetails` con el username, el hash del password y los roles.

```java
@Override
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    // En producción: buscar en BD con un repositorio JPA
    // Por ahora: usuario hardcodeado para desarrollo
    if ("admin".equals(username)) {
        return User.builder()
                .username("admin")
                .password("$2a$10$...") // hash BCrypt de "password"
                .roles("ADMIN")
                .build();
    }
    throw new UsernameNotFoundException("Usuario no encontrado: " + username);
}
```

**Importante**: el password en `UserDetails` debe ser el **hash BCrypt**, nunca el texto plano. Spring compara internamente el texto plano que llegó del cliente con el hash usando `BCryptPasswordEncoder.matches()`.

---

### DTOs (`model/`)

Los DTOs (Data Transfer Objects) definen el contrato de la API — qué datos acepta y qué datos devuelve.

```java
// Entrada del login — @NotBlank valida que no lleguen vacíos
public record LoginRequest(
    @NotBlank(message = "Username es requerido") String username,
    @NotBlank(message = "Password es requerido") String password
) {}

// Respuesta — nombres claros en vez de Map<String, String>
public record AuthResponse(
    String accessToken,
    String refreshToken
) {}
```

**Por qué `record` en vez de `class`?** Los records de Java 16+ son clases inmutables con constructor, getters, `equals`, `hashCode` y `toString` generados automáticamente. Para DTOs que solo transportan datos son perfectos — menos código, más legibles.

**El `@Valid` en el controller** activa las validaciones de Jakarta Bean Validation. Si `username` llega vacío, Spring devuelve automáticamente un `400 Bad Request` antes de que el código llegue al servicio.

```

**Sobre el `secret`**: debe ser una cadena Base64 de al menos 256 bits (32 bytes). En producción nunca lo pongas en el código — usa variables de entorno o un secrets manager (AWS Secrets Manager, Vault, etc.).

---

---

## Pruebas con curl

### 1. Login

```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"password\"}"
```

Respuesta esperada:

```json
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
  "refreshToken": "eyJhbGciOiJIUzM4NCJ9..."
}
```

### 2. Endpoint protegido con token válido

```bash
curl http://localhost:8081/api/hello \
  -H "Authorization: Bearer eyJhbGciOiJIUzM4NCJ9..."
```

Respuesta esperada:

```
Hola admin, tu token es válido.
```

### 3. Endpoint protegido sin token

```bash
curl http://localhost:8081/api/hello
```

Respuesta esperada: `403 Forbidden`

### 4. Refresh token

```bash
curl -X POST http://localhost:8081/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"eyJhbGciOiJIUzM4NCJ9...\"}"
```

Respuesta esperada:

```json
{
  "accessToken": "eyJ...(nuevo token)...",
  "refreshToken": "eyJ...(mismo refresh token)"
}
```

---

## Preguntas frecuentes en entrevista

**¿Por qué JWT en vez de sesiones HTTP?**
JWT es stateless — el servidor no necesita guardar estado. Escala horizontalmente sin necesidad de sesiones compartidas entre instancias. Las sesiones HTTP requieren que todas las instancias tengan acceso al mismo almacenamiento de sesiones (Redis, base de datos).

**¿Dónde guardas el token en el cliente?**
La respuesta correcta depende del contexto. `localStorage` es accesible por JavaScript (vulnerable a XSS). Una cookie `HttpOnly` no es accesible por JavaScript (más segura). Para SPAs se recomienda memoria en vez de `localStorage`, con el refresh token en cookie `HttpOnly`.

**¿Qué pasa si alguien roba el access token?**
El atacante puede usarlo hasta que expire. Por eso el access token tiene vida corta (minutos u horas). La mitigación real es HTTPS para evitar que sea robado en tránsito, y tiempos de expiración cortos.

**¿Cómo invalidas un JWT antes de que expire?**
JWT puro no se puede invalidar — no hay estado en el servidor. Las estrategias comunes son: mantener una blacklist de tokens revocados (en Redis), usar tokens de vida muy corta, o cambiar el secret key (invalida todos los tokens a la vez).

**¿Qué es `OncePerRequestFilter`?**
Es una clase de Spring que garantiza que tu filtro se ejecute exactamente una vez por request, incluso en casos como forwards o includes internos donde los filtros normales se ejecutarían varias veces.

**¿Qué hace `SecurityContextHolder`?**
Es un contenedor ThreadLocal que almacena el contexto de seguridad del request actual. Cuando guardas la autenticación aquí, todos los componentes del mismo thread (mismo request HTTP) pueden acceder a quién está autenticado con `SecurityContextHolder.getContext().getAuthentication()`. Spring lo limpia automáticamente al finalizar el request.

**¿Por qué `DaoAuthenticationProvider`?**
Es el provider estándar de Spring para autenticación con base de datos. Conecta el `UserDetailsService` (que carga el usuario) con el `PasswordEncoder` (que verifica el password). Si necesitas autenticación con LDAP o un proveedor externo usarías un provider diferente.

**¿Qué diferencia hay entre `@AuthenticationPrincipal` y `SecurityContextHolder`?**
Ambos acceden al usuario autenticado. `@AuthenticationPrincipal` en un parámetro del controller es la forma declarativa y limpia. `SecurityContextHolder.getContext().getAuthentication()` lo puedes llamar desde cualquier capa (servicio, filtro). En controllers siempre prefiere `@AuthenticationPrincipal`.

---

## Anotaciones más usadas de Spring Security

### Habilitar el sistema de anotaciones

Para que las anotaciones de método funcionen debes agregar `@EnableMethodSecurity` en tu `SecurityConfig`. Sin ella las anotaciones existen pero no hacen nada.

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // ← habilita @PreAuthorize, @PostAuthorize, etc.
public class SecurityConfig { ... }
```

---

### `@PreAuthorize` vs `@PostAuthorize`

La diferencia está en **cuándo** se evalúa la condición:

```
Request
   │
   ▼
@PreAuthorize   ← evalúa ANTES de ejecutar el método
   │               si falla → lanza AccessDeniedException → el método NUNCA se ejecuta
   ▼
Método ejecuta
   │
   ▼
@PostAuthorize  ← evalúa DESPUÉS de ejecutar el método
                   si falla → el método YA corrió, solo se bloquea la respuesta
```

**`@PreAuthorize`** — úsalo casi siempre. Es el más flexible, soporta SpEL completo.

```java
// Solo usuarios con rol ADMIN pueden acceder
@PreAuthorize("hasRole('ADMIN')")
public List<User> getAllUsers() { ... }

// El usuario solo puede ver su propio perfil
// #username referencia el parámetro del método
@PreAuthorize("#username == authentication.principal.username")
public UserProfile getProfile(String username) { ... }

// Múltiples condiciones con operadores lógicos
@PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
public void deleteOrder(Long id) { ... }

// Verificar permiso granular
@PreAuthorize("hasAuthority('ORDER_DELETE')")
public void deleteOrder(Long id) { ... }
```

**`@PostAuthorize`** — úsalo cuando necesitas evaluar el **resultado** del método. Poco común en la práctica.

```java
// El método corre y carga el User de BD,
// pero solo devuelve el resultado si es el dueño del recurso
@PostAuthorize("returnObject.username == authentication.principal.username")
public User getUserById(Long id) { ... }
// ↑ útil cuando no sabes de quién es el recurso hasta cargarlo de BD
```

---

### `@Secured` y `@RolesAllowed`

Alternativas más simples a `@PreAuthorize` pero sin soporte para SpEL.

```java
// @Secured — solo roles, sintaxis simple
// IMPORTANTE: debes incluir el prefijo ROLE_ explícitamente
@Secured("ROLE_ADMIN")
public void adminOnly() { ... }

@Secured({"ROLE_ADMIN", "ROLE_MANAGER"})
public void adminOrManager() { ... }

// @RolesAllowed — estándar Jakarta EE, equivalente a @Secured
@RolesAllowed("ADMIN")
public void adminOnly() { ... }

@RolesAllowed({"ADMIN", "MANAGER"})
public void adminOrManager() { ... }
```

| Anotación | SpEL | Prefijo ROLE_ | Estándar |
|---|---|---|---|
| `@PreAuthorize` | Sí | No (lo agrega automáticamente con `hasRole()`) | Spring |
| `@PostAuthorize` | Sí | No | Spring |
| `@Secured` | No | Sí (debes escribirlo) | Spring |
| `@RolesAllowed` | No | No | Jakarta EE |

---

### `@AuthenticationPrincipal`

Inyecta el usuario autenticado directamente como parámetro del controller, sin necesidad de acceder al `SecurityContextHolder` manualmente.

```java
// Inyecta el UserDetails completo
@GetMapping("/me")
public ResponseEntity<String> getMe(@AuthenticationPrincipal UserDetails user) {
    return ResponseEntity.ok("Hola " + user.getUsername());
}

// Si tienes tu propio UserDetails personalizado
@GetMapping("/me")
public ResponseEntity<String> getMe(@AuthenticationPrincipal CustomUserDetails user) {
    return ResponseEntity.ok("Hola " + user.getFullName());
}

// Acceder a un campo específico con SpEL
@GetMapping("/me")
public ResponseEntity<String> getMe(
        @AuthenticationPrincipal(expression = "username") String username) {
    return ResponseEntity.ok("Hola " + username);
}
```

---

### Anotaciones para tests

Esenciales para probar endpoints protegidos sin levantar un servidor real.

```java
// Simula un usuario autenticado con roles específicos
@Test
@WithMockUser(username = "admin", roles = {"ADMIN"})
void adminEndpointShouldReturn200() throws Exception {
    mockMvc.perform(get("/api/admin"))
           .andExpect(status().isOk());
}

// Simula usuario anónimo (sin autenticación)
@Test
@WithAnonymousUser
void protectedEndpointShouldReturn403() throws Exception {
    mockMvc.perform(get("/api/hello"))
           .andExpect(status().isForbidden());
}

// Carga un usuario real desde tu UserDetailsService
@Test
@WithUserDetails("admin")
void shouldLoadRealUserFromService() throws Exception {
    mockMvc.perform(get("/api/hello"))
           .andExpect(status().isOk());
}
```

---

### Expresiones SpEL más comunes

Las expresiones SpEL se usan dentro de `@PreAuthorize` y `@PostAuthorize`.

```java
// Roles y permisos
hasRole('ADMIN')                              // tiene ROLE_ADMIN
hasAnyRole('ADMIN', 'MANAGER')                // tiene cualquiera de los roles
hasAuthority('ORDER_READ')                    // tiene el permiso exacto ORDER_READ
hasAnyAuthority('ORDER_READ', 'ORDER_WRITE')  // tiene cualquiera de los permisos

// Estado de autenticación
isAuthenticated()                             // está autenticado (no anónimo)
isAnonymous()                                 // es usuario anónimo
isFullyAuthenticated()                        // autenticado y no por remember-me
permitAll()                                   // siempre permite
denyAll()                                     // siempre bloquea

// Acceder al usuario actual
authentication.principal.username             // username del usuario autenticado
authentication.name                           // equivalente a username
authentication.authorities                    // colección de roles/permisos

// Referenciar parámetros del método con #
#id == authentication.principal.id            // parámetro id vs id del usuario actual
#username == authentication.principal.username

// Referenciar el resultado del método (solo en @PostAuthorize)
returnObject.owner == authentication.name
returnObject.username == authentication.principal.username
```

---

### `hasRole()` vs `hasAuthority()` — diferencia clave en entrevistas

```java
// hasRole() agrega el prefijo "ROLE_" automáticamente
hasRole('ADMIN')           // busca internamente "ROLE_ADMIN"

// hasAuthority() busca el valor exacto, sin prefijos
hasAuthority('ROLE_ADMIN') // equivalente al anterior, pero explícito
hasAuthority('ORDER_READ') // permiso granular — NO es un rol

// En UserDetailsService, así defines roles vs authorities:
User.builder()
    .roles("ADMIN")                           // guarda como "ROLE_ADMIN"
    .authorities("ROLE_ADMIN", "ORDER_READ")  // guarda exactamente como están
    .build();
```

La convención es usar **roles** para grupos amplios (`ADMIN`, `USER`, `MANAGER`) y **authorities** para permisos granulares (`ORDER_READ`, `USER_DELETE`, `REPORT_EXPORT`). Spring solo prefija `ROLE_` automáticamente con `hasRole()` y `.roles()`.
