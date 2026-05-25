# module-keycloak — Spring Security con OAuth2 + Keycloak

> Módulo educativo del proyecto `spring-security-lab`.  
> Puerto: **8082** | Authorization Server: **Keycloak en Docker (puerto 8080)**

---

## ¿Qué problema resuelve este módulo?

En `module-jwt` **tu app** generaba y validaba tokens — eras responsable de todo.  
Aquí **delegas** esa responsabilidad a Keycloak. Tu app solo verifica que el token sea legítimo.

---

## Concepto central — Authorization Server vs Resource Server

| | Authorization Server | Resource Server |
|---|---|---|
| ¿Qué es? | Keycloak | Tu app Spring Boot |
| ¿Qué hace? | Autentica usuarios, emite tokens | Protege endpoints, valida tokens |
| ¿Qué sabe de passwords? | Todo | Nada |
| ¿Quién firma el token? | Keycloak (clave privada RSA) | — |
| ¿Quién verifica la firma? | — | Spring (clave pública RSA) |

**Pregunta de entrevista:** *"¿Por qué tu app no necesita saber la contraseña del usuario?"*  
Porque nunca ve las credenciales. El usuario se autentica con Keycloak, Keycloak emite un token firmado, tu app solo verifica la firma criptográfica.

---

## Comparación con módulos anteriores

| | module-jwt | module-keycloak |
|---|---|---|
| Genera tokens | Tu código (JwtService) | Keycloak |
| Valida firma | Tu código (JwtService) | Spring automático |
| Carga usuarios | UserDetailsService | No existe |
| Lee roles | Del token manual | `realm_access.roles` |
| Archivos de código | 7 | 3 |
| Tipo de firma | HMAC (simétrico) | RSA (asimétrico) |

---

## HMAC vs RSA — diferencia clave para entrevistas

**HMAC (module-jwt):** misma clave firma y verifica — si la compartes, todos pueden emitir tokens.

**RSA (Keycloak):** clave privada firma, clave pública verifica — puedes distribuir la clave pública libremente sin riesgo. Nadie puede emitir tokens sin la clave privada.

---

## Cómo funciona la validación automática

```
Request con Bearer token
  ↓
Spring lee issuer-uri del application.yml
  ↓
Descarga la clave pública de Keycloak (JWKs endpoint) — solo la primera vez
  ↓
Verifica la firma RSA del token
  ↓
Extrae los claims — incluyendo realm_access.roles
  ↓
Construye el SecurityContext
  ↓
@PreAuthorize evalúa los roles
```

Spring descarga la clave pública **una sola vez** al arrancar y la cachea. No contacta a Keycloak en cada request.

---

## El problema del claim anidado — lección aprendida

Los roles en el token de Keycloak vienen anidados:
```
realm_access → roles → ["ROLE_ADMIN", "ROLE_USER"]
```

El `JwtGrantedAuthoritiesConverter` por defecto busca roles en el claim `scope` — no en `realm_access.roles`. Por eso necesitas un converter personalizado que navegue el objeto anidado manualmente.

Sin el converter correcto: `@PreAuthorize("hasRole('ROLE_ADMIN')")` siempre da 403 aunque el token tenga el rol.

---

## Conceptos clave de Keycloak

**Realm** — espacio de identidad independiente. Nunca uses el realm `master` para tus apps — es solo para administración.

**Client** — representa tu aplicación ante Keycloak. Define qué flujos puede usar y qué URIs son válidas.

**Direct Access Grants** — flujo que permite obtener tokens con usuario/password directo (útil para pruebas con curl). En producción se usa Authorization Code Flow.

**Client público vs confidencial** — público: sin client secret (SPAs, mobile). Confidencial: con client secret (backends, server-side).

---

## Estructura del JWT de Keycloak

```
Header  → algoritmo RS256, kid (key ID para saber qué clave pública usar)
Payload → sub, preferred_username, email, realm_access.roles, exp, iss
Firma   → RSA con clave privada de Keycloak
```

El `iss` (issuer) es crítico — Spring verifica que coincida exactamente con el `issuer-uri` configurado. Si no coincide, rechaza el token.

---

## Configuración mínima en Spring

Solo dos cosas necesitas:

1. Dependencia `spring-boot-starter-oauth2-resource-server` en el `pom.xml`
2. `issuer-uri` en el `application.yml`

Todo lo demás (descarga de claves, validación de firma, expiración) lo hace Spring automáticamente.

---

## El principal ya no es UserDetails

En `module-jwt` el principal era `UserDetails` — un objeto cargado de tu base de datos.  
En `module-keycloak` el principal es `Jwt` — el token mismo.

```java
// module-jwt
public String hello(@AuthenticationPrincipal UserDetails user)

// module-keycloak  
public String hello(@AuthenticationPrincipal Jwt jwt)
```

Accedes a los datos del usuario leyendo claims del token: `jwt.getClaimAsString("preferred_username")`.

---

## Infraestructura

```bash
# Levantar Keycloak
docker compose up -d   # desde module-keycloak/

# Admin panel
http://localhost:8080  →  admin / admin

# Obtener token
curl -X POST "http://localhost:8080/realms/security-lab/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=spring-lab-client&username=admin-user&password=password"
```

---

## Endpoints disponibles

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/public/hello` | Público | Sin token |
| GET | `/api/hello` | Autenticado | Saludo con username del token |
| GET | `/api/me` | Autenticado | Todos los claims del token |
| GET | `/api/admin` | `ROLE_ADMIN` | Solo admins |

---

## Preguntas de entrevista que cubre este módulo

- ¿Qué es OAuth2? ¿Qué es OpenID Connect?
- ¿Cuál es la diferencia entre Authorization Server y Resource Server?
- ¿Por qué RSA es mejor que HMAC para tokens en arquitecturas distribuidas?
- ¿Cómo invalidas un token de Keycloak? (Keycloak mantiene sesiones — puede revocar. JWT puro no puede.)
- ¿Qué es un realm en Keycloak?
- ¿Qué diferencia hay entre un client público y uno confidencial?
- ¿Cómo escala este esquema horizontalmente? (Cualquier instancia valida con la clave pública cacheada — sin base de datos compartida.)
- ¿Qué pasa si Keycloak se cae? (Los tokens existentes siguen siendo válidos hasta expirar — la clave pública ya está cacheada en Spring.)

---

## Stack del módulo

- Spring Boot 3.3.5
- Spring Security OAuth2 Resource Server
- Keycloak 25.0.0 (Docker)
- Sin base de datos — este módulo no necesita persistencia
