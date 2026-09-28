# SSO (Google)

## How it works
1. Client navigates to `/api/1/auth/oauth2/authorization/google?return_to=<allowed uri>`.
2. `wanderer-auth` stores OAuth state + `return_to` in a Redis-backed session (`WANDERER_SSO_SESSION` cookie, 10 min) and redirects to Google.
3. Google redirects to `/api/1/auth/oauth2/callback/google`; Spring validates the ID token.
4. `SsoIdentityMapper` (strategy per provider) → `SsoService.signIn` → find / link (verified email only) / create.
5. Redirect to `return_to?code=<one-time code>` (60 s, single use) or `return_to?error=sso_failed`.
6. Client `POST /api/auth/sso/exchange {"code": "..."}` → same `LoginResponse` as `/login`.

## Google Cloud Console
- OAuth consent screen: scopes `openid`, `email`, `profile`.
- Create a **Web application** OAuth client. Authorized redirect URIs:
  - `http://localhost:8083/api/1/auth/oauth2/callback/google` (local)
  - `https://<dev host>/api/1/auth/oauth2/callback/google`
  - `https://<prod host>/api/1/auth/oauth2/callback/google`
- Mobile uses the same web client (system browser + `wanderer://` deep link); no Android/iOS client IDs needed.

## Configuration
| Where | Name | Value |
|---|---|---|
| GitHub variable | `GOOGLE_CLIENT_ID` | web client id |
| GitHub secret | `GOOGLE_CLIENT_SECRET` | web client secret |
| GitHub variable | `SSO_ALLOWED_RETURN_URIS` | comma-separated, exact match, first = fallback |

Startup fails if the client id is empty. Set the variables before deploying.

## Adding a provider
1. `spring.security.oauth2.client.registration.<id>.*` (+ `provider.<id>.*` if not built into Spring).
2. A `@Component` implementing `SsoIdentityMapper` with `provider()` = `<id>`.
3. Register `https://<host>/api/1/auth/oauth2/callback/<id>` with the provider.
Non-OIDC providers (e.g. GitHub) may not return a verified email in the user attributes; the mapper must fetch it or report `emailVerified=false`, which blocks auto-linking and sign-up.
