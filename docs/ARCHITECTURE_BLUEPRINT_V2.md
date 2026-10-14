# Polymath FileSystem — Architecture & Refinement Blueprint v2.0

**Target:** Android API 26–34 (`com.polymath.fs` / appId `com.aistudio.filesystem.pmfsxz`)
**Scope:** Stabilize current code, introduce a Unified Virtual File System (VFS), integrate
Storage Access Framework (SAF) and SFTP, and eliminate stubbed/mocked network paths.

---

## 1. Executive Summary

The project has a strong visual layer (glassmorphic UI, cognitive canvas, QuickJS extension
runtime) and a solid telemetry/search core. However, the **data + network layers are the weak
point**: the only real filesystem backend is `java.io.File` (+ root shell fallback), while all
remote transports are either stubs or misleading port probes. There is no storage-provider
abstraction, so every new backend (SAF, SFTP, SMB, FTP) would otherwise force changes across
UseCases, the ViewModel, the JS runtime and the UI.

This blueprint:

1. Records the concrete audit (bugs, stubs, architectural debt, security).
2. Defines a **Unified VFS** that keeps the existing `IFileSystemRepository` contract so the
   UseCases, `FileSystemViewModel`, JS bridge and UI keep working.
3. Specifies **SAF** integration (`DocumentFile` + `DocumentsContract`) for external/removable
   drives and user-granted trees, including the correct way to reach Termux data.
4. Specifies **SFTP** integration (`sshj` vs `mwiede/jsch`) behind a streaming, pooled provider.
5. Gives a prioritized fix backlog, phased roadmap, testing strategy and risk register.

---

## 2. Repository Audit

### 2.1 Critical bugs (fix first)

| # | Location | Defect | Impact |
|---|----------|--------|--------|
| C1 | `ui/canvas/preview/CanvasFilePreviewPanelController.kt:454`, `ui/canvas/CognitiveCanvasActivity.kt:630,727` | FileProvider authority is `${packageName}.provider`, manifest declares `${applicationId}.fileprovider` | `IllegalArgumentException: Failed to find configured root` at runtime whenever sharing/opening a file from the canvas |
| C2 | `data/repository/FileSystemRepository.kt:80–133` | `copy`/`move` declare `var copied`/`var moved` but never check them; only fall back to root on exceptions | Flow always emits `isComplete = true` even when a copy silently failed |
| C3 | `data/repository/FileSystemRepository.kt:206–214` | `chown()` returns `true` when root is **not** granted | UI reports success while nothing happened |
| C4 | `data/repository/FileSystemRepository.kt:192–204` | `chmod()` ignores `mode`, flips rwx for all, returns `true` without root | False success; permission model meaningless |
| C5 | `core/SynapseEngine.kt:37–41` + `ui/FileBrowserFragment.kt:170` | Fragment constructs a **second** `SynapseEngine` and calls `startSynapse()` while `PolymathApp` already holds a singleton on port 8888 | `BindException` swallowed by `printStackTrace`; toast still says "Broadcast Started" (false success) |
| C6 | `js/PolymathOSBridge.kt:248–289` | `ftpRequest`/`smbRequest` open a TCP socket then report *"Handshake verified"* / *"Authentication payload verified"* | Fabricated success; `FtpManager.js`/`SmbManager.js` act on it |
| C7 | `core/FileOperationService.kt` | Service is **not declared** in `AndroidManifest.xml` and never started | Dead code; if wired later it would throw `ForegroundServiceStartNotAllowedException` (API 34 needs `foregroundServiceType`) |
| C8 | `data/repository/MockFileSystemRepository.kt` | Fabricates files with `size = 1024L`, never touches disk | Violates project's "no mocked data" directive if ever enabled |

### 2.2 Stubbed / incomplete features

- **`network/SmbClient.java`** — returns the literal string `"SMB Native Support Pending"`.
- **`network/FtpClient.java`**
  - `STOR` is an explicit stub (no upload; no content parameter).
  - `RETR` uses `BufferedReader.readLine()` → corrupts binary payloads (line endings / charset).
  - Passive mode only (no EPSV/active); no timeouts; no TLS (FTPS); no UTF-8 `OPTS UTF8 ON`;
    no directory listing parser producing `FileNode`s; errors returned as `"Error: …"` strings.
- **`core/SynapseEngine.kt`** — hand-rolled HTTP server: unauthenticated, plaintext, `runBlocking`
  inside the handler, hand-written HTTP response parser (no chunked/keep-alive), only `GET /list`,
  no write operations, no `SO_REUSEADDR`, no path-traversal guard.
- **`js/runtime/PolymathJSRuntime.kt`** — `_posix` binds `FsNativeModule` (raw `java.io.File`),
  bypassing `IFileSystemRepository`, so extensions can never see SAF/remote volumes.
