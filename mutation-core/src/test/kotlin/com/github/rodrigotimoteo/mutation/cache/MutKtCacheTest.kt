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
        assertEquals(64, hash.length, "SHA-256 hex should be 64 chars, got: $hash")
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' }, "expected lowercase hex, got: $hash")
    }

    @Test
    fun `lookup returns null when no cache exists`() {
        val result = cache.lookup("nonexistent", "ARITHMETIC", "foo", 42)
        assertNull(result, "lookup with no cache file should return null")
    }

    @Test
    fun `store and lookup work correctly`() {
        val classHash = "abc123"
        cache.store(classHash, "ARITHMETIC", "foo", 42, 0, MutationStatus.KILLED)

        val result = cache.lookup(classHash, "ARITHMETIC", "foo", 42)
        assertEquals(MutationStatus.KILLED, result)
    }

    @Test
    fun `lookup returns null for different operator`() {
        val classHash = "abc123"
        cache.store(classHash, "ARITHMETIC", "foo", 42, 0, MutationStatus.KILLED)

        val result = cache.lookup(classHash, "RETURN_VALS", "foo", 42)
        assertNull(result, "different operator should not match, got: $result")
    }

    @Test
    fun `lookup returns null for different line number`() {
        val classHash = "abc123"
        cache.store(classHash, "ARITHMETIC", "foo", 42, 0, MutationStatus.KILLED)

        val result = cache.lookup(classHash, "ARITHMETIC", "foo", 43)
        assertNull(result, "different line number should not match, got: $result")
    }

    @Test
    fun `lookup returns null for different method name`() {
        val classHash = "abc123"
        cache.store(classHash, "ARITHMETIC", "foo", 42, 0, MutationStatus.KILLED)

        val result = cache.lookup(classHash, "ARITHMETIC", "bar", 42)
        assertNull(result, "different method name should not match, got: $result")
    }

    @Test
    fun `lookup returns null for different occurrence index`() {
        val classHash = "abc123"
        cache.store(classHash, "ARITHMETIC", "foo", 42, 0, MutationStatus.KILLED)

        val result = cache.lookup(classHash, "ARITHMETIC", "foo", 42, 1)
        assertNull(result, "different occurrence index should not match, got: $result")
    }

    @Test
    fun `store overwrites existing entry`() {
        val classHash = "abc123"
        cache.store(classHash, "ARITHMETIC", "foo", 42, 0, MutationStatus.KILLED)
        cache.store(classHash, "ARITHMETIC", "foo", 42, 0, MutationStatus.SURVIVED)

        val result = cache.lookup(classHash, "ARITHMETIC", "foo", 42)
        assertEquals(MutationStatus.SURVIVED, result)
    }

    @Test
    fun `multiple entries per class hash`() {
        val classHash = "abc123"
        cache.store(classHash, "ARITHMETIC", "foo", 42, 0, MutationStatus.KILLED)
        cache.store(classHash, "RETURN_VALS", "foo", 43, 0, MutationStatus.SURVIVED)
        cache.store(classHash, "NULL_RETURNS", "bar", 44, 0, MutationStatus.NO_COVERAGE)

        assertEquals(MutationStatus.KILLED, cache.lookup(classHash, "ARITHMETIC", "foo", 42))
        assertEquals(MutationStatus.SURVIVED, cache.lookup(classHash, "RETURN_VALS", "foo", 43))
        assertEquals(MutationStatus.NO_COVERAGE, cache.lookup(classHash, "NULL_RETURNS", "bar", 44))
    }

    @Test
    fun `stats returns correct counts`() {
        val (entries, _) = cache.stats()
        assertEquals(0, entries)

        cache.store("hash1", "ARITHMETIC", "foo", 42, 0, MutationStatus.KILLED)
        cache.store("hash1", "RETURN_VALS", "foo", 43, 0, MutationStatus.SURVIVED)
        cache.store("hash2", "NULL_RETURNS", "bar", 44, 0, MutationStatus.NO_COVERAGE)

        val (entriesAfter, _) = cache.stats()
        assertEquals(3, entriesAfter)
    }

    @Test
    fun `clear removes all cached data`() {
        cache.store("hash1", "ARITHMETIC", "foo", 42, 0, MutationStatus.KILLED)
        cache.store("hash2", "RETURN_VALS", "bar", 43, 0, MutationStatus.SURVIVED)

        cache.clear()

        val (entries, _) = cache.stats()
        assertEquals(0, entries)
        assertNull(cache.lookup("hash1", "ARITHMETIC", "foo", 42))
        assertNull(cache.lookup("hash2", "RETURN_VALS", "bar", 43))
    }

    @Test
    fun `cache creates directory structure`() {
        cache.store("abc123def456ghi789", "ARITHMETIC", "foo", 42, 0, MutationStatus.KILLED)

        val cacheDir = File(tempDir, ".mutkt/cache")
        assertTrue(cacheDir.exists(), "cache directory should be created at ${cacheDir.absolutePath}")
        assertTrue(cacheDir.isDirectory, "cache path should be a directory")
    }
}
