package com.starception.submission.download

import org.json.JSONObject
import java.net.URLEncoder

data class AssetEntry(
    val cdnKey: String,
    val size: Long,
    val sha256: String,
    val category: String,
    val required: Boolean,
)

data class CategoryInfo(
    val name: String,
    val totalSize: Long,
    val fileCount: Int,
    val required: Boolean,
)

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
        // URL-encode each path segment to handle special characters like ! and spaces
        val encodedPath = cdnKey.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        return "${baseUrl.trimEnd('/')}/$encodedPath"
    }

    companion object {
        fun fromJson(json: String): AssetManifest {
            val obj = JSONObject(json)
            val assetsObj = obj.getJSONObject("assets")
            val categoriesObj = obj.getJSONObject("categories")

            val assets = mutableMapOf<String, AssetEntry>()
            assetsObj.keys().forEach { key ->
                val entry = assetsObj.getJSONObject(key)
                assets[key] = AssetEntry(
                    cdnKey = key,
                    size = entry.getLong("size"),
                    sha256 = entry.getString("sha256"),
                    category = entry.getString("category"),
                    required = entry.getBoolean("required"),
                )
            }

            val categories = mutableMapOf<String, CategoryInfo>()
            categoriesObj.keys().forEach { key ->
                val cat = categoriesObj.getJSONObject(key)
                categories[key] = CategoryInfo(
                    name = key,
                    totalSize = cat.getLong("total_size"),
                    fileCount = cat.getInt("file_count"),
                    required = cat.getBoolean("required"),
                )
            }

            return AssetManifest(
                version = obj.getInt("version"),
                baseUrl = obj.getString("base_url"),
                totalSize = obj.getLong("total_size"),
                totalFiles = obj.getInt("total_files"),
                categories = categories,
                assets = assets,
            )
        }
    }
}