- **`core/DeepSearchEngine.kt`** — only traverses `java.io.File`; hard-coded default root
  `/storage/emulated/0`; cannot index SAF trees or remote mounts.
- **`domain/usecase/MkdirUseCase.kt`** — exists but is never called by `FileSystemViewModel`
  (no "new folder" action wired); dead code today.

### 2.3 Architectural debt

- **Two `Progress` types**: `data.repository.Progress(currentBytes, totalBytes, isComplete)` vs
  `models.Progress(currentBytes, totalBytes, currentFile, percentage)`. Confusing and
  `currentBytes` is actually a *file count* in the repository. Pick one.
- **No DI container**: `javax.inject` annotations are decorative; `PolymathApp` hand-rolls lazy
  singletons. `@Inject` on `FileSystemRepository` is never honored.
- **Leaky abstraction**: `synapse://` is special-cased inside `FileSystemViewModel.navigateTo`.
- **`FileNode` sealed hierarchy** (`LocalFile`, `RootFile`, `NetworkFile`) has no SAF/remote
  variants; UI only special-cases `RootFile` for permissions.
- **Persistence churn**: `FileSystemViewModel.updateTabState` writes the whole flow state to
  SharedPreferences on every scroll-idle event.
- **Search state**: replacing the active tab's file list with search results mutates
  `tab.files` without remembering the origin directory.
- **`file_paths.xml`** declares `<root-path name="root_files" path="."/>` which exposes the
  entire filesystem through `FileProvider`.
- **`requestLegacyExternalStorage="true"` + `MANAGE_EXTERNAL_STORAGE`** — Play policy risk and
  the legacy flag is ignored on API 30+.

### 2.4 Testing

Only `app/src/test/java/com/polymath/fs/CognitiveCanvasUnitTest.kt` exists. No tests for
repositories, use cases, VFS routing, SAF mapping or SFTP parsing.

---

## 3. Target Architecture — Unified Virtual File System (VFS)

### 3.1 Principle

> Every backend is a `VfsProvider`. Every path is a canonical URI string. The rest of the app
> (UseCases, ViewModel, JS runtime, UI) talks only to `IFileSystemRepository`.

This is an **adapter migration**: `VfsRepository` implements the existing interface, so the
current call sites compile unchanged while gaining SAF/SFTP.

### 3.2 URI model

| Backend | Canonical path form | Detected by |
|---|---|---|
| Local POSIX | `/data/...`, `/storage/...`, `/` | `startsWith("/")` |
| Root shell | `/data/...` (fallback when `canRead()==false` && root) | same + root granted |
| SAF / DocumentsProvider | `content://com.android.externalstorage.documents/tree/...` | `startsWith("content://")` |
| SFTP | `sftp://<profileId>/absolute/remote/path` | `startsWith("sftp://")` |
| FTP/FTPS | `ftp://<profileId>/path` | `startsWith("ftp://")` |
| SMB | `smb://<profileId>/share/path` | `startsWith("smb://")` |
| Synapse LAN | `synapse://<host>:<port>/path` | `startsWith("synapse://")` |

Keeping SAF as a raw `content://…` URI is deliberate: it is exactly what `DocumentFile`,
`ContentResolver` and `ACTION_VIEW` expect, so "open with" works without a translation layer.

### 3.3 Core interfaces

```kotlin
// domain/vfs/VfsUri.kt
data class VfsUri(
    val scheme: String,          // "file", "content", "sftp", "ftp", "smb", "synapse"
    val authority: String?,      // provider authority or profile id
    val path: String,            // canonical opaque path
) {
    fun serialized(): String = when (scheme) {
        "file" -> path
        "content" -> "content://$authority/$path"
        else -> "$scheme://$authority$path"
    }
    companion object { fun parse(raw: String): VfsUri { /* scheme sniffing */ } }
}

// domain/vfs/VfsCapabilities.kt
data class VfsCapabilities(
    val canRead: Boolean,
    val canWrite: Boolean,
    val canRename: Boolean,
    val canDelete: Boolean,
    val canMkdir: Boolean,
    val canChmod: Boolean,     // false for SAF/SFTP-by-default/exFAT
    val canChown: Boolean,
    val canSymlink: Boolean,
    val canStream: Boolean,    // random-access / streaming reads
    val canSeek: Boolean,
)

// domain/vfs/VfsStat.kt
data class VfsStat(
    val name: String,
    val uri: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    val lastModified: Long,
    val permissions: String?,   // POSIX string when available
    val mimeType: String?,
    val capabilities: VfsCapabilities,
)

// domain/vfs/VfsProvider.kt  (each backend implements this)
interface VfsProvider {
    val schemes: Set<String>
    fun capabilities(uri: VfsUri): VfsCapabilities
    suspend fun list(uri: VfsUri): List<VfsStat>
    suspend fun stat(uri: VfsUri): VfsStat?
    fun openRead(uri: VfsUri): InputStream
    fun openWrite(uri: VfsUri, append: Boolean): OutputStream
    suspend fun delete(uri: VfsUri, recursive: Boolean): Boolean
    suspend fun mkdir(uri: VfsUri): Boolean
    suspend fun rename(uri: VfsUri, newName: String): Boolean
    suspend fun chmod(uri: VfsUri, mode: String): Boolean = false
    suspend fun chown(uri: VfsUri, owner: String): Boolean = false
    fun close() {} // release pooled connections
}

// domain/vfs/VfsRegistry.kt
class VfsRegistry(private val providers: List<VfsProvider>) {
    fun providerFor(raw: String): VfsProvider =
        providers.first { VfsUri.parse(raw).scheme in it.schemes }
}
```

