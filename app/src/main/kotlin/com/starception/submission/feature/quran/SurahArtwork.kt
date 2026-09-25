/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.feature.quran

/**
 * Single source of truth for the chapter artwork shared by the Quran Grid widget
 * and the Surah detail header.
 *
 * The artwork is NOT bundled in the APK (it alone was ~60 MB); it ships from the
 * asset CDN and is downloaded on demand into cdn_assets/surah_artwork/ via the
 * normal manifest pipeline. While a chapter's art has not been downloaded yet,
 * callers fall back to the bundled [PLACEHOLDER] image.
 */
object SurahArtwork {

    /** Bundled fallback shown while a chapter's artwork is not downloaded. */
    const val PLACEHOLDER = com.starception.submission.R.drawable.insight_quran

    /** CDN keys of the 114 chapter artworks, in surah-number order. */
    val CDN_KEYS: List<String> = listOf(
        "surah_001_al_fatihah.webp",
        "surah_002_al_baqarah.webp",
        "surah_003_al_imran.webp",
        "surah_004_an_nisa.webp",
        "surah_005_al_maidah.webp",
        "surah_006_al_anam.webp",
        "surah_007_al_araf.webp",
        "surah_008_al_anfal.webp",
        "surah_009_at_taubah.webp",
        "surah_010_yunus.webp",
        "surah_011_hud.webp",
        "surah_012_yusuf.webp",
        "surah_013_ar_rad.webp",
        "surah_014_ibrahim.webp",
        "surah_015_al_hijr.webp",
        "surah_016_an_nahl.webp",
        "surah_017_al_isra.webp",
        "surah_018_al_kahf.webp",
        "surah_019_maryam.webp",
        "surah_020_ta_ha.webp",
        "surah_021_al_anbiya.webp",
        "surah_022_al_hajj.webp",
        "surah_023_al_muminun.webp",
        "surah_024_an_nur.webp",
        "surah_025_al_furqan.webp",
        "surah_026_ash_shuara.webp",
        "surah_027_an_naml.webp",
        "surah_028_al_qasas.webp",
        "surah_029_al_ankabut.webp",
        "surah_030_ar_rum.webp",
        "surah_031_luqman.webp",
        "surah_032_as_sajdah.webp",
        "surah_033_al_ahzab.webp",
        "surah_034_saba.webp",
        "surah_035_fatir.webp",
        "surah_036_ya_sin.webp",
        "surah_037_as_saffat.webp",
        "surah_038_sad.webp",
        "surah_039_az_zumar.webp",
        "surah_040_ghafir.webp",
        "surah_041_fussilat.webp",
        "surah_042_ash_shura.webp",
        "surah_043_az_zukhruf.webp",
        "surah_044_ad_dukhan.webp",
        "surah_045_al_jathiyah.webp",
        "surah_046_al_ahqaf.webp",
        "surah_047_muhammad.webp",
        "surah_048_al_fath.webp",
        "surah_049_al_hujurat.webp",
        "surah_050_qaf.webp",
        "surah_051_ad_dhariyat.webp",
        "surah_052_at_tur.webp",
        "surah_053_an_najm.webp",
        "surah_054_al_qamar.webp",
        "surah_055_ar_rahman.webp",
        "surah_056_al_waqiah.webp",
        "surah_057_al_hadid.webp",
        "surah_058_al_mujadilah.webp",
        "surah_059_al_hashr.webp",
        "surah_060_al_mumtahanah.webp",
        "surah_061_as_saff.webp",
        "surah_062_al_jumuah.webp",
        "surah_063_al_munafiqun.webp",
        "surah_064_at_taghabun.webp",
        "surah_065_at_talaq.webp",
        "surah_066_at_tahrim.webp",
        "surah_067_al_mulk.webp",
        "surah_068_al_qalam.webp",
        "surah_069_al_haqqah.webp",
        "surah_070_al_maarij.webp",
        "surah_071_nuh.webp",
        "surah_072_al_jinn.webp",
        "surah_073_al_muzzammil.webp",
        "surah_074_al_muddathir.webp",
        "surah_075_al_qiyamah.webp",
        "surah_076_al_insan.webp",
        "surah_077_al_mursalat.webp",
        "surah_078_an_naba.webp",
        "surah_079_an_naziat.webp",
        "surah_080_abasa.webp",
        "surah_081_at_takwir.webp",
        "surah_082_al_infitar.webp",
        "surah_083_al_mutaffifin.webp",
        "surah_084_al_inshiqaq.webp",
        "surah_085_al_buruj.webp",
        "surah_086_at_tariq.webp",
        "surah_087_al_ala.webp",
        "surah_088_al_ghashiyah.webp",
        "surah_089_al_fajr.webp",
        "surah_090_al_balad.webp",
        "surah_091_ash_shams.webp",
        "surah_092_al_lail.webp",
        "surah_093_ad_duha.webp",
        "surah_094_ash_sharh.webp",
        "surah_095_at_tin.webp",
        "surah_096_al_alaq.webp",
        "surah_097_al_qadr.webp",
        "surah_098_al_bayyinah.webp",
        "surah_099_az_zalzalah.webp",
        "surah_100_al_adiyat.webp",
        "surah_101_al_qariah.webp",
        "surah_102_at_takathur.webp",
        "surah_103_al_asr.webp",
        "surah_104_al_humazah.webp",
        "surah_105_al_fil.webp",
        "surah_106_quraish.webp",
        "surah_107_al_maun.webp",
        "surah_108_al_kawthar.webp",
        "surah_109_al_kafirun.webp",
        "surah_110_an_nasr.webp",
        "surah_111_al_masad.webp",
        "surah_112_al_ikhlas.webp",
        "surah_113_al_falaq.webp",
        "surah_114_an_nas.webp",
    )

    /** CDN key for [surahNumber]'s artwork, or null when out of range. */
    fun cdnKey(surahNumber: Int): String? =
        CDN_KEYS.getOrNull(surahNumber - 1)
}
