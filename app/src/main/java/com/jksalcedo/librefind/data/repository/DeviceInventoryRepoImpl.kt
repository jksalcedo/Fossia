package com.jksalcedo.librefind.data.repository

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.jksalcedo.librefind.data.local.InventorySource
import com.jksalcedo.librefind.data.local.PackageNameHeuristicsDb
import com.jksalcedo.librefind.data.local.TrustedRomSignerDb
import com.jksalcedo.librefind.domain.model.AppItem
import com.jksalcedo.librefind.domain.model.AppStatus
import com.jksalcedo.librefind.domain.repository.AppRepository
import com.jksalcedo.librefind.domain.repository.CacheRepository
import com.jksalcedo.librefind.domain.repository.DeviceInventoryRepo
import com.jksalcedo.librefind.domain.repository.IgnoredAppsRepository
import com.jksalcedo.librefind.domain.repository.ReclassifiedAppsRepository
import com.jksalcedo.librefind.utils.InstallerHeuristics
import com.jksalcedo.librefind.utils.SignerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Locale

class DeviceInventoryRepoImpl(
    private val localSource: InventorySource,
    private val signatureDb: PackageNameHeuristicsDb,
    private val appRepository: AppRepository,
    private val ignoredAppsRepository: IgnoredAppsRepository,
    private val cacheRepository: CacheRepository,
    private val reclassifiedAppsRepository: ReclassifiedAppsRepository,
    private val trustedRomSignerDb: TrustedRomSignerDb
) : DeviceInventoryRepo {

    companion object {
        private const val TAG = "DeviceInventory"


        private val OEM_BRANDS = setOf(
            "xiaomi", "redmi", "poco",
            "samsung",
            "oppo", "oneplus", "realme",
            "vivo", "iqoo",
            "huawei", "honor",
            "lenovo", "motorola",
            "meizu", "zte", "nubia"
        )

        private fun isLikelyRomNamespace(packageName: String, prefixes: List<String>): Boolean {
            val p = packageName.lowercase(Locale.US)
            return prefixes.any { prefix -> p.startsWith(prefix) }
        }

        private fun normalizeDigest(value: String): String = value.trim().lowercase(Locale.US)
    }

    override suspend fun scanAndClassify(): Flow<List<AppItem>> = flow {
        val rawApps = localSource.getRawApps()
        val ignoredAppsList = ignoredAppsRepository.getIgnoredPackageNames().first()
        val reclassifiedAppsMap = reclassifiedAppsRepository.getReclassifiedApps().first()

        val platformSigners = trustedRomSignerDb.platformSigners.first()
        val romAppSigners = trustedRomSignerDb.romAppSigners.first()
        val romPrefixes = trustedRomSignerDb.romPrefixes.first()

        val cacheFresh = cacheRepository.isCacheValid()
        val hasCache = cacheRepository.hasAnyCache()

        // Bulk-load labels from already-fetched ApplicationInfo and installers in parallel.
        // Labels use PackageManager.getApplicationLabel() which is fast (in-memory),
        // but installers need getInstallSourceInfo() which can be slow - parallelizing avoids serial wait.
        val (labelMap, installerMap) = coroutineScope {
            val labels = async {
                rawApps.associate { pkg ->
                    pkg.packageName to (pkg.applicationInfo
                        ?.let { ai -> localSource.getLabelFromInfo(ai) }
                        ?: pkg.packageName)
                }
            }
            val installers = async {
                rawApps.associate { pkg -> pkg.packageName to localSource.getInstaller(pkg.packageName) }
            }
            Pair(labels.await(), installers.await())
        }

        // Single bulk DB read: all cached targets + solutions in 2 queries total
        val (cachedTargetsMap, cachedSolutionsSet) = cacheRepository.getBulkCachedData()

        // Emit initial result immediately using local cache only
        val initialResult = coroutineScope {
            rawApps.map { pkg ->
                async {
                    classifyApp(
                        pkg = pkg,
                        label = labelMap[pkg.packageName] ?: pkg.packageName,
                        installer = installerMap[pkg.packageName],
                        ignoredApps = ignoredAppsList,
                        reclassifiedApps = reclassifiedAppsMap,
                        proprietaryMap = emptyMap(),
                        solutionsSet = emptySet(),
                        pendingPackages = emptySet(),
                        platformSigners = platformSigners,
                        romAppSigners = romAppSigners,
                        romPrefixes = romPrefixes,
                        isOfflineOrStaleMode = !cacheFresh && !hasCache,
                        cachedTargetsMap = cachedTargetsMap,
                        cachedSolutionsSet = cachedSolutionsSet
                    )
                }
            }.awaitAll()
        }
        emit(initialResult.sortedBy { it.status.sortWeight })

        // Refresh cache in background, then re-emit with updated data
        if (!cacheFresh) {
            var networkUpdated = false
            try {
                cacheRepository.refreshCache()
                networkUpdated = true
            } catch (e: Exception) {
                Log.w(TAG, "Background cache refresh failed", e)
            }

            val pendingPackages = try {
                appRepository.getPendingSubmissionPackages()
            } catch (_: Exception) { emptySet() }

            val unclassifiedPackages = initialResult.filter { it.status == AppStatus.UNKN }.map { it.packageName }
            val directSolutions = if (unclassifiedPackages.isNotEmpty()) {
                try {
                    appRepository.areSolutions(unclassifiedPackages)
                } catch (_: Exception) { emptySet() }
            } else emptySet()

            val directProprietary = if (unclassifiedPackages.isNotEmpty()) {
                try {
                    appRepository.areProprietary(unclassifiedPackages)
                } catch (_: Exception) { emptyMap() }
            } else emptyMap()

            if (networkUpdated || pendingPackages.isNotEmpty() || directSolutions.isNotEmpty() || directProprietary.isNotEmpty()) {
                val (freshTargets, freshSolutions) = cacheRepository.getBulkCachedData()
                val updatedResult = coroutineScope {
                    rawApps.map { pkg ->
                        async {
                            classifyApp(
                                pkg = pkg,
                                label = labelMap[pkg.packageName] ?: pkg.packageName,
                                installer = installerMap[pkg.packageName],
                                ignoredApps = ignoredAppsList,
                                reclassifiedApps = reclassifiedAppsMap,
                                proprietaryMap = directProprietary,
                                solutionsSet = directSolutions,
                                pendingPackages = pendingPackages,
                                platformSigners = platformSigners,
                                romAppSigners = romAppSigners,
                                romPrefixes = romPrefixes,
                                isOfflineOrStaleMode = false,
                                cachedTargetsMap = freshTargets,
                                cachedSolutionsSet = freshSolutions
                            )
                        }
                    }.awaitAll()
                }
                emit(updatedResult.sortedBy { it.status.sortWeight })
            }
        } else {
            val pendingPackages = try {
                appRepository.getPendingSubmissionPackages()
            } catch (_: Exception) { emptySet() }

            val unclassifiedPackages = initialResult.filter { it.status == AppStatus.UNKN }.map { it.packageName }
            val directSolutions = if (unclassifiedPackages.isNotEmpty()) {
                try {
                    appRepository.areSolutions(unclassifiedPackages)
                } catch (_: Exception) { emptySet() }
            } else emptySet()

            val directProprietary = if (unclassifiedPackages.isNotEmpty()) {
                try {
                    appRepository.areProprietary(unclassifiedPackages)
                } catch (_: Exception) { emptyMap() }
            } else emptyMap()

            if (pendingPackages.isNotEmpty() || directSolutions.isNotEmpty() || directProprietary.isNotEmpty()) {
                val updatedResult = coroutineScope {
                    rawApps.map { pkg ->
                        async {
                            classifyApp(
                                pkg = pkg,
                                label = labelMap[pkg.packageName] ?: pkg.packageName,
                                installer = installerMap[pkg.packageName],
                                ignoredApps = ignoredAppsList,
                                reclassifiedApps = reclassifiedAppsMap,
                                proprietaryMap = directProprietary,
                                solutionsSet = directSolutions,
                                pendingPackages = pendingPackages,
                                platformSigners = platformSigners,
                                romAppSigners = romAppSigners,
                                romPrefixes = romPrefixes,
                                isOfflineOrStaleMode = false,
                                cachedTargetsMap = cachedTargetsMap,
                                cachedSolutionsSet = cachedSolutionsSet
                            )
                        }
                    }.awaitAll()
                }
                emit(updatedResult.sortedBy { it.status.sortWeight })
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun classifyApp(
        pkg: PackageInfo,
        label: String,
        installer: String?,
        ignoredApps: List<String>,
        reclassifiedApps: Map<String, AppStatus>,
        proprietaryMap: Map<String, Boolean>,
        solutionsSet: Set<String>,
        pendingPackages: Set<String>,
        platformSigners: Set<String>,
        romAppSigners: Set<String>,
        romPrefixes: List<String>,
        isOfflineOrStaleMode: Boolean,
        cachedTargetsMap: Map<String, Int> = emptyMap(),
        cachedSolutionsSet: Set<String> = emptySet()
    ): AppItem {
        val packageName = pkg.packageName
        val icon = pkg.applicationInfo?.icon

        // Use standard PackageManager flags to determine if it is a system app
        val flags = pkg.applicationInfo?.flags ?: 0
        val isSystem =
            (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        if (packageName in ignoredApps) {
            return createAppItem(
                packageName,
                label,
                AppStatus.IGNORED,
                installer,
                icon,
                isUserReclassified = false,
                isSystemPackage = isSystem
            )
        }

        if (packageName in reclassifiedApps) {
            val status = reclassifiedApps[packageName] ?: AppStatus.FOSS
            return createAppItem(
                packageName,
                label,
                status,
                installer,
                icon,
                isUserReclassified = true,
                isSystemPackage = isSystem
            )
        }

        val isAospName = signatureDb.isAospSystemPackageName(packageName)
        if (isAospName) {
            // Non-system app pretending to be com.android.* > suspicious
            if (!isSystem) {
                return createAppItem(
                    packageName, label, AppStatus.PROP, installer, icon,
                    isUserReclassified = false, isSystemPackage = isSystem
                )
            }

            val normalizedDigests =
                SignerUtils.signerSha256Digests(pkg).map(::normalizeDigest).toSet()
            val trustedPlatform = normalizedDigests.any { it in platformSigners }
            val trustedRomApp = normalizedDigests.any { it in romAppSigners }

            if (trustedPlatform || trustedRomApp) {
                return createAppItem(
                    packageName, label, AppStatus.FOSS, installer, icon,
                    isUserReclassified = false, isSystemPackage = isSystem
                )
            }

            // Fallback when signer DB is incomplete
            // OEM ROM + AOSP-name system app > likely proprietary fork
            //  otherwise > pending for review
            val brand = Build.BRAND.orEmpty().lowercase(Locale.US)
            val manufacturer = Build.MANUFACTURER.orEmpty().lowercase(Locale.US)
            val fingerprint = Build.FINGERPRINT.orEmpty().lowercase(Locale.US)

            val isLikelyOemRom = OEM_BRANDS.any { oem ->
                brand.contains(oem) || manufacturer.contains(oem) || fingerprint.contains(oem)
            }

            val hasIndependentPropSignal =
                (proprietaryMap[packageName] == true) || InstallerHeuristics.isProprietaryInstaller(
                    installer
                )

            val hasExplicitCacheHit = try {
                cacheRepository.isTargetCached(packageName)
            } catch (_: Exception) {
                false
            }

            val status = when {
                hasIndependentPropSignal -> {
                    if (isOfflineOrStaleMode && !hasExplicitCacheHit) AppStatus.PENDING
                    else AppStatus.PROP
                }

                isLikelyOemRom -> AppStatus.PENDING
                else -> AppStatus.PENDING
            }

            return createAppItem(
                packageName, label, status, installer, icon,
                isUserReclassified = false, isSystemPackage = isSystem
            )
        }

        if (isSystem && isLikelyRomNamespace(packageName, romPrefixes)) {
            val digests = SignerUtils.signerSha256Digests(pkg)
            if (digests.any { it.lowercase() in romAppSigners }) {
                return createAppItem(
                    packageName, label, AppStatus.FOSS, installer, icon,
                    isUserReclassified = false, isSystemPackage = true
                )
            }
        }

        val isKnownSolution = try {
            cachedSolutionsSet.contains(packageName) || cacheRepository.isSolutionCached(packageName) || packageName in solutionsSet
        } catch (_: Exception) {
            false
        }

        if (isKnownSolution) {
            return createAppItem(
                packageName,
                label,
                AppStatus.FOSS,
                installer,
                icon,
                isUserReclassified = false,
                isSystemPackage = isSystem
            )
        }

        if (InstallerHeuristics.isFossInstaller(installer)) {
            return createAppItem(
                packageName,
                label,
                AppStatus.FOSS,
                installer,
                icon,
                isUserReclassified = false,
                isSystemPackage = isSystem
            )
        }

        val isProprietary = try {
            cachedTargetsMap.containsKey(packageName) || cacheRepository.isTargetCached(packageName) || (proprietaryMap[packageName] == true)
        } catch (_: Exception) {
            false
        }

        if (isProprietary) {
            return createAppItem(
                packageName,
                label,
                AppStatus.PROP,
                installer,
                icon,
                isUserReclassified = false,
                isSystemPackage = isSystem
            )
        }

        if (InstallerHeuristics.isProprietaryInstaller(installer)) {
            return createAppItem(
                packageName,
                label,
                AppStatus.PROP,
                installer,
                icon,
                isUserReclassified = false,
                isSystemPackage = isSystem
            )
        }

        // Only show PENDING if app isn't already classified
        if (packageName in pendingPackages) {
            return createAppItem(
                packageName,
                label,
                AppStatus.PENDING,
                installer,
                icon,
                isUserReclassified = false,
                isSystemPackage = isSystem
            )
        }

        return createAppItem(
            packageName,
            label,
            AppStatus.UNKN,
            installer,
            icon,
            isUserReclassified = false,
            isSystemPackage = isSystem
        )
    }

    private suspend fun createAppItem(
        packageName: String,
        label: String,
        status: AppStatus,
        installer: String?,
        icon: Int?,
        isUserReclassified: Boolean = false,
        isSystemPackage: Boolean = false,
        cachedTargetsMap: Map<String, Int> = emptyMap()
    ): AppItem {
        val alternativesCount = if (status == AppStatus.PROP) {
            cachedTargetsMap[packageName] ?: try {
                cacheRepository.getAlternativesCount(packageName) ?: 0
            } catch (_: Exception) {
                0
            }
        } else {
            0
        }

        return AppItem(
            packageName = packageName,
            label = label,
            status = status,
            installerId = installer,
            knownAlternatives = alternativesCount,
            icon = icon,
            isUserReclassified = isUserReclassified,
            isSystemPackage = isSystemPackage
        )
    }

    override fun getInstaller(packageName: String): String? {
        return localSource.getInstaller(packageName)
    }
}
