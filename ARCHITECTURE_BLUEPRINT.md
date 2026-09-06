# Polymath File System & JavaScript Engine Architecture Blueprint

**Document Version:** 2.0.0  
**Status:** Canonical Architectural Blueprint  
**Target Platform:** Android (API 26–34)  
**Primary Language:** Kotlin & ECMAScript (QuickJS Native Engine)

---

## 1. System Overview & Core Directives

Polymath File System is an advanced, production-grade Android filesystem browser, automation hub, and runtime environment. It empowers power users, developers, and automation specialists to manage files, execute system automation scripts, and run custom extensions with zero latency and zero mocked infrastructure.

### Guiding Principles
1. **Absolute Real-World Architecture:** No mocked data, no fake timers, and no simulated infrastructure. Every byte, file node, and process reflects actual OS-level states.
2. **Modular Architecture:** Clean separation of concerns across decoupled packages:
   - `core`: OS-level utilities, root shell interfaces, storage telemetry, and search algorithms.
   - `data`: Repositories, Room SQLite databases, DAOs, and entities.
   - `js`: QuickJS engine bridges, runtime sandbox, and extension security layer.
   - `models`: Immutable domain models and state representations.
   - `ui`: Presentation layer, custom view holders, fragments, and activities adhering strictly to Material 3.
   - `viewers`: Code editors, text viewers, media players, and specialized viewers.
3. **Low Latency & High Performance:** Sublinear search algorithms, IO-bounded Coroutine dispatchers, memory-efficient string matching, and zero UI-thread blocking.

---

## 2. Storage Telemetry Architecture (Accurate Hardware & Partition Accounting)

### 2.1 The Storage Inaccuracy Problem Analysis
Historically, standard Android apps exhibit severe storage telemetry inaccuracies due to:
1. **Misattributed Partition Targets:** Reading `Environment.getDataDirectory()` (`/data`) instead of primary shared external storage (`/storage/emulated/0`), leading to disjointed numbers compared to Android System Settings.
2. **Root Partition Quirks:** Reading `/` (rootfs) with `StatFs`, which in modern Android is an overlayfs or read-only ramdisk reporting `0 B Available`, creating an impression of broken telemetry.
3. **Block Size Arithmetic Overflow:** Multiplying 32-bit block counts before casting to 64-bit `Long`.
4. **Available vs. Free Blocks Discrepancy:** Conflating `freeBlocksLong` (including system-reserved blocks inaccessible to user apps) with `availableBlocksLong` (actually usable by user processes).

### 2.2 Accurate Storage Resolution Engine (`StorageTelemetryManager`)
The system introduces a unified, robust telemetry engine:

```
+-------------------------------------------------------------------------+
|                        StorageTelemetryManager                          |
+-------------------------------------------------------------------------+
       |                                       |
       v                                       v
[Primary Shared Storage]              [Partition Breakdown]
 - /storage/emulated/0                 - Internal Data (/data)
 - StorageStatsManager (if allowed)    - System Image (/system)
 - StatFs fallback                     - Root & RAM mounts
       |                                       |
       +-------------------+-------------------+
                           |
                           v
              +-------------------------+
              | StorageTelemetryReport  |
              | - totalBytes            |
              | - freeBytes             |
              | - usedBytes             |
              | - usedPercentage        |
              | - internalFreeBytes     |
              | - internalTotalBytes    |
              | - systemPartitionStatus |
              +-------------------------+
                           |
           +---------------+---------------+
           v                               v
[Dashboard UI View]             [PolymathOS JavaScript Bridge]
 - LinearProgressIndicator       - PolymathOS.getDiskStats()
 - Formatted Byte Strings        - Real JSON disk metrics
```

#### Multi-Tier Calculation Pipeline:
- **Tier 1 (Android System Quotas):** Queries `StorageStatsManager.getTotalBytes(UUID_DEFAULT)` and `getFreeBytes(UUID_DEFAULT)` where permission permits.
- **Tier 2 (Shared Volume StatFs):** Computes `statFs.blockSizeLong * statFs.blockCountLong` and `statFs.blockSizeLong * statFs.availableBlocksLong` on `/storage/emulated/0`.
- **Tier 3 (Data Partition StatFs):** Computes `/data` metrics independently so users see exact breakdown between shared storage and app internal partition.
- **Tier 4 (System Partition Inspection):** Checks `/system` mount for total capacity and read-only status, presenting a true Linux filesystem mount description rather than a misleading `0 B Free`.

---

## 3. High-Performance Search Engine Architecture

### 3.1 Low-Latency Search Specifications
The search engine (`DeepSearchEngine`) operates across hundreds of thousands of files on external storage with minimal latency:

1. **Pruned Traversal:** Explicit blacklisting of virtual and recursive kernel namespaces (`/proc`, `/sys`, `/dev`, `/acct`, `/config`).
2. **Breadth-First ArrayDeque Iteration:** Non-recursive, iterative queue processing that prevents stack overflow and limits active memory footprint.
3. **Sublinear Token Matching:** Multi-term whitespace tokenization evaluated against pre-lowercased file names with early-exit predicates.
4. **Reactive Chunking via Kotlin Flow:** Batched emission (20 items or 50ms intervals) via `Dispatchers.IO`, preventing UI thread stutter and ensuring instant first-result response times (<100ms).

---

## 4. JavaScript Engine & Runtime Architecture

### 4.1 QuickJS Embedded Sandbox
Polymath features a high-performance QuickJS engine embedded through JNI:
- **`PolymathFS`**: Direct bridge for high-speed file operations (`listDir`, `copy`, `move`, `delete`, `mkdir`, `rename`).
- **`PolymathOSNativeInterface`**: Low-level bridge executing real Android and Linux operations:
  - `getDiskStats()`: Direct access to real storage telemetry.
  - `findFiles(root, ext)`: Fast native file discovery.
  - `executeShell(cmd)`: Real root/user shell execution with stdout/stderr capture.
  - `readFile()` / `writeFile()`: Direct IO stream operations.
  - `alert2()` / `prompt2()`: Non-blocking native UI interactions.
  - `ftpRequest()` / `smbRequest()`: Real socket probes for remote network nodes.

---

## 5. UI/UX Architecture & Design System

### 5.1 Presentation Layer Principles
- **Aesthetic Direction:** Modern Dark Glassmorphic canvas with deep slate foundations (`#0F172A`, `#1E293B`), paired with luminous accent highlights (Sky Blue `#38BDF8`, Emerald `#34D399`, Violet `#A855F7`).
- **Visual Hierarchy:**
  - Storage telemetry card featuring clear progress gauge, byte accounting, and partition badges.
  - Directory navigation grid with responsive glass ripple feedback.
  - Virtualized RecyclerViews with smooth diffing for file lists and script catalogues.
- **Accessibility:** Minimum touch targets of 48dp x 48dp, semantic labels, TalkBack compatibility, and responsive typography scaling.

---

## 6. Kernel Engine & Hardware Telemetry Architecture

### 6.1 Direct Kernel Interface (`KernelEngineController`)
Polymath interfaces with the Linux and Android kernel through direct inspection of hardware buses and virtual file systems:
- **/proc/sys/kernel/osrelease & /proc/version**: Direct kernel release and compilation signature extraction.
- **/proc/uptime & /proc/loadavg**: Microsecond-precision system uptime and 1, 5, 15-minute kernel thread queue load averages.
- **/proc/cpuinfo & /sys/devices/system/cpu**: Probes core counts, BogoMIPS hardware scaling, frequency governors (`schedutil`, `performance`), and current operating MHz.
- **/proc/meminfo**: Evaluates hardware RAM capacity (`MemTotal`, `MemAvailable`, `Buffers`, `Cached`, `Swap`).
- **/sys/fs/selinux/enforce**: Inspects SELinux policy enforcement mode.
- **/sys/block/zram0/comp_algorithm**: Probes memory compression algorithm efficiency.

---

## 8. Real JavaScript OS Runtime Engine (Unrestricted System Execution)

### 8.1 Philosophy: Sandbox Elimination & Full System Control
Rather than trapping JavaScript scripts in a restricted, throwaway sandbox with artificial permission walls, Polymath implements a real, production-grade OS Runtime Environment inspired by Node.js and POSIX systems:
1. **Unrestricted System Privilege**: No artificial permission checks (`checkPermission`, `SecurityException`) or crippled APIs. Scripts have direct, raw access to the file system, network sockets, root/user shells, and kernel interfaces.
2. **CommonJS Module Subsystem (`require`)**:
   - Built-in core modules: `fs`, `os`, `path`, `child_process`, `http`, `crypto`, `kernel`.
   - File-based modules: `require('/path/to/file.js')` resolving local libraries.
3. **POSIX Global Environment**:
   - Full `process` object: `process.env`, `process.platform`, `process.arch`, `process.uptime()`, `process.pid`, `process.cwd()`, `process.chdir()`, `process.argv`, `process.exit()`.
   - Native `Buffer` support: utf-8, base64, hex encoding and byte slice manipulation.
   - Global `fetch(url, options)`: Synchronous and asynchronous HTTP/HTTPS client.
   - Comprehensive timers: `setTimeout`, `clearTimeout`, `setInterval`, `clearInterval`.
   - Console timing & trace: `console.time()`, `console.timeEnd()`, `console.trace()`.

