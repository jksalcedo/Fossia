package com.jksalcedo.librefind.domain.repository

import com.jksalcedo.librefind.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<UserProfile?>

    suspend fun signUp(email: String, password: String, username: String): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signInWithGithub(): Result<Unit>
    suspend fun signInAnonymously(): Result<Unit>
    suspend fun signOut()
    suspend fun getCurrentUser(): UserProfile?
    suspend fun getPublicProfile(userId: String): UserProfile?
    val topContributors: Flow<List<UserProfile>>
    suspend fun updateProfile(username: String): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
}
