package com.jksalcedo.librefind.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jksalcedo.librefind.domain.model.ReportPriority
import com.jksalcedo.librefind.domain.model.ReportType
import com.jksalcedo.librefind.domain.repository.AppRepository
import com.jksalcedo.librefind.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUiState(
    val title: String = "",
    val description: String = "",
    val selectedType: ReportType = ReportType.SUGGESTION,
    val selectedPriority: ReportPriority = ReportPriority.LOW,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

class ReportViewModel(
    private val repository: AppRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun updateType(type: ReportType) {
        _uiState.update { it.copy(selectedType = type) }
    }

    fun updatePriority(priority: ReportPriority) {
        _uiState.update { it.copy(selectedPriority = priority) }
    }

    fun submitReport() {
        val state = _uiState.value
        if (state.title.isBlank() || state.description.isBlank()) {
            _uiState.update { it.copy(error = "Title and description are required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            var user = authRepository.getCurrentUser()
            if (user == null) {
                authRepository.signInAnonymously()
                user = authRepository.getCurrentUser()
            }
            val userId = user?.uid ?: ""

            repository.submitReport(
                title = state.title,
                description = state.description,
                type = state.selectedType.name,
                priority = state.selectedPriority.name,
                userId = userId
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to submit report") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
