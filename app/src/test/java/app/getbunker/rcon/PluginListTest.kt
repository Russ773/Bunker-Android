package app.getbunker.rcon

import app.getbunker.rcon.rcon.PluginList
import app.getbunker.rcon.rcon.PluginListParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses a real `c.plugins` reply from a Carbon server (copied from the Windows app's tests). */
class PluginListTest {
    private fun carbon(): PluginList {
        val text = javaClass.classLoader!!.getResourceAsStream("carbon-plugins.txt")!!.bufferedReader().readText()
        return PluginListParser.tryParse(text)!!
    }

    @Test fun carbonTableIsReadIntoColumns() {
        val list = carbon()
        assertEquals("Carbon", list.framework)
        assertEquals(listOf("Type", "Plugin", "Author", "Version", "Hook time", "Hook fires", "Memory", "Compile time", "Uptime"), list.columns)
        assertEquals(12, list.rows.size) // 1 core + 9 scripts + 2 extensions
    }

    @Test fun eachValueLandsUnderItsOwnHeading() {
        val list = carbon()
        fun row(name: String) = list.rows.single { it[list.nameColumn] == name }
        assertEquals(listOf("Scripts", "AFK API", "2CHEVSKII", "v1.0.1", "23ms", "12,301", "40.0kb", "737ms [424ms]", "11h50m30s"), row("AFK API"))
        assertEquals("Wulf/lukespragg/Arainrr/James/Bushhy", row("Auto Doors")[2])
        assertEquals("73790ms", row("Neutral NPCs")[4])
        assertEquals("1m45s", row("Steam Groups")[8])
    }

    @Test fun emptyCellsStayEmptyAndDoNotShiftTheRow() {
        val list = carbon()
        val checker = list.rows.single { it[1] == "ChaosUpdateChecker" }
        assertEquals("", checker[4])
        assertEquals("2", checker[5])
        val spam = list.rows.single { it[1] == "Console Spam Cleaner" }
        assertEquals("", spam[6])
        assertEquals("80ms [5ms]", spam[7])
    }

    @Test fun groupsBecomeTheTypeWithoutTheirCount() {
        val list = carbon()
        assertEquals("Carbon Community", list.rows.single { it[1] == "Core" }[0])
        assertEquals("Chaos - k1lly0u (Oxide Extension)", list.rows.single { it[1] == "Chaos Commands" }[0])
    }

    @Test fun unloadedAndFailedPluginsAreReportedSeparately() {
        val list = carbon()
        assertEquals(listOf("PurgeFX.cs"), list.unloaded)
        assertEquals(3, list.failed.size)
        assertEquals("AutoRestart.cs", list.failed[0].file)
        assertEquals("16:17", list.failed[0].line)
        assertTrue(list.failed[0].message.startsWith("The type or namespace name 'CrashExtensions'"))
        assertEquals("The name 'CUIControls' does not exist in the current context", list.failed[2].message)
        assertTrue(list.summary.contains("1 failed to compile"))
        assertTrue(list.summary.contains("12 plugins loaded"))
    }

    @Test fun oxideListIsReadIntoColumns() {
        val reply = "Listing 2 plugins:\n  01 \"Better Chat\" (5.2.15) by LaserHydra (0.29s / 10 KiB) - BetterChat.cs\n  02 \"Welcomer\" (2.1.4) by Bunker (0.01s / 2 KiB) - Welcomer.cs\n"
        val list = PluginListParser.tryParse(reply)!!
        assertEquals("Oxide", list.framework)
        assertEquals(listOf("Better Chat", "5.2.15", "LaserHydra", "0.29s", "10 KiB", "BetterChat.cs"), list.rows[0])
        assertEquals(2, list.rows.size)
    }

    @Test fun otherRepliesAreNotPluginLists() {
        assertNull(PluginListParser.tryParse(""))
        assertNull(PluginListParser.tryParse("Unknown command: c.plugins"))
    }

    @Test fun sortValuesCompareNumbersNotText() {
        val v = { s: String -> PluginListParser.sortValue(s)!! }
        assertTrue(v("9ms") < v("1092ms"))
        assertTrue(v("40.0kb") < v("4.2mb"))
        assertTrue(v("1m45s") < v("11h50m"))
        assertTrue(v("v1.9.0") < v("v1.10.0"))
        assertNull(PluginListParser.sortValue("LaserHydra"))
    }

    @Test fun sortingPutsBigNumbersFirstWhenDescendingAndEmptyCellsLast() {
        val list = carbon()
        val byTime = PluginListParser.sorted(list, list.columns.indexOf("Hook time"), descending = true)
        assertEquals("Neutral NPCs", byTime[0][1]) // 73790ms
        assertEquals("TruePVE", byTime[1][1])      // 34804ms
        assertEquals("", byTime.last()[4])         // plugins with no hook time go last
        val ascending = PluginListParser.sorted(list, list.columns.indexOf("Hook time"), descending = false)
        assertEquals("Steam Groups", ascending[0][1]) // 2ms
        assertEquals("", ascending.last()[4])
    }
}
