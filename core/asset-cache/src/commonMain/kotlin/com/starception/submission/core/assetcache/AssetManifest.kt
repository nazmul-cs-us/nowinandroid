package com.starception.submission.core.assetcache

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

@Serializable
data class AssetEntry(
    @SerialName("cdn_key") val cdnKey: String,
    val size: Long,
    val sha256: String,
    val category: String,
    val required: Boolean,
)

@Serializable
data class CategoryInfo(
    val name: String,
    @SerialName("total_size") val totalSize: Long,
    @SerialName("file_count") val fileCount: Int,
    val required: Boolean,
)

/**
 * Portable representation of the CDN manifest.
 *
 * The wire format stores an asset's CDN key and a category's name as map keys. The custom
 * serializer preserves that format while retaining the convenient fields used by existing callers.
 */
@Serializable(with = AssetManifestSerializer::class)
data class AssetManifest(
    val version: Int,
    val baseUrl: String,
    val totalSize: Long,
    val totalFiles: Int,
    val categories: Map<String, CategoryInfo>,
    val assets: Map<String, AssetEntry>,
) {
    fun getAssetsByCategory(category: String): List<AssetEntry> =
        assets.values.filter { it.category == category }

    fun getRequiredAssets(): List<AssetEntry> =
        assets.values.filter { it.required }

    fun getAssetUrl(cdnKey: String): String {
        val encodedPath = cdnKey.split('/').joinToString("/") { encodePathSegment(it) }
        return "${baseUrl.trimEnd('/')}/$encodedPath"
    }

    fun toJson(): String = manifestJson.encodeToString(AssetManifestSerializer, this)

    companion object {
        fun fromJson(json: String): AssetManifest =
            manifestJson.decodeFromString(AssetManifestSerializer, json)
    }
}

private val manifestJson = Json {
    ignoreUnknownKeys = true
}

private fun encodePathSegment(segment: String): String = buildString {
    segment.encodeToByteArray().forEach { byte ->
        val value = byte.toInt() and 0xff
        val isFormUrlSafe =
            value in 'a'.code..'z'.code ||
                value in 'A'.code..'Z'.code ||
                value in '0'.code..'9'.code ||
                value == '-'.code ||
                value == '_'.code ||
                value == '.'.code ||
                value == '*'.code
        if (isFormUrlSafe) {
            append(value.toChar())
        } else {
            append('%')
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }
}

private const val HEX_DIGITS = "0123456789ABCDEF"

private object AssetManifestSerializer : KSerializer<AssetManifest> {
    override val descriptor: SerialDescriptor = AssetManifestWire.serializer().descriptor

    override fun deserialize(decoder: Decoder): AssetManifest {
        val wire = decoder.decodeSerializableValue(AssetManifestWire.serializer())
        return AssetManifest(
            version = wire.version,
            baseUrl = wire.baseUrl,
            totalSize = wire.totalSize,
            totalFiles = wire.totalFiles,
            categories = wire.categories.mapValues { (name, category) ->
                CategoryInfo(
                    name = name,
                    totalSize = category.totalSize,
                    fileCount = category.fileCount,
                    required = category.required,
                )
            },
            assets = wire.assets.mapValues { (cdnKey, asset) ->
                AssetEntry(
                    cdnKey = cdnKey,
                    size = asset.size,
                    sha256 = asset.sha256,
                    category = asset.category,
                    required = asset.required,
                )
            },
        )
    }

    override fun serialize(encoder: Encoder, value: AssetManifest) {
        val wire = AssetManifestWire(
            version = value.version,
            baseUrl = value.baseUrl,
            totalSize = value.totalSize,
            totalFiles = value.totalFiles,
            categories = value.categories.mapValues { (_, category) ->
                CategoryInfoWire(
                    totalSize = category.totalSize,
                    fileCount = category.fileCount,
                    required = category.required,
                )
            },
            assets = value.assets.mapValues { (_, asset) ->
                AssetEntryWire(
                    size = asset.size,
                    sha256 = asset.sha256,
                    category = asset.category,
                    required = asset.required,
                )
            },
        )
        encoder.encodeSerializableValue(AssetManifestWire.serializer(), wire)
    }
}

@Serializable
private data class AssetManifestWire(
    val version: Int,
    @SerialName("base_url") val baseUrl: String,
    @SerialName("total_size") val totalSize: Long,
    @SerialName("total_files") val totalFiles: Int,
    val categories: Map<String, CategoryInfoWire>,
    val assets: Map<String, AssetEntryWire>,
)

@Serializable
private data class AssetEntryWire(
    val size: Long,
    val sha256: String,
    val category: String,
    val required: Boolean,
)

@Serializable
private data class CategoryInfoWire(
    @SerialName("total_size") val totalSize: Long,
    @SerialName("file_count") val fileCount: Int,
    val required: Boolean,
)