### 3.4 Repository contract evolution (backward compatible)

```kotlin
interface IFileSystemRepository {
    // ---- existing, unchanged ----
    suspend fun listDir(path: String): List<FileNode>
    fun copy(src: List<String>, dest: String): Flow<Progress>
    fun move(src: List<String>, dest: String): Flow<Progress>
    suspend fun delete(paths: List<String>): Boolean
    suspend fun mkdir(path: String): Boolean
    suspend fun rename(oldPath: String, newName: String): Boolean
    suspend fun chmod(path: String, mode: String): Boolean
    suspend fun chown(path: String, owner: String): Boolean

    // ---- new, additive ----
    suspend fun stat(path: String): VfsStat?
    suspend fun exists(path: String): Boolean
    fun openRead(path: String): InputStream
    fun openWrite(path: String, append: Boolean = false): OutputStream
    fun capabilities(path: String): VfsCapabilities
    fun toSharableUri(path: String): android.net.Uri?   // SAF → itself; local → FileProvider; remote → null
}
```

`VfsRepository` implements this by dispatching to `VfsRegistry`, and adapts `VfsStat` → the
existing `FileNode` subclasses.

### 3.5 `FileNode` evolution

Add fields to the base rather than exploding the sealed hierarchy:

```kotlin
sealed class FileNode {
    abstract val name: String
    abstract val path: String          // canonical VFS path / URI string
    abstract val size: Long
    abstract val lastModified: Long
    abstract val isDirectory: Boolean
    abstract val scheme: String        // NEW: "file" | "content" | "sftp" | ...
    abstract val providerLabel: String // NEW: "Internal", "SD Card", "dev-box (sftp)"
    open val mimeType: String? = null  // NEW
    open var spatialX: Float = 0f
    open var spatialY: Float = 0f
    open var isRift: Boolean = false

    data class LocalFile(..., override val scheme: String = "file", ...) : FileNode()
    data class RootFile(..., val permissions: String, ...) : FileNode()
    data class DocumentFile(/* SAF */, override val scheme: String = "content", ...) : FileNode()
    data class RemoteFile(..., val profileId: String, ...) : FileNode()
}
```

`FileListAdapter` groups by `providerLabel` for the breadcrumb/volume chip instead of parsing
the path.

### 3.6 Package layout (new)

```
com.polymath.fs/
├── domain/vfs/
│   ├── VfsUri.kt, VfsStat.kt, VfsCapabilities.kt
│   ├── VfsProvider.kt, VfsRegistry.kt
│   └── provider/
│       ├── LocalVfsProvider.kt        (java.io.File + root shell fallback)
│       ├── SafVfsProvider.kt          (DocumentFile/DocumentsContract)
│       ├── SftpVfsProvider.kt         (sshj)
│       ├── FtpVfsProvider.kt          (commons-net or native)
│       ├── SmbVfsProvider.kt          (smbj, optional)
│       └── SynapseVfsProvider.kt      (replaces hand-rolled engine)
├── data/repository/VfsRepository.kt   (implements IFileSystemRepository)
├── data/remote/
│   ├── SshConnectionPool.kt
│   ├── RemoteProfileEntity.kt / Dao
│   └── SecretStore.kt                 (Keystore-backed)
├── data/saf/
│   ├── SafGrantEntity.kt / Dao
│   └── SafPermissionManager.kt
└── ui/volumes/                         (sidebar: Local, SD/USB via SAF, SFTP profiles)
```

---

## 4. Storage Access Framework (SAF) Integration

### 4.1 Why SAF

- No `MANAGE_EXTERNAL_STORAGE`, Play-policy-safe, works from API 26 easily.
- Gives access to **removable volumes** (SD card, USB OTG) exposed by the platform
  `com.android.externalstorage.documents` provider, plus any third-party `DocumentsProvider`.
