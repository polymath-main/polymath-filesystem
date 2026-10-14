package com.polymath.fs.domain.vfs

class VfsRegistry(
    providers: List<VfsProvider> = emptyList()
) {
    private val providers = mutableListOf<VfsProvider>()

    init {
        this.providers.addAll(providers)
    }

    fun register(provider: VfsProvider) {
        providers.add(provider)
    }

    fun unregister(provider: VfsProvider) {
        providers.remove(provider)
    }

    fun providerFor(uri: VfsUri): VfsProvider {
        return providers.firstOrNull { uri.scheme in it.schemes }
            ?: throw IllegalArgumentException("No VfsProvider registered for scheme '${uri.scheme}' (uri: ${uri.serialized()})")
    }

    fun providerFor(raw: String): VfsProvider {
        val uri = VfsUri.parse(raw)
        return providerFor(uri)
    }

    fun allProviders(): List<VfsProvider> = providers.toList()

    fun close() {
        for (provider in providers) {
            try {
                provider.close()
            } catch (_: Exception) {}
        }
    }
}
