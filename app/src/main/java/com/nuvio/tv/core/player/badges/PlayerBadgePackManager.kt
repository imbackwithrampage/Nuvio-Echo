package com.nuvio.tv.core.player.badges

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Manages custom imported badge packs for the player.
 * Validates, securely extracts (with strict Zip Slip and Zip Bomb protection),
 * caches, and resolves custom SVG/PNG badge assets.
 */
object PlayerBadgePackManager {
    private const val TAG = "PlayerBadgePackManager"
    private const val PACKS_DIR_NAME = "custom_badge_packs"
    private const val MAX_ZIP_ENTRIES = 500
    private const val MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024 // 100 MB
    private val ALLOWED_EXTENSIONS = setOf("svg", "png", "webp")

    // In-memory cache mapping pack directory path -> (normalized codec key -> resolved File)
    private val packFileCache = ConcurrentHashMap<String, Map<String, File>>()

    private val KEY_ALIASES: Map<String, List<String>> = mapOf(
        "4k" to listOf("4k", "2160p", "uhd", "4k_uhd", "ultra_hd"),
        "1440p" to listOf("1440p", "2k", "qhd"),
        "1080p" to listOf("1080p", "fhd", "1080", "full_hd"),
        "720p" to listOf("720p", "hd", "720"),
        "sd" to listOf("sd", "480p", "576p"),
        "dolby_vision" to listOf("dolby_vision", "dolbyvision", "dovi", "dv", "dolby-vision"),
        "hdr10plus" to listOf("hdr10plus", "hdr10+", "hdr10_plus", "hdr_plus"),
        "hdr10" to listOf("hdr10", "hdr"),
        "hlg" to listOf("hlg"),
        "sdr" to listOf("sdr"),
        "atmos" to listOf("atmos", "dolby_atmos", "dolbyatmos", "eac3_atmos", "truehd_atmos", "dolby-atmos"),
        "truehd" to listOf("truehd", "dolby_truehd", "dolbytruehd", "true_hd", "dolby-truehd"),
        "dts_x" to listOf("dts_x", "dtsx", "dts-x"),
        "dts_hd_ma" to listOf("dts_hd_ma", "dts_hd", "dtshd", "dts-hd", "dts_hd_master_audio"),
        "dts" to listOf("dts"),
        "eac3" to listOf("eac3", "e-ac3", "e_ac3", "ddp", "dd+", "dolby_digital_plus", "dd_plus"),
        "ac3" to listOf("ac3", "ac-3", "dd", "dolby_digital"),
        "aac" to listOf("aac"),
        "flac" to listOf("flac"),
        "opus" to listOf("opus"),
        "7.1" to listOf("7.1", "7_1", "7ch", "8ch"),
        "5.1" to listOf("5.1", "5_1", "5ch", "6ch"),
        "2.0" to listOf("2.0", "2_0", "stereo", "2ch"),
        "1.0" to listOf("1.0", "1_0", "mono", "1ch")
    )

    fun getPacksRoot(context: Context): File {
        val root = File(context.filesDir, PACKS_DIR_NAME)
        if (!root.exists()) root.mkdirs()
        return root
    }

