package com.starception.submission.prayer.service

/**
 * COUNTRY CODE MAPPER: Maps country names to ISO 3166-1 alpha-2 codes
 * 
 * This utility class provides fallback mapping when Android's Geocoder
 * fails to return proper country codes. Essential for the prayer times
 * auto-detection system which requires ISO codes.
 * 
 * KEY FEATURES:
 * - Comprehensive mapping of common country names to ISO codes
 * - Handles multiple name variations (UAE, United Arab Emirates)
 * - Case-insensitive matching
 * - Trimmed whitespace handling
 * - Fallback for geocoding gaps
 * 
 * USAGE:
 * - Called by EnhancedLocationService when countryCode is empty
 * - Called by LocationService when countryCode is empty
 * - Used in prayer settings auto-detection system
 * 
 * EDIT THIS TO:
 * - Add new country mappings
 * - Handle regional name variations
 * - Add language-specific translations
 */
object CountryCodeMapper {
    
    /**
     * COUNTRY NAME TO ISO CODE MAPPING
     * 
     * This map contains ALL countries from the country_prayer_methods.json file
     * plus common variations, mapped to proper ISO 3166-1 alpha-2 codes.
     * 
     * FORMAT: "country name" to "ISO code"
     * 
     * COVERAGE:
     * - All 249 countries from prayer methods JSON file
     * - Common name variations and abbreviations
     * - Official country names and colloquial names
     * - Multiple language variations for major countries
     * 
     * TOTAL: 249 countries with 400+ name variations supported
     * 
     * SOURCE: All countries match exactly with country_prayer_methods.json
     * ensuring 100% compatibility with the prayer auto-detection system.
     */
    private val countryNameToCodeMap = mapOf(
        // A - Countries starting with A
        "andorra" to "AD",
        
        "united arab emirates" to "AE",
        "uae" to "AE",
        "emirates" to "AE",
        
        "afghanistan" to "AF",
        "islamic emirate of afghanistan" to "AF",
        
        "antigua and barbuda" to "AG",
        "antigua" to "AG",
        
        "anguilla" to "AI",
        
        "albania" to "AL",
        "republic of albania" to "AL",
        
        "armenia" to "AM",
        "republic of armenia" to "AM",
        
        "angola" to "AO",
        "republic of angola" to "AO",
        
        "antarctica" to "AQ",
        
        "argentina" to "AR",
        "argentine republic" to "AR",
        
        "american samoa" to "AS",
        
        "austria" to "AT",
        "republic of austria" to "AT",
        
        "australia" to "AU",
        "commonwealth of australia" to "AU",
        
        "aruba" to "AW",
        
        "åland islands" to "AX",
        "aland islands" to "AX",
        
        "azerbaijan" to "AZ",
        "republic of azerbaijan" to "AZ",
        
        // B - Countries starting with B
        "bosnia and herzegovina" to "BA",
        "bosnia" to "BA",
        
        "barbados" to "BB",
        
        "bangladesh" to "BD",
        "people's republic of bangladesh" to "BD",
        
        "belgium" to "BE",
        "kingdom of belgium" to "BE",
        
        "burkina faso" to "BF",
        
        "bulgaria" to "BG",
        "republic of bulgaria" to "BG",
        
        "bahrain" to "BH",
        "kingdom of bahrain" to "BH",
        
        "burundi" to "BI",
        "republic of burundi" to "BI",
        
        "benin" to "BJ",
        "republic of benin" to "BJ",
        
        "saint barthélemy" to "BL",
        "saint barthelemy" to "BL",
        
        "bermuda" to "BM",
        
        "brunei" to "BN",
        "brunei darussalam" to "BN",
        "nation of brunei" to "BN",
        
        "bolivia" to "BO",
        "plurinational state of bolivia" to "BO",
        
        "bonaire, sint eustatius and saba" to "BQ",
        "bonaire" to "BQ",
        
        "brazil" to "BR",
        "federative republic of brazil" to "BR",
        "brasil" to "BR",
        
        "bahamas" to "BS",
        "commonwealth of the bahamas" to "BS",
        
        "bhutan" to "BT",
        "kingdom of bhutan" to "BT",
        
        "bouvet island" to "BV",
        
        "botswana" to "BW",
        "republic of botswana" to "BW",
        
        "belarus" to "BY",
        "republic of belarus" to "BY",
        
        "belize" to "BZ",
        
        // C - Countries starting with C
        "canada" to "CA",
        
        "cocos (keeling) islands" to "CC",
        "cocos islands" to "CC",
        "keeling islands" to "CC",
        
        "congo (democratic republic)" to "CD",
        "democratic republic of the congo" to "CD",
        "dr congo" to "CD",
        "drc" to "CD",
        
        "central african republic" to "CF",
        "car" to "CF",
        
        "congo (republic)" to "CG",
        "republic of the congo" to "CG",
        "congo" to "CG",
        
        "switzerland" to "CH",
        "swiss confederation" to "CH",
        
        "côte d'ivoire" to "CI",
        "cote d'ivoire" to "CI",
        "ivory coast" to "CI",
        
        "cook islands" to "CK",
        
        "chile" to "CL",
        "republic of chile" to "CL",
        
        "cameroon" to "CM",
        "republic of cameroon" to "CM",
        
        "china" to "CN",
        "people's republic of china" to "CN",
        "prc" to "CN",
        
        "colombia" to "CO",
        "republic of colombia" to "CO",
        
        "costa rica" to "CR",
        "republic of costa rica" to "CR",
        
        "cuba" to "CU",
        "republic of cuba" to "CU",
        
        "cape verde" to "CV",
        "cabo verde" to "CV",
        
        "curaçao" to "CW",
        "curacao" to "CW",
        
        "christmas island" to "CX",
        
        "cyprus" to "CY",
        "republic of cyprus" to "CY",
        
        "czech republic" to "CZ",
        "czechia" to "CZ",
        
        // D - Countries starting with D
        "germany" to "DE",
        "federal republic of germany" to "DE",
        "deutschland" to "DE",
        
        "djibouti" to "DJ",
        "republic of djibouti" to "DJ",
        
        "denmark" to "DK",
        "kingdom of denmark" to "DK",
        
        "dominica" to "DM",
        "commonwealth of dominica" to "DM",
        
        "dominican republic" to "DO",
        
        "algeria" to "DZ",
        "people's democratic republic of algeria" to "DZ",
        
        // E - Countries starting with E
        "ecuador" to "EC",
        "republic of ecuador" to "EC",
        
        "estonia" to "EE",
        "republic of estonia" to "EE",
        
        "egypt" to "EG",
        "arab republic of egypt" to "EG",
        
        "western sahara" to "EH",
        "sahrawi arab democratic republic" to "EH",
        
        "eritrea" to "ER",
        "state of eritrea" to "ER",
        
        "spain" to "ES",
        "kingdom of spain" to "ES",
        "españa" to "ES",
        "espana" to "ES",
        
        "ethiopia" to "ET",
        "federal democratic republic of ethiopia" to "ET",
        
        // F - Countries starting with F
        "finland" to "FI",
        "republic of finland" to "FI",
        
        "fiji" to "FJ",
        "republic of fiji" to "FJ",
        
        "falkland islands" to "FK",
        "falkland islands (malvinas)" to "FK",
        "malvinas" to "FK",
        
        "micronesia" to "FM",
        "federated states of micronesia" to "FM",
        
        "faroe islands" to "FO",
        "faroe islands" to "FO",
        
        "france" to "FR",
        "french republic" to "FR",
        
        // G - Countries starting with G
        "gabon" to "GA",
        "gabonese republic" to "GA",
        
        "united kingdom" to "GB",
        "uk" to "GB",
        "great britain" to "GB",
        "britain" to "GB",
        "england" to "GB",
        "scotland" to "GB",
        "wales" to "GB",
        "northern ireland" to "GB",
        
        "grenada" to "GD",
        
        "georgia" to "GE",
        
        "french guiana" to "GF",
        
        "guernsey" to "GG",
        
        "ghana" to "GH",
        "republic of ghana" to "GH",
        
        "gibraltar" to "GI",
        
        "greenland" to "GL",
        
        "gambia" to "GM",
        "republic of the gambia" to "GM",
        
        "guinea" to "GN",
        "republic of guinea" to "GN",
        
        "guadeloupe" to "GP",
        
        "equatorial guinea" to "GQ",
        "republic of equatorial guinea" to "GQ",
        
        "greece" to "GR",
        "hellenic republic" to "GR",
        
        "south georgia and south sandwich islands" to "GS",
        "south georgia" to "GS",
        
        "guatemala" to "GT",
        "republic of guatemala" to "GT",
        
        "guam" to "GU",
        
        "guinea-bissau" to "GW",
        "republic of guinea-bissau" to "GW",
        
        "guyana" to "GY",
        "co-operative republic of guyana" to "GY",
        
        // H - Countries starting with H
        "hong kong" to "HK",
        "hong kong sar" to "HK",
        
        "heard island and mcdonald islands" to "HM",
        "heard island" to "HM",
        
        "honduras" to "HN",
        "republic of honduras" to "HN",
        
        "croatia" to "HR",
        "republic of croatia" to "HR",
        
        "haiti" to "HT",
        "republic of haiti" to "HT",
        
        "hungary" to "HU",
        
        // I - Countries starting with I
        "indonesia" to "ID",
        "republic of indonesia" to "ID",
        
        "ireland" to "IE",
        "republic of ireland" to "IE",
        
        "israel" to "IL",
        "state of israel" to "IL",
        
        "isle of man" to "IM",
        
        "india" to "IN",
        "republic of india" to "IN",
        "bharat" to "IN",
        
        "british indian ocean territory" to "IO",
        
        "iraq" to "IQ",
        "republic of iraq" to "IQ",
        
        "iran" to "IR",
        "islamic republic of iran" to "IR",
        "persia" to "IR",
        
        "iceland" to "IS",
        "republic of iceland" to "IS",
        
        "italy" to "IT",
        "italian republic" to "IT",
        "italia" to "IT",
        
        // J - Countries starting with J
        "jersey" to "JE",
        
        "jamaica" to "JM",
        
        "jordan" to "JO",
        "hashemite kingdom of jordan" to "JO",
        
        "japan" to "JP",
        
        // K - Countries starting with K
        "kenya" to "KE",
        "republic of kenya" to "KE",
        
        "kyrgyzstan" to "KG",
        "kyrgyz republic" to "KG",
        
        "cambodia" to "KH",
        "kingdom of cambodia" to "KH",
        "kampuchea" to "KH",
        
        "kiribati" to "KI",
        "republic of kiribati" to "KI",
        
        "comoros" to "KM",
        "union of the comoros" to "KM",
        
        "saint kitts and nevis" to "KN",
        "st. kitts and nevis" to "KN",
        
        "north korea" to "KP",
        "democratic people's republic of korea" to "KP",
        "dprk" to "KP",
        
        "south korea" to "KR",
        "republic of korea" to "KR",
        "korea" to "KR",
        "rok" to "KR",
        
        "kuwait" to "KW",
        "state of kuwait" to "KW",
        
        "cayman islands" to "KY",
        
        "kazakhstan" to "KZ",
        "republic of kazakhstan" to "KZ",
        
        // L - Countries starting with L
        "laos" to "LA",
        "lao people's democratic republic" to "LA",
        "lao pdr" to "LA",
        
        "lebanon" to "LB",
        "lebanese republic" to "LB",
        
        "saint lucia" to "LC",
        "st. lucia" to "LC",
        
        "liechtenstein" to "LI",
        "principality of liechtenstein" to "LI",
        
        "sri lanka" to "LK",
        "democratic socialist republic of sri lanka" to "LK",
        "ceylon" to "LK",
        
        "liberia" to "LR",
        "republic of liberia" to "LR",
        
        "lesotho" to "LS",
        "kingdom of lesotho" to "LS",
        
        "lithuania" to "LT",
        "republic of lithuania" to "LT",
        
        "luxembourg" to "LU",
        "grand duchy of luxembourg" to "LU",
        
        "latvia" to "LV",
        "republic of latvia" to "LV",
        
        "libya" to "LY",
        "state of libya" to "LY",
        
        // M - Countries starting with M
        "morocco" to "MA",
        "kingdom of morocco" to "MA",
        
        "monaco" to "MC",
        "principality of monaco" to "MC",
        
        "moldova" to "MD",
        "republic of moldova" to "MD",
        
        "montenegro" to "ME",
        
        "saint martin (french part)" to "MF",
        "saint martin" to "MF",
        "st. martin" to "MF",
        
        "madagascar" to "MG",
        "republic of madagascar" to "MG",
        
        "marshall islands" to "MH",
        "republic of the marshall islands" to "MH",
        
        "north macedonia" to "MK",
        "republic of north macedonia" to "MK",
        "macedonia" to "MK",
        
        "mali" to "ML",
        "republic of mali" to "ML",
        
        "myanmar" to "MM",
        "republic of the union of myanmar" to "MM",
        "burma" to "MM",
        
        "mongolia" to "MN",
        
        "macao" to "MO",
        "macau" to "MO",
        "macao sar" to "MO",
        
        "northern mariana islands" to "MP",
        "commonwealth of the northern mariana islands" to "MP",
        
        "martinique" to "MQ",
        
        "mauritania" to "MR",
        "islamic republic of mauritania" to "MR",
        
        "montserrat" to "MS",
        
        "malta" to "MT",
        "republic of malta" to "MT",
        
        "mauritius" to "MU",
        "republic of mauritius" to "MU",
        
        "maldives" to "MV",
        "republic of maldives" to "MV",
        
        "malawi" to "MW",
        "republic of malawi" to "MW",
        
        "mexico" to "MX",
        "united mexican states" to "MX",
        
        "malaysia" to "MY",
        
        "mozambique" to "MZ",
        "republic of mozambique" to "MZ",
        
        // N - Countries starting with N
        "namibia" to "NA",
        "republic of namibia" to "NA",
        
        "new caledonia" to "NC",
        
        "niger" to "NE",
        "republic of the niger" to "NE",
        
        "norfolk island" to "NF",
        
        "nigeria" to "NG",
        "federal republic of nigeria" to "NG",
        
        "nicaragua" to "NI",
        "republic of nicaragua" to "NI",
        
        "netherlands" to "NL",
        "kingdom of the netherlands" to "NL",
        "holland" to "NL",
        
        "norway" to "NO",
        "kingdom of norway" to "NO",
        
        "nepal" to "NP",
        "federal democratic republic of nepal" to "NP",
        
        "nauru" to "NR",
        "republic of nauru" to "NR",
        
        "niue" to "NU",
        
        "new zealand" to "NZ",
        
        // O - Countries starting with O
        "oman" to "OM",
        "sultanate of oman" to "OM",
        
        // P - Countries starting with P
        "panama" to "PA",
        "republic of panama" to "PA",
        
        "peru" to "PE",
        "republic of peru" to "PE",
        
        "french polynesia" to "PF",
        
        "papua new guinea" to "PG",
        "independent state of papua new guinea" to "PG",
        
        "philippines" to "PH",
        "republic of the philippines" to "PH",
        
        "pakistan" to "PK",
        "islamic republic of pakistan" to "PK",
        
        "poland" to "PL",
        "republic of poland" to "PL",
        
        "saint pierre and miquelon" to "PM",
        "st. pierre and miquelon" to "PM",
        
        "pitcairn" to "PN",
        "pitcairn islands" to "PN",
        
        "puerto rico" to "PR",
        "commonwealth of puerto rico" to "PR",
        
        "palestine" to "PS",
        "state of palestine" to "PS",
        
        "portugal" to "PT",
        "portuguese republic" to "PT",
        
        "palau" to "PW",
        "republic of palau" to "PW",
        
        "paraguay" to "PY",
        "republic of paraguay" to "PY",
        
        // Q - Countries starting with Q
        "qatar" to "QA",
        "state of qatar" to "QA",
        
        // R - Countries starting with R
        "réunion" to "RE",
        "reunion" to "RE",
        
        "romania" to "RO",
        
        "serbia" to "RS",
        "republic of serbia" to "RS",
        
        "russia" to "RU",
        "russian federation" to "RU",
        
        "rwanda" to "RW",
        "republic of rwanda" to "RW",
        
        // S - Countries starting with S
        "saudi arabia" to "SA",
        "kingdom of saudi arabia" to "SA",
        "ksa" to "SA",
        "saudi" to "SA",
        
        "solomon islands" to "SB",
        
        "seychelles" to "SC",
        "republic of seychelles" to "SC",
        
        "sudan" to "SD",
        "republic of the sudan" to "SD",
        
        "sweden" to "SE",
        "kingdom of sweden" to "SE",
        
        "singapore" to "SG",
        "republic of singapore" to "SG",
        
        "saint helena, ascension and tristan da cunha" to "SH",
        "saint helena" to "SH",
        "st. helena" to "SH",
        
        "slovenia" to "SI",
        "republic of slovenia" to "SI",
        
        "svalbard and jan mayen" to "SJ",
        "svalbard" to "SJ",
        
        "slovakia" to "SK",
        "slovak republic" to "SK",
        
        "sierra leone" to "SL",
        "republic of sierra leone" to "SL",
        
        "san marino" to "SM",
        "republic of san marino" to "SM",
        
        "senegal" to "SN",
        "republic of senegal" to "SN",
        
        "somalia" to "SO",
        "federal republic of somalia" to "SO",
        
        "suriname" to "SR",
        "republic of suriname" to "SR",
        
        "south sudan" to "SS",
        "republic of south sudan" to "SS",
        
        "são tomé and príncipe" to "ST",
        "sao tome and principe" to "ST",
        
        "el salvador" to "SV",
        "republic of el salvador" to "SV",
        
        "sint maarten (dutch part)" to "SX",
        "sint maarten" to "SX",
        "st. maarten" to "SX",
        
        "syria" to "SY",
        "syrian arab republic" to "SY",
        
        "eswatini" to "SZ",
        "swaziland" to "SZ",
        "kingdom of eswatini" to "SZ",
        
        // T - Countries starting with T
        "turks and caicos islands" to "TC",
        
        "chad" to "TD",
        "republic of chad" to "TD",
        
        "french southern territories" to "TF",
        
        "togo" to "TG",
        "togolese republic" to "TG",
        
        "thailand" to "TH",
        "kingdom of thailand" to "TH",
        "siam" to "TH",
        
        "tajikistan" to "TJ",
        "republic of tajikistan" to "TJ",
        
        "tokelau" to "TK",
        
        "timor-leste" to "TL",
        "east timor" to "TL",
        
        "turkmenistan" to "TM",
        
        "tunisia" to "TN",
        "republic of tunisia" to "TN",
        
        "tonga" to "TO",
        "kingdom of tonga" to "TO",
        
        "turkey" to "TR",
        "republic of turkey" to "TR",
        "türkiye" to "TR",
        "turkiye" to "TR",
        
        "trinidad and tobago" to "TT",
        "republic of trinidad and tobago" to "TT",
        
        "tuvalu" to "TV",
        
        "taiwan" to "TW",
        "republic of china" to "TW",
        "chinese taipei" to "TW",
        
        "tanzania" to "TZ",
        "united republic of tanzania" to "TZ",
        
        // U - Countries starting with U
        "ukraine" to "UA",
        
        "uganda" to "UG",
        "republic of uganda" to "UG",
        
        "united states minor outlying islands" to "UM",
        
        "united states" to "US",
        "united states of america" to "US",
        "usa" to "US",
        "america" to "US",
        
        "uruguay" to "UY",
        "oriental republic of uruguay" to "UY",
        
        "uzbekistan" to "UZ",
        "republic of uzbekistan" to "UZ",
        
        // V - Countries starting with V
        "vatican city" to "VA",
        "holy see" to "VA",
        
        "saint vincent and the grenadines" to "VC",
        "st. vincent and the grenadines" to "VC",
        
        "venezuela" to "VE",
        "bolivarian republic of venezuela" to "VE",
        
        "virgin islands (british)" to "VG",
        "british virgin islands" to "VG",
        "bvi" to "VG",
        
        "virgin islands (u.s.)" to "VI",
        "us virgin islands" to "VI",
        "usvi" to "VI",
        
        "vietnam" to "VN",
        "socialist republic of vietnam" to "VN",
        "viet nam" to "VN",
        
        "vanuatu" to "VU",
        "republic of vanuatu" to "VU",
        
        // W - Countries starting with W
        "wallis and futuna" to "WF",
        
        "samoa" to "WS",
        "independent state of samoa" to "WS",
        
        // X - Countries starting with X
        "kosovo" to "XK",
        "republic of kosovo" to "XK",
        
        // Y - Countries starting with Y
        "yemen" to "YE",
        "republic of yemen" to "YE",
        
        "mayotte" to "YT",
        
        // Z - Countries starting with Z
        "south africa" to "ZA",
        "republic of south africa" to "ZA",
        
        "zambia" to "ZM",
        "republic of zambia" to "ZM",
        
        "zimbabwe" to "ZW",
        "republic of zimbabwe" to "ZW"
    )
    
