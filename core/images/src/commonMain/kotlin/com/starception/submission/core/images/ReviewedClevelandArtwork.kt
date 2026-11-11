package com.starception.submission.core.images

/** Official museum image proxy; direct Open Access CDN requests were blocked in testing. */
private fun cleveland(accession: String, title: String, imageKey: String, version: Int, hash: String,
    crop: ArtworkCrop, file: String = "${accession}_o5.jpg"): ReviewedHomeArtwork {
    val image = "https://www.clevelandart.org/_next/image?url=https%3A%2F%2Fpiction.clevelandart.org%2Fcma%2Fump.di%3Fe%3D$imageKey%26s%3D24247294%26se%3D399313166%26v%3D$version%26f%3D$file&w=1080&q=75"
    return ReviewedHomeArtwork(OnlineHomeArtwork("cma-${accession.replace('.', '-')}", title,
        "Unknown artist · Cleveland Museum of Art", "https://www.clevelandart.org/art/$accession", image), hash, crop)
}

/** Full photographs reviewed 2026-11-11: public-domain floral/geometry, no people or animal figures. */
val reviewedClevelandArtwork = listOf(
    cleveland("1915.651", "Ottoman floral wall tiles",
        "1AAC948B54F1DB9CF9074165BE8173060F11988DF1765497A140F99F27ECC204", 1,
        "260f71fb85a9fae4d2e5906e8c66fefb35ecbe8530179b98a76c9385f19d7ae2",
        ArtworkCrop(.08f, .08f, .92f, .92f), "%5Cd6640%5Cu237466402%5C1915.651_o5.jpg"),
    cleveland("1915.645", "Lotus blossom tile",
        "9B38E22710B16DB40554AAD717FC6A15C32ED391CB7F1D7CFFC4E41FB932D6D8", 0,
        "cc4751cfe4d393a17b0c668b9560c8a3a8b59efe13be321aa394c113cfe1a709",
        ArtworkCrop(.25f, .25f, .75f, .75f)),
    cleveland("2015.61", "Classical Turkish carpet with the Lotto pattern",
        "CB577B48E1DB4283802DEF2555A4DAAA4A18A641093E13707BD441BB186933CE", 0,
        "1bc38648a92c7307a895ac07383cf1d08c7c1c63f24b0747b000bd551cd4c465",
        ArtworkCrop(.09f, .06f, .91f, .94f)),
    cleveland("1926.533", "Royal floral carpet with silk and metal thread",
        "3E5FD0622CD4F929F91D69333BEAB2F82E5B833D3F225D26FEB62D44A89B4540", 1,
        "76ea57ef07180efb80ed32a0e6a5eef5fe4a7a6db27721beda898ee316ed2424",
        ArtworkCrop(.13f, .09f, .87f, .80f)),
    cleveland("2010.190", "Turkmen geometric carpet",
        "EB77C01E220A3F948F466C5021DF687295A472EA06FE1F1BA656CFD3D61EDB71", 0,
        "4f7acd956e1e6653f2f18239b604f7102cf4c52049dc40ede387183f2a218543",
        ArtworkCrop(.13f, .11f, .87f, .89f)),
)