### 8.2 Architectural Modular Decomposition
- **`com.polymath.fs.js.runtime.PolymathJSRuntime`**: Engine lifecycle manager, execution supervisor, and event loop dispatcher.
- **`com.polymath.fs.js.runtime.PolymathModuleRegistry`**: Registry providing modular CommonJS interfaces.
- **`com.polymath.fs.js.runtime.modules.FsNativeModule`**: Synchronous POSIX file system (`readFileSync`, `writeFileSync`, `appendFileSync`, `readdirSync`, `statSync`, `existsSync`, `unlinkSync`, `mkdirSync`, `copyFileSync`, `renameSync`).
- **`com.polymath.fs.js.runtime.modules.OsNativeModule`**: Direct OS hardware interrogation (`arch`, `platform`, `cpus`, `totalmem`, `freemem`, `loadavg`, `uptime`, `networkInterfaces`).
- **`com.polymath.fs.js.runtime.modules.ChildProcessNativeModule`**: Subprocess spawning and shell commands (`execSync`, `exec`).
- **`com.polymath.fs.js.runtime.modules.HttpNativeModule`**: Network HTTP/HTTPS client.
- **`com.polymath.fs.js.runtime.modules.CryptoNativeModule`**: Cryptographic digests (SHA-256, SHA-512, MD5), UUID, and random byte generation.
- **`com.polymath.fs.js.runtime.modules.KernelNativeModule`**: Kernel telemetry and direct `/proc` / `/sys` inspection.

---

## 9. Implementation Blueprint Checklist

1. [x] **Architecture Blueprint Creation**: Comprehensive `ARCHITECTURE_BLUEPRINT.md` documenting all system specifications.
2. [x] **`StorageTelemetryManager` Core Module**:
   - Decoupled `com.polymath.fs.core.StorageTelemetryManager` with multi-tier storage evaluation.
   - Strongly typed `StorageTelemetryReport` and `StoragePartitionInfo` models.
3. [x] **Dashboard Storage Telemetry Integration**:
   - `HomeDashboardFragment.kt` consuming `StorageTelemetryManager`.
   - Accurate presentation for Primary Shared Storage, Internal Partition (`/data`), and System Image (`/system`).
4. [x] **Kernel Engine Controller Subsystem**:
   - Implemented `com.polymath.fs.core.KernelEngineController` for real-time kernel and hardware CPU/RAM/OS load metrics.
   - Built modern telemetry card in `fragment_home_dashboard.xml` with active kernel, CPU architecture, load averages, and governor states.
5. [x] **JavaScript Bridge Kernel & Storage APIs**:
   - Exposed `PolymathOS.getDiskStats()` and `PolymathOS.getKernelStats()` through `PolymathOSBridge.kt` and `PolymathJSBridge.kt`.
   - Upgraded `SystemAnalytics` extension script to leverage native kernel telemetrics.
6. [x] **Low-Latency Search Engine Enhancements**:
   - `DeepSearchEngine` and JS `PolymathOS.findFiles()` employ optimized pruned traversal over safe paths.
7. [x] **Compilation & Build Verification**:
   - Full applet compilation verified.
8. [x] **Real JavaScript OS Runtime Engine (No-Sandbox Transformation)**:
   - Modular `com.polymath.fs.js.runtime` package and native modules (`fs`, `os`, `path`, `child_process`, `http`, `crypto`, `kernel`).
   - Implemented CommonJS `require()`, `process`, `Buffer`, `fetch()`, timers (`setTimeout`, `setInterval`).
   - Eliminated artificial sandbox permission gating.
   - Wired `PolymathJSRuntime` into `PolymathJSBridge` and `PolymathApp`.
   - Verified compilation with `compile_applet`.
9. [x] **QuickJS JNI Type Binding Alignment**:
   - Replaced Java `Long` primitive with IEEE 754 `Double` for `getTotalMem()` and `getFreeMem()`.
   - Resolved QuickJS runtime `IllegalArgumentException: Unsupported Java type long`.
10. [x] **Kernel Telemetrics SELinux AVC Audit Rate-Limit Remediation**:
   - Replaced direct attempts on restricted sysfs/procfs nodes (`/sys/fs/selinux/enforce`, `/sys/devices/system/cpu/...`, `/sys/block/zram0/...`, `/proc` listing) with zero-audit platform APIs.
   - Used reflection on platform `android.os.SELinux.isSELinuxEnforced()` and standard `System.getProperty("os.version")`.
   - Added thread-safe static hardware telemetry caching in `KernelEngineController` to eliminate logcat `E/audit: rate limit exceeded`.

