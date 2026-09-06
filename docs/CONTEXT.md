# Polymath File System & JavaScript Engine - Context

**Document Version:** 2.0.0  
**Target Platform:** Android (API 26–34)  
**Primary Language:** Kotlin & ECMAScript (QuickJS Native Engine)

## System Overview & Core Directives

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