- Returns persistable, revocable grants — the right model for a file manager.

### 4.2 Dependencies & manifest

```gradle
implementation 'androidx.documentfile:documentfile:1.0.1'
```

```xml
<!-- no new storage permission is required for SAF -->
```

`MANAGE_EXTERNAL_STORAGE` should become an **optional power-user toggle**, not a launch gate.
Prefer SAF for all shared/removable storage.

### 4.3 Permission flow

```kotlin
// SafPermissionManager.kt
val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri ?: return@registerForActivityResult
    context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    )
    val treeDocId = DocumentsContract.getTreeDocumentId(uri)
    val authority = uri.authority ?: return@registerForActivityResult
    val isWritable = (DocumentsContract.getTreeDocumentId(uri)).isNotEmpty()
    viewModel.addSafGrant(SafGrantEntity(
        treeUri   = uri.toString(),
        authority = authority,
        treeDocId = treeDocId,
        writable  = true,
        label     = DocumentFile.fromTreeUri(context, uri)?.name ?: "Storage"
    ))
}

fun launch() = openTree.launch(null)   // user picks SD card / USB / folder
```

Launch intent must include `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` (the `OpenDocumentTree`
contract adds it automatically). Persisted grants survive reboot but **not** app uninstall, and
there is a per-app cap (128 historically, 512 on Android 11+). Surface a "manage access"
screen that lists and revokes grants.

### 4.4 Efficient traversal with `DocumentsContract`

Avoid `DocumentFile.listFiles()` for large directories — every child access can issue a query.
Query the provider once and build children URIs from document IDs:

```kotlin
class SafVfsProvider(private val context: Context) : VfsProvider {
    override val schemes = setOf("content")

    override suspend fun list(uri: VfsUri): List<VfsStat> = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(uri.serialized())
        val docUri  = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getDocumentId(treeUri)
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
        val out = ArrayList<VfsStat>()
        context.contentResolver.query(docUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0)
                val name  = c.getString(1)
                val mime  = c.getString(2)
                val size  = if (c.isNull(3)) 0L else c.getLong(3)
                val mtime = if (c.isNull(4)) 0L else c.getLong(4)
                val flags = if (c.isNull(5)) 0 else c.getInt(5)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val child = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                out += VfsStat(
                    name = name, uri = child.toString(), isDirectory = isDir,
                    isSymlink = false, size = size, lastModified = mtime,
                    permissions = null, mimeType = mime,
                    capabilities = capabilitiesOf(flags, isDir)
                )
            }
        }
        out.sortedWith(compareByDescending<VfsStat> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun capabilitiesOf(flags: Int, isDir: Boolean): VfsCapabilities {
        val write = flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0
        val create = flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
        return VfsCapabilities(
            canRead = true, canWrite = write,
            canRename = flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0,
            canDelete = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0,
            canMkdir = isDir && create, canChmod = false, canChown = false,
            canSymlink = false, canStream = true, canSeek = false
        )
    }
}
```

Notes:
- `COLUMN_FLAGS` is the source of truth for which operations the provider allows.
- `FLAG_SUPPORTS_IS_CHILD`, `FLAG_SUPPORTS_COPY/MOVE` may allow provider-native copies.
- On API 30+ `DocumentsContract.copyDocument` / `moveDocument` are available; use them when both
  endpoints share the same tree, otherwise stream.

### 4.5 Creating / writing / deleting

```kotlin
// Create
val newDocUri = DocumentsContract.createDocument(
    contentResolver, parentDocumentUri, mimeType, displayName)

// Write
contentResolver.openOutputStream(docUri, if (append) "wa" else "w")!!.use { out -> input.copyTo(out) }

// Delete
DocumentsContract.deleteDocument(contentResolver, documentUri)

// Rename (API 21+, flag-gated)
DocumentsContract.renameDocument(contentResolver, documentUri, newName)
```

For opening externally, `toSharableUri(path)` returns the same `content://` URI; callers use
`Intent.ACTION_VIEW` directly with `FLAG_GRANT_READ_URI_PERMISSION` (no FileProvider needed).

### 4.6 Browsing external drives

External SD/USB appear under authority `com.android.externalstorage.documents`. Two options:

1. **User-granted trees** (recommended): user picks the SD card root from the SAF picker, app
   persists the tree and enumerates.
2. **Auto-discovery**: query the provider's roots and/or call `StorageManager.getStorageVolumes()`
   (`VolumeManager` already does this) and show volume cards; tapping prompts the SAF picker
   scoped to that volume. There is no public API to enumerate a volume's tree without a grant.

`VolumeManager` should be extended to map a `StorageVolume` to an optional persisted SAF grant
(via `StorageVolume.createAccessIntent` / `ACTION_OPEN_DOCUMENT_TREE` with an initial URI).

