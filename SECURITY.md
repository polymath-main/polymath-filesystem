# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 2.0.x   | :white_check_mark: |
| 1.x.x   | :x:                |

---

## Reporting a Vulnerability

We take the security and integrity of Polymath FileSystem seriously. If you discover a security vulnerability, please report it responsibly rather than opening a public issue.

### Disclosure Process
1. Email your findings and reproduction steps to **security@polymath.native** or open a private security advisory via GitHub Security Advisories.
2. Please include:
   - Description of the vulnerability and attack vector.
   - Affected component(s) (e.g., VFS Provider, FileProvider, Keystore, IPC).
   - Minimal proof of concept (PoC) or step-by-step reproduction guide.
   - Any proposed remediation or mitigation.
3. You will receive an initial response within 48 hours acknowledging receipt.
4. We will coordinate a fix and release a patched version before public disclosure.

---

## Core Security Architecture & Invariants

Polymath FileSystem is built upon strict security invariants to ensure data privacy, sandboxed execution, and transport integrity on Android:

### 1. Storage Access Framework (SAF) First
- External volumes (SD cards, USB OTG, user-selected trees) are browsed via standard Android Storage Access Framework (SAF).
- Scoped access uses persistable, revocable document tree permissions (`ACTION_OPEN_DOCUMENT_TREE`).
- `MANAGE_EXTERNAL_STORAGE` is strictly an optional power-user toggle; it is never required for app launch.

### 2. Hardened FileProvider
- FileProvider paths in `file_paths.xml` are strictly bounded to `external-files-path`, `external-cache-path`, and app-internal files.
- `<root-path>` is **strictly prohibited** to prevent arbitrary filesystem traversal vulnerabilities through external intents.
- Authority is centrally managed via `${applicationId}.fileprovider`.

### 3. Hardware-Backed Keystore Encryption
- All remote credentials (passwords, private key passphrases) are encrypted at rest using **AES-256-GCM** keys generated and held in the **Android Keystore** (`SecretStore`).
- Secrets are never persisted in plaintext.

### 4. Trust-On-First-Use (TOFU) Host-Key Verification
- Remote SSH/SFTP connections enforce Trust-On-First-Use host key pinning (`KnownHostsStore`).
- Blind acceptance (`PromiscuousVerifier`) is forbidden in production builds. If a host key fingerprint changes, connections are aborted with a `SecurityException` unless explicitly accepted by the user.

### 5. Network Security Policy
- Cleartext HTTP traffic is disabled by default via `network_security_config.xml`.
- TLS is enforced for all external network endpoints. Cleartext communication is permitted exclusively on loopback addresses (`127.0.0.1`, `localhost`) for local Termux SFTP bridges and local debugging.

### 6. Shell & Root Privilege Gates
- Operations requiring root privileges (`chmod`, `chown`, privileged shell commands) fail-fast and return `false` if root access is not granted.
- Shell commands sanitize and escape all user-controlled paths (`replace("'", "'\\''")`) to prevent command injection vulnerabilities.
