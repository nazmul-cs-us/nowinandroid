package com.starception.submission.core.images

enum class ArtworkTheme(val label: String) { ISLAMIC("Islamic"), SPACE("Space"), NATURE("Nature") }

data class ArtworkService(val name: String, val status: String)

val artworkServices = listOf(
    ArtworkService("The Met / Cleveland", "Enabled · reviewed Islamic artwork"),
    ArtworkService("NASA Image Library", "Enabled · reviewed Space collection"),
    ArtworkService("Lorem Picsum", "Enabled · reviewed Nature IDs only"),
    ArtworkService("NASA APOD", "Disabled · API key not configured"),
    ArtworkService("Unsplash API", "Disabled · credentials and API terms review required"),
    ArtworkService("Pexels / Pixabay", "Disabled · API keys not configured"),
    ArtworkService("ESA/Hubble / ESA/Webb", "Not connected · per-image licensing review required"),
    ArtworkService("Openverse", "Discovery enabled · CC0/public-domain approval queue"),
    ArtworkService("Placehold.co", "Development placeholders only · not a Home artwork source"),
)
