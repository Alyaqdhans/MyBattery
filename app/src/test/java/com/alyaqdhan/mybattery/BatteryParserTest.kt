package com.alyaqdhan.mybattery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class BatteryParserTest {

    private val fileName = "dumpState_SM-G991B_202611010900.log"

    private fun parse(log: String, name: String = fileName) =
        BatteryParser.parseInputStream(log.byteInputStream(), name)

    private fun sections(log: String) = BatteryParser.readBatterySections(log.byteInputStream())

    private fun millis(date: String, pattern: String = "yyyy-MM-dd HH:mm:ss") =
        SimpleDateFormat(pattern, Locale.US).parse(date)!!.time

    // ── Health ────────────────────────────────────────────────────────────────

    @Test
    fun `asoc is the main health value`() {
        val info = parse("  mSavedBatteryAsoc: 87\n  mSavedBatteryBsoh: 90\n")
        assertEquals(87, info.healthPercent)
        assertEquals("asoc", info.healthSource)
        assertFalse(info.healthUnsupported)
        assertTrue(info.readSuccess)
    }

    @Test
    fun `bsoh is used when asoc is missing`() {
        val info = parse("  mSavedBatteryBsoh: 74\n")
        assertEquals(74, info.healthPercent)
        assertEquals("bsoh", info.healthSource)
    }

    @Test
    fun `bsoh is used when asoc is unsupported`() {
        val info = parse("  mSavedBatteryAsoc: [unsupported]\n  mSavedBatteryBsoh: 74\n")
        assertEquals(74, info.healthPercent)
        assertEquals("bsoh", info.healthSource)
    }

    @Test
    fun `health can be in brackets`() {
        assertEquals(87, parse("mSavedBatteryAsoc: [87]\n").healthPercent)
    }

    @Test
    fun `text after the health number is ignored`() {
        assertEquals(87, parse("mSavedBatteryAsoc: 87 percent\n").healthPercent)
    }

    @Test
    fun `negative health counts as missing`() {
        val info = parse("mSavedBatteryAsoc: -1\n")
        assertNull(info.healthPercent)
        assertTrue(info.healthUnsupported)
    }

    @Test
    fun `no health value means unsupported`() {
        val info = parse("mSavedBatteryUsage: 124500\n")
        assertNull(info.healthPercent)
        assertTrue(info.healthUnsupported)
    }

    // ── Cycles ────────────────────────────────────────────────────────────────

    @Test
    fun `usage is stored as cycles times one hundred`() {
        assertEquals(1245, parse("mSavedBatteryUsage: 124500\n").cycleCount)
    }

    @Test
    fun `usage rounds down to whole cycles`() {
        assertEquals(1245, parse("mSavedBatteryUsage: 124599\n").cycleCount)
    }

    @Test
    fun `unsupported usage has no cycle count`() {
        assertNull(parse("mSavedBatteryUsage: [unsupported]\n").cycleCount)
    }

    // ── Dates ─────────────────────────────────────────────────────────────────

    @Test
    fun `llb cal gives the battery date`() {
        val info = parse("LLB CAL: 20240115\n")
        assertEquals(millis("20240115", "yyyyMMdd"), info.batteryDateMs)
        assertEquals("CAL", info.llbType)
    }

    @Test
    fun `llb man gives the battery date`() {
        val info = parse("llb man: 20230405\n")
        assertEquals(millis("20230405", "yyyyMMdd"), info.batteryDateMs)
        assertEquals("MAN", info.llbType)
    }

    @Test
    fun `the first llb line wins`() {
        val info = parse("LLB CAL: 20240115\nLLB MAN: 20230405\n")
        assertEquals(millis("20240115", "yyyyMMdd"), info.batteryDateMs)
    }

    @Test
    fun `an llb line with extra text is ignored`() {
        assertEquals(0L, parse("LLB CAL: 20240115 trailing\n").batteryDateMs)
    }

    @Test
    fun `log time comes from the dumpstate header`() {
        val info = parse("== dumpstate: 2026-10-31 08:15:30\n", name = "dumpState_nostamp.log")
        assertEquals(millis("2026-10-31 08:15:30"), info.logTimestampMs)
    }

    @Test
    fun `log time can come from a bracketed line`() {
        val info = parse("[2026-10-31 08:15:30] something\n", name = "dumpState_nostamp.log")
        assertEquals(millis("2026-10-31 08:15:30"), info.logTimestampMs)
    }

    @Test
    fun `log time falls back to the file name`() {
        assertEquals(millis("2026-11-01 09:00:00"), parse("no dates here\n").logTimestampMs)
    }

    // ── Read success ──────────────────────────────────────────────────────────

    @Test
    fun `a log without battery lines is not a success`() {
        assertFalse(parse("nothing useful here\n").readSuccess)
    }

    @Test
    fun `seeing an unsupported asoc still counts as a success`() {
        val info = parse("mSavedBatteryAsoc: [unsupported]\n")
        assertTrue(info.readSuccess)
        assertTrue(info.healthUnsupported)
    }

    @Test
    fun `a battery date alone counts as a success`() {
        assertTrue(parse("LLB CAL: 20240115\n").readSuccess)
    }

    // ── File names ────────────────────────────────────────────────────────────

    @Test
    fun `dumpstate logs are recognised whatever the case`() {
        assertTrue(BatteryParser.isDumpStateLog("dumpState_SM-G991B_202611010900.log"))
        assertTrue(BatteryParser.isDumpStateLog("DUMPSTATE_x.LOG"))
    }

    @Test
    fun `other files are not dumpstate logs`() {
        assertFalse(BatteryParser.isDumpStateLog("dumpState_SM-G991B.txt"))
        assertFalse(BatteryParser.isDumpStateLog("kernel_dumpState.log"))
    }

    @Test
    fun `display name keeps just the model`() {
        assertEquals("dumpstate_G991B", BatteryParser.truncateLogDisplayName(fileName))
        assertEquals(
            "dumpstate_G9766H",
            BatteryParser.truncateLogDisplayName("dumpstate_SM-G9766HE_20231015_123045.log")
        )
    }

    @Test
    fun `display name falls back when no model is found`() {
        assertEquals("dumpstate_abc", BatteryParser.truncateLogDisplayName("dumpstate_abc_x.log"))
    }

    @Test
    fun `other names only lose the extension and trailing timestamp`() {
        assertEquals("other", BatteryParser.truncateLogDisplayName("other_20231015123045.log"))
    }

    @Test
    fun `timestamp is read from twelve digits in the file name`() {
        assertEquals(
            millis("2026-11-01 09:00:00"),
            BatteryParser.extractTimestampFromFileName(fileName)
        )
    }

    @Test
    fun `a file name without twelve digits has no timestamp`() {
        assertEquals(0L, BatteryParser.extractTimestampFromFileName("dumpState_20231015_123045.log"))
    }

    // ── Battery sections ──────────────────────────────────────────────────────

    @Test
    fun `both sections are extracted and labelled`() {
        val log = """
            unrelated header
            BatteryInfoBackUp
              mSavedBatteryAsoc: 87
              mSavedBatteryUsage: 124500
            another top level line
            DUMP OF SERVICE battery:
            Current Battery Service state:
              level: 84
            DUMP OF SERVICE batteryproperties:
            done
        """.trimIndent()
        assertEquals(
            "━━━ BatteryInfoBackUp ━━━\n\n" +
                "mSavedBatteryAsoc: 87\n  mSavedBatteryUsage: 124500\n\n" +
                "━━━ Battery Service Dump ━━━\n\n" +
                "Current Battery Service state:\n  level: 84",
            sections(log)
        )
    }

    @Test
    fun `the service dump can follow the backup section directly`() {
        val log = "BatteryInfoBackUp\n  mSavedBatteryAsoc: 87\nDUMP OF SERVICE battery:\n  level: 84\n"
        assertEquals(
            "━━━ BatteryInfoBackUp ━━━\n\nmSavedBatteryAsoc: 87\n\n" +
                "━━━ Battery Service Dump ━━━\n\nlevel: 84",
            sections(log)
        )
    }

    @Test
    fun `a section still open at the end of the file is kept`() {
        assertEquals(
            "━━━ Battery Service Dump ━━━\n\nlevel: 84",
            sections("DUMP OF SERVICE battery:\n  level: 84\n")
        )
    }

    @Test
    fun `log buffers are left out`() {
        val log = """
            DUMP OF SERVICE battery:
              level: 84
              [ChargerLogBuffer]
              12:00:01 plugged
              12:00:02 unplugged
              health: 2
            DUMP OF SERVICE other:
        """.trimIndent()
        assertEquals("━━━ Battery Service Dump ━━━\n\nlevel: 84\n  health: 2", sections(log))
    }

    @Test
    fun `a log with no battery sections gives null`() {
        assertNull(sections("just some text\nand more\n"))
    }

    @Test
    fun `a large section is read quickly`() {
        val log = buildString {
            appendLine("DUMP OF SERVICE battery:")
            repeat(200_000) { appendLine("  line $it: some value") }
        }
        val started = System.currentTimeMillis()
        val result  = sections(log)!!
        val elapsed = System.currentTimeMillis() - started
        assertTrue(result.endsWith("line 199999: some value"))
        assertTrue("Took ${elapsed}ms", elapsed < 10_000)
    }
}
