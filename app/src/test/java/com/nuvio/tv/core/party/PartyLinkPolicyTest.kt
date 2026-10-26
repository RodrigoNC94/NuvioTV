package com.nuvio.tv.core.party

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyLinkPolicyTest {

    @Test
    fun `plain public links may be shared`() {
        assertTrue(PartyLinkPolicy.isShareable("https://cdn.example.org/films/film.mkv"))
        assertTrue(PartyLinkPolicy.isShareable("http://203.0.113.9:8080/v/film.mp4?sig=abc123"))
    }

    @Test
    fun `account links and device links stay on this device`() {
        val refused = listOf(
            null,
            "",
            "magnet:?xt=urn:btih:abc",
            "torrent://abc",
            "http://127.0.0.1:8091/stream?link=abc",
            "http://localhost/video.mkv",
            "http://192.168.1.10:8096/Videos/1/stream.mkv",
            "http://10.0.0.4:8080/v.mkv",
            "http://172.20.1.1/v.mkv",
            "http://100.64.0.3/v.mkv",
            "http://nas/v.mkv",
            "http://box.local/v.mkv",
            "https://user:secret@example.org/v.mkv",
            "https://abc.download.real-debrid.com/d/XYZ/film.mkv",
            "https://lax1.rdeb.io/d/XYZ/film.mkv",
            "https://store-1.torbox.app/dl/film.mkv",
            "https://server.premiumize.me/dl/film.mkv",
            "https://addon.example.org/realdebrid=KEY/stream/film.mkv",
            "https://media.example.org/Videos/1/stream.mkv?api_key=abc",
            "https://media.example.org/emby/Videos/1/stream.mkv",
            "https://plex.example.org/library/parts/1/file.mkv?X-Plex-Token=abc",
        )
        refused.forEach { assertFalse(it.toString(), PartyLinkPolicy.isShareable(it)) }
    }

    @Test
    fun `addon links carrying a whole encoded config stay on this device`() {
        val blob = "eyJkZWJyaWRTZXJ2aWNlIjoiYWxsZGVicmlkIiwiZGVicmlkQXBpS2V5IjoiQUJDREVGR0hJSktMTU5PUCJ9"
        assertFalse(PartyLinkPolicy.isShareable("https://comet.example.org/$blob/playback/abc/0/film.mkv"))
        assertFalse(PartyLinkPolicy.isShareable("https://mf.example.org/streaming_provider/${blob}_x/stream/film.mkv"))
        assertFalse(PartyLinkPolicy.isShareable("https://proxy.example.org/play?c=$blob"))
    }

    @Test
    fun `hashes and long release names are still shareable`() {
        val infoHash = "0123456789abcdef0123456789abcdef01234567"
        val sha256 = "0123456789abcdef".repeat(4)
        assertTrue(PartyLinkPolicy.isShareable("https://cdn.example.org/$infoHash/0/film.mkv"))
        assertTrue(PartyLinkPolicy.isShareable("https://cdn.example.org/$sha256/film.mkv"))
        assertTrue(
            PartyLinkPolicy.isShareable(
                "https://cdn.example.org/Some.Film.2019.2160p.UHD.BluRay.REMUX.HDR.HEVC.TrueHD.Atmos.7.1-GROUP.mkv"
            )
        )
    }
}