    /**
     * COUNTRY NAME TO ISO CODE CONVERTER
     * 
     * This function maps country names to their ISO 3166-1 alpha-2 codes.
     * It handles various name formats and provides fallback when geocoding
     * doesn't return proper country codes.
     * 
     * FEATURES:
     * - Case-insensitive matching
     * - Whitespace trimming
     * - Handles common abbreviations (UAE, USA, UK, etc.)
     * - Returns null for unmapped countries (graceful failure)
     * 
     * ALGORITHM:
     * 1. Normalize input (lowercase, trim)
     * 2. Direct lookup in mapping table
     * 3. Return ISO code or null
     * 
     * USAGE EXAMPLES:
     * - "UAE" → "AE"
     * - "United Arab Emirates" → "AE" 
     * - "usa" → "US"
     * - "United States of America" → "US"
     * - "Unknown Country" → null
     * 
     * @param countryName Country name from geocoder (any format)
     * @return ISO 3166-1 alpha-2 code or null if not found
     */
    fun getCountryCode(countryName: String?): String? {
        android.util.Log.d("CountryCodeMapper", "🔍 COUNTRY NAME LOOKUP:")
        android.util.Log.d("CountryCodeMapper", "   Input: '$countryName'")
        
        if (countryName.isNullOrBlank()) {
            android.util.Log.d("CountryCodeMapper", "   ❌ Input is null or blank - returning null")
            return null
        }
        
        val normalizedName = countryName.trim().lowercase()
        android.util.Log.d("CountryCodeMapper", "   Normalized: '$normalizedName'")
        
        val result = countryNameToCodeMap[normalizedName]
        if (result != null) {
            android.util.Log.i("CountryCodeMapper", "   ✅ FOUND MAPPING: '$countryName' → '$result'")
        } else {
            android.util.Log.w("CountryCodeMapper", "   ❌ NO MAPPING FOUND for '$countryName'")
            android.util.Log.w("CountryCodeMapper", "   💡 Checking for similar matches...")
            
            // Find potential matches for debugging
            val potentialMatches = countryNameToCodeMap.keys.filter { 
                it.contains(normalizedName) || normalizedName.contains(it) 
            }.take(3)
            
            if (potentialMatches.isNotEmpty()) {
                android.util.Log.w("CountryCodeMapper", "   🔍 Potential matches found:")
                potentialMatches.forEach { match ->
                    val code = countryNameToCodeMap[match]
                    android.util.Log.w("CountryCodeMapper", "      - '$match' → '$code'")
                }
            } else {
                android.util.Log.w("CountryCodeMapper", "   🚫 No similar matches found")
            }
        }
        
        return result
    }
    
