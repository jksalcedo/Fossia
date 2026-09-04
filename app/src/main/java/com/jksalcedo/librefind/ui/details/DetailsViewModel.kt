package com.jksalcedo.librefind.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jksalcedo.librefind.domain.model.Alternative
import com.jksalcedo.librefind.domain.model.Submission
import com.jksalcedo.librefind.domain.repository.AppRepository
import com.jksalcedo.librefind.domain.repository.AuthRepository
import com.jksalcedo.librefind.domain.repository.CacheRepository
import com.jksalcedo.librefind.domain.repository.DeviceInventoryRepo
import com.jksalcedo.librefind.domain.usecase.GetAlternativeUseCase
import com.jksalcedo.librefind.utils.InstallerHeuristics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DetailsViewModel(
    private val getAlternativeUseCase: GetAlternativeUseCase,
    private val authRepository: AuthRepository,
    private val appRepository: AppRepository,
    private val cacheRepository: CacheRepository,
    private val deviceInventoryRepo: DeviceInventoryRepo
) : ViewModel() {

    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    fun loadAlternatives(packageName: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            launch { isAppUnknown(packageName) }
            launch { loadPendingSubmission(packageName) }

            try {
                val isFoss = cacheRepository.isSolutionCached(packageName) ||
                        appRepository.isSolution(packageName)

                val alternatives = if (!isFoss) {
                    getAlternativeUseCase(packageName)
                } else {
                    emptyList()
                }

                val appInfo = if (isFoss) {
                    appRepository.getAlternative(packageName)
                } else {
                    appRepository.getTarget(packageName)
                }

                val siblings = if (isFoss) {
                    appRepository.getSiblingAlternatives(packageName)
                } else {
                    emptyList()
                }
                val fossCategoryUnset = isFoss && siblings == null

                val user = authRepository.getCurrentUser()

                _state.update {
                    it.copy(
                        isLoading = false,
                        packageName = packageName,
                        alternatives = alternatives,
                        siblingAlternatives = siblings.orEmpty(),
                        isFoss = isFoss,
                        fossCategoryUnset = fossCategoryUnset,
                        appInfo = appInfo,
                        isSignedIn = user != null,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load alternatives"
                    )
                }
            }
        }
    }

    fun castMatchVote(solutionPackage: String, vote: Int) {
        viewModelScope.launch {
            if (authRepository.getCurrentUser() == null) return@launch
            val targetPackage = _state.value.packageName
            if (targetPackage.isBlank()) return@launch

            var newVoteToCast: Int? = null

            // Optimistic update: flip if same vote (toggle off), otherwise apply
            _state.update { s ->
                s.copy(
                    alternatives = s.alternatives.map { alt ->
                        if (alt.packageName != solutionPackage) return@map alt
                        val prev = alt.userMatchVote
                        val newVote = if (prev == vote) 0 else vote
                        newVoteToCast = newVote
                        val upDelta = when {
                            newVote == 1 -> 1
                            prev == 1 -> -1
                            else -> 0
                        }
                        val downDelta = when {
                            newVote == -1 -> 1
                            prev == -1 -> -1
                            else -> 0
                        }
                        alt.copy(
                            userMatchVote = newVote.takeIf { it != 0 },
                            matchUpvotes = alt.matchUpvotes + upDelta,
                            matchDownvotes = alt.matchDownvotes + downDelta,
                            matchScore = alt.matchScore + upDelta - downDelta
                        )
                    }
                )
            }

            val actualVote = newVoteToCast ?: return@launch

            appRepository.castMatchVote(targetPackage, solutionPackage, actualVote)
                .onFailure {
                    // Revert optimistic update on failure
                    loadAlternatives(targetPackage)
                }
        }
    }

    fun retry(packageName: String) {
        loadAlternatives(packageName)
    }

    /**
     * Lightweight check using cached data instead of a full device scan.
     * An app is "unknown" if it's neither a known proprietary target nor a known FOSS solution,
     * and it does not have a proprietary installer signal.
     */
    private suspend fun isAppUnknown(packageName: String) {
        val isTarget = try {
            cacheRepository.isTargetCached(packageName) || appRepository.isProprietary(packageName)
        } catch (_: Exception) {
            false
        }

        val isSolution = try {
            cacheRepository.isSolutionCached(packageName) || appRepository.isSolution(packageName)
        } catch (_: Exception) {
            false
        }

        val installer = deviceInventoryRepo.getInstaller(packageName)
        val isProprietaryInstaller = InstallerHeuristics.isProprietaryInstaller(installer)

        val isUnknown = !isTarget && !isSolution && !isProprietaryInstaller

        _state.update {
            it.copy(isUnknown = isUnknown)
        }
    }

    private suspend fun loadPendingSubmission(packageName: String) {
        try {
            val pending = appRepository.getAllPendingSubmissions()
            val matches = pending.filter { sub ->
                sub.proprietaryPackages.split(",").map { it.trim() }.contains(packageName)
            }
            if (matches.isNotEmpty()) {
                val voteCounts = appRepository.getSubmissionVoteCounts(
                    matches.map { it.id },
                    forceRefresh = true
                )
                val enrichedMatches = matches.map { s ->
                    val agg = voteCounts[s.id]
                    s.copy(
                        upvotes = agg?.upvotes ?: s.upvotes,
                        downvotes = agg?.downvotes ?: s.downvotes,
                        userVote = agg?.userVote ?: s.userVote
                    )
                }
                _state.update { it.copy(pendingSubmissions = enrichedMatches) }
            } else {
                _state.update { it.copy(pendingSubmissions = emptyList()) }
            }
        } catch (_: Exception) {}
    }
}

data class DetailsState(
    val isLoading: Boolean = false,
    val packageName: String = "",
    val alternatives: List<Alternative> = emptyList(),
    val siblingAlternatives: List<Alternative> = emptyList(),
    val isFoss: Boolean = false,
    val fossCategoryUnset: Boolean = false,
    val appInfo: Alternative? = null,
    val isSignedIn: Boolean = false,
    val error: String? = null,
    val isUnknown: Boolean = false,
    val pendingSubmissions: List<Submission> = emptyList()
)
