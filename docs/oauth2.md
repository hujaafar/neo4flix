# Google sign-in (OAuth 2.0 / OpenID Connect)

Neo4flix supports **Continue with Google** alongside email/password login and authenticator 2FA. The authorization-code flow uses Spring Security, PKCE (S256), state, nonce, and verified Google ID tokens. Google credentials are optional: email/password sign-in remains available when they are absent.

## Enable Google sign-in

1. Open [Google Auth Platform](https://console.cloud.google.com/auth/overview) and select or create your own project. Configure the app name, support email, audience and contact details. For a testing audience, add the Google accounts that will test the app.
2. Create an **OAuth client ID**, application type **Web application**. Add this authorized redirect URI, replacing the origin with the actual `APP_ORIGIN` in your private `.env`:

   ```text
   https://localhost:9443/api/auth/oauth2/callback/google
   ```

   A default fresh installation uses port `8443`, so its URI is `https://localhost:8443/api/auth/oauth2/callback/google`. The URI must match exactly, including HTTPS and port. Production uses your public HTTPS domain. No client secret or Google JavaScript SDK is placed in Angular.

3. On Windows, run this from the repository:

   ```powershell
   .\scripts\configure-google.ps1
   ```

   Enter the client ID and secret at the prompts. The secret prompt is hidden; both values are saved in the ignored `.env`. On other systems, edit these two entries directly in that file:

   ```dotenv
   GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=your-client-secret
   ```

4. Apply the configuration:

   ```powershell
   docker compose up -d --no-deps user-service
   ```

   For an unbuilt checkout, first follow the normal project startup/build. Reload the login page after the user service is healthy. The Google button is enabled only when both values are present.

5. Test in Chrome, Firefox or Edge with a trusted local HTTPS certificate. Some embedded browsers are rejected by Google; use your normal browser if that happens. Provider references: [Google's server flow](https://developers.google.com/identity/protocols/oauth2/web-server), [OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect), and [Spring OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/advanced.html).

Never commit client secrets, authorization codes, provider tokens or private keys. Rotate the Google client secret in its console if exposed. Keep the secret only in the user service's private configuration.

## Machines with HTTPS inspection

Normally Java's standard CA store is sufficient. If an installed security product or company proxy intercepts Google HTTPS and Java reports `PKIX path building failed`, use a separate trust store containing the image's standard CAs plus that **already trusted** inspection CA. Never disable TLS verification.

Copy `/opt/java/openjdk/lib/security/cacerts` from the user-service container into the ignored `secrets/oauth/cacerts`, then use `keytool -importcert` to add the verified inspection certificate to that copy (the default CA-store password is `changeit`; it contains public certificates, not private keys). Set this private `.env` entry and recreate the user service:

```dotenv
OAUTH_JAVA_OPTIONS=-Djavax.net.ssl.trustStore=/run/oauth/cacerts -Djavax.net.ssl.trustStorePassword=changeit
```

The optional `secrets/oauth` directory is mounted read-only. Keep the option empty on other computers, and refresh the copied store when updating the Java image. This does not change Windows/browser trust or disable certificate/hostname validation.

## User experience

| Situation                    | Result                                                                                                                                                                     |
| ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| New Google user              | Google verifies identity/email, then Neo4flix asks for a display name and a strong Neo4flix password for account security and email sign-in. Future visits can use Google. |
| Existing Neo4flix email      | Confirm the existing password and authenticator code, if enabled, before connecting Google. Matching email alone never links an account.                                   |
| Returning connected user     | Google restores the same collection/profile. Neo4flix's authenticator code is still required when 2FA is enabled.                                                          |
| Google account email changes | The established connection uses Google's stable subject identifier; Neo4flix does not silently change the profile email.                                                   |
| Disconnect Google            | Account & security → Google sign-in. Confirm password and optional authenticator code; all Neo4flix sessions are revoked. Email/password sign-in remains available.        |
| Cancelled/expired login      | A useful retry message; no application session is issued.                                                                                                                  |
| Shared movie link            | The local return route is preserved through Google login and first-time linking/signup.                                                                                    |

## Security and storage

- Spring validates the ID token's signature, issuer, audience, expiry and nonce. Neo4flix also checks verified email and the Google issuer. Only `openid profile email` scopes are requested.
- The temporary OAuth cookie is Secure, HttpOnly, SameSite=Lax and restricted to `/api/auth/oauth2`, allowing Google's top-level callback. State stays server-side. Sessions rotate after provider verification; incomplete proofs expire after five minutes with at most five completion attempts.
- A Google principal alone cannot access the JWT APIs or bypass local 2FA. Completion issues the existing RS256 JWT and rotating Secure HttpOnly SameSite=Strict refresh cookie.
- Completion/cancellation require the exact application `Origin`. Application tokens never appear in redirect URLs or browser storage. Provider tokens are discarded after login.
  Nginx access logging is disabled for auth endpoints so callback codes and state are not retained in request logs; keep equivalent redaction if adding an external proxy.
- Private `User.googleSubject` has a Neo4j uniqueness constraint. Existing data and roles are preserved. Provider claims cannot grant ADMIN. Profiles expose only `googleLinked`, never the provider subject/tokens.
- Only the user service joins the additional `oauth` bridge for outbound Google HTTPS requests. It publishes no host ports; Neo4j and the other APIs remain on the private backend network.
- Account security versions invalidate incomplete logins after password/2FA changes or disconnect. Database constraints and transactions protect concurrent identity bindings.

## Verification

`OAuthSecurityTest` exercises the actual Spring authorization-code filters against a local provider with signed RSA ID tokens: PKCE, state, nonce, forged/expired/wrong-issuer/wrong-audience tokens, verified email, completion Origin checks and session consumption. This provider exists only in test sources and is not packaged in the app. `OAuthServiceTest` covers account linking, local TOTP delegation, changed security versions and safe return routes.

Run Maven `verify` and the Playwright suite. Browser OAuth tests explicitly mock provider/completion responses to verify screens without external credentials; they do not claim live Google login. After configuring a real Google client, test signup, existing-account linking, enabled 2FA and disconnect/reconnect with real provider accounts.
