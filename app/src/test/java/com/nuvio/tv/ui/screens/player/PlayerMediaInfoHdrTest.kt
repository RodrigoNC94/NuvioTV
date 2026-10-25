package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMediaInfoHdrTest {

    private val hdr10Sps = "00000001420101022000000300900000030000030099a001e020021c4d9657924c226a12201201"
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun hevc(colorTransfer: Int? = null, initData: ByteArray? = null): Format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .apply { colorTransfer?.let { setColorInfo(ColorInfo.Builder().setColorTransfer(it).build()) } }
            .apply { initData?.let { setInitializationData(listOf(it)) } }
            .build()

    @Test
    fun `hdr10 remux without the mkv colour element reads the sps`() {
        assertEquals(listOf("HDR10"), hevc(initData = hdr10Sps).hdrFormats())
    }

    @Test
    fun `container colour info wins`() {
        assertEquals(listOf("HLG"), hevc(C.COLOR_TRANSFER_HLG, hdr10Sps).hdrFormats())
        assertEquals(emptyList<String>(), hevc(C.COLOR_TRANSFER_SDR, hdr10Sps).hdrFormats())
    }

    @Test
    fun `dolby vision keeps its hdr10 base`() {
        val dv = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
            .setColorInfo(ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_ST2084).build())
            .build()
        assertEquals(listOf("DV", "HDR10"), dv.hdrFormats())
    }

    @Test
    fun `sdr without any colour data`() {
        assertEquals(emptyList<String>(), hevc().hdrFormats())
    }
}
