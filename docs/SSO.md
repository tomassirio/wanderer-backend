# SSO (Google)

## How it works
1. Client generates a fresh PKCE pair per login: `code_verifier` = 32 random bytes, base64url without padding (43 chars); `code_challenge` = `BASE64URL-NOPAD(SHA-256(code_verifier))`. Only `S256` is accepted. Then it navigates to `/api/1/auth/oauth2/authorization/google?return_to=<allowed uri>&code_challenge=<challenge>&code_challenge_method=S256` (`code_challenge_method` may be omitted; any value other than `S256` is rejected).
2. `wanderer-auth` stores OAuth state + `return_to` + the client's `code_challenge` in a Redis-backed session (`WANDERER_SSO_SESSION` cookie, `Path=/api/1/auth/oauth2`, 10 min) and redirects to Google (with its own, separate PKCE pair).
3. Google redirects to `/api/1/auth/oauth2/callback/google`; Spring validates the ID token.
4. `SsoIdentityMapper` (strategy per provider) → `SsoService.resolveUser` → find / link (verified email, case-insensitive) / create; returns the user id. No tokens are minted yet. A missing or invalid `code_challenge` fails here, before resolving the user.
5. `SsoLoginCodeStore` stores `{codeChallenge, userId}` in Redis (60 s, single use) — no tokens, so nothing bearer-equivalent sits in Redis before the code is redeemed. Redirect to `return_to?code=<one-time code>` or `return_to?error=sso_failed`.
6. Client `POST /api/1/auth/sso/exchange {"code": "...", "codeVerifier": "..."}` → GETDEL the code, verify PKCE, then `SsoService.issueTokens` mints a fresh `LoginResponse` (same shape as `/login`), reading roles at that moment. The code is burned on the first attempt: a wrong verifier returns 400 and the code can no longer be used. This binds the code to the client that started the login, so an app that hijacks the `wanderer://` redirect (or an attacker injecting their own code) cannot redeem it.

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
