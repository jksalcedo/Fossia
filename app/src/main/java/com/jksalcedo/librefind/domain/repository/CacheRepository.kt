package com.jksalcedo.librefind.domain.repository

interface CacheRepository {
    suspend fun refreshCache()
    suspend fun isCacheValid(): Boolean
    suspend fun isTargetCached(packageName: String): Boolean
    suspend fun isSolutionCached(packageName: String): Boolean
    suspend fun getAlternativesCount(packageName: String): Int?
    suspend fun clearCache()

    suspend fun hasAnyCache(): Boolean
    suspend fun getCacheLastUpdated(): Long?
    suspend fun getTotalCachedItems(): Int

    /**
     * Bulk-load all cached targets (with alternatives counts) and solution package names.
     * Returns Pair(targetPackageName -> alternativesCount, setOf solutionPackageNames).
     */
    suspend fun getBulkCachedData(): Pair<Map<String, Int>, Set<String>>
}