### 4.7 Reaching Termux documents — the accurate picture

Termux's home is `/data/data/com.termux/files/home`, which is **app-private** to Termux. SAF
cannot see it, and `MANAGE_EXTERNAL_STORAGE` does not grant `/data/data` access. The viable
routes are:

1. **Shared storage equivalence (no root, recommended):** Termux exposes
   `~/storage/shared` → `/storage/emulated/0`. Anything the user keeps in shared storage is
   browsable via SAF. Document this as the primary path.
2. **Termux `RUN_COMMAND` IPC** (no root): invoke `com.termux.app.RunCommandService` with
   permission `com.termux.permission.RUN_COMMAND`. Extras:
   `com.termux.RUN_COMMAND_PATH`, `…_ARGUMENTS`, `…_WORKDIR`, `…_BACKGROUND`,
   `…_SESSION_ACTION`. This runs `ls`, `cat`, `find` **inside Termux** and lets the app browse
   `$HOME`. Implement as `TermuxVfsProvider` (read-oriented; writes via `tee`/redirection).
   The user must grant the RUN_COMMAND permission in Termux.
3. **Root** (optional power-user): `LocalVfsProvider` root-shell fallback already reaches
   `/data/data/com.termux` when `RootShellHolder.hasRootAccess()`.
4. **Termux:API bridge**: `termux-storage-get` / `termux-storage-set` use SAF on the Termux
   side to shuttle files between Termux and shared storage; useful for import/export.

Do **not** claim SAF alone browses Termux `$HOME` — it does not.

### 4.8 SAF pitfalls checklist

- `SecurityException` / `FileNotFoundException` after a provider restart, app update or user
  revoke → catch, drop the grant, re-prompt.
- Never reuse a `DocumentFile` after process death; always re-resolve from the persisted tree URI.
- MIME type can be `application/octet-stream` for many docs; use extension fallback.
- `COLUMN_SIZE` may be `null` for directories / streaming providers.
- Do not assume `DocumentFile.isDirectory` is cheap; use the `MIME_TYPE_DIR` from the query.
- Persisted grants are lost on uninstall — document this and support re-adding.

---

## 5. SFTP Integration

### 5.1 Library choice

| Criterion | **sshj** (`com.hierynomus:sshj`) | **mwiede/jsch** (`com.github.mwiede:jsch`) |
|---|---|---|
| License | Apache-2.0 | BSD-style (JSch) |
| Maintenance | Active | Active fork of unmaintained JSch |
| Modern KEX/ciphers | curve25519, ed25519, chacha20-poly1305 | Good, via fork, some newer algos |
| API quality | Fluent, typed exceptions | Older, imperative |
| Android compatibility | Good with BouncyCastle; strip `java.nio`/SLF4J | Very good, smaller, `jzlib` |
| APK size | Larger | Smaller |
| `known_hosts` | Built-in | Built-in |
| ProxyJump / jump host | Built-in | Partial |

**Recommendation:** `sshj` for a modern, maintainable provider; fall back to `mwiede/jsch`
if APK size or Android `java.nio` friction becomes a problem. Both must be wrapped behind
`SftpVfsProvider` so the choice is reversible.

### 5.2 Gradle

```gradle
implementation 'com.hierynomus:sshj:0.38.0'
implementation 'org.bouncycastle:bcprov-jdk18on:1.78.1'
implementation 'org.bouncycastle:bcpkix-jdk18on:1.78.1'
implementation 'org.slf4j:slf4j-android:1.7.36'   // or slf4j-nop
// if jsch instead:
// implementation 'com.github.mwiede:jsch:0.2.18'
// implementation 'com.jcraft:jzlib:1.1.3'
```

Register the provider once in `PolymathApp.onCreate()`:

```kotlin
Security.removeProvider("BC")
Security.insertProviderAt(BouncyCastleProvider(), 1)
```

ProGuard keep rules (`app/proguard-rules.pro`) — essential because `minifyEnabled true` in release:

```
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn net.schmizz.**
-dontwarn org.slf4j.**
```

### 5.3 Provider design

