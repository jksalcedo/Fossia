package com.jksalcedo.librefind.data.repository

import com.jksalcedo.librefind.data.remote.model.ProfileDto
import com.jksalcedo.librefind.domain.model.UserProfile
import com.jksalcedo.librefind.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SupabaseAuthRepository(
    private val supabase: SupabaseClient
) : AuthRepository {

    private val auth: Auth = supabase.auth

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val currentUser: Flow<UserProfile?> = auth.sessionStatus.transformLatest { status ->
        if (status is SessionStatus.Authenticated) {
            status.session.user?.let { user ->
                val fallbackUsername = extractUsernameFromMetadata(user).ifBlank { "Guest" }
                emit(
                    UserProfile(
                        uid = user.id,
                        username = fallbackUsername,
                        email = user.email ?: ""
                    )
                )
                ensureProfileCreated(user)
                val profile = fetchUserProfile(user.id)
                if (profile != null && profile.username.isBlank()) {
                    emit(profile.copy(username = fallbackUsername))
                } else if (profile != null) {
                    emit(profile)
                }
            }
        } else {
            emit(null)
        }
    }

    override val topContributors: Flow<List<UserProfile>> = kotlinx.coroutines.flow.flow {
        try {
            val dtos = supabase.postgrest.from("profiles")
                .select() {
                    order(
                        "reputation_score",
                        io.github.jan.supabase.postgrest.query.Order.DESCENDING
                    )
                    limit(20)
                }.decodeList<ProfileDto>()

            emit(dtos.map { dto ->
                UserProfile(
                    uid = dto.id,
                    username = dto.username ?: "Unknown",
                    email = "",
                    joinedAt = dto.createdAt?.let { dateStr ->
                        try {
                            Instant.parse(dateStr).toEpochMilliseconds()
                        } catch (_: Exception) {
                            0L
                        }
                    } ?: 0L,
                    submissionCount = dto.submissionCount,
                    approvedCount = dto.approvedCount,
                    rejectedCount = dto.rejectedCount,
                    reputationScore = dto.reputationScore,
                    badge = dto.badge
                )
            })
        } catch (e: Exception) {
            android.util.Log.e("AuthRepo", "Failed to fetch top contributors", e)
            emit(emptyList())
        }
    }

    override suspend fun signUp(email: String, password: String, username: String): Result<Unit> =
        runCatching {
            auth.signUpWith(Email, redirectUrl = "librefind://login-callback") {
                this.email = email
                this.password = password
                data = buildJsonObject {
                    put("username", username)
                }
            }

            val userId = auth.currentUserOrNull()?.id
            if (userId != null) {
                supabase.postgrest.from("profiles").upsert(
                    ProfileDto(id = userId, username = username)
                )
            }
        }.also { result ->
            result.exceptionOrNull()?.let {
                android.util.Log.e("SignUp", "SignUp failed: ${it.message}", it)
            }
        }

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signInWithGithub(): Result<Unit> = runCatching {
        auth.signInWith(Github, redirectUrl = "librefind://login-callback")
    }

    override suspend fun signInAnonymously(): Result<Unit> = runCatching {
        auth.signInAnonymously()
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun getCurrentUser(): UserProfile? {
        val user = auth.currentUserOrNull()
        if (user == null) {
            android.util.Log.d("AuthRepo", "getCurrentUser: currentUserOrNull returned null")
            return null
        }
        ensureProfileCreated(user)
        val profile = fetchUserProfile(user.id)

        if (profile != null && profile.username.isBlank()) {
            val fallback = extractUsernameFromMetadata(user).ifBlank { "Guest" }
            return profile.copy(username = fallback)
        }
        return profile
    }

    override suspend fun getPublicProfile(userId: String): UserProfile? {
        return fetchUserProfile(userId)
    }

    override suspend fun updateProfile(username: String): Result<Unit> = runCatching {
        val userId = auth.currentUserOrNull()?.id ?: throw IllegalStateException("Not logged in")

        val profileUpdate = ProfileDto(
            id = userId,
            username = username
        )

        supabase.postgrest.from("profiles").update(profileUpdate) {
            filter {
                eq("id", userId)
            }
        }

    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        supabase.postgrest.rpc("delete_account")
        auth.signOut()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun fetchUserProfile(userId: String?): UserProfile? {
        if (userId == null) return null
        return try {
            val profileDto = supabase.postgrest.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }.decodeSingleOrNull<ProfileDto>()

            profileDto?.let {
                UserProfile(
                    uid = it.id,
                    username = it.username ?: "",
                    email = auth.currentUserOrNull()?.email ?: "",
                    joinedAt = it.createdAt?.let { dateStr ->
                        try {
                            Instant.parse(dateStr).toEpochMilliseconds()
                        } catch (_: Exception) {
                            System.currentTimeMillis()
                        }
                    } ?: System.currentTimeMillis(),
                    submissionCount = it.submissionCount,
                    approvedCount = it.approvedCount,
                    rejectedCount = it.rejectedCount,
                    reputationScore = it.reputationScore,
                    badge = it.badge
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractUsernameFromMetadata(user: UserInfo): String {
        val metadata = user.userMetadata
        return try {
            metadata?.let { m ->
                listOf("user_name", "preferred_username", "full_name", "name")
                    .firstNotNullOfOrNull { key -> m[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } }
            }
        } catch (_: Exception) {
            null
        }
            ?: user.email?.substringBefore("@")
            ?: ""
    }

    private suspend fun ensureProfileCreated(user: UserInfo) {
        try {
            val userId = user.id
            val username = extractUsernameFromMetadata(user).ifBlank { "Guest" }

            val existing = supabase.postgrest.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<ProfileDto>()

            if (existing == null) {
                supabase.postgrest.from("profiles").insert(
                    ProfileDto(id = userId, username = username)
                )
            } else if (existing.username.isNullOrBlank() && username.isNotBlank()) {
                supabase.postgrest.from("profiles").update({
                    set("username", username)
                }) { filter { eq("id", userId) } }
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepo", "ensureProfileCreated failed", e)
        }
    }
}
