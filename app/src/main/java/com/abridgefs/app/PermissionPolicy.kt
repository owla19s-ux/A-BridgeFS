package com.abridgefs.app

enum class FileAction {
    LIST, READ, WRITE, EDIT, COMMIT
}

enum class Decision {
    ALLOW, CONFIRM, DENY
}

data class Authorization(
    val root: String,
    val allowed: Set<FileAction>,
    val confirm: Set<FileAction>
) {
    fun decide(action: FileAction): Decision = when {
        action !in allowed -> Decision.DENY
        action in confirm -> Decision.CONFIRM
        else -> Decision.ALLOW
    }
}

object PermissionPolicy {
    fun authorization(context: android.content.Context, project: BridgeProject? = null, conversation: BridgeConversation? = null): Authorization {
        val prefs = context.getSharedPreferences("bridgefs", android.content.Context.MODE_PRIVATE)
        val allowed = mutableSetOf<FileAction>()
        val confirm = mutableSetOf<FileAction>()
        FileAction.values().forEach { action ->
            when (prefs.getString(
                "perm_" + action.name,
                if (action == FileAction.LIST || action == FileAction.READ) "allow" else "confirm"
            )) {
                "allow" -> allowed += action
                "confirm" -> {
                    allowed += action
                    confirm += action
                }
            }
        }
        val modifyAllowed = conversation?.localFileModifyOverride ?: project?.localFileModifyEnabled
        if (modifyAllowed == false) {
            allowed.remove(FileAction.WRITE)
            allowed.remove(FileAction.EDIT)
            allowed.remove(FileAction.COMMIT)
            confirm.remove(FileAction.WRITE)
            confirm.remove(FileAction.EDIT)
            confirm.remove(FileAction.COMMIT)
        }
        val root = project?.localAddress ?: prefs.getString("root_path", "").orEmpty()
        return Authorization(root, allowed, confirm)
    }

    fun action(command: Command): FileAction = when (command) {
        Command.ListTree -> FileAction.LIST
        is Command.Read, is Command.Search, is Command.Grep, is Command.Path, is Command.CopyPath -> FileAction.READ
        is Command.Write, is Command.Mkdir -> FileAction.WRITE
        is Command.Edit -> FileAction.EDIT
        is Command.Commit -> FileAction.COMMIT
    }

    fun check(command: Command, authorization: Authorization): Decision =
        authorization.decide(action(command))
}