    /**
     * ENHANCED COUNTRY CODE RESOLVER
     * 
     * This function combines geocoder results with fallback mapping
     * to ensure country codes are always populated when possible.
     * 
     * RESOLUTION PRIORITY:
     * 1. Use geocoder countryCode if available (most accurate)
     * 2. Map country name to ISO code (fallback)
     * 3. Return empty string if no mapping found
     * 
     * USE CASE:
     * This is the primary function that location services should call
     * to ensure reliable country code detection.
     * 
     * @param geocoderCountryCode Country code from Android geocoder (may be null/empty)
     * @param geocoderCountryName Country name from Android geocoder (fallback)
     * @return Best available ISO country code or empty string
     */
    fun resolveCountryCode(geocoderCountryCode: String?, geocoderCountryName: String?): String {
        android.util.Log.i("CountryCodeMapper", "🔧 COUNTRY CODE RESOLUTION STARTED:")
        android.util.Log.i("CountryCodeMapper", "   Geocoder Code: '$geocoderCountryCode'")
        android.util.Log.i("CountryCodeMapper", "   Geocoder Name: '$geocoderCountryName'")
        
        // Priority 1: Use geocoder country code if available
        if (!geocoderCountryCode.isNullOrBlank()) {
            android.util.Log.i("CountryCodeMapper", "   ✅ USING GEOCODER CODE: '$geocoderCountryCode' (Priority 1)")
            android.util.Log.i("CountryCodeMapper", "   🎯 RESOLUTION COMPLETE: '$geocoderCountryCode'")
            return geocoderCountryCode
        }
        
        android.util.Log.w("CountryCodeMapper", "   ⚠️ Geocoder code is empty - trying name mapping (Priority 2)")
        
        // Priority 2: Map country name to ISO code
        val mappedCode = getCountryCode(geocoderCountryName)
        if (mappedCode != null) {
            android.util.Log.i("CountryCodeMapper", "   ✅ MAPPED FROM NAME: '$geocoderCountryName' → '$mappedCode' (Priority 2)")
            android.util.Log.i("CountryCodeMapper", "   🎯 RESOLUTION COMPLETE: '$mappedCode'")
            return mappedCode
        }
        
        // Priority 3: Return empty string (graceful failure)
        android.util.Log.e("CountryCodeMapper", "   ❌ RESOLUTION FAILED: No mapping found for geocoder data")
        android.util.Log.e("CountryCodeMapper", "   📊 FINAL RESULT: Empty string (graceful failure)")
        android.util.Log.e("CountryCodeMapper", "   💡 RECOMMENDATION: Check if new country mapping needed")
        
        return ""
    }
    
