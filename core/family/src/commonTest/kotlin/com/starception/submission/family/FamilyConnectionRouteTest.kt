package com.starception.submission.family

import kotlin.test.*

class FamilyConnectionRouteTest {
    private val classifier = FamilyConnectionRoute()
    private fun report(local: String, remote: String, extras: String = "") = """[
        {"id":"t","type":"transport","selectedCandidatePairId":"p"},
        {"id":"p","type":"candidate-pair","state":"succeeded","localCandidateId":"l","remoteCandidateId":"r"},
        {"id":"l","type":"local-candidate","candidateType":"$local"},
        {"id":"r","type":"remote-candidate","candidateType":"$remote"}$extras]"""
    @Test fun hostAndReflexivePathsAreDirect() {
        for (local in listOf("host", "srflx", "prflx")) for (remote in listOf("host", "srflx", "prflx"))
            assertEquals("direct", classifier.classify(report(local, remote)))
    }
    @Test fun eitherSelectedEndpointCanUseARelay() {
        assertEquals("relay", classifier.classify(report("relay", "host")))
        assertEquals("relay", classifier.classify(report("srflx", "relay")))
    }
    @Test fun unusedRelayCandidatesDoNotImplyRelayUsage() {
        assertEquals("direct", classifier.classify(report("host", "srflx",
            """,{"id":"unused","type":"local-candidate","candidateType":"relay"}""")))
    }
    @Test fun selectedTransportWinsOverOldNominatedPairs() {
        val old = """,{"id":"old","type":"candidate-pair","state":"succeeded","nominated":true,"localCandidateId":"relay","remoteCandidateId":"r"},
            {"id":"relay","type":"local-candidate","candidateType":"relay"}"""
        assertEquals("direct", classifier.classify(report("host", "host", old)))
    }
    @Test fun missingOrMalformedStatsNeverClaimDirect() {
        for (value in listOf("bad json", "[]", "{}", report("", "host"), report("host", "")))
            assertEquals("unknown", classifier.classify(value))
    }
    @Test fun relayEvidenceIsUsefulEvenWhenTheOtherCandidateIsUnknown() {
        assertEquals("relay", classifier.classify(report("relay", "")))
    }
    @Test fun failedPairsDoNotProveAnActiveRoute() {
        assertEquals("unknown", classifier.classify(report("relay", "host").replace("succeeded", "failed")))
    }
    @Test fun legacySelectedPairIsSupportedWithoutTransportStats() {
        val stats = report("host", "srflx").replace("{\"id\":\"t\",\"type\":\"transport\",\"selectedCandidatePairId\":\"p\"},", "")
            .replace("\"state\":\"succeeded\"", "\"state\":\"succeeded\",\"selected\":true")
        assertEquals("direct", classifier.classify(stats))
    }
    @Test fun labelsDescribeMediaRouting() {
        assertEquals("Direct (P2P)", classifier.label("direct"))
        assertEquals("TURN relay", classifier.label("relay"))
        assertEquals("Checking connection…", classifier.label("unknown"))
    }
}
