package com.jksalcedo.librefind.domain.repository

import com.jksalcedo.librefind.domain.model.Alternative
import com.jksalcedo.librefind.domain.model.DuplicateStatus
import com.jksalcedo.librefind.domain.model.Report
import com.jksalcedo.librefind.domain.model.SigningKeyVote
import com.jksalcedo.librefind.domain.model.Submission
import com.jksalcedo.librefind.domain.model.SubmissionType

interface AppRepository {

    suspend fun areProprietary(packageNames: List<String>): Map<String, Boolean>
    suspend fun isProprietary(packageName: String): Boolean
    suspend fun isSolution(packageName: String): Boolean
    suspend fun getAlternatives(packageName: String): List<Alternative>
    suspend fun getAlternative(packageName: String): Alternative?
    suspend fun getTarget(packageName: String): Alternative?
    suspend fun getProprietaryTargets(): List<String>


    /**
     * Bulk fetch: returns all targets with their alternatives count in a single query.
     */
    suspend fun getProprietaryTargetsWithAlternativesCount(): Map<String, Int>

    /**
     * Bulk fetch: returns all solution package names in a single query.
     * Used during cache refresh to avoid per-app isSolution() calls.
     */
    suspend fun getAllSolutionPackageNames(): List<String>

    /**
     * Bulk check: returns a set of package names that are known solutions.
     * Used during classification to avoid per-app network calls.
     */
    suspend fun areSolutions(packageNames: List<String>): Set<String>

    suspend fun submitReport(
        title: String,
        description: String,
        type: String,
        priority: String,
        userId: String
    ): Result<Unit>

    suspend fun getMyReports(userId: String): List<Report>

    suspend fun submitAlternative(
        proprietaryPackage: String,
        alternativePackage: String,
        appName: String,
        description: String,
        repoUrl: String,
        fdroidId: String,
        license: String,
        userId: String,
        alternatives: List<String> = emptyList(),
        submissionType: SubmissionType,
        category: String = ""
    ): Result<Unit>

    suspend fun submitLinkedAlternatives(
        proprietaryPackage: String,
        alternatives: List<String>,
        submitterId: String
    ): Result<Unit>

    suspend fun updateSubmission(
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
        submissionType: SubmissionType? = null
        ): Result<Unit>

    suspend fun castVote(
        packageName: String,
        voteType: String, // 'privacy' or 'usability'
        value: Int // 1-5 star rating
    ): Result<Unit>

    /**
     * Cast a match vote on a target ↔ solution pairing.
     * [vote] must be +1 (upvote) or -1 (downvote).
     * Passing 0 removes an existing vote.
     */
    suspend fun castMatchVote(
        targetPackage: String,
        solutionPackage: String,
        vote: Int
    ): Result<Unit>

    suspend fun getUserSubmissions(
        userId: String,
        status: com.jksalcedo.librefind.domain.model.SubmissionStatus? = null
    ): List<Submission>
    suspend fun getAllPendingSubmissions(forceRefresh: Boolean = false): List<Submission>
    suspend fun getPendingSubmissionsPage(
        page: Int,
        pageSize: Int = 50,
        forceRefresh: Boolean = false
    ): List<Submission>

    suspend fun submitFeedback(
        packageName: String,
        type: String, // 'PRO' or 'CON'
        text: String
    ): Result<Unit>

    suspend fun checkDuplicateApp(packageName: String): DuplicateStatus

    suspend fun getUserVote(packageName: String, userId: String): Map<String, Int?>

    suspend fun searchSolutions(query: String, limit: Int = 20): List<Alternative>
    suspend fun searchProprietary(query: String, limit: Int = 20): List<Alternative>

    suspend fun getAlternativesCount(packageName: String): Int

    /**
     * Returns other FOSS apps in the same category as [packageName], excluding itself.
     * Returns null if the app has no meaningful category set (i.e. "Other"),
     * as opposed to an empty list which means the category is set but has no peers yet.
     */
    suspend fun getSiblingAlternatives(packageName: String): List<Alternative>?

    suspend fun getPendingSubmissionPackages(): Set<String>

    suspend fun approveSubmission(id: String, type: SubmissionType): Result<Unit>
    suspend fun rejectSubmission(id: String, type: SubmissionType, reason: String): Result<Unit>

    suspend fun submitScanStats(
        deviceId: String,
        fossCount: Int,
        proprietaryCount: Int,
        unknownCount: Int,
        pwaCount: Int = 0,
        appVersion: String? = null
    ): Result<Unit>

    suspend fun submitAppReport(
        packageName: String,
        issueType: String,
        description: String
    ): Result<Unit>

    suspend fun submitCorrection(
        packageName: String,
        correctionType: String,
        correctionValue: String,
        description: String
    ): Result<Unit>

    suspend fun castSubmissionVote(
        submissionId: String,
        submissionTable: String,
        vote: Int,
        reason: String? = null,
        reasonDetail: String? = null
    ): Result<Unit>

    suspend fun getSubmissionVoteCounts(
        submissionIds: List<String>,
        forceRefresh: Boolean = false
    ): Map<String, com.jksalcedo.librefind.data.remote.model.SubmissionVoteAggregate>

    suspend fun getComments(targetId: String): List<com.jksalcedo.librefind.domain.model.Comment>
    suspend fun submitComment(targetId: String, content: String): Result<Unit>

    suspend fun submitSigningKeyVote(
        packageName: String,
        appLabel: String,
        sha256Digest: String
    ): Result<Unit>

    suspend fun getSigningKeyVotes(forceRefresh: Boolean = false): List<SigningKeyVote>

    suspend fun getSigningKeyVoteCount(packageName: String, sha256Digest: String): Int

    suspend fun hasUserSubmittedKeyVote(packageName: String): Boolean

    suspend fun endorseSigningKeyVote(packageName: String, sha256Digest: String): Result<Unit>
}
