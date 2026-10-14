# Polymath FileSystem — VFS & Storage Access Framework (SAF) Invariants

This rule applies specifically to the `polymath-filesystem` workspace.

## 1. Unified Virtual File System (VFS) Architecture
- **Adapter Migration Pattern:** The existing `IFileSystemRepository` contract must remain preserved so that all UseCases, ViewModels, and the QuickJS bridge continue functioning without disruption.
- **Provider Dispatch:** All file operations route through `VfsRegistry` to specific `VfsProvider` implementations based on the URI scheme (`file://`, `content://`, `sftp://`, `smb://`, `synapse://`).
- **Capability Matrices:** Every provider declares `VfsCapabilities` (`canRead`, `canWrite`, `canRename`, `canDelete`, `canMkdir`, `canChmod`, `canChown`, `canSymlink`, `canStream`, `canSeek`). UseCases and UI dynamically check capabilities rather than assuming blanket support.

## 2. High-Performance Android SAF Implementation
- **Batch Projections Over Tree Iteration:** Never use `DocumentFile.listFiles()` for directory enumerations; it triggers an expensive Binder IPC roundtrip per child. Always query `DocumentsContract.buildChildDocumentsUriUsingTree()` in a single batch projection cursor with standard columns (`COLUMN_DOCUMENT_ID`, `COLUMN_DISPLAY_NAME`, `COLUMN_MIME_TYPE`, `COLUMN_SIZE`, `COLUMN_LAST_MODIFIED`, `COLUMN_FLAGS`).
- **Tree URI to Document URI Translation:** SAF file operations (read, write, delete, rename) require document URIs. Resolve child document URIs via `DocumentsContract.buildDocumentUriUsingTree()`.
- **Write Creation Fallback:** SAF's `ContentResolver.openOutputStream()` throws `FileNotFoundException` if the target document does not already exist. Before opening an output stream on a new file, the provider must call `DocumentsContract.createDocument(parentDocUri, mimeType, displayName)`.
- **Persistable Permissions:** Always request persistable URI permissions via `takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)`. Persist grant metadata in `SafGrantEntity` via `SafGrantDao`.
- **Termux Sandbox Awareness:** Standard SAF cannot access Termux private storage (`/data/data/com.termux/files/home`). Use local SFTP (`127.0.0.1:8022`), Termux `RUN_COMMAND` IPC, or root shell fallback for Termux environments.
