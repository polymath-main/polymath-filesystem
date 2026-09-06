package com.polymath.fs.js.runtime

/**
 * Native bridge interfaces exposed to the QuickJS execution runtime.
 * Provides raw POSIX, OS, Process, HTTP, Crypto, and Kernel primitives.
 */

interface PolymathJSPOSIXInterface {
    fun readFileSync(path: String, encoding: String): String
    fun writeFileSync(path: String, content: String): Boolean
    fun appendFileSync(path: String, content: String): Boolean
    fun existsSync(path: String): Boolean
    fun readdirSync(path: String): String
    fun statSync(path: String): String
    fun mkdirSync(path: String, recursive: Boolean): Boolean
    fun unlinkSync(path: String): Boolean
    fun rmdirSync(path: String, recursive: Boolean): Boolean
    fun copyFileSync(src: String, dest: String): Boolean
    fun renameSync(oldPath: String, newPath: String): Boolean
    fun chmodSync(path: String, mode: String): Boolean
}

interface PolymathJSOSInterface {
    fun getArch(): String
    fun getPlatform(): String
    fun getHostname(): String
    fun getCpus(): String
    fun getTotalMem(): Double
    fun getFreeMem(): Double
    fun getUptime(): Double
    fun getLoadAvg(): String
    fun getHomeDir(): String
    fun getTmpDir(): String
    fun getNetworkInterfaces(): String
}

interface PolymathJSProcessInterface {
    fun getEnv(): String
    fun getPid(): Int
    fun getCwd(): String
    fun setCwd(path: String): Boolean
    fun execSync(command: String): String
    fun getUptime(): Double
}

interface PolymathJSHttpInterface {
    fun fetchSync(url: String, method: String, headersJson: String, body: String): String
}

interface PolymathJSCryptoInterface {
    fun hash(algo: String, input: String): String
    fun randomUuid(): String
    fun randomHex(byteLength: Int): String
    fun base64Encode(input: String): String
    fun base64Decode(input: String): String
}

interface PolymathJSKernelInterface {
    fun getKernelReport(): String
    fun readVirtualFile(path: String): String
    fun setCpuGovernor(governor: String): Boolean
}

interface PolymathModuleLoaderInterface {
    fun loadModuleSource(specifier: String, currentDir: String): String
}
