package com.dustin.callguard

import com.dustin.callguard.data.ScreenedCall
import com.dustin.callguard.data.VerificationStatus
import com.dustin.callguard.screening.NumberProbe
import com.dustin.callguard.screening.NxxDirectory
import com.dustin.callguard.screening.ProbeInput
import com.dustin.callguard.screening.SuspicionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.ZoneId

class NumberProbeTest {
    private val directory = NxxDirectory.parse(ByteArrayInputStream(NxxTestData.sample))
    private val zone = ZoneId.of("America/New_York")

    private fun probe(text: String, calls: List<ScreenedCall> = emptyList(), own: String? = null) =
        NumberProbe.run(ProbeInput(text, directory, own, emptyList(), calls, zone))

    private fun lines(result: com.dustin.callguard.screening.ProbeResult, title: String) =
        result.sections.first { it.title == title }.lines

    @Test
    fun caribbeanAreaCodeIsExplainedAndScored() {
        val result = probe("876-555-0123")
        assertTrue(lines(result, "number").any { it.contains("Jamaica") && it.contains("one-ring") })
        assertTrue(result.suspicion!!.reasons.any { it.contains("Jamaica") })
        assertTrue(lines(result, "phone number library").any { it.startsWith("country: Jamaica") })
    }

    @Test
    fun reportedNeighborsInTheBlockAreShownAndScored() {
        val complaints = com.dustin.callguard.complaints.ComplaintIndex.Builder().apply {
            listOf("3155550101", "3155550102", "3155550103").forEach {
                add(com.dustin.callguard.complaints.ComplaintReport(it, "2026-11-10", true, "Other"))
            }
        }.build(1)
        val result = NumberProbe.run(ProbeInput("315-555-0199", directory, null, emptyList(), emptyList(), zone, complaints))
        assertTrue(lines(result, "public complaints").any { it.startsWith("3 other numbers in the same block") })
        assertTrue(result.suspicion!!.reasons.any { it.contains("same block") })
    }

    @Test
    fun contactNeighborsAreShownInTheRelationshipSection() {
        val result = NumberProbe.run(
            ProbeInput("315-948-8351", directory, null, emptyList(), emptyList(), zone, contactNumbers = listOf("3159480000", "2125550123")),
        )
        assertTrue(lines(result, "relationship to you").any { it.contains("1 of your contacts") })
    }

    private val blockDirectory = NxxDirectory.parse(ByteArrayInputStream(NxxTestData.sample)).also {
        it.blocks = com.dustin.callguard.screening.BlockDirectory.parse(ByteArrayInputStream(BlockTestData.sample))
    }

    private fun probeBlocks(text: String) = NumberProbe.run(ProbeInput(text, blockDirectory, null, emptyList(), emptyList(), zone))

    @Test
    fun anAssignedBlockShowsItsHolderAndNotTheGenericPoolingWarning() {
        val lines = lines(probeBlocks("315-200-5551"), "carrier block")
        assertTrue(lines.any { it == "1,000-number block 200-5xxx: held by BOOST SUBSCRIBERCO L.L.C. (OCN 490J), assigned 2014-09" })
        assertTrue(lines.any { it.contains("different carrier from the one holding the whole exchange") })
        assertTrue(lines.none { it.startsWith("pooled code:") })
        assertTrue(lines.any { it == "block data as of 2026-11-13" })
    }

    @Test
    fun anUnheldBlockIsShownAndScored() {
        val result = probeBlocks("315-201-3000")
        assertTrue(lines(result, "carrier block").any { it.contains("not assigned to any carrier right now") })
        assertTrue(result.suspicion!!.reasons.any { it.contains("201-3xxx") || it.contains("315-201-3xxx") })
    }

    @Test
    fun withoutBlockDataThePoolingWarningStays() {
        val lines = lines(probe("315-200-5551"), "carrier block")
        assertTrue(lines.any { it.startsWith("pooled code:") })
    }

    @Test
    fun blankInputHasNothingToProbe() {
        val result = probe("  ")
        assertNull(result.suspicion)
        assertTrue(lines(result, "number").first().startsWith("nothing to probe"))
    }

