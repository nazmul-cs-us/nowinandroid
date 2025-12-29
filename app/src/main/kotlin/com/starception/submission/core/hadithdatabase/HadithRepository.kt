package com.starception.submission.core.hadithdatabase

import android.content.Context
import com.starception.submission.core.duadatabase.DuaDatabase
import com.starception.submission.core.duadatabase.HadithReference
import com.starception.submission.core.duadatabase.toHadithReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for accessing hadith data across collections
 * Coordinates between fortress_of_the_muslim references and individual hadith databases
 */
class HadithRepository(private val context: Context) {

    private val duaDatabase by lazy { DuaDatabase.getInstance(context) }

    /**
     * Get all hadith references for a specific dua invocation
     * @param invocationId The invocation ID from fortress_of_the_muslim database
     */
    suspend fun getReferencesForInvocation(invocationId: Int): List<HadithReference> {
        return withContext(Dispatchers.IO) {
            try {
                duaDatabase.duaDao()
                    .getHadithReferencesForInvocation(invocationId)
                    .map { it.toHadithReference() }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error getting references for invocation $invocationId", e)
                emptyList()
            }
        }
    }

    /**
     * Get a specific hadith from its collection
     * @param databaseFile The database file name (e.g., "sahih_bukhari.db")
     * @param hadithNumber The hadith number/ID
     */
    suspend fun getHadith(databaseFile: String, hadithNumber: Int): Hadith? {
        return withContext(Dispatchers.IO) {
            try {
                val hadithDb = HadithDatabase.getInstance(context, databaseFile)
                val entity = hadithDb.hadithDao().getHadithById(hadithNumber)

                if (entity != null) {
                    // Get metadata for collection info
                    val metadata = HadithDatabase.getCollectionMetadata(context, databaseFile)
                    entity.toHadith(
                        collectionName = metadata?.name ?: HadithDatabase.getCollectionNameFromFile(databaseFile),
                        collectionNameArabic = metadata?.nameArabic ?: "",
                        collectionNameEnglish = metadata?.nameEnglish ?: "",
                        author = metadata?.author ?: "",
                        authorArabic = metadata?.authorArabic ?: ""
                    )
                } else {
                    android.util.Log.w(TAG, "⚠️ Hadith not found: $databaseFile #$hadithNumber")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Error getting hadith: $databaseFile #$hadithNumber", e)
                null
            }
        }
    }

    /**
     * Get hadith from a reference
     * @param reference The hadith reference from fortress_of_the_muslim
     */
    suspend fun getHadithFromReference(reference: HadithReference): Hadith? {
        val databaseFile = reference.databaseFile ?: return null
        val hadithNumber = reference.hadithNumber ?: return null
        return getHadith(databaseFile, hadithNumber)
    }

    /**
     * Get all hadiths for an invocation with full text
     * @param invocationId The invocation ID
     */
    suspend fun getHadithsForInvocation(invocationId: Int): List<Pair<HadithReference, Hadith?>> {
        return withContext(Dispatchers.IO) {
            val references = getReferencesForInvocation(invocationId)
            references.map { ref ->
                ref to getHadithFromReference(ref)
            }
        }
    }

    /**
     * Search hadiths across all collections
     * @param query Search query
     * @param collections List of collection files to search (null for all)
     */
    suspend fun searchHadiths(
        query: String,
        collections: List<String>? = null,
        limit: Int = 50
    ): List<Pair<String, Hadith>> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<Pair<String, Hadith>>()
            val collectionsToSearch = collections ?: HadithDatabase.getAvailableCollections()

            for (dbFile in collectionsToSearch) {
                if (results.size >= limit) break

                try {
                    val hadithDb = HadithDatabase.getInstance(context, dbFile)
                    val metadata = HadithDatabase.getCollectionMetadata(context, dbFile)
                    val hadiths = hadithDb.hadithDao().searchHadiths(query, limit - results.size)

                    hadiths.forEach { entity ->
                        results.add(
                            dbFile to entity.toHadith(
                                collectionName = metadata?.name ?: HadithDatabase.getCollectionNameFromFile(dbFile),
                                collectionNameArabic = metadata?.nameArabic ?: "",
                                collectionNameEnglish = metadata?.nameEnglish ?: "",
                                author = metadata?.author ?: "",
                                authorArabic = metadata?.authorArabic ?: ""
                            )
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "❌ Error searching in $dbFile", e)
                }
            }

            results.take(limit)
        }
    }

    /**
     * Get collection statistics
     */
    suspend fun getCollectionStats(): Map<String, HadithCollectionMetadata> {
        return withContext(Dispatchers.IO) {
            val stats = mutableMapOf<String, HadithCollectionMetadata>()
            for (dbFile in HadithDatabase.getAvailableCollections()) {
                try {
                    val metadata = HadithDatabase.getCollectionMetadata(context, dbFile)
                    if (metadata != null) {
                        stats[dbFile] = metadata
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "❌ Error getting stats for $dbFile", e)
                }
            }
            stats
        }
    }

    companion object {
        private const val TAG = "HadithRepository"

        @Volatile
        private var INSTANCE: HadithRepository? = null

        fun getInstance(context: Context): HadithRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HadithRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
