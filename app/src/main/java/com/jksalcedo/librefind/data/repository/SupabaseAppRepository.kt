package com.jksalcedo.librefind.data.repository

import android.os.Build
import android.util.Log
import com.jksalcedo.librefind.data.remote.model.AlternativeWithVoteDto
import com.jksalcedo.librefind.data.remote.model.AppFeedbackDto
import com.jksalcedo.librefind.data.remote.model.AppReport
import com.jksalcedo.librefind.data.remote.model.AppScanStatsDto
import com.jksalcedo.librefind.data.remote.model.MatchVoteDto
import com.jksalcedo.librefind.data.remote.model.ProfileDto
import com.jksalcedo.librefind.data.remote.model.SigningKeyVoteDto
import com.jksalcedo.librefind.data.remote.model.SigningKeyVoteWithProfileDto
import com.jksalcedo.librefind.data.remote.model.SolutionDto
import com.jksalcedo.librefind.data.remote.model.SubmissionVoteAggregate
import com.jksalcedo.librefind.data.remote.model.SubmissionVoteDto
import com.jksalcedo.librefind.data.remote.model.UserLinkingSubmissionsDto
import com.jksalcedo.librefind.data.remote.model.UserReportDto
import com.jksalcedo.librefind.data.remote.model.UserSubmissionDto
import com.jksalcedo.librefind.data.remote.model.UserVoteDto
import com.jksalcedo.librefind.domain.model.Alternative
import com.jksalcedo.librefind.domain.model.DuplicateStatus
import com.jksalcedo.librefind.domain.model.Report
import com.jksalcedo.librefind.domain.model.ReportPriority
import com.jksalcedo.librefind.domain.model.ReportStatus
import com.jksalcedo.librefind.domain.model.ReportType
import com.jksalcedo.librefind.domain.model.SigningKeyVote
import com.jksalcedo.librefind.domain.model.Submission
import com.jksalcedo.librefind.domain.model.SubmissionStatus
import com.jksalcedo.librefind.domain.model.SubmissionType
import com.jksalcedo.librefind.domain.model.SubmittedApp
import com.jksalcedo.librefind.domain.repository.AppRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class SupabaseAppRepository(
    private val supabase: SupabaseClient
) : AppRepository {

    override suspend fun areProprietary(packageNames: List<String>): Map<String, Boolean> {
        if (packageNames.isEmpty()) return emptyMap()

        return try {
            val uniquePackages = packageNames.distinct()
            val foundPackages = mutableSetOf<String>()
            val chunkSize = 200

            uniquePackages.chunked(chunkSize).forEach { chunk ->
                val found = supabase.postgrest.from("targets")
                    .select(columns = Columns.list("package_name")) {
                        filter { isIn("package_name", chunk) }
                    }
                    .decodeList<PackageNameDto>()
                    .map { it.packageName }

                foundPackages.addAll(found)
            }

            packageNames.associateWith { foundPackages.contains(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    override suspend fun isProprietary(packageName: String): Boolean {
        return try {
            val count = supabase.postgrest.from("targets")
                .select(columns = Columns.list("package_name")) {
                    count(Count.EXACT)
                    filter { eq("package_name", packageName) }
                    limit(1)
                }.countOrNull() ?: 0

            count > 0
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun isSolution(packageName: String): Boolean {
        return try {
            val count = supabase.postgrest.from("solutions")
                .select(columns = Columns.list("package_name")) {
                    count(Count.EXACT)
                    filter { eq("package_name", packageName) }
                    limit(1)
                }.countOrNull() ?: 0

            count > 0
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun getAlternatives(packageName: String): List<Alternative> {
        // Try the RPC first (includes match-vote aggregates).
        // Falls back to direct query if the function isn't available yet.
        return try {
            val userId = supabase.auth.currentUserOrNull()?.id

            @Serializable
            data class GetAlternativesParams(
                @SerialName("target_pkg") val targetPkg: String,
                @SerialName("p_user_id") val userId: String?
            )

            val results = supabase.postgrest.rpc(
                "get_alternatives_with_match_votes",
                GetAlternativesParams(packageName, userId)
            ).decodeList<AlternativeWithVoteDto>()

            results.map { dto -> dto.toAlternative() }
        } catch (rpcError: Exception) {
            Log.w(
                "SupabaseAppRepo",
                "get_alternatives_with_match_votes unavailable, falling back",
                rpcError
            )
            getAlternativesDirect(packageName)
        }
    }

    private suspend fun getAlternativesDirect(packageName: String): List<Alternative> {
        return try {
            val target = supabase.postgrest.from("targets")
                .select(columns = Columns.list("alternatives")) {
                    filter { eq("package_name", packageName) }
                    limit(1)
                }.decodeSingleOrNull<TargetAlternativesDto>()

            val altPackageNames = target?.alternatives.orEmpty()
            if (altPackageNames.isEmpty()) return emptyList()

            val solutions = supabase.postgrest.from("solutions")
                .select(
                    columns = Columns.list(
                        "package_name", "name", "license", "repo_url", "fdroid_id",
                        "icon_url", "description", "features", "pros", "cons",
                        "rating_usability", "rating_privacy", "rating_features",
                        "vote_count", "category"
                    )
                ) {
                    filter { isIn("package_name", altPackageNames) }
                }.decodeList<SolutionDto>()

            @Serializable
            data class MatchVoteRowDto(
                @SerialName("solution_package") val solutionPackage: String,
                @SerialName("user_id") val userId: String,
                val vote: Int
            )

            val allVotes: List<MatchVoteRowDto> = try {
                supabase.postgrest.from("target_solution_votes")
                    .select(columns = Columns.list("solution_package", "user_id", "vote")) {
                        filter { eq("target_package", packageName) }
                    }.decodeList()
            } catch (_: Exception) {
                emptyList()
            }

            val currentUserId = supabase.auth.currentUserOrNull()?.id

            data class VoteAgg(val upvotes: Int, val downvotes: Int, val userVote: Int?)

            val votesByPkg = allVotes
                .groupBy { it.solutionPackage }
                .mapValues { (_, rows) ->
                    VoteAgg(
                        upvotes = rows.count { it.vote == 1 },
                        downvotes = rows.count { it.vote == -1 },
                        userVote = rows.firstOrNull { it.userId == currentUserId }?.vote
                    )
                }

            solutions.map { dto ->
                val usabilityRating = dto.ratingUsability ?: 0f
                val privacyRating = dto.ratingPrivacy ?: 0f
                val featuresRating = dto.ratingFeatures ?: 0f
                val rawRatingCount = dto.voteCount ?: 0

                val ratingAvg = if (rawRatingCount > 0) {
                    val sum = usabilityRating + privacyRating + featuresRating
                    val nonZeroCount =
                        listOf(usabilityRating, privacyRating, featuresRating).count { it > 0 }
                    if (nonZeroCount > 0) sum / nonZeroCount else 0f
                } else 0f

                val agg = votesByPkg[dto.packageName]

                Alternative(
                    id = dto.packageName,
                    name = dto.name,
                    packageName = dto.packageName,
                    license = dto.license,
                    repoUrl = dto.repoUrl ?: "",
                    fdroidId = dto.fdroidId ?: "",
                    iconUrl = dto.iconUrl,
                    category = dto.category,
                    matchUpvotes = agg?.upvotes ?: 0,
                    matchDownvotes = agg?.downvotes ?: 0,
                    matchScore = (agg?.upvotes ?: 0) - (agg?.downvotes ?: 0),
                    userMatchVote = agg?.userVote,
                    ratingAvg = ratingAvg,
                    ratingCount = if (ratingAvg == 0f) 0 else rawRatingCount,
                    usabilityRating = usabilityRating,
                    privacyRating = privacyRating,
                    featuresRating = featuresRating,
                    description = dto.description,
                    features = dto.features.orEmpty(),
                    pros = dto.pros.orEmpty(),
                    cons = dto.cons.orEmpty()
                )
            }
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "getAlternativesDirect failed", e)
            emptyList()
        }
    }

    private fun AlternativeWithVoteDto.toAlternative(): Alternative {
        val usabilityRating = ratingUsability ?: 0f
        val privacyRating = ratingPrivacy ?: 0f
        val featuresRating = ratingFeatures ?: 0f
        val rawRatingCount = voteCount ?: 0

        val ratingAvg = if (rawRatingCount > 0) {
            val sum = usabilityRating + privacyRating + featuresRating
            val nonZeroCount =
                listOf(usabilityRating, privacyRating, featuresRating).count { it > 0 }
            if (nonZeroCount > 0) sum / nonZeroCount else 0f
        } else 0f

        val ratingCount = if (ratingAvg == 0f) 0 else rawRatingCount

        return Alternative(
            id = packageName,
            name = name,
            packageName = packageName,
            license = license,
            repoUrl = repoUrl ?: "",
            fdroidId = fdroidId ?: "",
            iconUrl = iconUrl,
            category = category,
            matchScore = matchScore,
            matchUpvotes = matchUpvotes,
            matchDownvotes = matchDownvotes,
            userMatchVote = userMatchVote,
            ratingAvg = ratingAvg,
            ratingCount = ratingCount,
            usabilityRating = usabilityRating,
            privacyRating = privacyRating,
            featuresRating = featuresRating,
            description = description,
            features = features.orEmpty(),
            pros = pros.orEmpty(),
            cons = cons.orEmpty()
        )
    }

    override suspend fun getAlternative(packageName: String): Alternative? {
        return try {
            val dto = supabase.postgrest.from("solutions")
                .select(
                    columns = Columns.list(
                        "package_name", "name", "license", "repo_url", "fdroid_id",
                        "icon_url", "description", "features", "pros", "cons",
                        "rating_usability", "rating_privacy", "rating_features", "vote_count",
                        "category"
                    )
                ) {
                    filter {
                        eq("package_name", packageName)
                    }
                }.decodeSingleOrNull<SolutionDto>() ?: return null

            val usabilityRating = dto.ratingUsability ?: 0f
            val privacyRating = dto.ratingPrivacy ?: 0f
            val featuresRating = dto.ratingFeatures ?: 0f
            val rawRatingCount = dto.voteCount ?: 0

            val ratingAvg = if (rawRatingCount > 0) {
                val sum = usabilityRating + privacyRating + featuresRating
                val nonZeroCount =
                    listOf(usabilityRating, privacyRating, featuresRating).count { it > 0 }
                if (nonZeroCount > 0) sum / nonZeroCount else 0f
            } else 0f

            val ratingCount = if (ratingAvg == 0f) 0 else rawRatingCount

            Alternative(
                id = dto.packageName,
                name = dto.name,
                packageName = dto.packageName,
                license = dto.license,
                repoUrl = dto.repoUrl ?: "",
                fdroidId = dto.fdroidId ?: "",
                iconUrl = dto.iconUrl,
                category = dto.category,
                ratingAvg = ratingAvg,
                ratingCount = ratingCount,
                usabilityRating = usabilityRating,
                privacyRating = privacyRating,
                featuresRating = featuresRating,
                description = dto.description,
                features = dto.features.orEmpty(),
                pros = dto.pros.orEmpty(),
                cons = dto.cons.orEmpty()
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getTarget(packageName: String): Alternative? {
        return try {
            val dto = supabase.postgrest.from("targets")
                .select(
                    columns = Columns.list(
                        "package_name", "name", "description", "category", "icon_url"
                    )
                ) {
                    filter {
                        eq("package_name", packageName)
                    }
                }.decodeSingleOrNull<com.jksalcedo.librefind.data.remote.model.TargetDto>()
                ?: return null

            Alternative(
                id = dto.packageName,
                name = dto.name,
                packageName = dto.packageName,
                license = "Proprietary",
                repoUrl = "",
                fdroidId = "",
                iconUrl = dto.iconUrl,
                category = dto.category ?: "Other",
                description = dto.description ?: ""
            )
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "getTarget failed for $packageName", e)
            null
        }
    }

    @Serializable
    private data class PackageNameDto(@SerialName("package_name") val packageName: String)

    @Serializable
    private data class TargetAlternativesDto(val alternatives: List<String>? = null)

    override suspend fun getProprietaryTargets(): List<String> {
        return try {
            supabase.postgrest.from("targets")
                .select(columns = Columns.list("package_name"))
                .decodeList<PackageNameDto>()
                .map { it.packageName }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun getProprietaryTargetsWithAlternativesCount(): Map<String, Int> {
        return try {
            val allTargets = mutableMapOf<String, Int>()
            var from = 0L
            val pageSize = 1000L
            while (true) {
                val page = supabase.postgrest.from("targets")
                    .select(columns = Columns.list("package_name", "alternatives")) {
                        range(from, from + pageSize - 1)
                    }
                    .decodeList<TargetWithAlternativesDto>()

                page.forEach {
                    allTargets[it.packageName] = it.alternatives?.size ?: 0
                }
                if (page.size < pageSize) break
                from += pageSize
            }
            allTargets
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "Failed to fetch targets with counts", e)
            emptyMap()
        }
    }

    override suspend fun getAllSolutionPackageNames(): List<String> {
        return try {
            val allPackages = mutableListOf<String>()
            var from = 0L
            val pageSize = 1000L
            while (true) {
                val page = supabase.postgrest.from("solutions")
                    .select(columns = Columns.list("package_name")) {
                        range(from, from + pageSize - 1)
                    }
                    .decodeList<PackageNameDto>()
                    .map { it.packageName }

                allPackages.addAll(page)
                if (page.size < pageSize) break
                from += pageSize
            }
            allPackages
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "Failed to fetch solution package names", e)
            emptyList()
        }
    }

    override suspend fun areSolutions(packageNames: List<String>): Set<String> {
        if (packageNames.isEmpty()) return emptySet()

        return try {
            val uniquePackages = packageNames.distinct()
            val foundPackages = mutableSetOf<String>()
            val chunkSize = 200

            uniquePackages.chunked(chunkSize).forEach { chunk ->
                val found = supabase.postgrest.from("solutions")
                    .select(columns = Columns.list("package_name")) {
                        filter { isIn("package_name", chunk) }
                    }
                    .decodeList<PackageNameDto>()
                    .map { it.packageName }

                foundPackages.addAll(found)
            }

            foundPackages
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "Failed bulk solution lookup", e)
            emptySet()
        }
    }

    private suspend fun ensureAuthenticatedUser(): String {
        val existingUser = supabase.auth.currentUserOrNull()
        if (existingUser != null) {
            return existingUser.id
        }
        supabase.auth.signInAnonymously()
        return supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Failed to authenticate session")
    }

    override suspend fun submitAlternative(
        proprietaryPackage: String,
        alternativePackage: String,
        appName: String,
        description: String,
        repoUrl: String,
        fdroidId: String,
        license: String,
        userId: String,
        alternatives: List<String>,
        submissionType: SubmissionType,
        category: String
    ): Result<Unit> = runCatching {
        try {
            // Final repository-level check to prevent race conditions
            val duplicateStatus = checkDuplicateApp(alternativePackage)
            if (duplicateStatus != DuplicateStatus.NONE) {
                return@runCatching
            }

            val submitterId = if (userId.isNotBlank()) userId else ensureAuthenticatedUser()

            val submission = UserSubmissionDto(
                appName = appName,
                appPackage = alternativePackage,
                description = description,
                proprietaryPackage = proprietaryPackage.ifBlank { null },
                repoUrl = repoUrl.ifBlank { null },
                fdroidId = fdroidId.ifBlank { null },
                license = license.ifBlank { null },
                alternatives = alternatives.ifEmpty { null },
                submissionType = submissionType.name,
                submitterId = submitterId,
                category = category.ifBlank { null }
            )
            supabase.postgrest.from("user_submissions").insert(submission)
            Log.d("SupabaseAppRepo", "Submission successful")
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "Submission failed", e)
            throw e
        }
    }

    override suspend fun submitLinkedAlternatives(
        proprietaryPackage: String,
        alternatives: List<String>,
        submitterId: String
    ): Result<Unit> = runCatching {
        try {
            // Check for existing pending linking for this package
            val existing = supabase.postgrest.from("user_linking_submissions")
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("proprietary_package", proprietaryPackage)
                        eq("status", "PENDING")
                    }
                    limit(1)
                }.countOrNull() ?: 0

            if (existing > 0) {
                // If a pending linking already exists, we might still want to allow it if alternatives are different,
                // but for now let's just prevent spamming the same target app.
                return@runCatching
            }

            val actualSubmitterId = if (submitterId.isNotBlank()) submitterId else ensureAuthenticatedUser()

            val submission = UserLinkingSubmissionsDto(
                proprietaryPackage = proprietaryPackage,
                alternatives = alternatives,
                submitterId = actualSubmitterId,
                status = "PENDING",
                rejectionReason = null
            )
            supabase.postgrest.from("user_linking_submissions").insert(submission)
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun updateSubmission(
        id: String,
        proprietaryPackage: String,
        alternativePackage: String,
        appName: String,
        description: String,
        repoUrl: String,
        fdroidId: String,
        license: String,
        alternatives: List<String>,
        category: String,
        originalSubmitterId: String?,
        contributors: List<String>?,
        submissionType: SubmissionType?
    ): Result<Unit> = runCatching {
        try {
            val currentUserId = supabase.auth.currentUserOrNull()?.id
                ?: throw IllegalStateException("Not logged in")

            val isCommunityEdit =
                originalSubmitterId != null && originalSubmitterId != currentUserId

            val newContributors = if (isCommunityEdit) {
                val currentList = contributors ?: emptyList()
                if (!currentList.contains(currentUserId)) {
                    currentList + currentUserId
                } else {
                    currentList
                }
            } else {
                contributors
            }

            val finalSubmitterId = originalSubmitterId ?: currentUserId

            val lastEditedByVal = if (isCommunityEdit) currentUserId else null
            val lastEditedAtVal = if (isCommunityEdit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Instant.now().toString()
                } else {
                    null
                }
            } else null

            val contributorsVal = newContributors?.ifEmpty { null }

            if (submissionType == SubmissionType.LINKING) {
                val updateData = UserLinkingSubmissionsDto(
                    id = id,
                    proprietaryPackage = proprietaryPackage,
                    alternatives = alternatives,
                    submitterId = finalSubmitterId,
                    status = "PENDING",
                    rejectionReason = null,
                    lastEditedBy = lastEditedByVal,
                    lastEditedAt = lastEditedAtVal,
                    contributors = contributorsVal,
                    submissionType = submissionType,
                )
                val result =
                    supabase.postgrest.from("user_linking_submissions").update(updateData) {
                        filter { eq("id", id) }
                        select()
                    }
                val updated = result.decodeList<UserLinkingSubmissionsDto>()
                if (updated.isEmpty()) {
                    supabase.postgrest.from("user_linking_submissions")
                        .insert(updateData.copy(id = null))
                }
            } else {
                val updateData = UserSubmissionDto(
                    id = id,
                    appName = appName,
                    appPackage = alternativePackage,
                    description = description,
                    proprietaryPackage = proprietaryPackage.ifBlank { null },
                    repoUrl = repoUrl.ifBlank { null },
                    fdroidId = fdroidId.ifBlank { null },
                    license = license.ifBlank { null },
                    alternatives = alternatives.ifEmpty { null },
                    submissionType = submissionType?.name,
                    type = submissionType?.name,
                    submitterId = finalSubmitterId,
                    status = "PENDING",
                    rejectionReason = null,
                    category = category.ifBlank { null },
                    lastEditedBy = lastEditedByVal,
                    lastEditedAt = lastEditedAtVal,
                    contributors = contributorsVal
                )

                val result = supabase.postgrest.from("user_submissions").update(updateData) {
                    filter { eq("id", id) }
                    select()
                }

                val updated = result.decodeList<UserSubmissionDto>()
                if (updated.isEmpty()) {
                    // Fallback: Insert as new submission (proposal)
                    supabase.postgrest.from("user_submissions").insert(updateData.copy(id = null))
                }
            }
            Log.d("SupabaseAppRepo", "Update/Upsert successful for ID: $id")
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "Update failed", e)
            throw e
        }
    }

    override suspend fun castMatchVote(
        targetPackage: String,
        solutionPackage: String,
        vote: Int
    ): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Not logged in")

        if (vote == 0) {
            // Remove existing vote
            supabase.postgrest.from("target_solution_votes").delete {
                filter {
                    eq("user_id", userId)
                    eq("target_package", targetPackage)
                    eq("solution_package", solutionPackage)
                }
            }
        } else {
            val matchVote = MatchVoteDto(
                userId = userId,
                targetPackage = targetPackage,
                solutionPackage = solutionPackage,
                vote = vote.coerceIn(-1, 1)
            )
            supabase.postgrest.from("target_solution_votes").upsert(matchVote) {
                onConflict = "user_id,target_package,solution_package"
                defaultToNull = false
            }
        }
    }

    override suspend fun castVote(
        packageName: String,
        voteType: String,
        value: Int
    ): Result<Unit> = runCatching {
        val userId =
            supabase.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Not logged in")

        val vote = UserVoteDto(
            userId = userId,
            packageName = packageName,
            voteType = voteType,
            value = value
        )

        Log.d("SupabaseAppRepo", "castVote: upserting vote")
        try {
            supabase.postgrest.from("user_votes").upsert(vote) {
                onConflict = "user_id,package_name,vote_type"
                defaultToNull = false
            }
            Log.d("SupabaseAppRepo", "castVote: upsert complete")
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "castVote: upsert failed", e)
            throw e
        }

        Log.d("SupabaseAppRepo", "castVote: calling RPC")
        try {
            supabase.postgrest.rpc(
                "vote_for_app",
                VoteForAppParams(packageName, voteType, value)
            )
            Log.d("SupabaseAppRepo", "castVote: RPC complete")
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "castVote: RPC failed", e)
            throw e
        }
    }

    @Serializable
    private data class VoteForAppParams(
        @SerialName("package_name") val packageName: String,
        @SerialName("vote_type") val voteType: String,
        val value: Int
    )

    override suspend fun submitFeedback(
        packageName: String,
        type: String,
        text: String
    ): Result<Unit> = runCatching {
        val userId = ensureAuthenticatedUser()

        val feedback = AppFeedbackDto(
            packageName = packageName,
            feedbackType = type,
            content = text,
            submitterId = userId,
            status = "PENDING"
        )

        supabase.postgrest.from("app_feedback").insert(feedback)
    }

    override suspend fun getUserSubmissions(
        userId: String,
        status: SubmissionStatus?
    ): List<Submission> {
        return try {
            // 1. Fetch standard submissions
            val standardDtos =
                supabase.postgrest.from("user_submissions")
                    .select(
                        columns = Columns.list(
                            "id",
                            "app_name",
                            "app_package",
                            "description",
                            "proprietary_package",
                            "repo_url",
                            "fdroid_id",
                            "license",
                            "submission_type",
                            "type",
                            "status",
                            "submitter_id",
                            "rejection_reason",
                            "created_at",
                            "category",
                            "last_edited_by",
                            "last_edited_at",
                            "contributors",
                            "alternatives",
                            "profile:profiles!fk_submissions_profiles(id, username, reputation_score, badge)"
                        )
                    ) {
                        filter {
                            eq("submitter_id", userId)
                            status?.let { eq("status", it.name) }
                        }
                    }.decodeList<UserSubmissionWithProfileDto>()

            // 2. Fetch linking submissions
            val linkingDtos =
                supabase.postgrest.from("user_linking_submissions")
                    .select(
                        columns = Columns.list(
                            "id",
                            "proprietary_package",
                            "alternatives",
                            "status",
                            "submitter_id",
                            "rejection_reason",
                            "created_at",
                            "last_edited_by",
                            "last_edited_at",
                            "contributors",
                            "profile:profiles!user_linking_submissions_submitter_id_fkey(id, username, reputation_score, badge)"
                        )
                    ) {
                        filter {
                            eq("submitter_id", userId)
                            status?.let { eq("status", it.name) }
                        }
                    }.decodeList<UserLinkingSubmissionWithProfileDto>()

            mapDtosToSubmissions(standardDtos, linkingDtos)

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private var cachedPendingSubmissions: List<Submission>? = null
    private var lastPendingSubmissionsFetchTime: Long = 0
    private val CACHE_DURATION_MS = 10 * 60 * 1000L // 10 minutes

    override suspend fun getAllPendingSubmissions(forceRefresh: Boolean): List<Submission> {
        val currentTime = System.currentTimeMillis()
        if (!forceRefresh && cachedPendingSubmissions != null && (currentTime - lastPendingSubmissionsFetchTime < CACHE_DURATION_MS)) {
            return cachedPendingSubmissions ?: emptyList()
        }

        return try {
            // Fetch standard pending submissions
            val standardDtos =
                supabase.postgrest.from("user_submissions")
                    .select(
                        columns = Columns.list(
                            "id",
                            "app_name",
                            "app_package",
                            "description",
                            "proprietary_package",
                            "repo_url",
                            "fdroid_id",
                            "license",
                            "submission_type",
                            "type",
                            "status",
                            "submitter_id",
                            "rejection_reason",
                            "created_at",
                            "category",
                            "last_edited_by",
                            "last_edited_at",
                            "contributors",
                            "alternatives",
                            "profile:profiles!fk_submissions_profiles(id, username, reputation_score, badge)",
                            // Join editor profile so UI can show username instead of uuid
                            "editor_profile:profiles!last_edited_by(id, username, reputation_score, badge)"
                        )
                    ) {
                        filter { eq("status", "PENDING") }
                        order("last_edited_at", Order.DESCENDING)
                        order("created_at", Order.DESCENDING)
                    }.decodeList<UserSubmissionWithProfileDto>()

            // Fetch linking pending submissions
            val linkingDtos =
                supabase.postgrest.from("user_linking_submissions")
                    .select(
                        columns = Columns.list(
                            "id",
                            "proprietary_package",
                            "alternatives",
                            "status",
                            "submitter_id",
                            "rejection_reason",
                            "created_at",
                            "last_edited_by",
                            "last_edited_at",
                            "contributors",
                            "profile:profiles!user_linking_submissions_submitter_id_fkey(id, username, reputation_score, badge)",
                            "editor_profile:profiles!last_edited_by(id, username, reputation_score, badge)"
                        )
                    ) {
                        filter { eq("status", "PENDING") }
                        order("last_edited_at", Order.DESCENDING)
                        order("created_at", Order.DESCENDING)
                    }.decodeList<UserLinkingSubmissionWithProfileDto>()

            val mapped = mapDtosToSubmissions(standardDtos, linkingDtos)
            cachedPendingSubmissions = mapped
            lastPendingSubmissionsFetchTime = currentTime
            mapped
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override suspend fun getPendingSubmissionsPage(
        page: Int,
        pageSize: Int,
        forceRefresh: Boolean
    ): List<Submission> {
        val all = getAllPendingSubmissions(forceRefresh)
        val from = page * pageSize
        if (from >= all.size) return emptyList()
        val to = minOf(from + pageSize, all.size)
        return all.subList(from, to)
    }



    private fun mapDtosToSubmissions(
        standardDtos: List<UserSubmissionWithProfileDto>,
        linkingDtos: List<UserLinkingSubmissionWithProfileDto>
    ): List<Submission> {
        val standardList = standardDtos.map { dto ->
            Submission(
                id = dto.id ?: "",
                type = (dto.submissionType ?: dto.type)?.let {
                    try {
                        SubmissionType.valueOf(it)
                    } catch (_: Exception) {
                        null
                    }
                } ?: if (!dto.license.isNullOrBlank() || !dto.repoUrl.isNullOrBlank())
                    SubmissionType.NEW_ALTERNATIVE
                else
                    SubmissionType.NEW_PROPRIETARY,
                proprietaryPackages = dto.proprietaryPackage ?: "",
                submittedApp = SubmittedApp(
                    name = dto.appName ?: "Unknown App",
                    packageName = dto.appPackage ?: "",
                    description = dto.description ?: "",
                    repoUrl = dto.repoUrl ?: "",
                    fdroidId = dto.fdroidId ?: "",
                    license = dto.license ?: ""
                ),
                submitterUid = dto.submitterId ?: "",
                submitterUsername = dto.profile?.username ?: "Deleted User",
                submitterReputation = dto.profile?.reputationScore ?: 0,
                submitterBadge = dto.profile?.badge,
                // Parse created_at timestamp or use current time if missing
                submittedAt = dto.createdAt?.let { parseTimestamp(it) }
                    ?: System.currentTimeMillis(),
                status = try {
                    SubmissionStatus.valueOf(dto.status ?: "PENDING")
                } catch (_: Exception) {
                    SubmissionStatus.PENDING
                },
                rejectionReason = dto.rejectionReason,
                category = dto.category,
                linkedAlternatives = dto.alternatives ?: emptyList(),
                // Prefer a human-friendly username for display; fall back to UUID if profile is hidden.
                lastEditedBy = dto.editorProfile?.username ?: dto.lastEditedBy,
                lastEditedAt = dto.lastEditedAt?.let { parseTimestamp(it) },
                contributors = dto.contributors ?: emptyList()
            )
        }

        val linkingList = linkingDtos.map { dto ->
            Submission(
                id = dto.id ?: "",
                type = SubmissionType.LINKING,
                proprietaryPackages = dto.proprietaryPackage ?: "",
                submittedApp = SubmittedApp(
                    // format the name in the UI based on the type.
                    name = "Link ${(dto.alternatives ?: emptyList()).size} Alternatives",
                    packageName = dto.proprietaryPackage ?: "",
                    description = "Linking request for ${dto.proprietaryPackage ?: "unknown"}"
                ),
                submitterUid = dto.submitterId ?: "",
                submitterUsername = dto.profile?.username ?: "Deleted User",
                submitterReputation = dto.profile?.reputationScore ?: 0,
                submitterBadge = dto.profile?.badge,
                submittedAt = dto.createdAt?.let { parseTimestamp(it) }
                    ?: System.currentTimeMillis(),
                status = try {
                    SubmissionStatus.valueOf(dto.status ?: "PENDING")
                } catch (_: Exception) {
                    SubmissionStatus.PENDING
                },
                rejectionReason = dto.rejectionReason,
                linkedAlternatives = dto.alternatives ?: emptyList(),
                lastEditedBy = dto.editorProfile?.username ?: dto.lastEditedBy,
                lastEditedAt = dto.lastEditedAt?.let { parseTimestamp(it) },
                contributors = dto.contributors ?: emptyList()
            )
        }

        return (standardList + linkingList).sortedByDescending { it.submittedAt }
    }

    private fun parseTimestamp(isoString: String): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Instant.parse(isoString).toEpochMilli()
            } else {
                // Regex to truncate fractional seconds to 3 digits
                val truncated = isoString.replace(Regex("(\\.\\d{3})\\d+"), "$1")
                val format =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                format.parse(truncated)?.time ?: System.currentTimeMillis()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            System.currentTimeMillis()
        }
    }

    override suspend fun getUserVote(packageName: String, userId: String): Map<String, Int?> {
        return try {
            val votes = supabase.postgrest.from("user_votes")
                .select(columns = Columns.list("vote_type", "value")) {
                    filter {
                        eq("user_id", userId)
                        eq("package_name", packageName)
                    }
                }.decodeList<UserVoteDto>()

            mapOf(
                "usability" to votes.find { it.voteType == "usability" }?.value,
                "privacy" to votes.find { it.voteType == "privacy" }?.value,
                "features" to votes.find { it.voteType == "features" }?.value
            )
        } catch (e: Exception) {
            e.printStackTrace()
            mapOf(
                "usability" to null,
                "privacy" to null,
                "features" to null
            )
        }
    }

    override suspend fun checkDuplicateApp(packageName: String): DuplicateStatus {
        if (packageName.isBlank()) return DuplicateStatus.NONE

        return try {
            // Check approved FOSS alternatives (solutions table)
            val solutionsCount = supabase.postgrest.from("solutions")
                .select(columns = Columns.list("package_name")) {
                    count(Count.EXACT)
                    filter { eq("package_name", packageName) }
                    limit(1)
                }.countOrNull() ?: 0

            if (solutionsCount > 0) return DuplicateStatus.APPROVED_SOLUTION

            // Check approved proprietary targets (targets table)
            val targetsCount = supabase.postgrest.from("targets")
                .select(columns = Columns.list("package_name")) {
                    count(Count.EXACT)
                    filter { eq("package_name", packageName) }
                    limit(1)
                }.countOrNull() ?: 0

            if (targetsCount > 0) return DuplicateStatus.APPROVED_TARGET

            // Check pending submissions (user_submissions table)
            val pendingCount = supabase.postgrest.from("user_submissions")
                .select(columns = Columns.list("app_package")) {
                    count(Count.EXACT)
                    filter {
                        eq("app_package", packageName)
                        neq("status", "REJECTED")
                    }
                    limit(1)
                }.countOrNull() ?: 0

            if (pendingCount > 0) DuplicateStatus.PENDING else DuplicateStatus.NONE
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "checkDuplicateApp failed for $packageName", e)
            DuplicateStatus.NONE
        }
    }

    override suspend fun searchSolutions(query: String, limit: Int): List<Alternative> {
        if (query.isBlank()) return emptyList()

        return try {
            // Escape SQL LIKE wildcards in user input
            val sanitizedQuery = query
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")

            val solutions = supabase.postgrest.from("solutions")
                .select(
                    columns = Columns.list(
                        "package_name", "name", "license", "repo_url", "fdroid_id",
                        "icon_url", "description", "features", "pros", "cons",
                        "rating_usability", "rating_privacy", "rating_features", "vote_count"
                    )
                ) {
                    filter {
                        or {
                            ilike("name", "%$sanitizedQuery%")
                            ilike("package_name", "%$sanitizedQuery%")
                        }
                    }
                    limit(limit.toLong())
                }.decodeList<SolutionDto>()

            solutions.map { dto ->
                val usabilityRating = dto.ratingUsability ?: 0f
                val privacyRating = dto.ratingPrivacy ?: 0f
                val featuresRating = dto.ratingFeatures ?: 0f
                val rawRatingCount = dto.voteCount ?: 0

                val ratingAvg = if (rawRatingCount > 0) {
                    val sum = usabilityRating + privacyRating + featuresRating
                    val nonZeroCount =
                        listOf(usabilityRating, privacyRating, featuresRating).count { it > 0 }
                    if (nonZeroCount > 0) sum / nonZeroCount else 0f
                } else 0f

                val ratingCount = if (ratingAvg == 0f) 0 else rawRatingCount

                Alternative(
                    id = dto.packageName,
                    name = dto.name,
                    packageName = dto.packageName,
                    license = dto.license,
                    repoUrl = dto.repoUrl ?: "",
                    fdroidId = dto.fdroidId ?: "",
                    iconUrl = dto.iconUrl,
                    ratingAvg = ratingAvg,
                    ratingCount = ratingCount,
                    usabilityRating = usabilityRating,
                    privacyRating = privacyRating,
                    featuresRating = featuresRating,
                    description = dto.description,
                    features = dto.features.orEmpty(),
                    pros = dto.pros.orEmpty(),
                    cons = dto.cons.orEmpty()
                )
            }
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "searchSolutions failed", e)
            emptyList()
        }
    }

    override suspend fun searchProprietary(query: String, limit: Int): List<Alternative> {
        if (query.isBlank()) return emptyList()

        return try {
            val sanitizedQuery = query
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")

            val targets = supabase.postgrest.from("targets")
                .select(
                    columns = Columns.list(
                        "package_name",
                        "name",
                        "description",
                        "category",
                        "icon_url"
                    )
                ) {
                    filter {
                        or {
                            ilike("name", "%$sanitizedQuery%")
                            ilike("package_name", "%$sanitizedQuery%")
                        }
                    }
                    limit(limit.toLong())
                }.decodeList<com.jksalcedo.librefind.data.remote.model.TargetDto>()

            targets.map { dto ->
                Alternative(
                    id = dto.packageName,
                    packageName = dto.packageName,
                    name = dto.name,
                    license = "Proprietary",
                    repoUrl = "",
                    fdroidId = "",
                    iconUrl = dto.iconUrl,
                    category = dto.category ?: "Other",
                    description = dto.description ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "searchProprietary failed", e)
            emptyList()
        }
    }

    override suspend fun getAlternativesCount(packageName: String): Int {
        return try {
            val target = supabase.postgrest.from("targets")
                .select(columns = Columns.list("alternatives")) {
                    filter {
                        eq("package_name", packageName)
                    }
                }.decodeSingleOrNull<TargetAlternativesDto>() ?: return 0

            target.alternatives?.size ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    override suspend fun getSiblingAlternatives(packageName: String): List<Alternative>? {
        return try {
            // 1. Look up this solution's category
            @Serializable
            data class CategoryDto(val category: String = "Other")

            val categoryDto = supabase.postgrest.from("solutions")
                .select(columns = Columns.list("category")) {
                    filter { eq("package_name", packageName) }
                    limit(1)
                }.decodeSingleOrNull<CategoryDto>() ?: return null

            // Return null (not empty) so callers can distinguish "category unset" from "no peers"
            if (categoryDto.category == "Other") return null

            // 2. Fetch all solutions in the same category, excluding self
            val siblings = supabase.postgrest.from("solutions")
                .select(
                    columns = Columns.list(
                        "package_name", "name", "license", "repo_url", "fdroid_id",
                        "icon_url", "description", "features", "pros", "cons",
                        "rating_usability", "rating_privacy", "rating_features", "vote_count",
                        "category"
                    )
                ) {
                    filter {
                        eq("category", categoryDto.category)
                        neq("package_name", packageName)
                    }
                }.decodeList<SolutionDto>()

            siblings.map { dto ->
                val usabilityRating = dto.ratingUsability ?: 0f
                val privacyRating = dto.ratingPrivacy ?: 0f
                val featuresRating = dto.ratingFeatures ?: 0f
                val rawRatingCount = dto.voteCount ?: 0

                val ratingAvg = if (rawRatingCount > 0) {
                    val sum = usabilityRating + privacyRating + featuresRating
                    val nonZeroCount =
                        listOf(usabilityRating, privacyRating, featuresRating).count { it > 0 }
                    if (nonZeroCount > 0) sum / nonZeroCount else 0f
                } else 0f

                val ratingCount = if (ratingAvg == 0f) 0 else rawRatingCount

                Alternative(
                    id = dto.packageName,
                    name = dto.name,
                    packageName = dto.packageName,
                    license = dto.license,
                    repoUrl = dto.repoUrl ?: "",
                    fdroidId = dto.fdroidId ?: "",
                    iconUrl = dto.iconUrl,
                    category = dto.category,
                    ratingAvg = ratingAvg,
                    ratingCount = ratingCount,
                    usabilityRating = usabilityRating,
                    privacyRating = privacyRating,
                    featuresRating = featuresRating,
                    description = dto.description,
                    features = dto.features.orEmpty(),
                    pros = dto.pros.orEmpty(),
                    cons = dto.cons.orEmpty()
                )
            }.sortedByDescending { it.ratingAvg }
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "getSiblingAlternatives failed", e)
            null
        }
    }

    @Serializable
    private data class UserSubmissionWithProfileDto(
        val id: String? = null,
        @SerialName("app_name") val appName: String? = null,
        @SerialName("app_package") val appPackage: String? = null,
        val description: String? = "",
        @SerialName("proprietary_package") val proprietaryPackage: String? = null,
        @SerialName("repo_url") val repoUrl: String? = null,
        @SerialName("fdroid_id") val fdroidId: String? = null,
        val license: String? = null,
        @SerialName("submission_type") val submissionType: String? = null,
        val type: String? = null,
        val status: String? = null,
        @SerialName("submitter_id") val submitterId: String? = null,
        @SerialName("rejection_reason") val rejectionReason: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("last_edited_by") val lastEditedBy: String? = null,
        @SerialName("last_edited_at") val lastEditedAt: String? = null,
        val contributors: List<String>? = null,
        val category: String? = null,
        val alternatives: List<String>? = null,
        @SerialName("profile") val profile: ProfileDto? = null,
        @SerialName("editor_profile") val editorProfile: ProfileDto? = null
    )

    @Serializable
    private data class UserLinkingSubmissionWithProfileDto(
        val id: String? = null,
        @SerialName("proprietary_package") val proprietaryPackage: String? = null,
        val alternatives: List<String>? = emptyList(),
        val status: String? = null,
        @SerialName("submitter_id") val submitterId: String? = null,
        @SerialName("rejection_reason") val rejectionReason: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("last_edited_by") val lastEditedBy: String? = null,
        @SerialName("last_edited_at") val lastEditedAt: String? = null,
        val contributors: List<String>? = null,
        @SerialName("profile") val profile: ProfileDto? = null,
        @SerialName("editor_profile") val editorProfile: ProfileDto? = null
    )

    override suspend fun submitReport(
        title: String,
        description: String,
        type: String,
        priority: String,
        userId: String
    ): Result<Unit> = runCatching {
        val submitterId = if (userId.isNotBlank()) userId else ensureAuthenticatedUser()
        val report = UserReportDto(
            title = title,
            description = description,
            reportType = type,
            priority = priority,
            submitterId = submitterId
        )
        supabase.postgrest.from("user_reports").insert(report)
    }

    override suspend fun getMyReports(userId: String): List<Report> {
        return try {
            val result = supabase.postgrest.from("user_reports")
                .select(
                    columns = Columns.list(
                        "id", "title", "description", "report_type",
                        "status", "priority", "submitter_id",
                        "admin_response", "resolved_at", "created_at",
                        "profile:profiles!user_reports_submitter_id_fkey(id, username)"
                    )
                ) {
                    filter { eq("submitter_id", userId) }
                    order("created_at", Order.DESCENDING)
                }

            result.decodeList<UserReportWithProfileDto>().map { dto ->
                Report(
                    id = dto.id ?: "",
                    title = dto.title,
                    description = dto.description,
                    type = try {
                        ReportType.valueOf(dto.reportType)
                    } catch (_: Exception) {
                        ReportType.OTHER
                    },
                    status = try {
                        ReportStatus.valueOf(dto.status)
                    } catch (_: Exception) {
                        ReportStatus.OPEN
                    },
                    priority = try {
                        ReportPriority.valueOf(dto.priority)
                    } catch (_: Exception) {
                        ReportPriority.LOW
                    },
                    submitterUid = dto.submitterId,
                    submitterUsername = dto.profile?.username ?: "Deleted User",
                    adminResponse = dto.adminResponse
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    @Serializable
    private data class UserReportWithProfileDto(
        val id: String? = null,
        val title: String,
        val description: String,
        @SerialName("report_type") val reportType: String,
        val status: String = "OPEN",
        val priority: String = "LOW",
        @SerialName("submitter_id") val submitterId: String,
        @SerialName("admin_response") val adminResponse: String? = null,
        @SerialName("resolved_at") val resolvedAt: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("profile") val profile: ProfileDto? = null
    )

    override suspend fun getPendingSubmissionPackages(): Set<String> {
        return try {
            val result = supabase.postgrest.from("user_submissions")
                .select(columns = Columns.list("app_package", "proprietary_package")) {
                    filter { eq("status", "PENDING") }
                }
            val submissions = result.decodeList<PendingPackageDto>()
            val packages = mutableSetOf<String>()
            submissions.forEach { dto ->
                packages.add(dto.appPackage)
                dto.proprietaryPackage?.let { packages.add(it) }
            }
            packages
        } catch (_: Exception) {
            emptySet()
        }
    }

    override suspend fun getSubmissionById(id: String): Submission? {
        cachedPendingSubmissions?.find { it.id == id }?.let { return it }

        if (cachedPendingSubmissions == null) {
            try {
                val allPending = getAllPendingSubmissions()
                allPending.find { it.id == id }?.let { return it }
            } catch (e: Exception) {
                Log.e("SupabaseAppRepo", "Failed to fetch pending submissions for getSubmissionById", e)
            }
        }

        return try {
            val standardDtos = supabase.postgrest.from("user_submissions")
                .select(
                    columns = Columns.list(
                        "id", "app_name", "app_package", "description", "proprietary_package",
                        "repo_url", "fdroid_id", "license", "submission_type", "type", "status",
                        "submitter_id", "rejection_reason", "created_at", "category",
                        "last_edited_by", "last_edited_at", "contributors", "alternatives",
                        "profile:profiles!fk_submissions_profiles(id, username, reputation_score, badge)",
                        "editor_profile:profiles!last_edited_by(id, username, reputation_score, badge)"
                    )
                ) {
                    filter { eq("id", id) }
                    limit(1)
                }.decodeList<UserSubmissionWithProfileDto>()

            if (standardDtos.isNotEmpty()) {
                return mapDtosToSubmissions(standardDtos, emptyList()).firstOrNull()
            }

            val linkingDtos = supabase.postgrest.from("user_linking_submissions")
                .select(
                    columns = Columns.list(
                        "id", "proprietary_package", "alternatives", "status", "submitter_id",
                        "rejection_reason", "created_at", "last_edited_by", "last_edited_at",
                        "contributors",
                        "profile:profiles!user_linking_submissions_submitter_id_fkey(id, username, reputation_score, badge)",
                        "editor_profile:profiles!last_edited_by(id, username, reputation_score, badge)"
                    )
                ) {
                    filter { eq("id", id) }
                    limit(1)
                }.decodeList<UserLinkingSubmissionWithProfileDto>()

            mapDtosToSubmissions(emptyList(), linkingDtos).firstOrNull()
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "getSubmissionById DB fallback failed for ID: $id", e)
            null
        }
    }

    override suspend fun approveSubmission(id: String, type: SubmissionType): Result<Unit> =
        runCatching {
            val table =
                if (type == SubmissionType.LINKING) "user_linking_submissions" else "user_submissions"
            supabase.postgrest.from(table).update(mapOf("status" to "APPROVED")) {
                filter { eq("id", id) }
            }
            lastPendingSubmissionsFetchTime = 0 // Invalidate cache
        }

    override suspend fun rejectSubmission(
        id: String,
        type: SubmissionType,
        reason: String
    ): Result<Unit> = runCatching {
        val table =
            if (type == SubmissionType.LINKING) "user_linking_submissions" else "user_submissions"
        supabase.postgrest.from(table)
            .update(mapOf("status" to "REJECTED", "rejection_reason" to reason)) {
                filter { eq("id", id) }
            }
        lastPendingSubmissionsFetchTime = 0 // Invalidate cache
    }

    @Serializable
    private data class PendingPackageDto(
        @SerialName("app_package") val appPackage: String,
        @SerialName("proprietary_package") val proprietaryPackage: String? = null
    )

    @Serializable
    private data class TargetWithAlternativesDto(
        @SerialName("package_name") val packageName: String,
        val alternatives: List<String>? = null
    )

    override suspend fun submitScanStats(
        deviceId: String,
        fossCount: Int,
        proprietaryCount: Int,
        unknownCount: Int,
        pwaCount: Int,
        appVersion: String?
    ): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id

        val stats = AppScanStatsDto(
            deviceId = deviceId,
            userId = userId,
            fossCount = fossCount,
            proprietaryCount = proprietaryCount,
            unknownCount = unknownCount,
            totalApps = fossCount + proprietaryCount + unknownCount,
            appVersion = appVersion
        )

        supabase.postgrest.from("app_scan_stats").upsert(stats) {
            onConflict = "device_id"
            defaultToNull = false
        }
        Log.d("SupabaseAppRepo", "Scan stats submitted for device: $deviceId")
    }

    override suspend fun submitAppReport(
        packageName: String,
        issueType: String,
        description: String
    ): Result<Unit> = runCatching {
        val userId = ensureAuthenticatedUser()

        val report = AppReport(
            userId = userId,
            packageName = packageName,
            issueType = issueType,
            description = description
        )

        supabase.postgrest.from("app_reports").insert(report)
    }

    override suspend fun submitCorrection(
        packageName: String,
        correctionType: String,
        correctionValue: String,
        description: String
    ): Result<Unit> = runCatching {
        val userId = ensureAuthenticatedUser()

        val correction = AppCorrectionDto(
            userId = userId,
            packageName = packageName,
            correctionType = correctionType,
            correctionValue = correctionValue,
            description = description
        )

        supabase.postgrest.from("app_corrections").insert(correction)
    }

    @Serializable
    private data class AppCorrectionDto(
        @SerialName("user_id") val userId: String,
        @SerialName("package_name") val packageName: String,
        @SerialName("correction_type") val correctionType: String,
        @SerialName("correction_value") val correctionValue: String,
        @SerialName("description") val description: String,
        @SerialName("status") val status: String = "PENDING"
    )

    override suspend fun castSubmissionVote(
        submissionId: String,
        submissionTable: String,
        vote: Int,
        reason: String?,
        reasonDetail: String?
    ): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Not logged in")

        if (vote == 0) {
            supabase.postgrest.from("submission_votes").delete {
                filter {
                    eq("submission_id", submissionId)
                    eq("user_id", userId)
                }
            }
        } else {
            val voteDto = SubmissionVoteDto(
                submissionId = submissionId,
                submissionTable = submissionTable,
                userId = userId,
                vote = vote.coerceIn(-1, 1),
                reason = reason,
                reasonDetail = reasonDetail
            )
            supabase.postgrest.from("submission_votes").upsert(voteDto) {
                onConflict = "submission_id,user_id"
                defaultToNull = false
            }
        }
        lastVoteCountsFetchTime = 0 // Invalidate cache
    }

    private var cachedVoteCounts: Map<String, SubmissionVoteAggregate>? = null
    private var lastVoteCountsFetchTime: Long = 0

    override suspend fun getSubmissionVoteCounts(
        submissionIds: List<String>,
        forceRefresh: Boolean
    ): Map<String, SubmissionVoteAggregate> {
        val currentTime = System.currentTimeMillis()
        if (!forceRefresh && cachedVoteCounts != null && (currentTime - lastVoteCountsFetchTime < CACHE_DURATION_MS)) {
            return cachedVoteCounts ?: emptyMap()
        }

        if (submissionIds.isEmpty()) return emptyMap()
        return try {
            val currentUserId = supabase.auth.currentUserOrNull()?.id

            @Serializable
            data class VoteRow(
                @SerialName("submission_id") val submissionId: String,
                @SerialName("user_id") val userId: String,
                val vote: Int,
                val reason: String? = null,
                @SerialName("reason_detail") val reasonDetail: String? = null
            )

            val allRows = mutableListOf<VoteRow>()
            for (chunk in submissionIds.chunked(50)) {
                val rows = supabase.postgrest.from("submission_votes")
                    .select(
                        columns = Columns.list(
                            "submission_id", "user_id", "vote", "reason", "reason_detail"
                        )
                    ) {
                        filter { isIn("submission_id", chunk) }
                    }.decodeList<VoteRow>()
                allRows.addAll(rows)
            }

            val grouped = allRows.groupBy { it.submissionId }
            val result = submissionIds.associateWith { id ->
                val group = grouped[id] ?: emptyList()
                val userRow = group.firstOrNull { it.userId == currentUserId }
                SubmissionVoteAggregate(
                    upvotes = group.count { it.vote == 1 },
                    downvotes = group.count { it.vote == -1 },
                    userVote = userRow?.vote,
                    userReason = userRow?.reason,
                    userReasonDetail = userRow?.reasonDetail
                )
            }
            cachedVoteCounts = result
            lastVoteCountsFetchTime = currentTime
            result
        } catch (e: Exception) {
            Log.e("SupabaseAppRepo", "getSubmissionVoteCounts failed", e)
            emptyMap()
        }
    }

    override suspend fun submitSigningKeyVote(
        packageName: String,
        appLabel: String,
        sha256Digest: String
    ): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Not logged in")
        val dto = SigningKeyVoteDto(
            packageName = packageName,
            appLabel = appLabel,
            sha256Digest = sha256Digest,
            submitterId = userId
        )
        supabase.postgrest.from("signing_key_votes").upsert(dto) {
            onConflict = "package_name,submitter_id"
            defaultToNull = false
        }
    }

    private var cachedKeyVotes: List<SigningKeyVote>? = null
    private var lastKeyVotesFetchTime: Long = 0
    private val KEY_VOTES_CACHE_MS = 5 * 60 * 1000L


    override suspend fun getSigningKeyVotes(forceRefresh: Boolean): List<SigningKeyVote> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedKeyVotes != null && (now - lastKeyVotesFetchTime < KEY_VOTES_CACHE_MS)) {
            return cachedKeyVotes ?: emptyList()
        }
        val userId = supabase.auth.currentUserOrNull()?.id
        val rows = supabase.postgrest.from("signing_key_votes")
            .select(
                columns = Columns.list(
                    "id", "package_name", "app_label", "sha256_digest",
                    "submitter_id", "created_at",
                    "profile:profiles!submitter_id(id, username, reputation_score, badge)"
                )
            ) {
                order("created_at", order = Order.DESCENDING)
            }.decodeList<SigningKeyVoteWithProfileDto>()

        val deduped = rows
            .groupBy { it.packageName to it.sha256Digest }
            .map { (_, group) ->
                val first = group.first()
                SigningKeyVote(
                    id = first.id ?: "",
                    packageName = first.packageName,
                    appLabel = first.appLabel ?: "",
                    sha256Digest = first.sha256Digest,
                    submitterUid = first.submitterId,
                    submitterUsername = first.profile?.username ?: first.submitterId.take(8),
                    submitterReputation = first.profile?.reputationScore ?: 0,
                    submitterBadge = first.profile?.badge,
                    endorseCount = group.size,
                    hasUserEndorsed = userId != null && group.any { it.submitterId == userId },
                    submittedAt = first.createdAt?.let { parseTimestamp(it) } ?: 0L
                )
            }
        cachedKeyVotes = deduped
        lastKeyVotesFetchTime = now
        return deduped
    }

    override suspend fun getSigningKeyVoteCount(packageName: String, sha256Digest: String): Int {
        return try {
            supabase.postgrest.from("signing_key_votes")
                .select(columns = Columns.list("id")) {
                    count(Count.EXACT)
                    filter {
                        eq("package_name", packageName)
                        eq("sha256_digest", sha256Digest)
                    }
                }.countOrNull()?.toInt() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    override suspend fun hasUserSubmittedKeyVote(packageName: String): Boolean {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return false
        return try {
            val count = supabase.postgrest.from("signing_key_votes")
                .select(columns = Columns.list("id")) {
                    count(Count.EXACT)
                    filter {
                        eq("package_name", packageName)
                        eq("submitter_id", userId)
                    }
                    limit(1)
                }.countOrNull() ?: 0
            count > 0
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun endorseSigningKeyVote(
        packageName: String,
        sha256Digest: String
    ): Result<Unit> = runCatching {
        submitSigningKeyVote(packageName, "", sha256Digest).getOrThrow()
    }

    override suspend fun getComments(targetId: String): List<com.jksalcedo.librefind.domain.model.Comment> {
        return try {
            val dtos = supabase.postgrest.from("comments")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("*, profile:profiles(*)")) {
                    filter {
                        eq("target_id", targetId)
                    }
                    order("created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }.decodeList<com.jksalcedo.librefind.data.remote.model.CommentWithProfileDto>()
                
            dtos.map { dto ->
                com.jksalcedo.librefind.domain.model.Comment(
                    id = dto.id,
                    targetId = dto.targetId,
                    userId = dto.userId,
                    username = dto.profile?.username,
                    avatarUrl = dto.profile?.avatarUrl,
                    badge = dto.profile?.badge,
                    content = dto.content,
                    createdAt = try {
                        java.time.Instant.parse(dto.createdAt).toEpochMilli()
                    } catch (e: Exception) {
                        0L
                    }
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("SupabaseAppRepo", "Failed to fetch comments", e)
            emptyList()
        }
    }

    override suspend fun submitComment(targetId: String, content: String): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Not logged in")
            
        val commentDto = com.jksalcedo.librefind.data.remote.model.CommentDto(
            targetId = targetId,
            userId = userId,
            content = content
        )
        
        supabase.postgrest.from("comments").insert(commentDto)
    }
}
