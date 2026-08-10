package com.jksalcedo.librefind.data.repository

import android.util.Log
import com.jksalcedo.librefind.data.local.cache.AppCacheDao
import com.jksalcedo.librefind.data.local.cache.entities.CachedSolution
import com.jksalcedo.librefind.data.local.cache.entities.CachedTarget
import com.jksalcedo.librefind.domain.repository.AppRepository
import com.jksalcedo.librefind.domain.repository.CacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.coroutines.CoroutineDispatcher

class CacheRepositoryImpl(
    private val appCacheDao: AppCacheDao,
    private val appRepository: AppRepository,
    private val ioDispatcher: CoroutineDispatcher
) : CacheRepository {

    companion object {
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
        private const val TAG = "CacheRepo"
    }

    override suspend fun refreshCache() {
        withContext(ioDispatcher) {
            try {
                Log.d(TAG, "Refreshing cache from remote...")

                // Single bulk fetch
                val targetsWithCounts = appRepository.getProprietaryTargetsWithAlternativesCount()
                Log.d(TAG, "Fetched ${targetsWithCounts.size} targets from remote (bulk)")

                val now = System.currentTimeMillis()
                val cachedTargets = targetsWithCounts.map { (packageName, count) ->
                    CachedTarget(
                        packageName = packageName,
                        name = packageName,
                        alternativesCount = count,
                        lastUpdated = now
                    )
                }

                appCacheDao.clearTargets()
                appCacheDao.upsertTargets(cachedTargets)
                Log.d(TAG, "Cached ${cachedTargets.size} targets")

                // Also bulk-cache solutions
                val solutionPackages = appRepository.getAllSolutionPackageNames()
                val cachedSolutions = solutionPackages.map { packageName ->
                    CachedSolution(
                        packageName = packageName,
                        lastUpdated = now,
                        name = packageName
                    )
                }

                appCacheDao.clearSolutions()
                appCacheDao.upsertSolutions(cachedSolutions)
                Log.d(TAG, "Cached ${cachedSolutions.size} solutions")

                Log.d(TAG, "Cache refresh complete")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh cache", e)
                throw e
            }
        }
    }

    override suspend fun isCacheValid(): Boolean = withContext(ioDispatcher) {
        val lastUpdated = getCacheLastUpdated() ?: return@withContext false
        val age = System.currentTimeMillis() - lastUpdated
        val isValid = age < CACHE_TTL_MS
        Log.d(TAG, "Cache age: ${age / 1000 / 60}min, valid: $isValid")
        isValid
    }

    override suspend fun isTargetCached(packageName: String): Boolean =
        withContext(ioDispatcher) {
            appCacheDao.getTarget(packageName) != null
        }

    override suspend fun isSolutionCached(packageName: String): Boolean =
        withContext(ioDispatcher) {
            appCacheDao.getSolution(packageName) != null
        }

    override suspend fun getAlternativesCount(packageName: String): Int? =
        withContext(ioDispatcher) {
            appCacheDao.getAlternativesCount(packageName)
        }

    override suspend fun clearCache() {
        withContext(ioDispatcher) {
            appCacheDao.clearTargets()
            appCacheDao.clearSolutions()
            Log.d(TAG, "Cache cleared")
        }
    }

    override suspend fun hasAnyCache(): Boolean = withContext(ioDispatcher) {
        appCacheDao.getTotalCachedRows() > 0
    }

    override suspend fun getTotalCachedItems(): Int = withContext(ioDispatcher) {
        appCacheDao.getTotalCachedRows()
    }

    override suspend fun getCacheLastUpdated(): Long? = withContext(ioDispatcher) {
        val t = appCacheDao.getTargetsLastUpdated()
        val s = appCacheDao.getSolutionsLastUpdated()
        when {
            t == null -> s
            s == null -> t
            else -> maxOf(t, s)
        }
    }

    override suspend fun getBulkCachedData(): Pair<Map<String, Int>, Set<String>> =
        withContext(ioDispatcher) {
            val targets = appCacheDao.getAllTargetsWithCounts()
                .associate { it.packageName to it.alternativesCount }
            val solutions = appCacheDao.getAllSolutionPackageNames().toSet()
            Pair(targets, solutions)
        }
}