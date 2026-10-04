package com.abridgefs.app

/**
 * Exclusive construction permission for one Project + Repository + Branch.
 *
 * API Profiles identify connection resources; AI Members identify collaborators.
 * The construction holder is always an AI Member, never an API Profile.
 */
data class ConstructionLock(
    val projectId: String,
    val repository: String,
    val branch: String,
    val holderAiMemberId: String? = null,
    val acquiredAt: Long? = null
) {
    val isFree: Boolean
        get() = holderAiMemberId.isNullOrBlank()

    fun heldBy(aiMemberId: String): Boolean =
        aiMemberId.isNotBlank() && holderAiMemberId == aiMemberId
}

/**
 * Project-scoped construction lock boundary.
 *
 * The lock is deliberately kept separate from GitHub credentials and API
 * profiles. A project may have multiple conversations and AI members, while
 * one Repository/Branch can have at most one construction holder.
 */
class ConstructionLockStore(private val context: android.content.Context) {
    private val prefs = context.getSharedPreferences("construction_locks", android.content.Context.MODE_PRIVATE)

    @Synchronized
    fun get(project: BridgeProject, repository: String? = project.github.repository, branch: String? = project.github.branch): ConstructionLock? {
        val repo = repository?.trim().orEmpty()
        val ref = branch?.trim().orEmpty()
        if (repo.isBlank() || ref.isBlank()) return null
        val key = key(project.id, repo, ref)
        val holder = prefs.getString("${key}_holder", null)
        val acquiredAt = if (prefs.contains("${key}_time")) prefs.getLong("${key}_time", 0L) else null
        return ConstructionLock(project.id, repo, ref, holder, acquiredAt)
    }

    @Synchronized
    fun acquire(project: BridgeProject, aiMemberId: String): ConstructionLock {
        require(aiMemberId.isNotBlank()) { "AI Member 未指定" }
        require(project.github.writeEnabled) { "当前项目未允许 GitHub 修改" }
        val repo = project.github.repository?.trim().orEmpty()
        val branch = project.github.branch?.trim().orEmpty()
        require(repo.isNotBlank()) { "GitHub Repository 未配置" }
        require(branch.isNotBlank()) { "GitHub Branch 未配置" }

        val members = project.aiMembers.map { it.id }
        require(aiMemberId in members) { "AI Member 不属于当前项目" }

        val current = get(project, repo, branch)
        if (current != null && !current.isFree && !current.heldBy(aiMemberId)) {
            error("当前 Repository / Branch 已由其他 AI 持有施工权")
        }
        if (current?.heldBy(aiMemberId) == true) return current

        val now = System.currentTimeMillis()
        val lock = ConstructionLock(project.id, repo, branch, aiMemberId, now)
        val key = key(project.id, repo, branch)
        prefs.edit()
            .putString("${key}_holder", aiMemberId)
            .putLong("${key}_time", now)
            .apply()
        return lock
    }

    @Synchronized
    fun release(project: BridgeProject, aiMemberId: String) {
        val lock = get(project) ?: return
        require(lock.heldBy(aiMemberId)) { "当前 AI 不持有施工权" }
        val key = key(lock.projectId, lock.repository, lock.branch)
        prefs.edit().remove("${key}_holder").remove("${key}_time").apply()
    }

    @Synchronized
    fun transfer(project: BridgeProject, fromAiMemberId: String, toAiMemberId: String): ConstructionLock {
        require(toAiMemberId.isNotBlank()) { "目标 AI Member 未指定" }
        require(toAiMemberId in project.aiMembers.map { it.id }) { "目标 AI Member 不属于当前项目" }
        val current = get(project) ?: error("当前没有施工权")
        require(current.heldBy(fromAiMemberId)) { "当前 AI 不持有施工权" }
        require(fromAiMemberId != toAiMemberId) { "不能转移给同一个 AI" }

        val now = System.currentTimeMillis()
        val key = key(current.projectId, current.repository, current.branch)
        prefs.edit()
            .putString("${key}_holder", toAiMemberId)
            .putLong("${key}_time", now)
            .apply()
        return current.copy(holderAiMemberId = toAiMemberId, acquiredAt = now)
    }

    @Synchronized
    fun requireHolder(project: BridgeProject, aiMemberId: String): ConstructionLock {
        val lock = get(project) ?: error("当前 Repository / Branch 没有施工权")
        check(lock.heldBy(aiMemberId)) { "当前 AI 未持有 Repository / Branch 施工权" }
        return lock
    }

    @Synchronized
    fun clearProject(projectId: String) {
        val prefix = "lock_${safe(projectId)}_"
        prefs.all.keys
            .filter { it.startsWith(prefix) }
            .forEach { key ->
                prefs.edit().remove(key).apply()
            }
    }

    private fun key(projectId: String, repository: String, branch: String): String =
        "lock_" + safe(projectId) + "_" + safe(repository) + "_" + safe(branch)

    private fun safe(value: String): String =
        value.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
}
