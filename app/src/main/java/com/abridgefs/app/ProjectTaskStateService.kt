package com.abridgefs.app

import android.content.Context

/**
 * Owns the lifecycle state of the currently selected Project task.
 * Execution and AI continuation remain outside this class.
 */
class ProjectTaskStateService(private val context: Context) {
    private val store = BridgeProjectStore(context)

    fun start(projectId: String, taskId: String, maxContinuations: Int = 10): BridgeProject {
        val project = requireProject(projectId)
        val task = project.tasks.firstOrNull { it.id == taskId }
            ?: error("任务不存在：$taskId")
        require(!task.completed) { "已完成任务不能重新启动" }

        val limit = maxContinuations.coerceIn(0, 50)
        project.tasks.forEach {
            if (it.id != taskId && it.status == "RUNNING") it.status = "WAITING"
        }
        task.status = "RUNNING"
        task.maxContinuations = limit
        project.taskState = BridgeTaskExecutionState(
            activeTaskId = taskId,
            status = "RUNNING",
            continuationCount = 0,
            maxContinuations = limit
        )
        store.save(listOf(project))
        return project
    }

    fun pause(projectId: String): BridgeProject {
        val project = requireProject(projectId)
        project.taskState.status = "WAITING"
        project.taskState.activeTaskId?.let { id ->
            project.tasks.firstOrNull { it.id == id }?.let {
                if (!it.completed) it.status = "WAITING"
            }
        }
        releaseLock(project)
        store.save(listOf(project))
        return project
    }

    fun complete(projectId: String): BridgeProject {
        val project = requireProject(projectId)
        project.taskState.status = "COMPLETED"
        project.taskState.activeTaskId?.let { id ->
            project.tasks.firstOrNull { it.id == id }?.let {
                it.completed = true
                it.status = "COMPLETED"
            }
        }
        releaseLock(project)
        store.save(listOf(project))
        return project
    }

    fun clear(projectId: String): BridgeProject {
        val project = requireProject(projectId)
        project.taskState = BridgeTaskExecutionState()
        project.tasks.filter { it.status == "RUNNING" }.forEach { it.status = "WAITING" }
        releaseLock(project)
        store.save(listOf(project))
        return project
    }

    private fun requireProject(projectId: String): BridgeProject =
        store.load().firstOrNull { it.id == projectId }
            ?: error("Project 不存在：$projectId")

    private fun releaseLock(project: BridgeProject) {
        val memberId = project.defaultMemberId ?: return
        runCatching { ConstructionLockStore(context).release(project, memberId) }
    }
}
