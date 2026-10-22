package com.nuvio.tv.core.streams

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AioStreamsBadgeScopeTest {
    @Test
    fun `matches base and personalized AIOStreams manifest ids`() {
        assertTrue(isAioStreamsManifestId("com.aiostreams.viren070"))
        assertTrue(isAioStreamsManifestId("com.aiostreams.viren070.1234abcd"))
        assertTrue(isAioStreamsManifestId("COM.AIOSTREAMS.VIREN070.session"))
    }

    @Test
    fun `does not match other addons or malformed prefixes`() {
        assertFalse(isAioStreamsManifestId(null))
        assertFalse(isAioStreamsManifestId(""))
        assertFalse(isAioStreamsManifestId("com.aiostreams.viren070evil"))
        assertFalse(isAioStreamsManifestId("com.cinemeta"))
        assertFalse(isAioStreamsManifestId("com.torrentio"))
    }
}
