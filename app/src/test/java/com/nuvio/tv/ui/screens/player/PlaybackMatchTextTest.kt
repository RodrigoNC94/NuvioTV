package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.streams.StreamBadgeFilter
import com.nuvio.tv.core.streams.StreamBadgeImport
import com.nuvio.tv.core.streams.StreamBadgeMatcher
import com.nuvio.tv.core.streams.StreamBadgeRules
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackMatchTextTest {
    private val filters = StreamBadgeMatcher.compile(
        StreamBadgeRules(
            imports = listOf(
                StreamBadgeImport(
                    sourceUrl = "https://example.com/badges.json",
                    filters = listOf(
                        filter("4K", "(?i)\\b(2160p|4k)\\b"),
                        filter("WebDL", "(?i)\\bweb[-. ]?dl\\b"),
                        filter("Vision", "(?i)\\b(dv|dovi|dolby[ .]?vision)\\b"),
                        filter("HDR", "(?i)\\bhdr(10)?\\b"),
                        filter("10bit", "(?i)\\b10[ -]?bit\\b"),
                        filter("HEVC", "(?i)\\b(hevc|[xh][ .]?265)\\b"),
                        filter("Atmos", "(?i)\\batmos\\b"),
                        filter("English", "(?i)\\b(eng|english)\\b")
                    )
                )
            )
        )
    )

    private fun filter(name: String, pattern: String) =
        StreamBadgeFilter(name = name, pattern = pattern, imageURL = "https://example.com/$name.png")

    private fun matchedNames(info: PlayerMediaInfo, languages: List<String> = emptyList()): List<String> =
        StreamBadgeMatcher.matchedBadges(listOf(playbackMatchText(info, languages)), filters).map { it.name }

    @Test
    fun `matches what is playing`() {
        val info = PlayerMediaInfo(
            videoCodec = "HEVC",
            videoWidth = 3840,
            videoHeight = 1600,
            audioCodec = "E-AC-3-JOC",
            audioChannels = 6,
            hdr = listOf("DV", "HDR10"),
            bitDepth = 10
        )
        assertEquals(
            listOf("4K", "Vision", "HDR", "10bit", "HEVC", "Atmos", "English"),
            matchedNames(info, listOf("eng"))
        )
    }

    @Test
    fun `skips badges the playback does not have`() {
        val info = PlayerMediaInfo(videoCodec = "AVC", videoWidth = 1920, videoHeight = 1080, audioCodec = "AAC", audioChannels = 2)
        assertEquals(emptyList<String>(), matchedNames(info))
    }

    @Test
    fun `reads atmos from mpv audio profile`() {
        val info = PlayerMediaInfo(audioCodec = "TrueHD", audioProfile = "Dolby TrueHD + Dolby Atmos")
        assertEquals(listOf("Atmos"), matchedNames(info))
    }

    @Test
    fun `uses the pack chosen for the player`() {
        val streamPack = StreamBadgeImport(
            sourceUrl = "https://example.com/stream.json",
            filters = listOf(filter("Stream4K", "\u2063\u200b\u2063"))
        )
        val playerPack = StreamBadgeImport(
            sourceUrl = "https://example.com/player.json",
            filters = listOf(filter("Player4K", "(?i)\\b2160p\\b"))
        )
        val rules = StreamBadgeRules(imports = listOf(streamPack, playerPack)).normalized()
        val text = playbackMatchText(PlayerMediaInfo(videoWidth = 3840, videoHeight = 2160), emptyList())
        fun namesFor(source: String) =
            StreamBadgeMatcher.matchedBadges(listOf(text), StreamBadgeMatcher.compile(rules.forPlayer(source))).map { it.name }

        assertEquals(emptyList<String>(), namesFor(""))
        assertEquals(listOf("Player4K"), namesFor("https://example.com/player.json"))
        assertEquals(emptyList<String>(), namesFor("https://example.com/removed.json"))
    }

    @Test
    fun `ExoPlayer takes DTS-HD MA and Atmos from the release name like the built-in pictures`() {
        val release = "Film.2023.2160p.UHD.BluRay.REMUX.DV.HDR.HEVC.TrueHD.Atmos.7.1-GRP Film DTS-HD.MA.5.1"
        val truehd = playbackMatchText(PlayerMediaInfo(audioCodec = "TrueHD"), emptyList(), release)
        assertEquals(true, " Atmos" in " $truehd")
        val dts = playbackMatchText(PlayerMediaInfo(audioCodec = "DTS-HD"), emptyList(), release)
        assertEquals(true, "DTS-HD MA" in dts)
        val aac = playbackMatchText(PlayerMediaInfo(audioCodec = "AAC"), emptyList(), release)
        assertEquals(false, "Atmos" in aac || "DTS-HD MA" in aac)
        val dtsCore = playbackMatchText(PlayerMediaInfo(audioCodec = "DTS"), emptyList(), release)
        assertEquals(false, "DTS-HD MA" in dtsCore)
    }

    private val filenamePack = StreamBadgeMatcher.compile(
        StreamBadgeRules(
            imports = listOf(
                StreamBadgeImport(
                    sourceUrl = "https://example.com/filenames.json",
                    filters = listOf(
                        filter("DDP Atmos", "(?i)DDP[ .]?5\\.1[ .]?Atmos"),
                        filter("TrueHD Atmos", "(?i)TrueHD[ .]?(7\\.1[ .]?)?Atmos"),
                        filter("DV HDR", "(?i)\\b(DV|DoVi)[ .]HDR"),
                        filter("DV Only", "DV Only"),
                        filter("HDR Only", "HDR Only"),
                        filter("SDR", "\\bSDR\\b"),
                        filter("DTS-HD MA", "(?i)DTS[-. ]?HD[-. ]?MA"),
                        filter("DTS-X", "(?i)DTS[-:. ]?X\\b"),
                        filter("AC3", "(?i)\\b(DD|AC3)\\b(?!\\+)"),
                        filter("Stereo", "(?i)\\bstereo\\b"),
                        filter("French", "\\b(FRE|FRA|French)\\b")
                    )
                )
            )
        )
    )

    private fun filenameNames(info: PlayerMediaInfo, language: String? = null, release: String = ""): List<String> =
        StreamBadgeMatcher.matchedBadges(listOf(playbackMatchText(info, playbackLanguageTokens(language), release)), filenamePack)
            .map { it.name }

    @Test
    fun `matches packs written for release names`() {
        val info = PlayerMediaInfo(
            videoCodec = "HEVC", videoWidth = 3840, videoHeight = 2160,
            audioCodec = "E-AC-3-JOC", audioChannels = 6, hdr = listOf("DV", "HDR10")
        )
        assertEquals(listOf("DDP Atmos", "DV HDR"), filenameNames(info))
    }

    @Test
    fun `a dolby vision file shown as hdr10 reads hdr only`() {
        val info = PlayerMediaInfo(videoCodec = "HEVC", videoWidth = 3840, videoHeight = 2160, audioCodec = "TrueHD", audioChannels = 8, hdr = listOf("HDR10"))
        assertEquals(
            listOf("TrueHD Atmos", "HDR Only"),
            filenameNames(info, release = "Film.2160p.REMUX.DV.HDR.TrueHD.7.1.Atmos-GRP")
        )
        assertEquals(listOf("DV Only"), filenameNames(PlayerMediaInfo(hdr = listOf("DV"))))
    }

    @Test
    fun `sdr stereo and the track language`() {
        val info = PlayerMediaInfo(videoCodec = "AVC", videoWidth = 1920, videoHeight = 800, audioCodec = "AAC", audioChannels = 2)
        assertEquals(listOf("SDR", "Stereo", "French"), filenameNames(info, "fr"))
    }

    @Test
    fun `dts-hd takes ma and x from the release name on exoplayer and from the profile on mpv`() {
        val exo = PlayerMediaInfo(audioCodec = "DTS-HD", audioChannels = 8)
        assertEquals(listOf("DTS-HD MA", "DTS-X"), filenameNames(exo, release = "Film.BluRay.DTS-HD.MA.7.1.DTS-X-GRP"))
        assertEquals(emptyList<String>(), filenameNames(exo))
        val mpv = PlayerMediaInfo(audioCodec = "DTS", audioProfile = "DTS-HD MA + DTS:X", audioChannels = 8)
        assertEquals(listOf("DTS-HD MA", "DTS-X"), filenameNames(mpv))
    }

    @Test
    fun `dolby digital plus does not read as dolby digital`() {
        assertEquals(emptyList<String>(), filenameNames(PlayerMediaInfo(audioCodec = "E-AC-3", audioChannels = 6)))
        assertEquals(listOf("AC3"), filenameNames(PlayerMediaInfo(audioCodec = "AC-3", audioChannels = 6)))
    }

    @Test
    fun `release-name spellings`() {
        val text = playbackMatchText(
            PlayerMediaInfo(videoCodec = "HEVC", audioCodec = "E-AC-3-JOC", audioChannels = 6), emptyList()
        ).split(' ')
        listOf("DDP5.1", "DD+5.1", "EAC3.5.1", "DDP5.1.Atmos", "DDPA5.1", "H.265", "x265").forEach {
            assertEquals(it, true, it in text)
        }
        assertEquals(false, "EAC35.1" in text)
    }

    @Test
    fun `language tokens carry both iso 639-2 codes`() {
        assertEquals(listOf("de", "deu", "ger", "German"), playbackLanguageTokens("de").take(4))
        assertEquals(emptyList<String>(), playbackLanguageTokens("und"))
        assertEquals(emptyList<String>(), playbackLanguageTokens(null))
    }

    @Test
    fun `shows nothing before playback info loads`() {
        assertEquals(emptyList<String>(), matchedNames(PlayerMediaInfo()))
    }
}