    /**
     * Lists all currently installed custom badge packs.
     */
    fun getInstalledPacks(context: Context): List<BadgePackInfo> {
        return runCatching {
            val root = getPacksRoot(context)
            root.listFiles { file -> file.isDirectory }?.mapNotNull { dir ->
                val assetCount = countValidAssets(dir)
                if (assetCount > 0) {
                    BadgePackInfo(
                        id = dir.name,
                        name = dir.name.replace("_", " ").replaceFirstChar { it.titlecase(Locale.ROOT) },
                        directoryPath = dir.absolutePath,
                        assetCount = assetCount
                    )
                } else null
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun countValidAssets(dir: File): Int {
        var count = 0
        dir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension.lowercase(Locale.ROOT) in ALLOWED_EXTENSIONS) {
                count++
            }
        }
        return count
    }

    /**
     * Imports a badge pack from a ZIP input stream on [Dispatchers.IO].
     * Includes strict Zip Slip path traversal checks and Zip Bomb limits.
     */
    suspend fun importFromZipStream(
        context: Context,
        inputStream: InputStream,
        rawPackName: String
    ): Result<BadgePackInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val root = getPacksRoot(context)
            val sanitizedName = rawPackName
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .trim('_')
                .ifBlank { "pack_${System.currentTimeMillis()}" }
                .take(48)

            val targetDir = File(root, sanitizedName)
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            val canonicalTarget = targetDir.canonicalPath
            var entryCount = 0
            var totalBytes = 0L

            ZipInputStream(inputStream.buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) {
                        targetDir.deleteRecursively()
                        throw IllegalStateException("Too many files in ZIP archive (limit $MAX_ZIP_ENTRIES)")
                    }

                    val entryName = entry.name
                    val destFile = File(targetDir, entryName)
                    val canonicalDest = destFile.canonicalPath

                    // Strict Zip Slip protection: ensure entry does not escape target directory
                    if (!canonicalDest.startsWith(canonicalTarget + File.separator) && canonicalDest != canonicalTarget) {
                        targetDir.deleteRecursively()
                        throw SecurityException("Zip Slip detected: Entry $entryName resolves outside target directory")
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        val ext = destFile.extension.lowercase(Locale.ROOT)
                        if (ext in ALLOWED_EXTENSIONS) {
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().use { fos ->
                                val buffer = ByteArray(8192)
                                var len: Int
                                while (zis.read(buffer).also { len = it } != -1) {
                                    totalBytes += len
                                    if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                                        targetDir.deleteRecursively()
                                        throw IllegalStateException("Archive size exceeds $MAX_UNCOMPRESSED_BYTES bytes limit")
                                    }
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val validAssets = countValidAssets(targetDir)
            if (validAssets == 0) {
                targetDir.deleteRecursively()
                throw IllegalArgumentException("No valid SVG, PNG, or WebP badge assets found in archive")
            }

            // Invalidate resolution cache for this directory
            packFileCache.remove(targetDir.absolutePath)

            BadgePackInfo(
                id = targetDir.name,
                name = targetDir.name.replace("_", " ").replaceFirstChar { it.titlecase(Locale.ROOT) },
                directoryPath = targetDir.absolutePath,
                assetCount = validAssets
            )
        }
    }

    /**
     * Imports from a local file Uri (e.g. from Storage Access Framework).
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        packNameOverride: String? = null
    ): Result<BadgePackInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val name = packNameOverride?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                ?: "pack_${System.currentTimeMillis()}"

            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open stream for Uri: $uri")

            inputStream.use { stream ->
                importFromZipStream(context, stream, name).getOrThrow()
            }
        }
    }

    /**
     * Imports from a local File path.
     */
    suspend fun importFromLocalFile(
        context: Context,
        file: File
    ): Result<BadgePackInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists() || !file.isFile) {
                throw IllegalArgumentException("File does not exist: ${file.absolutePath}")
            }
            file.inputStream().use { stream ->
                importFromZipStream(context, stream, file.nameWithoutExtension).getOrThrow()
            }
        }
    }

    /**
     * Deletes an installed custom badge pack by its directory ID.
     */
    fun deletePack(context: Context, packId: String): Boolean {
        return runCatching {
            val root = getPacksRoot(context)
            val dir = File(root, packId)
            packFileCache.remove(dir.absolutePath)
            if (dir.exists() && dir.isDirectory) {
                dir.deleteRecursively()
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /**
     * Resolves a custom badge asset file matching [codecKey] in the given [packDir].
     * Returns null if no asset matches or if the pack directory is invalid.
     */
    fun resolveBadgeFile(packDir: File?, codecKey: String): File? {
        if (packDir == null || !packDir.exists() || !packDir.isDirectory) return null
        val path = packDir.absolutePath

        val packMap = packFileCache.computeIfAbsent(path) {
            indexPackFiles(packDir)
        }

        val canonicalKey = codecKey.lowercase(Locale.ROOT)
        // Check direct key match
        packMap[canonicalKey]?.let { return it }

        // Check alias matches
        val aliases = KEY_ALIASES[canonicalKey] ?: listOf(canonicalKey)
        for (alias in aliases) {
            packMap[alias]?.let { return it }
        }

        return null
    }

    private fun indexPackFiles(packDir: File): Map<String, File> {
        val map = HashMap<String, File>()
        runCatching {
            packDir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension.lowercase(Locale.ROOT) in ALLOWED_EXTENSIONS) {
                    val baseName = file.nameWithoutExtension.lowercase(Locale.ROOT).trim()
                    if (baseName.isNotBlank() && !map.containsKey(baseName)) {
                        map[baseName] = file
                    }
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "Error indexing badge pack: ${packDir.absolutePath}", e)
        }
        return map
    }
}
