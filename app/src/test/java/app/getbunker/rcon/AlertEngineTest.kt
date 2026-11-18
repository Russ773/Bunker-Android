package app.getbunker.rcon

import app.getbunker.rcon.core.AlertConfig
import app.getbunker.rcon.core.AlertEngine
import app.getbunker.rcon.core.SessionEvent
import app.getbunker.rcon.rcon.ServerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEngineTest {
    private val minute = 60_000L

    private fun info(players: Int = 20, fps: Double = 50.0, uptime: Long = 100_000) =
        ServerInfo("Test", players, 100, 0, 0, 180_000, fps, uptime, 6000, "Procedural Map")

    private val all = AlertConfig(restart = true, lowFps = true, fpsBelow = 20, fpsMinutes = 2, playerDrop = true)

    @Test fun aRestartIsSpottedWhenUptimeGoesBackwards() {
        val e = AlertEngine()
        assertTrue(e.onReading(0, info(uptime = 50_000), all).isEmpty())
        assertTrue(e.onReading(5_000, info(uptime = 50_005), all).isEmpty())
        val events = e.onReading(10_000, info(uptime = 12), all)
        assertEquals(listOf<SessionEvent>(SessionEvent.Restarted(50_005)), events)
        assertTrue(e.onReading(15_000, info(uptime = 17), all).isEmpty())
    }

    @Test fun smallWobblesInUptimeAreNotARestart() {
        val e = AlertEngine()
        e.onReading(0, info(uptime = 50_000), all)
        assertTrue(e.onReading(5_000, info(uptime = 49_990), all).isEmpty()) // 10 seconds back: clock noise
    }

    @Test fun restartAlertCanBeSwitchedOff() {
        val e = AlertEngine()
        val cfg = all.copy(restart = false)
        e.onReading(0, info(uptime = 50_000), cfg)
        assertTrue(e.onReading(5_000, info(uptime = 5), cfg).isEmpty())
    }

    @Test fun lowFpsOnlyFiresAfterItHasStayedLow() {
        val e = AlertEngine()
        assertTrue(e.onReading(0, info(fps = 10.0), all).isEmpty())
        assertTrue(e.onReading(minute, info(fps = 11.0), all).isEmpty())
        val fired = e.onReading(2 * minute, info(fps = 9.0), all)
        assertEquals(listOf<SessionEvent>(SessionEvent.LowFps(9, 2)), fired)
        assertTrue("only once per dip", e.onReading(3 * minute, info(fps = 9.0), all).isEmpty())
    }

    @Test fun aShortDipDoesNotFireAndStartsOverAfterRecovery() {
        val e = AlertEngine()
        e.onReading(0, info(fps = 10.0), all)
        e.onReading(minute, info(fps = 40.0), all)                          // recovered
        assertTrue(e.onReading(minute + 90_000, info(fps = 10.0), all).isEmpty())
        assertTrue("the clock restarted", e.onReading(minute + 2 * minute, info(fps = 10.0), all).isEmpty())
    }

    @Test fun lowFpsWaitsForCooldownBeforeFiringAgain() {
        val e = AlertEngine()
        e.onReading(0, info(fps = 10.0), all)
        assertEquals(1, e.onReading(2 * minute, info(fps = 10.0), all).size)
        e.onReading(2 * minute + 10_000, info(fps = 40.0), all)             // recovers
        e.onReading(2 * minute + 20_000, info(fps = 10.0), all)             // dips again soon
        assertTrue(e.onReading(5 * minute, info(fps = 10.0), all).isEmpty()) // still inside the 15 minute cooldown
        assertEquals("cooldown over", 1, e.onReading(17 * minute, info(fps = 10.0), all).size)
        assertTrue("and once only", e.onReading(20 * minute, info(fps = 10.0), all).isEmpty())
    }

    @Test fun lowFpsOffMeansSilence() {
        val e = AlertEngine()
        val cfg = all.copy(lowFps = false)
        e.onReading(0, info(fps = 1.0), cfg)
        assertTrue(e.onReading(10 * minute, info(fps = 1.0), cfg).isEmpty())
    }

    @Test fun aBigDropInPlayersIsReported() {
        val e = AlertEngine()
        e.onReading(0, info(players = 30), all)
        e.onReading(minute, info(players = 28), all)
        val events = e.onReading(2 * minute, info(players = 12), all)
        assertEquals(listOf<SessionEvent>(SessionEvent.PlayerDrop(30, 12)), events)
        assertTrue("not again straight away", e.onReading(2 * minute + 5_000, info(players = 10), all).isEmpty())
    }

    @Test fun aSlowTrickleOrASmallServerIsNotADrop() {
        val e = AlertEngine()
        e.onReading(0, info(players = 8), all)
        assertTrue(e.onReading(minute, info(players = 2), all).isEmpty())      // small server: peak below 10
        val e2 = AlertEngine()
        e2.onReading(0, info(players = 40), all)
        assertTrue(e2.onReading(minute, info(players = 36), all).isEmpty())   // a few leaving is normal
        // the old peak ages out, so a slow slide over many minutes never counts as one drop
        for (m in 1..10) e2.onReading(m * 4 * minute, info(players = 40 - m * 3), all)
    }

    @Test fun aRestartIsOneAlertNotTwo() {
        val e = AlertEngine()
        e.onReading(0, info(players = 40, uptime = 90_000), all)
        val events = e.onReading(minute, info(players = 0, uptime = 20), all)
        assertEquals(listOf<SessionEvent>(SessionEvent.Restarted(90_000)), events)
    }

    @Test fun playerDropCanBeSwitchedOff() {
        val e = AlertEngine()
        val cfg = all.copy(playerDrop = false)
        e.onReading(0, info(players = 40), cfg)
        assertTrue(e.onReading(minute, info(players = 5), cfg).isEmpty())
    }
}
