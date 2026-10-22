package com.v2ray.ang.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerListLogicTest {

    @Test
    fun `unknown latency stays neutral`() {
        assertEquals(LatencyTier.Unknown, latencyTier(0L))
    }

    @Test
    fun `latency buckets cover good fair and poor`() {
        assertEquals(LatencyTier.Good, latencyTier(1L))
        assertEquals(LatencyTier.Good, latencyTier(300L))
        assertEquals(LatencyTier.Fair, latencyTier(301L))
        assertEquals(LatencyTier.Fair, latencyTier(800L))
        assertEquals(LatencyTier.Poor, latencyTier(801L))
    }

    @Test
    fun `failed latency is poor`() {
        assertEquals(LatencyTier.Poor, latencyTier(-1L))
    }
}
