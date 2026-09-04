package com.jksalcedo.librefind.ui.community

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jksalcedo.librefind.domain.model.SigningKeyVote
import com.jksalcedo.librefind.domain.model.Submission
import com.jksalcedo.librefind.domain.model.SubmissionType
import com.jksalcedo.librefind.domain.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val PAGE_SIZE = 50

enum class SortOption {
    NEWEST,
    OLDEST,
    MOST_UPVOTED,
    MOST_DOWNVOTED
}

data class CommunitySubmissionsState(
    val submissions: List<Submission> = emptyList(),
    val selectedSubmission: Submission? = null,
    val signingKeyVotes: List<SigningKeyVote> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val filterType: SubmissionType? = null,
    val isKeyVoteFilter: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val sortOption: SortOption = SortOption.NEWEST
)

class CommunitySubmissionsViewModel(
    private val appRepository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunitySubmissionsState())
    val uiState: StateFlow<CommunitySubmissionsState> = _uiState.asStateFlow()

    private var currentPage = 0
    private var isPageLoading = false

    init {
        loadSubmissions()
    }

    fun loadSubmissions(forceRefresh: Boolean = false) {
        if (isPageLoading) return
        viewModelScope.launch {
            isPageLoading = true
            if (forceRefresh) {
                currentPage = 0
                _uiState.update {
                    it.copy(
                        isRefreshing = true,
                        error = null,
                        submissions = emptyList(),
                        canLoadMore = true
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            try {
                val page = appRepository.getPendingSubmissionsPage(
                    page = 0,
                    pageSize = PAGE_SIZE,
                    forceRefresh = forceRefresh
                )
                val enriched = enrichWithVotes(page, forceRefresh)
                val keyVotes = try {
                    appRepository.getSigningKeyVotes(forceRefresh)
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = "Key votes error: ${e.message}") }
                    emptyList()
                }
                currentPage = 1
                _uiState.update { state ->
                    val mergedSubmissions = if (state.selectedSubmission != null && enriched.none { s -> s.id == state.selectedSubmission.id }) {
                        enriched + state.selectedSubmission
                    } else {
                        enriched
                    }
                    state.copy(
                        submissions = mergedSubmissions,
                        signingKeyVotes = keyVotes,
                        isLoading = false,
                        isRefreshing = false,
                        canLoadMore = page.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "Failed to load submissions"
                    )
                }
            } finally {
                isPageLoading = false
            }
        }
    }

    fun loadNextPage() {
        if (isPageLoading || !_uiState.value.canLoadMore || _uiState.value.isLoadingMore) return
        isPageLoading = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val page = appRepository.getPendingSubmissionsPage(
                    page = currentPage,
                    pageSize = PAGE_SIZE
                )
                if (page.isEmpty()) {
                    _uiState.update { it.copy(isLoadingMore = false, canLoadMore = false) }
                } else {
                    val enriched = enrichWithVotes(page)
                    currentPage++
                    _uiState.update {
                        it.copy(
                            submissions = it.submissions + enriched,
                            isLoadingMore = false,
                            canLoadMore = page.isNotEmpty()
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        error = e.message ?: "Failed to load more submissions"
                    )
                }
            } finally {
                isPageLoading = false
            }
        }
    }

    private suspend fun enrichWithVotes(
        submissions: List<Submission>,
        forceRefresh: Boolean = false
    ): List<Submission> {
        if (submissions.isEmpty()) return emptyList()
        // Always force-refresh: each page has different IDs so the
        // cached result from a prior page would miss these entries.
        val voteCounts = appRepository.getSubmissionVoteCounts(
            submissions.map { it.id },
            forceRefresh = true
        )
        return submissions.map { s ->
            val agg = voteCounts[s.id]
            s.copy(
                upvotes = agg?.upvotes ?: s.upvotes,
                downvotes = agg?.downvotes ?: s.downvotes,
                userVote = agg?.userVote ?: s.userVote
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilterType(type: SubmissionType?) {
        _uiState.update { it.copy(filterType = type, isKeyVoteFilter = false) }
    }

    fun setKeyVoteFilter(enabled: Boolean) {
        _uiState.update { it.copy(isKeyVoteFilter = enabled, filterType = null) }
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option) }
    }

    fun loadSubmissionById(id: String) {
        val existing = _uiState.value.selectedSubmission?.takeIf { it.id == id }
            ?: _uiState.value.submissions.find { it.id == id }

        if (existing != null) {
            _uiState.update { it.copy(selectedSubmission = existing) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetail = true) }
            try {
                val submission = appRepository.getSubmissionById(id)
                if (submission != null) {
                    val enriched = enrichWithVotes(listOf(submission)).firstOrNull() ?: submission
                    _uiState.update { state ->
                        state.copy(
                            selectedSubmission = enriched,
                            submissions = if (state.submissions.any { it.id == id }) {
                                state.submissions.map { if (it.id == id) enriched else it }
                            } else {
                                state.submissions + enriched
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("CommunityVM", "Failed to load submission by ID: $id", e)
            } finally {
                _uiState.update { it.copy(isLoadingDetail = false) }
            }
        }
    }

    fun approveSubmission(submission: Submission) {
        viewModelScope.launch {
            appRepository.approveSubmission(submission.id, submission.type)
                .onSuccess { loadSubmissions() }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to approve submission") }
                }
        }
    }

    fun rejectSubmission(submission: Submission, reason: String) {
        viewModelScope.launch {
            appRepository.rejectSubmission(submission.id, submission.type, reason)
                .onSuccess { loadSubmissions() }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message ?: "Failed to reject submission") }
                }
        }
    }

    fun castVote(
        submission: Submission,
        vote: Int,
        reason: String? = null,
        reasonDetail: String? = null
    ) {
        val newVote = if (submission.userVote == vote) 0 else vote

        _uiState.update { state ->
            val wasUpvoted = submission.userVote == 1
            val wasDownvoted = submission.userVote == -1
            val updated = submission.copy(
                upvotes = when {
                    newVote == 1 -> submission.upvotes + 1
                    wasUpvoted -> submission.upvotes - 1
                    else -> submission.upvotes
                },
                downvotes = when {
                    newVote == -1 -> submission.downvotes + 1
                    wasDownvoted -> submission.downvotes - 1
                    else -> submission.downvotes
                },
                userVote = if (newVote == 0) null else newVote
            )
            state.copy(
                submissions = state.submissions.map { s -> if (s.id == submission.id) updated else s },
                selectedSubmission = if (state.selectedSubmission?.id == submission.id) updated else state.selectedSubmission
            )
        }

        val table = if (submission.type == SubmissionType.LINKING)
            "user_linking_submissions" else "user_submissions"

        viewModelScope.launch {
            appRepository.castSubmissionVote(
                submissionId = submission.id,
                submissionTable = table,
                vote = newVote,
                reason = if (newVote == -1) reason else null,
                reasonDetail = if (newVote == -1) reasonDetail else null
            ).onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to cast vote") }
                loadSubmissions()
            }
        }
    }
}
