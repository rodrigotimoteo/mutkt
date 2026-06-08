package com.github.rodrigotimoteo.mutation.cache

import com.github.rodrigotimoteo.mutation.model.MutationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MutKtCacheTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var cache: MutKtCache

    @BeforeEach
    fun setUp() {
        cache = MutKtCache(tempDir)
    }

    @Test
    fun `computeClassHash returns consistent hash`() {
        val bytes = "test class bytes".toByteArray()
        val hash1 = cache.computeClassHash(bytes)
        val hash2 = cache.computeClassHash(bytes)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `computeClassHash returns different hash for different input`() {
        val hash1 = cache.computeClassHash("input1".toByteArray())
        val hash2 = cache.computeClassHash("input2".toByteArray())
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `computeClassHash returns 64 char hex string`() {
        val hash = cache.computeClassHash("test".toByteArray())
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `lookup returns null when no cache exists`() {
        val result = cache.lookup("nonexistent", "ARITHMETIC", 42)
        assertNull(result)
    }

    @Test
    fun `store and lookup work correctly`() {
        val classHash = "abc123"
        cache.store(classHash, "ARITHMETIC", 42, MutationStatus.KILLED)

        val result = cache.lookup(classHash, "ARITHMETIC", 42)
        assertEquals(MutationStatus.KILLED, result)
    }

    @Test
    fun `lookup returns null for different operator`() {
        val classHash = "abc123"
        cache.store(classHash, "ARITHMETIC", 42, MutationStatus.KILLED)

        val result = cache.lookup(classHash, "RETURN_VALS", 42)
        assertNull(result)
    }

    @Test
    fun `lookup returns null for different line number`() {
        val classHash = "abc123"
        cache.store(classHash, "ARITHMETIC", 42, MutationStatus.KILLED)

        val result = cache.lookup(classHash, "ARITHMETIC", 43)
        assertNull(result)
    }

    @Test
    fun `store overwrites existing entry`() {
        val classHash = "abc123"
        cache.store(classHash, "ARITHMETIC", 42, MutationStatus.KILLED)
        cache.store(classHash, "ARITHMETIC", 42, MutationStatus.SURVIVED)

        val result = cache.lookup(classHash, "ARITHMETIC", 42)
        assertEquals(MutationStatus.SURVIVED, result)
    }

    @Test
    fun `multiple entries per class hash`() {
        val classHash = "abc123"
        cache.store(classHash, "ARITHMETIC", 42, MutationStatus.KILLED)
        cache.store(classHash, "RETURN_VALS", 43, MutationStatus.SURVIVED)
        cache.store(classHash, "NULL_RETURNS", 44, MutationStatus.NO_COVERAGE)

        assertEquals(MutationStatus.KILLED, cache.lookup(classHash, "ARITHMETIC", 42))
        assertEquals(MutationStatus.SURVIVED, cache.lookup(classHash, "RETURN_VALS", 43))
        assertEquals(MutationStatus.NO_COVERAGE, cache.lookup(classHash, "NULL_RETURNS", 44))
    }

    @Test
    fun `stats returns correct counts`() {
        val (entries, _) = cache.stats()
        assertEquals(0, entries)

        cache.store("hash1", "ARITHMETIC", 42, MutationStatus.KILLED)
        cache.store("hash1", "RETURN_VALS", 43, MutationStatus.SURVIVED)
        cache.store("hash2", "NULL_RETURNS", 44, MutationStatus.NO_COVERAGE)

        val (entriesAfter, _) = cache.stats()
        assertEquals(3, entriesAfter)
    }

    @Test
    fun `clear removes all cached data`() {
        cache.store("hash1", "ARITHMETIC", 42, MutationStatus.KILLED)
        cache.store("hash2", "RETURN_VALS", 43, MutationStatus.SURVIVED)

        cache.clear()

        val (entries, _) = cache.stats()
        assertEquals(0, entries)
        assertNull(cache.lookup("hash1", "ARITHMETIC", 42))
        assertNull(cache.lookup("hash2", "RETURN_VALS", 43))
    }

    @Test
    fun `cache creates directory structure`() {
        cache.store("abc123def456ghi789", "ARITHMETIC", 42, MutationStatus.KILLED)

        val cacheDir = File(tempDir, ".mutkt/cache")
        assertTrue(cacheDir.exists())
        assertTrue(cacheDir.isDirectory)
    }
}
