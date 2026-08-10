package com.jksalcedo.librefind.data.local.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.jksalcedo.librefind.data.local.cache.entities.CachedSolution
import com.jksalcedo.librefind.data.local.cache.entities.CachedTarget
import com.jksalcedo.librefind.domain.model.TargetWithCount

@Dao
interface AppCacheDao {

    @Upsert
    suspend fun upsertTargets(targets: List<CachedTarget>)

    @Upsert
    suspend fun upsertSolutions(solutions: List<CachedSolution>)

    @Query("SELECT packageName FROM cached_targets")
    suspend fun getAllTargetPackageNames(): List<String>

    @Query("SELECT packageName FROM cached_solutions")
    suspend fun getAllSolutionPackageNames(): List<String>

    @Query("SELECT alternativesCount FROM cached_targets WHERE packageName = :packageName")
    suspend fun getAlternativesCount(packageName: String): Int?

    @Query("SELECT * FROM cached_targets WHERE packageName = :packageName")
    suspend fun getTarget(packageName: String): CachedTarget?

    @Query("SELECT * FROM cached_solutions WHERE packageName = :packageName")
    suspend fun getSolution(packageName: String): CachedSolution?

    @Query("SELECT packageName, alternativesCount FROM cached_targets")
    suspend fun getAllTargetsWithCounts(): List<TargetWithCount>

    @Query("SELECT packageName FROM cached_targets WHERE packageName IN (:packageNames)")
    suspend fun getMatchingTargetPackageNames(packageNames: List<String>): List<String>

    @Query("DELETE FROM cached_targets")
    suspend fun clearTargets()

    @Query("DELETE FROM cached_solutions")
    suspend fun clearSolutions()

    @Query("SELECT MAX(lastUpdated) FROM cached_targets")
    suspend fun getTargetsLastUpdated(): Long?

    @Query("SELECT MAX(lastUpdated) FROM cached_solutions")
    suspend fun getSolutionsLastUpdated(): Long?

    @Query("SELECT (SELECT COUNT(*) FROM cached_targets) + (SELECT COUNT(*) FROM cached_solutions)")
    suspend fun getTotalCachedRows(): Int
}
