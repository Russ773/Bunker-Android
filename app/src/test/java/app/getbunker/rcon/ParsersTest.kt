package app.getbunker.rcon

import app.getbunker.rcon.core.ChatSender
import app.getbunker.rcon.core.ReconnectPlan
import app.getbunker.rcon.core.ScheduleMath
import app.getbunker.rcon.data.HideRule
import app.getbunker.rcon.data.ScheduledTask
import app.getbunker.rcon.rcon.BanListParser
import app.getbunker.rcon.rcon.ChatText
import app.getbunker.rcon.rcon.Cmd
import app.getbunker.rcon.rcon.CommandGuess
import app.getbunker.rcon.rcon.ConsoleFilter
import app.getbunker.rcon.rcon.Fmt
import app.getbunker.rcon.rcon.LogEntry
import app.getbunker.rcon.rcon.LogKind
import app.getbunker.rcon.rcon.RconParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    private fun line(text: String, kind: LogKind = LogKind.Normal) = LogEntry(1, 0, text, kind)

    @Test fun serverInfoIsRead() {
        val info = RconParsers.parseInfo("""{"Hostname":"My Server","MaxPlayers":100,"Players":12,"Queued":2,"Joining":1,"EntityCount":180000,"Uptime":93784,"Map":"Procedural Map","Framerate":59.8,"Memory":6200}""")
        assertNotNull(info)
        assertEquals("My Server", info!!.hostname)
        assertEquals(12, info.players)
        assertEquals(100, info.maxPlayers)
        assertEquals(2, info.queued)
        assertEquals(59.8, info.fps, 0.001)
        assertEquals(93784L, info.uptimeSeconds)
    }

    @Test fun badServerInfoIsNull() {
        assertNull(RconParsers.parseInfo("not json"))
        assertNull(RconParsers.parsePlayers("{}"))
    }

    @Test fun playerListIsRead() {
        val list = RconParsers.parsePlayers("""[{"SteamID":"76561198000000001","DisplayName":"Russ","Ping":42,"Address":"1.2.3.4:5","ConnectedSeconds":600,"Health":87.5}]""")!!
        assertEquals(1, list.size)
        assertEquals("76561198000000001", list[0].steamId)
        assertEquals("Russ", list[0].name)
        assertEquals(42, list[0].ping)
        assertEquals(87.5, list[0].health, 0.001)
    }

    @Test fun jsonChatIsParsedWithColourAndChannel() {
        val c = RconParsers.parseChat("""{"Channel":1,"Message":"hello team","UserId":"76561198000000001","Username":"Russ","Color":"#5af","Time":1}""")
        assertEquals("Russ: hello team", c.text)
        assertEquals("Russ", c.name)
        assertEquals("hello team", c.message)
        assertEquals("#5af", c.color)
        assertEquals(1, c.channel)
        assertNull(c.tags)
    }

    @Test fun pluginTagsAreSplitOffChat() {
        val c = RconParsers.parseChat("""{"Channel":0,"Message":"[ADMIN] Russ: Testing","UserId":"1","Username":"Russ","Color":"#fff"}""")
        assertEquals("[ADMIN] Russ: Testing", c.text)
        assertEquals("[ADMIN]", c.tags)
        assertEquals("Testing", c.message)
    }

    @Test fun plainChatFallsBack() {
        val c = RconParsers.parseChat("SERVER: hi\n")
        assertEquals("SERVER: hi", c.text)
        assertNull(c.name)
    }

    @Test fun markupIsStripped() {
        assertEquals("Hello", ChatText.stripMarkup("<color=red>Hello</color>"))
    }

    @Test fun quoteRemovesQuotesAndControlCharacters() {
        assertEquals("\"it's fine\"", Cmd.quote("it\"s fine".replace("\"s", "'s")))
        assertEquals("\"ab\"", Cmd.quote("a\nb"))
        assertEquals(202, Cmd.quote("x".repeat(500)).length)
    }

    @Test fun commandGuessSeparatesCommandsFromChatter() {
        assertTrue(CommandGuess.looksLikeCommand("server.save"))
        assertTrue(CommandGuess.looksLikeCommand("kick \"765\" bye"))
        assertTrue(CommandGuess.looksLikeCommand("c.reload Economics"))
        assertFalse(CommandGuess.looksLikeCommand("hello everyone how are you"))
        assertTrue(CommandGuess.looksLikeCommand("hello there", listOf("hello")))
    }

    @Test fun banListIsParsed() {
        val bans = BanListParser.tryParse("1 76561198012345678 \"Griefer\" \"Griefing\" -1\n2 76561198087654321 \"Speedy\" \"Speed hack\" 1893456000")!!
        assertEquals(2, bans.size)
        assertTrue(bans[0].permanent)
        assertEquals("Griefer", bans[0].name)
        assertEquals("Speed hack", bans[1].reason)
        assertFalse(bans[1].permanent)
        assertEquals("Permanent", BanListParser.describeExpiry(-1))
    }

    @Test fun otherRepliesAreNotBanLists() {
        assertNull(BanListParser.tryParse("Unknown command: banlistex"))
        assertNull(BanListParser.tryParse(""))
    }

    @Test fun hideRulesMatchPluginsAndWords() {
        val rules = listOf(HideRule(HideRule.KIND_PLUGIN, "Economics"), HideRule(HideRule.KIND_WORDS, "refreshing group"))
        assertTrue(ConsoleFilter.isHidden(rules, line("[Economics] Saving balances")))
        assertTrue(ConsoleFilter.isHidden(rules, line("14:30:47 [Economics] Saving balances")))
        assertTrue(ConsoleFilter.isHidden(rules, line("[Steam Groups] Refreshing group membership")))
        assertFalse(ConsoleFilter.isHidden(rules, line("[Kits] hello")))
        // your own commands and Bunker's notices are never hidden
        assertFalse(ConsoleFilter.isHidden(rules, line("> say [Economics]", LogKind.Command)))
        assertFalse(ConsoleFilter.isHidden(rules, line("[Economics] reconnecting", LogKind.Info)))
    }

    @Test fun pluginsSeenAreCountedBusiestFirst() {
        val seen = ConsoleFilter.pluginsSeen(listOf(line("[Kits] a"), line("[Economics] b"), line("[Economics] c"), line("[Error] ignored level tag")))
        assertEquals("economics", seen[0].first)
        assertEquals(2, seen[0].second)
        assertEquals(2, seen.size)
    }

    @Test fun chatCommandsAreBuilt() {
        assertEquals("bunker.say 76561198000000001 \"Russ\" \"#aaff55\" \"hi\"", ChatSender.pluginCommand("76561198000000001", "Russ", "#aaff55", "hi"))
        assertEquals("bunker.say 0 \"Russ\" \"#aaff55\" \"hi\"", ChatSender.pluginCommand("", "Russ", "nonsense", "hi"))
        assertEquals("say \"Russ: hi\"", ChatSender.sayCommand("Russ", "hi"))
        assertTrue(ChatSender.looksUnknown("Unknown command: bunker.say"))
    }

    @Test fun waitStepsAreRecognised() {
        assertEquals(30, ScheduleMath.waitSeconds("wait 30"))
        assertEquals(3600, ScheduleMath.waitSeconds("WAIT 99999".replace("99999", "9999")))
        assertNull(ScheduleMath.waitSeconds("say wait 30"))
    }

    @Test fun intervalSchedulesAreDueAfterTheirInterval() {
        val t = ScheduledTask(mode = ScheduledTask.MODE_INTERVAL, intervalMinutes = 10, lastRunMillis = 0)
        val connected = 1_000_000L
        assertFalse(ScheduleMath.isDue(t, connected, connected + 5 * 60_000))
        assertTrue(ScheduleMath.isDue(t, connected, connected + 10 * 60_000))
        assertFalse(ScheduleMath.isDue(t.copy(enabled = false), connected, connected + 60 * 60_000))
    }

    @Test fun timeOfDayIsValidated() {
        assertEquals(4 to 0, ScheduleMath.parseTime("04:00"))
        assertEquals(23 to 59, ScheduleMath.parseTime("23:59"))
        assertNull(ScheduleMath.parseTime("24:00"))
        assertNull(ScheduleMath.parseTime("nonsense"))
    }

    @Test fun reconnectWaitsGrowThenSettle() {
        assertEquals(3, ReconnectPlan.delaySeconds(1))
        assertEquals(30, ReconnectPlan.delaySeconds(5))
        assertEquals(60, ReconnectPlan.delaySeconds(6))
        assertEquals(60, ReconnectPlan.delaySeconds(99))
    }

    @Test fun durationsAreReadable() {
        assertEquals("59s", Fmt.duration(59))
        assertEquals("1h 1m", Fmt.duration(3700))
        assertEquals("1d 2h", Fmt.duration(93784))
        assertEquals("6.0 GB", Fmt.memory(6144))
    }
}
