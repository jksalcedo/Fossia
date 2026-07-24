package com.jksalcedo.librefind.ui.details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jksalcedo.librefind.domain.model.Alternative
import com.jksalcedo.librefind.domain.model.VoteType
import com.jksalcedo.librefind.domain.repository.AppRepository
import com.jksalcedo.librefind.domain.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class AlternativeDetailViewModel(
    private val appRepository: AppRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AlternativeDetailState())
    val state: StateFlow<AlternativeDetailState> = _state.asStateFlow()

    private var currentAltId: String = ""
    private var rateJob: Job? = null
    private var lastFeedbackSubmitTime = 0L

    fun loadAlternative(altId: String) {
        currentAltId = altId
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val user = authRepository.getCurrentUser()
            val alternative = appRepository.getAlternative(altId)

            val userVotes: Map<String, Int?> = if (user != null) {
                appRepository.getUserVote(altId, user.uid)
            } else {
                mapOf("usability" to null, "privacy" to null, "features" to null)
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    alternative = alternative?.copy(
                        userUsabilityRating = userVotes["usability"],
                        userPrivacyRating = userVotes["privacy"],
                        userFeaturesRating = userVotes["features"],
                        userRating = userVotes["usability"]
                    ),
                    isSignedIn = user != null,
                    username = user?.username
                )
            }
        }
    }

    fun rate(stars: Int) {
        rateDimension(VoteType.USABILITY, stars)
    }

    fun rateDimension(voteType: VoteType, stars: Int) {
        if (!_state.value.isSignedIn) return

        //  UI update
        _state.update { state ->
            state.copy(
                alternative = state.alternative?.copy(
                    userUsabilityRating = if (voteType == VoteType.USABILITY) stars else state.alternative.userUsabilityRating,
                    userPrivacyRating = if (voteType == VoteType.PRIVACY) stars else state.alternative.userPrivacyRating,
                    userFeaturesRating = if (voteType == VoteType.FEATURES) stars else state.alternative.userFeaturesRating,
                    userRating = if (voteType == VoteType.USABILITY) stars else state.alternative.userRating
                )
            )
        }

        //  cancel previous vote call, wait 600ms before sending
        rateJob?.cancel()
        rateJob = viewModelScope.launch {
            delay(600.milliseconds)
            try {
                appRepository.castVote(currentAltId, voteType.key, stars).getOrThrow()
                refreshUserVotes()
            } catch (e: Exception) {
                Log.e("AltDetailVM", "Vote failed", e)
                loadAlternative(currentAltId)
            }
        }
    }

    private fun refreshUserVotes() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser() ?: return@launch
            val userVotes = appRepository.getUserVote(currentAltId, user.uid)

            _state.update { state ->
                state.copy(
                    alternative = state.alternative?.copy(
                        userUsabilityRating = userVotes["usability"],
                        userPrivacyRating = userVotes["privacy"],
                        userFeaturesRating = userVotes["features"],
                        userRating = userVotes["usability"]
                    )
                )
            }
        }
    }

    fun submitFeedback(type: String, text: String) {
        val now = System.currentTimeMillis()
        if (now - lastFeedbackSubmitTime < 5_000) {
            return
        }
        lastFeedbackSubmitTime = now

        viewModelScope.launch {
            try {
                appRepository.submitFeedback(currentAltId, type, text)
            } catch (e: Exception) {
                Log.e("AltDetailVM", "Feedback submission failed", e)
            }
        }
    }

    fun submitAppReport(issueType: String, description: String) {
        viewModelScope.launch {
            appRepository.submitAppReport(currentAltId, issueType, description)
                .onSuccess {
                    _state.update { it.copy(reportSubmitted = true) }
                }
                .onFailure { e ->
                    Log.e("AltDetailVM", "Report submission failed", e)
                    _state.update { it.copy(reportError = e.message) }
                }
        }
    }

    fun clearReportState() {
        _state.update { it.copy(reportSubmitted = false, reportError = null) }
    }
}

data class AlternativeDetailState(
    val isLoading: Boolean = false,
    val alternative: Alternative? = null,
    val isSignedIn: Boolean = false,
    val username: String? = null,
    val reportSubmitted: Boolean = false,
    val reportError: String? = null
)
