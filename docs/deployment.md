# Deployment

The default Compose file is a local, single-host deployment: only loopback ports 8080 and 8443 are published. Public hosting requires an actual server and a domain you control. No public server or DNS record is provisioned by this repository.

## A single Docker host with a public domain

1. Install Docker Engine and Docker Compose 2.24.4 or newer on the server. Provide adequate RAM and persistent storage; begin with at least 5 GB free for this stack and monitor usage.
2. Copy the source to the server and generate new secrets there using `sh scripts/setup.sh`. Do not reuse the local development keys or passwords.
3. Point the domain's DNS records at the server. Allow inbound TCP ports 80 and 443; UDP 443 is optional for HTTP/3.
4. Set these values in the server's ignored `.env`:

   ```dotenv
   APP_ORIGIN=https://films.example.com
   SITE_DOMAIN=films.example.com
   ACME_EMAIL=operator@example.com
   ```

   Replace the example domain and email with values you own. Keep the generated database and encryption secrets. If bootstrap administration is desired, choose a strong admin password and a suitable admin address before first boot.

5. Start the production override:

   ```sh
   docker compose -f compose.yaml -f compose.production.yaml up --build -d
   docker compose -f compose.yaml -f compose.production.yaml ps
   ```

The production Caddyfile uses automated public ACME certificates and renewal; it does not use the local CA. Caddy's default automation includes Let's Encrypt support. Verify the actual HTTPS certificate, HTTP redirection, allowed Origin, sign-in, and 2FA from the deployed hostname. Certificate issuance cannot be tested until your DNS and server are configured.

## Data and credentials

### Local startup and inspected build connections

On Windows, `./scripts/start.ps1 -TrustLocalCertificate` preserves existing secrets and volumes, builds the stack, waits for readiness, exports Caddy's local CA and trusts that CA for the current Windows account. It then checks `/login` with normal HTTPS verification. Restart an already-open browser if it cached the old certificate error. This helper is for the default localhost deployment; use the production Compose override above for a public hostname.

Some antivirus or corporate proxies inspect HTTPS. If Maven reports `PKIX path building failed` or npm rejects that proxy's certificate during the Docker build, export the organization's **already trusted public root certificate** as PEM. Do not export private keys or disable TLS verification. Supply the PEM with:

```powershell
.\scripts\start.ps1 -TrustLocalCertificate -BuildCaFile C:\path\to\trusted-build-ca.pem
```

Alternatively, place it in the ignored `secrets/build-ca.pem`; the helper detects that file. The optional `compose.build-ca.yaml` sends it as a BuildKit secret. Only the Maven build-stage trust store and npm build invocation use it; it is not copied into the runtime images. Ordinary builds without an inspecting proxy need no override. From another shell, set `BUILD_CA_FILE` and include `-f compose.yaml -f compose.build-ca.yaml` in the build command.

### Persistence

- Back up Neo4j with a supported dump/restore workflow and test restoration. Do not copy an actively written database directory as a substitute for a consistent backup.
- Back up the encryption key alongside the graph through a separately secured channel. Lost encryption keys cannot decrypt saved TOTP secrets.
- Protect the signing key, `.env`, Docker socket, and host access. Use an appropriate secret manager for a shared operating environment.
- Bootstrap admin credentials are applied only when that email does not exist. Editing `ADMIN_PASSWORD` after account creation does not reset the account password. Use account settings for password changes.
- Pinned major/minor image tags can receive maintenance updates. Record deployed digests, review release notes, scan images/dependencies, and deploy patched versions deliberately.
- Do not run integration tests on a live production dataset. They write test records and exercise account-security actions.

## Traffic boundaries

Browser traffic and public API access use HTTPS at Caddy. The default single-host deployment uses HTTP between containers and Bolt to Neo4j on an isolated, unpublished Docker network. Docker network isolation does not encrypt traffic. If services or the database move across hosts or an untrusted network, enable service TLS/mTLS and verified Neo4j TLS (`neo4j+s`/`bolt+s`), distribute certificate authorities, and enforce network policies before doing so.

Nginx resolves Docker service names dynamically, which allows container recreation without stale upstream IP addresses. REST clients use bounded connection/read timeouts. Health probes check Neo4j connectivity; the frontend starts after all APIs are healthy. The local stack remains a single point of failure and needs monitoring, backups, capacity planning, and an incident/recovery process for public use.

References: [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https), [Docker Compose overrides](https://docs.docker.com/compose/how-tos/multiple-compose-files/merge/), [Neo4j backup operations](https://neo4j.com/docs/operations-manual/current/backup-restore/).
