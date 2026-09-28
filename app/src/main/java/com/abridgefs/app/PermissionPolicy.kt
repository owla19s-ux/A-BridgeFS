package com.abridgefs.app

enum class FileAction { LIST, READ, WRITE, EDIT }
enum class Decision { ALLOW, CONFIRM, DENY }

data class Authorization(
    val root: String,
    val allowed: Set<FileAction> = setOf(FileAction.LIST, FileAction.READ),
    val confirm: Set<FileAction> = emptySet()
) {
    fun decide(action: FileAction): Decision = when {
        action !in allowed -> Decision.DENY
        action in confirm -> Decision.CONFIRM
        else -> Decision.ALLOW
    }
}

object PermissionPolicy {
    fun action(command: Command): FileAction = when (command) {
        Command.ListTree -> FileAction.LIST
        is Command.Read -> FileAction.READ
        is Command.Write -> FileAction.WRITE
        is Command.Edit -> FileAction.EDIT
    }
    fun check(command: Command, authorization: Authorization): Decision =
        authorization.decide(action(command))
}