    @Test
    fun unassignedBlockInACoveredAreaCodeIsHigh() {
        val result = probe("315-999-1234")
        assertEquals(SuspicionLevel.HIGH, result.suspicion?.level)
        assertTrue(lines(result, "carrier block").first().contains("not assigned to any carrier"))
    }

    @Test
    fun assignedBlockShowsCarrierRateCenterAndPooledNote() {
        val block = lines(probe("3152001234"), "carrier block")
        assertTrue(block.any { it.startsWith("carrier: CELLCO PARTNERSHIP DBA VERIZON WIRELESS (OCN 6563)") })
        assertTrue(block.any { it.contains("wireless carrier") })
        assertTrue(block.any { it == "rate center: SYRACUSE, NY" })
        assertTrue(block.any { it.startsWith("pooled code") })
        assertTrue(block.any { it == "nanpa data as of 2026-11-12" })
    }

    @Test
    fun areaCodesOutsideTheFileAddNoSuspicion() {
        val result = probe("4169991234")
        assertTrue(lines(result, "carrier block").first().startsWith("no data for this area code"))
        assertEquals(SuspicionLevel.LOW, result.suspicion?.level)
    }

    @Test
    fun summarizesHistoryAndSameBlockNeighbors() {
        val calls = listOf(
            ScreenedCall(1, "3152001234", "3152001234", 1_000_000_000_000, "SILENCED", VerificationStatus.FAILED),
            ScreenedCall(2, "3152001234", "3152001234", 1_000_000_100_000, "SILENCED"),
            ScreenedCall(3, "3152009999", "3152009999", 1_000_000_200_000, "SILENCED"),
        )
        val history = lines(probe("3152001234", calls), "your history")
        assertTrue(history.first().startsWith("2 calls"))
        assertTrue(history.any { it == "1 failed caller verification" })
        assertTrue(history.any { it.startsWith("1 other number from the same block") })
        assertNotNull(probe("3152001234", calls).suspicion)
    }

    @Test
    fun neighborOfYourOwnNumberIsNoted() {
        val relation = lines(probe("3152001234", own = "3152005678"), "relationship to you")
        assertTrue(relation.first().contains("neighbor spoofing"))
    }

    private fun complaintIndex() = com.dustin.callguard.complaints.ComplaintIndex.Builder().apply {
        add(com.dustin.callguard.complaints.ComplaintReport("3152001234", "2026-11-11", true, "Warranties"))
        add(com.dustin.callguard.complaints.ComplaintReport("3152001234", "2026-11-10", true, "Warranties"))
    }.build(dayCount = 5)

    @Test
    fun reportedNumbersShowComplaintsAndRaiseTheScore() {
        val withData = NumberProbe.run(ProbeInput("3152001234", directory, null, emptyList(), emptyList(), zone, complaintIndex()))
        val lines = lines(withData, "public complaints")
        assertTrue(lines.first().startsWith("2 reports"))
        assertTrue(lines.any { it.contains("unverified") })
        val without = probe("3152001234")
        assertEquals((without.suspicion?.score ?: 0) + 20, withData.suspicion?.score)
    }

    @Test
    fun missingComplaintDataSaysHowToGetIt() {
        assertTrue(lines(probe("3152001234"), "public complaints").first().startsWith("no complaint data yet"))
    }

    @Test
    fun numbersAbsentFromComplaintDataAreReportedAsSuch() {
        val result = NumberProbe.run(ProbeInput("3152009999", directory, null, emptyList(), emptyList(), zone, complaintIndex()))
        assertTrue(lines(result, "public complaints").first().startsWith("no reports of this number in the last 5 days"))
    }

    @Test
    fun partiallyTypedNumbersDoNotCrash() {
        // the probe re-runs on every keystroke, so every prefix of a number must be safe
        val full = "8338083984"
        for (length in 1..full.length) {
            val result = probe(full.take(length))
            assertTrue("no sections for ${full.take(length)}", result.sections.isNotEmpty())
        }
        assertTrue(lines(probe("833"), "carrier block").first().startsWith("no data for this area code"))
    }
}
