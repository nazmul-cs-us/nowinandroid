package com.starception.submission.family

import kotlinx.serialization.json.*

/** Classifies the selected ICE path, never the list of available TURN servers. */
class FamilyConnectionRoute {
    fun classify(report: String): String = runCatching {
        val rows = Json.parseToJsonElement(report).jsonArray.map { it.jsonObject }
        val byId = rows.associateBy { it.text("id") }
        val transports = rows.filter { it.text("type") == "transport" && it.text("dtlsState") != "closed" }
        val pairs = if (transports.isNotEmpty()) {
            transports.map { byId[it.text("selectedCandidatePairId")] }
        } else {
            val successful = rows.filter { it.text("type") == "candidate-pair" && it.text("state") == "succeeded" }
            val selected = successful.filter { it.flag("selected") }
            if (selected.isNotEmpty()) selected else successful.filter { it.flag("nominated") }.singleOrNull()?.let { listOf(it) }.orEmpty()
        }
        if (pairs.isEmpty()) return "unknown"
        val routes = pairs.map { pair ->
            if (pair == null || pair.text("type") != "candidate-pair" || pair.text("state") != "succeeded") "unknown"
            else {
                val local = byId[pair.text("localCandidateId")]?.text("candidateType")
                val remote = byId[pair.text("remoteCandidateId")]?.text("candidateType")
                when {
                    local == "relay" || remote == "relay" -> "relay"
                    local in directTypes && remote in directTypes -> "direct"
                    else -> "unknown"
                }
            }
        }
        when { "relay" in routes -> "relay"; routes.all { it == "direct" } -> "direct"; else -> "unknown" }
    }.getOrDefault("unknown")

    fun label(route: String): String = when (route) {
        "direct" -> "Direct (P2P)"
        "relay" -> "TURN relay"
        else -> "Checking connection…"
    }
    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.flag(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull == true
    private val directTypes = setOf("host", "srflx", "prflx")
}
