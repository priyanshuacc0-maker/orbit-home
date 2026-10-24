package com.orbit.home

import com.orbit.home.apps.AppIconLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * P1-38: icon sizes are bucketed before they key the cache, so a moving icon-size
 * slider does not create a new entry per step and evict everything else.
 */
class IconBucketTest {

    @Test
    fun `nearby sizes share a bucket`() {
        assertEquals(AppIconLoader.bucket(160), AppIconLoader.bucket(164))
        assertEquals(AppIconLoader.bucket(160), AppIconLoader.bucket(156))
    }

    @Test
    fun `sizes far enough apart stay distinct`() {
        assertNotEquals(AppIconLoader.bucket(100), AppIconLoader.bucket(200))
    }

    @Test
    fun `non-positive sizes are rejected`() {
        assertEquals(0, AppIconLoader.bucket(0))
        assertEquals(0, AppIconLoader.bucket(-5))
    }

    @Test
    fun `every positive size lands on a bucket boundary`() {
        for (px in 1..600) {
            val bucket = AppIconLoader.bucket(px)
            assertEquals(0, bucket % AppIconLoader.BUCKET_PX)
            // And never further than half a bucket from what was asked for.
            assertTrue(abs(bucket - px) <= AppIconLoader.BUCKET_PX / 2)
        }
    }
}
