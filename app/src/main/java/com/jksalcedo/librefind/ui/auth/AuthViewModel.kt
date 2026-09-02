package com.jksalcedo.librefind.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jksalcedo.librefind.domain.model.UserProfile
import com.jksalcedo.librefind.domain.repository.AuthRepository
import com.jksalcedo.librefind.utils.ErrorUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSignedIn: Boolean = false,
    val needsProfileSetup: Boolean = false,
    val profileComplete: Boolean = false,
    val userProfile: UserProfile? = null,
    val showCheckEmailDialog: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    _uiState.value = _uiState.value.copy(
                        isSignedIn = true,
                        needsProfileSetup = user.username.isBlank(),
                        profileComplete = user.username.isNotBlank(),
                        userProfile = user
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSignedIn = false,
                        needsProfileSetup = false,
                        profileComplete = false,
                        userProfile = null
                    )
                }
            }
        }
    }

    fun signUp(email: String, password: String, username: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.signUp(email, password, username)
                .onSuccess {
                    val currentUser = authRepository.getCurrentUser()

                    if (currentUser == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            showCheckEmailDialog = true
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isSignedIn = true,
                            needsProfileSetup = true
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = sanitizeAuthError(e.message)
                    )
                }

        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.signIn(email, password)
                .onSuccess {
                    val currentUser = authRepository.getCurrentUser()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedIn = true,
                        needsProfileSetup = currentUser?.username?.isBlank() ?: false,
                        userProfile = currentUser
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = sanitizeAuthError(e.message)
                    )
                }
        }
    }

    fun signInWithGithub() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.signInWithGithub()
                .onSuccess {
                    // Logic for success similar to signIn/signUp
                    val currentUser = authRepository.getCurrentUser()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedIn = true,
                        needsProfileSetup = currentUser?.username?.isBlank() ?: false,
                        userProfile = currentUser
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = sanitizeAuthError(e.message)
                    )
                }
        }
    }

    fun signInAnonymously() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepository.signInAnonymously()
                .onSuccess {
                    val currentUser = authRepository.getCurrentUser()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedIn = true,
                        needsProfileSetup = false,
                        profileComplete = true,
                        userProfile = currentUser
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = sanitizeAuthError(e.message)
                    )
                }
        }
    }

    fun saveProfile(username: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            authRepository.updateProfile(username)
                .onSuccess {
                    // Fetch updated profile or rely on flow
                    val updatedUser = authRepository.getCurrentUser()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        needsProfileSetup = false,
                        profileComplete = true,
                        userProfile = updatedUser
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = sanitizeAuthError(e.message)
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissCheckEmailDialog() {
        _uiState.value = _uiState.value.copy(showCheckEmailDialog = false)
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            // State update is handled by the flow collector in init block
        }
    }

    private fun sanitizeAuthError(rawMessage: String?): String {
        if (rawMessage == null) return "An error occurred"
        return ErrorUtils.sanitizeAuthError(Exception(rawMessage))
    }
}