```kotlin
class SftpVfsProvider(
    private val context: Context,
    private val pool: SshConnectionPool,
    private val knownHosts: KnownHostsStore,
) : VfsProvider {
    override val schemes = setOf("sftp")

    override suspend fun list(uri: VfsUri): List<VfsStat> = withContext(Dispatchers.IO) {
        pool.withSftp(uri.authority!!) { sftp, home ->
            sftp.ls(uri.path.ifEmpty { home }).entries
                .filter { it.filename != "." && it.filename != ".." }
                .map { e -> e.toVfsStat(uri) }
        }
    }

    override fun openRead(uri: VfsUri): InputStream =
        pool.sftp(uri.authority!!).open(uri.path).remoteInputStream

    override fun openWrite(uri: VfsUri, append: Boolean): OutputStream =
        pool.sftp(uri.authority!!).open(uri.path,
            EnumSet.of(OpenMode.WRITE, OpenMode.CREAT,
                if (append) OpenMode.APPEND else OpenMode.TRUNC)).remoteOutputStream

    override suspend fun rename(uri: VfsUri, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            val parent = uri.path.substringBeforeLast('/', "")
            pool.sftp(uri.authority!!).rename(uri.path, "$parent/$newName"); true
        }
    // delete/mkdir/chmod via sftp.rm/mkdir/chmod with proper exceptions
}
```

### 5.4 Connection pool

- Key by `profileId`; hold one `SSHClient` + one `SFTPClient` + current working directory.
- **Keepalive:** `client.connection.keepAlive.keepAliveInterval = 30` seconds.
- **Timeout:** connect 15 s, auth 15 s, read 30 s (configurable per profile).
- **Reconnect:** exponential backoff (1s→2s→4s→…→30s) with jitter; health-check before reuse;
  transparently re-auth and re-open SFTP on `TransportException`/`SFTPException`.
- **Lifecycle:** close all connections in `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)`/app background to
  respect Android LMK; never hold >1 SFTP channel per profile.
- **Concurrency:** serialize operations per profile (single channel) but allow N profiles in
  parallel. Transfers run on `Dispatchers.IO` limited to `max(2, cores/2)`.

### 5.5 Authentication & host-key verification

