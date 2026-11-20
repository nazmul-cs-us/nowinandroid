package com.starception.submission.voice.handsfree

import com.starception.submission.feature.quran.QuranData
import java.util.Locale

/** Only non-destructive Quran playback commands are allowed from the lock screen. */
internal sealed interface HandsFreeCommand {
    data class PlaySurah(val number: Int) : HandsFreeCommand
    data object Pause : HandsFreeCommand
    data object Resume : HandsFreeCommand
    data object Stop : HandsFreeCommand
    data object Next : HandsFreeCommand
    data object Previous : HandsFreeCommand
}

internal object HandsFreeCommandParser {
    private fun words(value: String): String = value.lowercase(Locale.ROOT)
        .map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
        .split(' ').filter(String::isNotBlank).joinToString(" ")

    private fun name(value: String): String = words(value).split(' ')
        .filterNot { it in setOf("al", "an", "as", "at", "az", "ash", "ad", "surah", "surat", "sura") }
        .joinToString("")

    fun parse(transcript: String): HandsFreeCommand? {
        var query = words(transcript).removePrefix("hey deenly ").removeSuffix(" please").trim()
        val politePrefixes = listOf("please ", "can you ", "could you ", "would you ")
        while (true) {
            val prefix = politePrefixes.firstOrNull(query::startsWith) ?: break
            query = query.removePrefix(prefix)
        }
        when (query) {
            "pause", "pause playback", "pause quran" -> return HandsFreeCommand.Pause
            "resume", "continue", "resume playback", "resume quran" -> return HandsFreeCommand.Resume
            "stop", "stop playback", "stop quran" -> return HandsFreeCommand.Stop
            "next", "next surah", "next chapter" -> return HandsFreeCommand.Next
            "previous", "previous surah", "previous chapter" -> return HandsFreeCommand.Previous
        }
        val verb = listOf("play ", "recite ", "listen to ", "start ").firstOrNull(query::startsWith) ?: return null
        query = query.removePrefix(verb)
        val number = query.removePrefix("surah ").removePrefix("chapter ").toIntOrNull()
        if (number != null) return number.takeIf { it in 1..114 }?.let(HandsFreeCommand::PlaySurah)
        val key = name(query)
        val aliases = mapOf("bakara" to 2, "bakarah" to 2, "baqara" to 2, "baqarah" to 2,
            "fatiha" to 1, "yaseen" to 36, "yasin" to 36, "rahman" to 55, "ikhlas" to 112)
        val surah = aliases[key] ?: QuranData.surahs.singleOrNull { name(it.nameEnglish) == key }?.number
        return surah?.let(HandsFreeCommand::PlaySurah)
    }
}

/** The wake model has its own keyword file, never the yes/no completion keywords. */
internal object DeenlyWakePhrase {
    val spellings = listOf("▁HE Y ▁DE EN LY", "▁HE Y ▁DE AN ▁LE E")

    fun keywords(vocabulary: String): String {
        val tokens = vocabulary.lineSequence().map { it.substringBeforeLast(' ') }.toSet()
        val usable = spellings.filter { phrase -> phrase.split(' ').all { it in tokens } }
        require(usable.isNotEmpty()) { "The downloaded wake-word model is incompatible. Download the current keyword model." }
        return usable.joinToString("\n", postfix = "\n") { "$it @hey_deenly" }
    }
}
