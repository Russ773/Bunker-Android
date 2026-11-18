package app.getbunker.rcon

import app.getbunker.rcon.steam.SteamLookup
import app.getbunker.rcon.steam.SteamProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLookupTest {
    private val id = "76561198000000001"

    private val profileXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes" ?>
<profile><steamID64>$id</steamID64><steamID><![CDATA[Ironclad]]></steamID><onlineState>offline</onlineState>
<privacyState>public</privacyState><avatarIcon>https://avatars.steamstatic.com/a.jpg</avatarIcon>
<avatarFull>https://avatars.steamstatic.com/a_full.jpg</avatarFull><vacBanned>0</vacBanned><tradeBanState>None</tradeBanState>
<isLimitedAccount>0</isLimitedAccount><memberSince>March 3, 2014</memberSince><location>Netherlands</location></profile>"""

    private fun base() = SteamLookup.parseProfile(profileXml, id)!!

    @Test fun communityProfileIsRead() {
        val p = base()
        assertEquals("Ironclad", p.personaName)
        assertEquals(id, p.steamId)
        assertTrue(p.isPublic)
        assertFalse(p.limited)
        assertEquals("March 3, 2014", p.memberSince)
        assertEquals("Netherlands", p.location)
        assertEquals("https://avatars.steamstatic.com/a_full.jpg", p.avatarUrl)
        assertFalse(p.hasKeyData)
    }

    @Test fun htmlCodesInNamesAreDecoded() {
        val p = SteamLookup.parseProfile(profileXml.replace("Ironclad", "Mia &lt;3 Games &amp; &quot;Fun&quot; &#233;&#x1F600;"), id)!!
        assertEquals("Mia <3 Games & \"Fun\" é😀", p.personaName)
        assertEquals("a &lt; b", SteamLookup.decodeEntities("a &amp;lt; b")) // decoded once, not twice
        assertEquals("&#0; &#99999999999; &nope;", SteamLookup.decodeEntities("&#0; &#99999999999; &nope;")) // nonsense is left alone
    }

    @Test fun thingsThatAreNotProfilesAreRejected() {
        assertNull(SteamLookup.parseProfile("not xml at all", id))
        assertNull(SteamLookup.parseProfile("<response><error>The specified profile could not be found.</error></response>", id))
        assertNull(SteamLookup.parseProfile("<profile><steamID64>$id</steamID64></profile>", id)) // no name
        assertNull(SteamLookup.parseProfile("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY a \"b\">]><profile><steamID>x</steamID></profile>", id)) // DTDs are never read
    }

    @Test fun keyDataAddsHoursAgeAndBans() {
        val summaries = """{"response":{"players":[{"steamid":"$id","timecreated":1393804800,"loccountrycode":"NL"}]}}"""
        val bans = """{"players":[{"SteamId":"$id","CommunityBanned":false,"VACBanned":true,"NumberOfVACBans":1,"DaysSinceLastBan":420,"NumberOfGameBans":0,"EconomyBan":"none"}]}"""
        val games = """{"response":{"game_count":1,"games":[{"appid":252490,"playtime_forever":131040,"playtime_2weeks":2160}]}}"""
        val p = SteamLookup.applyKeyData(base(), summaries, bans, games)

        assertTrue(p.hasKeyData)
        assertEquals(1393804800_000L, p.accountCreatedMillis)
        assertEquals("NL", p.countryCode)
        assertEquals(2184.0, p.rustHours!!, 0.001)
        assertEquals(36.0, p.rustHoursLast2Weeks!!, 0.001)
        assertFalse(p.gamesHidden)
        assertEquals(1, p.vacBans)
        assertEquals(420, p.daysSinceLastBan)
        assertEquals("VAC ban (420 days ago)" to true, SteamLookup.bans(p))
        assertEquals("2,184 hours  (36 in last 2 weeks)", SteamLookup.rustLine(p, haveKey = true))
    }

    @Test fun aPrivateGameListIsSaidToBePrivate() {
        val p = SteamLookup.applyKeyData(base(), "{}", """{"players":[]}""", """{"response":{}}""")
        assertTrue(p.gamesHidden)
        assertNull(p.rustHours)
        assertEquals("Hidden (the player keeps this private)", SteamLookup.rustLine(p, haveKey = true))
        assertEquals("Needs a Steam key (Settings)", SteamLookup.rustLine(base(), haveKey = false))
    }

    @Test fun garbageFromSteamDoesNotBreakTheCard() {
        val p = SteamLookup.applyKeyData(base(), "<html>", "oops", "")
        assertEquals("Ironclad", p.personaName)
        assertTrue(p.hasKeyData)
        assertEquals("None on record" to false, SteamLookup.bans(p))
    }

    @Test fun banWordsAreSpelledOut() {
        val p = SteamProfile(id, "x", vacBans = 2, vacBanned = true, gameBans = 1, communityBanned = true, tradeBan = "Banned", daysSinceLastBan = 1234)
        val (text, bad) = SteamLookup.bans(p)
        assertTrue(bad)
        assertEquals("2 VAC bans, Game ban, Community ban, Trade ban: Banned (1,234 days ago)", text)
        assertEquals("None on record" to false, SteamLookup.bans(SteamProfile(id, "x")))
    }

    @Test fun accountAgeIsPutInYears() {
        val created = 1393804800_000L // 3 March 2014
        val p = SteamProfile(id, "x", accountCreatedMillis = created)
        val ten = SteamLookup.accountAge(p, created + 10 * 365L * 86_400_000L + 3 * 86_400_000L)
        assertTrue(ten, ten.contains("2014") && ten.contains("10 years old"))
        assertEquals("March 3, 2014", SteamLookup.accountAge(SteamProfile(id, "x", memberSince = "March 3, 2014")))
        assertEquals("Hidden", SteamLookup.accountAge(SteamProfile(id, "x")))
        assertEquals("1 year old", SteamLookup.yearsText(400 * 86_400_000L))
        assertEquals("3 months old", SteamLookup.yearsText(100 * 86_400_000L))
        assertEquals("under a month old", SteamLookup.yearsText(5 * 86_400_000L))
    }

    @Test fun onlySteamPicturesAreFetched() {
        assertTrue(SteamLookup.isAvatarAddress("https://avatars.steamstatic.com/a_full.jpg"))
        assertTrue(SteamLookup.isAvatarAddress("https://avatars.akamaihd.net/steam/a.jpg"))
        assertFalse(SteamLookup.isAvatarAddress("http://avatars.steamstatic.com/a.jpg"))   // not https
        assertFalse(SteamLookup.isAvatarAddress("https://evil.example.com/steamstatic.com/a.jpg"))
        assertFalse(SteamLookup.isAvatarAddress("https://steamstatic.com.evil.example/a.jpg"))
        assertFalse(SteamLookup.isAvatarAddress(null))
    }

    @Test fun idsAndKeysAreChecked() {
        assertTrue(SteamLookup.isSteamId(id))
        assertFalse(SteamLookup.isSteamId("1234"))
        assertFalse(SteamLookup.isSteamId("7656119800000000a"))
        assertTrue(SteamLookup.looksLikeKey("0123456789ABCDEF0123456789abcdef"))
        assertFalse(SteamLookup.looksLikeKey("short"))
        assertFalse(SteamLookup.looksLikeKey("0123456789ABCDEF0123456789abcdeg"))
        assertNotNull(base())
    }
}
