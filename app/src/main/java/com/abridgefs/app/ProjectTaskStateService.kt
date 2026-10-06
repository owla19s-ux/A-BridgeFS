package com.abridgefs.app

import android.content.Context

/**
 * Owns the lifecycle state of the currently selected Project task.
 * This class does not start or chain execution; execution remains an explicit Project action.
 *
 * ConstructionLock follows the Project task lifecycle:
 * - a running task may retain the lock across multiple commands;
 * - pausing, completing, clearing, or switching the active task releases the Project lock;
 * - starting the same already-running task does not disturb its current lock.
 */
class ProjectTaskStateService(private val context: Context) {
    private val store = BridgeProjectStore(context)
    private val lockStore = ConstructionLockStore(context)

    fun start(projectId: String, taskId: String): BridgeProject {
        val project = requireProject(projectId)
        val task = project.tasks.firstOrNull { it.id == taskId }
            ?: error("任务不存在：$taskId")
        require(!task.completed) { "已完成任务不能重新启动" }

        val switchingTask = project.taskState.status != "RUNNING" ||
            project.taskState.activeTaskId != taskId
        if (switchingTask) {
            lockStore.clearProject(project.id)
        }

        project.tasks.forEach {
            if (it.id != taskId && it.status == "RUNNING") it.status = "WAITING"
        }
        task.completed = false
        task.status = "RUNNING"
        project.taskState = BridgeTaskExecutionState(
            activeTaskId = taskId,
            status = "RUNNING"
        )
        store.saveProject(project)
        return project
    }

    fun setCompleted(projectId: String, taskId: String, completed: Boolean): BridgeProject {
        val project = requireProject(projectId)
        val task = project.tasks.firstOrNull { it.id == taskId }
            ?: error("任务不存在：$taskId")

        if (completed) {
            require(task.status != "RUNNING") { "施工中的任务不能直接标记完成，请先暂停或完成施工" }
            task.completed = true
            task.status = "COMPLETED"
            if (project.taskState.activeTaskId == taskId) {
                project.taskState = BridgeTaskExecutionState()
                lockStore.clearProject(project.id)
            }
        } else {
            task.completed = false
            task.status = "PENDING"
            if (project.taskState.activeTaskId == taskId) {
                project.taskState = BridgeTaskExecutionState()
                lockStore.clearProject(project.id)
            }
        }

        store.saveProject(project)
        return project
    }

    fun pause(projectId: String): BridgeProject {
        val project = requireProject(projectId)
        require(project.taskState.activeTaskId != null) { "当前没有施工中的任务" }
        require(project.taskState.status == "RUNNING") { "当前任务不处于施工状态" }
        project.taskState.status = "WAITING"
        project.taskState.activeTaskId?.let { id ->
            project.tasks.firstOrNull { it.id == id }?.let {
                if (!it.completed) it.status = "WAITING"
            }
        }
        lockStore.clearProject(project.id)
        store.saveProject(project)
        return project
    }

    fun complete(projectId: String): BridgeProject {
        val project = requireProject(projectId)
        require(project.taskState.activeTaskId != null) { "当前没有施工中的任务" }
        require(project.taskState.status == "RUNNING" || project.taskState.status == "WAITING") { "当前任务不能完成" }

        val activeTaskId = project.taskState.activeTaskId
        val activeTask = project.tasks.firstOrNull { it.id == activeTaskId }
            ?: error("当前施工任务不存在")
        activeTask.completed = true
        activeTask.status = "COMPLETED"
        project.taskState = BridgeTaskExecutionState()
        lockStore.clearProject(project.id)
        store.saveProject(project)
        return project
    }

    fun clear(projectId: String): BridgeProject {
        val project = requireProject(projectId)
        project.taskState = BridgeTaskExecutionState()
        project.tasks.filter { it.status == "RUNNING" }.forEach { it.status = "WAITING" }
        lockStore.clearProject(project.id)
        store.saveProject(project)
        return project
    }

    private fun requireProject(projectId: String): BridgeProject =
        store.load().firstOrNull { it.id == projectId }
            ?: error("Project 不存在：$projectId")
}