- Password, public-key (PEM/OpenSSH/PKCS#8, encrypted keys), and optionally keyboard-interactive.
- **Host keys:** TOFU by default — store fingerprint in a `known_hosts`-style `KnownHostsStore`
  (Room/prefs). Show the fingerprint on first connect; refuse to connect on mismatch unless the
  user explicitly accepts the new key.
- **Never** ship a `PromiscuousVerifier` as the default.

### 5.6 Secrets at rest

- Store passwords / passphrases encrypted with an **AES-256-GCM key held in the Android
  Keystore** (`SecretStore`), never plaintext. Private key files stay in app-private storage with
  `MODE_PRIVATE`; optionally require biometric/device-credential unlock.
- Persist non-secret profile metadata (host, port, user, label, auth type, key path) in Room.

```kotlin
@Entity(tableName = "remote_profiles")
data class RemoteProfileEntity(
    @PrimaryKey val id: String,          // UUID, used in sftp://<id>/…
    val label: String,
    val scheme: String,                  // "sftp" | "ftp" | "smb"
    val host: String,
    val port: Int,
    val username: String,
    val authType: String,                // "password" | "key" | "agent"
    val keyPath: String? = null,
    val encryptedSecretRef: String? = null, // key alias in SecretStore
    val hostKeyFingerprint: String? = null,
)
```

### 5.7 Progress & cross-provider transfers

Implement a single `TransferEngine` in the VFS layer:

```kotlin
suspend fun transfer(src: String, dest: String, onProgress: (copied: Long, total: Long) -> Unit)
```

- Local↔Local: `FileChannel.transferTo` (fast path).
- Any↔Any: bounded buffer (64–256 KiB), sequential chunks, report every N bytes/250 ms.
- Same-provider SAF: prefer `DocumentsContract.copyDocument`.
- Cancellation-aware (`ensureActive()` each chunk); resume support via offset for large files.
- This also fixes the existing `copy`/`move` "file count vs bytes" mismatch by feeding the real
  `models.Progress(currentBytes, totalBytes, currentFile, percentage)`.

---

## 6. UI / ViewModel Integration

- **Volumes sidebar (Zone A):** replace the hard-coded `/sdcard` item with dynamic entries from
  `VolumeManager` + persisted SAF grants + SFTP profiles. Each entry carries a `providerLabel`.
- **Breadcrumb:** derive from `providerLabel` + path segments; for `content://` show the document
  display names (maintain a path stack in `TabState`).
- **New Folder / Paste / Rename / Delete:** gate menu items on
  `capabilities(path)`; disable rather than fail.
- **Opening files:** `viewModel.openFile(node)` → if `toSharableUri` non-null use `ACTION_VIEW`
  with the returned URI; for remote files without a content URI, download to `cacheDir` via
  `TransferEngine` then share via FileProvider.
- **Search:** `DeepSearchEngine` should iterate over the list of mounted `VfsUri` roots and call
  `provider.list` recursively, not `java.io.File`. Keep the pruned-traversal design.
- **JS runtime:** bind `_posix` to a `VfsPosixAdapter` backed by `IFileSystemRepository` (not
  `java.io.File`) so extensions see SAF/SFTP. Keep synchronous semantics by `runBlocking` on a
  dedicated single-thread dispatcher, never the main thread.
- **Search state:** save `tab.originalPath` before swapping in search results and restore when the
  query is cleared.
- **Scroll persistence:** debounce `persistFlowState` (e.g. 500 ms) instead of writing on every
  scroll-idle.
- **Remove** the `synapse://` special-case from the ViewModel; it belongs in `SynapseVfsProvider`.

---

## 7. Cross-Cutting Refinements

### 7.1 Persistence additions

```kotlin
@Entity(tableName = "saf_grants")
data class SafGrantEntity(
    @PrimaryKey val treeUri: String,
    val authority: String,
    val treeDocId: String,
    val label: String,
    val writable: Boolean,
    val addedAt: Long,
)
```

Bump `AppDatabase` version to 5 and add the DAO. (Note: the DB currently uses
`fallbackToDestructiveMigration()`; either add real migrations or accept the wipe.)

### 7.2 Security

- Remove `<root-path>` from `file_paths.xml`; scope to `external-files-path` and `cache-path`.
- Fix the FileProvider authority mismatch (C1) and centralize it in a constant.
- Authenticate/tokenize `SynapseEngine`, bind to the LAN interface only, add path-traversal
  guards, or retire it in favour of a proper SFTP/WebDAV server.
- Make `MANAGE_EXTERNAL_STORAGE` opt-in; never gate the whole app on it.
- Add `network_security_config.xml` and disable cleartext by default (except explicit FTPS/SFTP).
- Add keep rules for network classes (see §5.2).

### 7.3 Testing strategy

| Layer | Test |
|---|---|
| VFS routing | `VfsRegistry` scheme dispatch, unknown scheme → typed error |
| Local provider | temp-dir list/stat/copy/rename/delete; quoted paths with spaces/`|` |
| SAF provider | instrumented with `DocumentsContract` fake provider (or `androidx.test` + `content://` mock) |
| SFTP provider | integration test against `sshj`'s in-process server or a containerized `sshd` |
| Transfer engine | chunk math, progress reporting, cancellation, large-file resume |
| ViewModel | `navigateTo` per scheme, search state restore, paste progress |
| Regression | authority mismatch (C1), chown/chmod false success (C3/C4) |

Use sequential test execution (project rules forbid unrestricted parallelism / `Promise.all`).

### 7.4 Build / CI

- `minifyEnabled true` in release requires the ProGuard keeps; add a release smoke test to CI.
- Cache the Gradle dependency tree in CI to avoid the cache-save warnings seen in `build.log`.
- Pin SSHJ/BouncyCastle versions; watch for duplicate `bcprov` from transitive deps.

---

## 8. Prioritized Fix Backlog

| Priority | Item | Files |
|---|---|---|
| P0 | Fix FileProvider authority mismatch | `CanvasFilePreviewPanelController`, `CognitiveCanvasActivity` |
| P0 | Correct `copy`/`move` success signalling | `FileSystemRepository` |
| P0 | Stop false `chown`/`chmod` success | `FileSystemRepository` |
| P0 | Make `ftpRequest`/`smbRequest` honest or implement | `PolymathOSBridge` |
| P0 | Fix duplicate `SynapseEngine` instantiation | `FileBrowserFragment`, `PolymathApp` |
| P1 | Introduce `VfsRegistry` + `VfsRepository` adapter | new `domain/vfs`, `data/repository` |
| P1 | SAF provider + grant persistence + picker UI | new `data/saf`, `ui/volumes` |
| P1 | Wire `MkdirUseCase` to a real "New Folder" action | `FileSystemViewModel`, `FileBrowserFragment` |
| P1 | Unify `Progress` model | `models`, `data/repository` |
| P1 | Route JS `_posix` through repository | `PolymathJSRuntime` |
| P2 | SFTP provider (`sshj`) + connection pool + secrets | new `data/remote` |
| P2 | `TransferEngine` with byte progress + cancel | new `domain/vfs` |
| P2 | SAF-aware `DeepSearchEngine` | `DeepSearchEngine` |
| P2 | Termux RUN_COMMAND provider | new `vfs/provider/TermuxVfsProvider` |
| P3 | Real FTP/FTPS provider; SMB via `smbj`; retire Synapse | `network/*` |
| P3 | `FileOperationService` manifest entry + `foregroundServiceType`, or delete | manifest, `FileOperationService` |
| P3 | Real DI (Hilt/Koin) or remove decorative `@Inject` | `PolymathApp` |
| P3 | Scope `file_paths.xml`, add `network_security_config.xml` | `res/xml` |

---

## 9. Phased Roadmap

**Phase 0 — Stabilize (1–2 days).** P0 bugs, remove/repair misleading network bridges, fix
duplicate Synapse, add missing tests.

**Phase 1 — VFS core (3–5 days).** `VfsUri`/`VfsStat`/`VfsCapabilities`/`VfsProvider`/
`VfsRegistry`; refactor `FileSystemRepository` → `LocalVfsProvider` + `VfsRepository`; unify
`Progress`; route JS `_posix` through the repository. No user-visible change.

**Phase 2 — SAF (3–5 days).** `SafVfsProvider`, `SafPermissionManager`, `SafGrantEntity`,
volumes sidebar, capability-gated menus, SAF "open with". This delivers external drive browsing
and Play-compliant shared-storage access.

**Phase 3 — SFTP (4–6 days).** Profile entity/DAO, `SecretStore` (Keystore), `SshConnectionPool`,
`SftpVfsProvider`, host-key TOFU, connection-manager UI, `TransferEngine` progress.

**Phase 4 — Expand (ongoing).** Termux `RUN_COMMAND` provider, real FTP/FTPS, SMB (`smbj`),
SAF-aware search, retire the hand-rolled Synapse server for a proper authenticated backend.

---

## 10. Risk Register

| Risk | Likelihood | Mitigation |
|---|---|---|
| SSHJ pulls conflicting BouncyCastle / bloats APK | Medium | Wrap behind provider; fall back to `mwiede/jsch` |
| SAF provider grants revoked / lost | High | Catch and re-prompt; "manage access" screen |
| Android LMK kills long transfers | High (Termux/Android) | Keep transfers in foreground service with progress notification; persist offsets for resume |
| `content://` paths overflow SQLite/Room keys | Low | Hash long tree URIs as PK; store full URI |
| Remote credentials compromised | Medium | Keystore AES-GCM, optional biometric, no plaintext |
| SFTP blocking calls on main thread | Medium | All provider I/O on `Dispatchers.IO`; typed suspend API |
| `minifyEnabled` strips network classes | High | ProGuard keeps + release smoke test |
| Termux `$HOME` inaccessible expectations | High | Document shared-storage equivalence; use RUN_COMMAND/root explicitly |

---

## 11. Appendix — Key Snippets

### 11.1 VFS dispatcher

```kotlin
class VfsRepository(
    private val registry: VfsRegistry,
) : IFileSystemRepository {

    override suspend fun listDir(path: String): List<FileNode> =
        registry.providerFor(path).list(VfsUri.parse(path)).map(::toFileNode)

    override fun copy(src: List<String>, dest: String): Flow<Progress> = flow {
        val total = src.sumOf { stat(it)?.size ?: 0L }
        var copied = 0L
        emit(Progress(0, total, "", 0))
        for (s in src) {
            val name = VfsUri.parse(s).path.substringAfterLast('/')
            val target = (if (dest.endsWith("/")) dest else "$dest/") + name
            transferEngine.transfer(s, target) { c, _ ->
                emit(Progress(copied + c, total, name, ((copied + c) * 100 / total).toInt()))
            }
            copied += stat(s)?.size ?: 0L
        }
        emit(Progress(total, total, "", 100))
    }
    // …
}
```

### 11.2 SFTP connection pool

```kotlin
class SshConnectionPool(private val secrets: SecretStore, private val knownHosts: KnownHostsStore) {
    private val clients = ConcurrentHashMap<String, PooledConnection>()

    suspend fun withSftp(profileId: String, block: (SFTPClient, String) -> Unit) =
        withContext(Dispatchers.IO) {
            val conn = clients[profileId] ?: synchronized(this) {
                clients[profileId] ?: open(profileId).also { clients[profileId] = it }
            }
            try { block(conn.sftp, conn.home) }
            catch (e: TransportException) { conn.close(); clients.remove(profileId); throw e }
        }
}
```

### 11.3 SAF open-with

```kotlin
fun openFile(node: FileNode) {
    val uri = repository.toSharableUri(node.path) ?: downloadToCacheAndShare(node)
    val mime = node.mimeType ?: MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(node.name.substringAfterLast('.', "")) ?: "*/*"
    startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
}
```

---

## 12. Definition of Done

- All P0 backlog items fixed with regression tests.
- `IFileSystemRepository` fully served by `VfsRepository`; no direct `java.io.File` in
  ViewModel/JS/UI.
- SAF: can add an SD/USB tree, browse it, create/rename/delete/copy within granted trees, and
  reopen the grant after process death.
- SFTP: can add a profile, connect with password and key auth, browse, stream read/write with
  progress, survive a dropped connection via reconnect, and store secrets in Keystore.
- Transfer engine reports real bytes and supports cancellation.
- JS `fs` module transparently reads from SAF and SFTP paths.
- `assembleRelease` passes with R8 enabled and the ProGuard keeps in place.