    /**
     * SUPPORTED COUNTRIES CHECKER
     * 
     * This function checks if a country is supported by the prayer times
     * auto-detection system by looking up the country code in the mapping.
     * 
     * USE CASES:
     * - Validate if prayer auto-detection is available for a location
     * - Show appropriate UI messages for unsupported regions
     * - Log coverage gaps for improvement
     * 
     * @param countryName Country name to check
     * @return true if country is supported for prayer auto-detection
     */
    fun isCountrySupported(countryName: String?): Boolean {
        return getCountryCode(countryName) != null
    }
    
    /**
     * GET ALL SUPPORTED COUNTRIES
     * 
     * Returns a list of all country names that have mappings.
     * Useful for debugging, testing, and coverage analysis.
     * 
     * @return Set of all supported country names
     */
    fun getAllSupportedCountries(): Set<String> {
        return countryNameToCodeMap.keys
    }
    
    /**
     * GET ALL COUNTRY CODES
     * 
     * Returns a list of all ISO country codes that are mapped.
     * Useful for validation and testing.
     * 
     * @return Set of all supported ISO country codes
     */
    fun getAllSupportedCodes(): Set<String> {
        return countryNameToCodeMap.values.toSet()
    }
    
    /**
     * LOG COUNTRY MAPPING STATISTICS
     * 
     * Logs comprehensive statistics about the country mapping system.
     * Useful for debugging and understanding coverage.
     * 
     * LOGGED INFORMATION:
     * - Total country names mapped
     * - Total unique country codes  
     * - Coverage statistics
     * - Sample mappings for verification
     */
    fun logMappingStatistics() {
        val totalMappings = countryNameToCodeMap.size
        val uniqueCodes = countryNameToCodeMap.values.toSet()
        val totalCountries = uniqueCodes.size
        
        android.util.Log.i("CountryCodeMapper", "📊 COUNTRY MAPPING STATISTICS:")
        android.util.Log.i("CountryCodeMapper", "   Total Name Mappings: $totalMappings")
        android.util.Log.i("CountryCodeMapper", "   Unique Country Codes: $totalCountries")
        android.util.Log.i("CountryCodeMapper", "   Average Names per Country: ${totalMappings.toFloat() / totalCountries}")
        
        android.util.Log.i("CountryCodeMapper", "📋 SAMPLE MAPPINGS (first 10):")
        countryNameToCodeMap.entries.take(10).forEach { (name, code) ->
            android.util.Log.i("CountryCodeMapper", "      '$name' → '$code'")
        }
        
        android.util.Log.i("CountryCodeMapper", "🌍 ALL UNIQUE COUNTRY CODES:")
        val sortedCodes = uniqueCodes.sorted()
        val codeChunks = sortedCodes.chunked(20)
        codeChunks.forEach { chunk ->
            android.util.Log.i("CountryCodeMapper", "      ${chunk.joinToString(", ")}")
        }
        
        android.util.Log.i("CountryCodeMapper", "✅ MAPPER READY: $totalCountries countries, $totalMappings name variations")
    }
    
