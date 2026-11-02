package com.starception.submission.shared.ui

import com.starception.submission.feature.quran.ReciterGroup
import com.starception.submission.feature.quran.reciterGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class ReciterGroupsTest {
    @Test fun articleRecitersBelongToTheirDiscoveryGroups() {
        val groups = mapOf(
            ReciterGroup.EGYPTIAN_LEGENDS to listOf(
                "Abdul Basit Abdul Samad", "Muhammad Siddiq Al-Minshawi",
                "Mahmoud Khalil Al-Husary", "Mustafa Ismail", "Mahmoud Ali Al-Banna",
            ),
            ReciterGroup.HARAMAIN_IMAMS to listOf(
                "Abdul Rahman Al-Sudais", "Saud Al-Shuraim", "Maher Al-Muaiqly",
                "Abdullah Al-Juhany", "Yasser Al-Dosari",
            ),
            ReciterGroup.CONTEMPORARY to listOf(
                "Mishary Rashid Al-Alafasy", "Saad Al-Ghamdi", "Ahmed Al-Ajmi",
                "Nasser Al-Qatami", "Hani Ar-Rifai", "Idris Abkar",
            ),
            ReciterGroup.NEW_WAVE to listOf(
                "Fares Abbad", "Raad Muhammad Al-Kurdi", "Khalifa Al-Tunaiji", "Abdul Rahman Al-Ossi",
            ),
        )
        groups.forEach { (group, names) ->
            names.forEach { name -> assertEquals(group, reciterGroup(name), name) }
        }
    }

    @Test fun catalogTransliterationsAndRecordingVariantsKeepTheirGroups() {
        assertEquals(ReciterGroup.EGYPTIAN_LEGENDS, reciterGroup("Abdelbasset Abdessamad · Rewayat Hafs - Murattal"))
        assertEquals(ReciterGroup.EGYPTIAN_LEGENDS, reciterGroup("Mohamed Seddik El Menchaoui"))
        assertEquals(ReciterGroup.HARAMAIN_IMAMS, reciterGroup("Yasser Al Dossary"))
        assertEquals(ReciterGroup.HARAMAIN_IMAMS, reciterGroup("Maher Al Mueaqly"))
        assertEquals(ReciterGroup.CONTEMPORARY, reciterGroup("Mshari Al Afasi"))
        assertEquals(ReciterGroup.NEW_WAVE, reciterGroup("Raad Mohammad Al Kurdi"))
    }

    @Test fun otherVoicesAreStillAvailableWithoutAssigningThemAnUnsupportedGroup() {
        for (name in listOf("Abdelbari Al-Toubayti", "Abdelmoujib Benkirane", "Khalid Al-Ghamdi", "Nasser Al-Dosari")) {
            assertEquals(ReciterGroup.MORE, reciterGroup(name), name)
        }
    }
}
