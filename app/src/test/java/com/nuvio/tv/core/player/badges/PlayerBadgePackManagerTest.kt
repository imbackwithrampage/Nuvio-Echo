package com.nuvio.tv.core.player.badges

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PlayerBadgePackManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var filesDir: File

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns filesDir
    }

    private fun createZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, data) in entries) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun importFromZipStream_successWithValidAssets() = runBlocking {
        val zipBytes = createZip(
            mapOf(
                "4k.svg" to "<svg>4K</svg>".toByteArray(),
                "1080p.png" to byteArrayOf(1, 2, 3),
                "atmos.png" to byteArrayOf(4, 5, 6),
                "readme.txt" to "This is a badge pack".toByteArray() // non-image ignored
            )
        )

        val result = PlayerBadgePackManager.importFromZipStream(
            context = mockContext,
            inputStream = ByteArrayInputStream(zipBytes),
            rawPackName = "Modern Pack"
        )

        assertTrue(result.isSuccess)
        val pack = result.getOrThrow()
        assertEquals(3, pack.assetCount)
        assertEquals("Modern Pack", pack.name)

        val packDir = File(pack.directoryPath)
        assertTrue(packDir.exists())

        // Test asset resolution with aliases
        val resolved4k = PlayerBadgePackManager.resolveBadgeFile(packDir, "4k")
        assertNotNull(resolved4k)
        assertEquals("4k.svg", resolved4k?.name)

        val resolved1080p = PlayerBadgePackManager.resolveBadgeFile(packDir, "1080p")
        assertNotNull(resolved1080p)
        assertEquals("1080p.png", resolved1080p?.name)

        val resolvedAtmos = PlayerBadgePackManager.resolveBadgeFile(packDir, "atmos")
        assertNotNull(resolvedAtmos)
        assertEquals("atmos.png", resolvedAtmos?.name)

        // Non-existent asset returns null
        val resolvedDts = PlayerBadgePackManager.resolveBadgeFile(packDir, "dts")
        assertNull(resolvedDts)
    }

    @Test
    fun importFromZipStream_blocksZipSlipAttack() = runBlocking {
        val zipBytes = createZip(
            mapOf(
                "../../../../evil.png" to "malicious payload".toByteArray(),
                "4k.png" to "valid".toByteArray()
            )
        )

        val result = PlayerBadgePackManager.importFromZipStream(
            context = mockContext,
            inputStream = ByteArrayInputStream(zipBytes),
            rawPackName = "ExploitPack"
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is SecurityException)
        assertTrue(exception?.message?.contains("Zip Slip") == true)

        // Verify target directory was completely cleaned up
        val packsRoot = PlayerBadgePackManager.getPacksRoot(mockContext)
        val exploitDir = File(packsRoot, "ExploitPack")
        assertFalse(exploitDir.exists())
    }

    @Test
    fun importFromZipStream_failsWhenNoValidImages() = runBlocking {
        val zipBytes = createZip(
            mapOf(
                "info.json" to "{}".toByteArray(),
                "notes.txt" to "text".toByteArray()
            )
        )

        val result = PlayerBadgePackManager.importFromZipStream(
            context = mockContext,
            inputStream = ByteArrayInputStream(zipBytes),
            rawPackName = "NoImagesPack"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun resolveBadgeFile_resolvesAliasesCorrectly() = runBlocking {
        val zipBytes = createZip(
            mapOf(
                "dovi.svg" to "<svg>DV</svg>".toByteArray(),
                "2160p.png" to byteArrayOf(1),
                "eac3_atmos.png" to byteArrayOf(2)
            )
        )

        val result = PlayerBadgePackManager.importFromZipStream(
            context = mockContext,
            inputStream = ByteArrayInputStream(zipBytes),
            rawPackName = "AliasPack"
        )

        val pack = result.getOrThrow()
        val packDir = File(pack.directoryPath)

        // "dolby_vision" should resolve to dovi.svg via alias
        val dv = PlayerBadgePackManager.resolveBadgeFile(packDir, "dolby_vision")
        assertNotNull(dv)
        assertEquals("dovi.svg", dv?.name)

        // "4k" should resolve to 2160p.png via alias
        val fourK = PlayerBadgePackManager.resolveBadgeFile(packDir, "4k")
        assertNotNull(fourK)
        assertEquals("2160p.png", fourK?.name)

        // "atmos" should resolve to eac3_atmos.png via alias
        val atmos = PlayerBadgePackManager.resolveBadgeFile(packDir, "atmos")
        assertNotNull(atmos)
        assertEquals("eac3_atmos.png", atmos?.name)
    }

    @Test
    fun deletePack_removesFolderCleanly() = runBlocking {
        val zipBytes = createZip(mapOf("4k.png" to byteArrayOf(1)))
        val pack = PlayerBadgePackManager.importFromZipStream(
            context = mockContext,
            inputStream = ByteArrayInputStream(zipBytes),
            rawPackName = "ToDelete"
        ).getOrThrow()

        val packDir = File(pack.directoryPath)
        assertTrue(packDir.exists())

        val deleted = PlayerBadgePackManager.deletePack(mockContext, pack.id)
        assertTrue(deleted)
        assertFalse(packDir.exists())
    }
}
