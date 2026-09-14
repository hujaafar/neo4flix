# GitHub sign-in

Neo4flix supports **Continue with GitHub** alongside Google, email/password and authenticator 2FA. Configure a GitHub **OAuth App** to activate the button. Each provider can be enabled independently.

## Enable it locally

1. Open [GitHub → Settings → Developer settings → OAuth Apps](https://github.com/settings/developers) and choose **New OAuth App** (or **Register a new application**).
2. Enter these values for this laptop:

   | Field                      | Value                                                    |
   | -------------------------- | -------------------------------------------------------- |
   | Application name           | Neo4flix Local                                           |
   | Homepage URL               | `https://localhost:9443`                                 |
   | Authorization callback URL | `https://localhost:9443/api/auth/oauth2/callback/github` |

   Use the actual `APP_ORIGIN` from your private `.env`. A fresh default installation uses `8443`; production uses your public HTTPS domain. Leave device flow disabled. Expiring provider tokens are supported: Neo4flix uses the access token only during sign-in and discards it and any provider refresh token.

3. Register the app, then generate a client secret. From the repository folder, run:

   ```powershell
   .\scripts\configure-github.ps1
   ```

   At the first prompt, copy the value under **Client ID**, above the **Client secrets** section on GitHub. At the second prompt, paste the newly generated **client secret**. These are different values. Both prompts hide input, including accidental secret pastes; press Enter after each. The script rejects a secret-shaped value in the Client ID field without saving anything. Values go in the ignored `.env`. Do not put them in source code, screenshots, issues or chat. On other systems set `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` directly in that private file.

4. Apply the settings:

   ```powershell
   docker compose up -d --no-deps --wait user-service
   ```

   For a checkout that has not been built, complete the normal project setup/build first. Reload `/login` after the user service is healthy. The button becomes available when both client values are present; successful provider authorization still depends on valid credentials and an exact callback URL.

Official references: [Register an OAuth App](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app), [GitHub authorization-code flow and PKCE](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps), [verified primary email API](https://docs.github.com/en/rest/users/emails#list-email-addresses-for-the-authenticated-user).

## What users see

- **New user:** GitHub verifies identity, then Neo4flix asks for a name and strong Neo4flix password. This password supports email login and account security operations.
- **Existing Neo4flix email:** entering the existing password and a fresh authenticator code, if enabled, explicitly connects GitHub. Matching email alone never links accounts.
- **Returning connected user:** the stable GitHub numeric user ID selects the original profile, even if the GitHub username or email changes. Neo4flix still requires its own authenticator code when 2FA is enabled.
- **Google already connected:** GitHub can connect to the same account without replacing Google. Movie ratings, watchlists, roles and profile IDs are preserved.
- **Disconnect:** Account & security → GitHub sign-in → Disconnect GitHub. Password and enabled 2FA are required; all Neo4flix sessions are revoked. Email/password login and the other provider remain available.
- **Private GitHub email:** supported through GitHub's authenticated email API. The primary address must be verified. A missing/unverified primary email or API failure cancels sign-in safely.

## Security

The Spring authorization-code flow uses state and PKCE S256. GitHub is OAuth2, not an OpenID Connect identity provider: the server checks `/user` and `/user/emails` over verified TLS using the exchanged bearer token. The primary verified email and stable numeric ID form the identity. Only `read:user` and `user:email` scopes are requested; repository permissions are unnecessary.

Provider identifiers use separate unique Neo4j properties, `User.googleSubject` and `User.githubSubject`. Provider keys used in database queries come from a fixed server enum. They cannot be selected through arbitrary browser claims. The profile exposes only `googleLinked` and `githubLinked` flags. Provider tokens and IDs are never put into app JWTs, browser storage, or redirect URLs.

The same five-minute, browser-bound completion proof, exact-Origin checks, password/2FA rules, security-version checks and single-use session issuance described in [OAuth2 security](oauth2.md) apply to both providers. GitHub's own 2FA and Neo4flix's 2FA are separate: a GitHub login does not skip Neo4flix's authenticator requirement. Callback request logging remains disabled.

On machines with an already trusted HTTPS inspection proxy, see the [Java CA-store instructions](oauth2.md#machines-with-https-inspection). Keep TLS verification enabled.

## Verify after configuring a real client

1. On `/login`, choose Continue with GitHub and approve the identity/email scopes.
2. Test signup on a spare account, and explicit linking to an existing test account.
3. Enable Neo4flix 2FA on the test account. A returning GitHub login must stop for the Neo4flix code: missing/wrong codes fail and a fresh unused code succeeds.
4. Confirm that ratings and watchlist survive sign-out and GitHub sign-in.
5. Disconnect GitHub using the password and fresh 2FA code. Verify that old app sessions are revoked and email/password login still works.
6. Test cancellation and retry. No application session should appear after a cancelled provider login.

Automated Java tests exercise real Spring code exchange and HTTP user/email verification against a local test provider, including PKCE/state failures, callback mix-ups, unverified/missing email, and API failures. Shared policy tests cover both providers, and the real AuthService/TOTP tests reject missing, invalid and reused codes. Browser provider tests explicitly mock external responses to verify UI contracts on desktop/mobile. These do not substitute for live GitHub authorization with your own configured OAuth App.