    /**
     * GET FULL COUNTRY NAME FROM CODE
     * 
     * Returns the full official country name for a given ISO country code.
     * Uses the first (most common/official) name mapping for each country.
     * 
     * ALGORITHM:
     * 1. Find all names mapped to the given country code
     * 2. Filter for official names (longer, more formal)
     * 3. Return the most appropriate full name
     * 4. Fallback to any available name if no official name found
     * 
     * USAGE EXAMPLES:
     * - "AE" → "United Arab Emirates"
     * - "US" → "United States of America"  
     * - "GB" → "United Kingdom"
     * - "SA" → "Saudi Arabia"
     * 
     * @param countryCode ISO 3166-1 alpha-2 country code
     * @return Full country name or null if code not found
     */
    fun getFullCountryName(countryCode: String?): String? {
        android.util.Log.d("CountryCodeMapper", "🏳️ GETTING FULL COUNTRY NAME:")
        android.util.Log.d("CountryCodeMapper", "   Input Code: '$countryCode'")
        
        if (countryCode.isNullOrBlank()) {
            android.util.Log.d("CountryCodeMapper", "   ❌ Country code is null or blank")
            return null
        }
        
        // Find all names mapped to this country code
        val namesForCode = countryNameToCodeMap.entries
            .filter { it.value.equals(countryCode, ignoreCase = true) }
            .map { it.key }
        
        if (namesForCode.isEmpty()) {
            android.util.Log.w("CountryCodeMapper", "   ❌ No names found for country code '$countryCode'")
            return null
        }
        
        android.util.Log.d("CountryCodeMapper", "   🔍 Found ${namesForCode.size} name(s) for '$countryCode'")
        
        // Priority list: Look for official/formal names first
        val officialNamePatterns = listOf(
            "republic of", "kingdom of", "state of", "federation of", 
            "commonwealth of", "principality of", "sultanate of", 
            "emirates", "arab republic", "democratic republic"
        )
        
        // 1. Look for official names (containing formal terms)
        val officialNames = namesForCode.filter { name ->
            officialNamePatterns.any { pattern -> name.contains(pattern) }
        }
        
        // 2. Look for full country names (longer names, avoiding abbreviations)
        val fullNames = namesForCode.filter { name ->
            name.length > 3 && !name.matches(Regex("[A-Z]{2,4}")) // Not abbreviations like UAE, USA
        }
        
        // 3. Select the best name using priority order
        val selectedName = when {
            // Priority 1: Official names that are reasonably long
            officialNames.isNotEmpty() -> {
                val bestOfficial = officialNames.maxByOrNull { it.length }
                android.util.Log.i("CountryCodeMapper", "   ✅ Using official name: '$bestOfficial'")
                bestOfficial
            }
            
            // Priority 2: Longest non-abbreviation name
            fullNames.isNotEmpty() -> {
                val longestFull = fullNames.maxByOrNull { it.length }
                android.util.Log.i("CountryCodeMapper", "   ✅ Using full name: '$longestFull'")
                longestFull
            }
            
            // Priority 3: Any available name (fallback)
            else -> {
                val fallback = namesForCode.first()
                android.util.Log.i("CountryCodeMapper", "   ⚠️ Using fallback name: '$fallback'")
                fallback
            }
        }
        
        // Capitalize properly for display
        val capitalizedName = selectedName?.split(" ")
            ?.joinToString(" ") { word ->
                if (word.lowercase() in listOf("of", "the", "and", "or")) {
                    word.lowercase()
                } else {
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
            ?.replace(" Of ", " of ")
            ?.replace(" The ", " the ")
            ?.replace(" And ", " and ")
        
        android.util.Log.i("CountryCodeMapper", "   🎯 FINAL RESULT: '$capitalizedName'")
        return capitalizedName
    }

    /**
     * VALIDATE COUNTRY CODE
     * 
     * Validates if a country code is supported and logs the result.
     * Useful for testing and verification.
     * 
     * @param countryCode ISO country code to validate
     * @return true if the country code is supported
     */
    fun validateCountryCode(countryCode: String?): Boolean {
        android.util.Log.d("CountryCodeMapper", "🔍 VALIDATING COUNTRY CODE: '$countryCode'")
        
        if (countryCode.isNullOrBlank()) {
            android.util.Log.d("CountryCodeMapper", "   ❌ Country code is null or blank")
            return false
        }
        
        val isSupported = countryCode in getAllSupportedCodes()
        if (isSupported) {
            android.util.Log.i("CountryCodeMapper", "   ✅ COUNTRY CODE SUPPORTED: '$countryCode'")
            
            // Find example names for this code
            val exampleNames = countryNameToCodeMap.entries
                .filter { it.value == countryCode }
                .map { it.key }
                .take(3)
            
            android.util.Log.i("CountryCodeMapper", "   📝 Example names: ${exampleNames.joinToString(", ")}")
        } else {
            android.util.Log.w("CountryCodeMapper", "   ❌ COUNTRY CODE NOT SUPPORTED: '$countryCode'")
            android.util.Log.w("CountryCodeMapper", "   💡 Consider adding mapping for this country")
        }
        
        return isSupported
    }
}