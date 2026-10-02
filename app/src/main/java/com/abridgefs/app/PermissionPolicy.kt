package com.abridgefs.app

enum class FileAction {
    LIST, READ, WRITE, EDIT
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
    fun authorization(context: android.content.Context): Authorization {
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
        return Authorization(prefs.getString("root_path", "").orEmpty(), allowed, confirm)
    }

    fun action(command: Command): FileAction = when (command) {
        Command.ListTree -> FileAction.LIST
        is Command.Read, is Command.Search, is Command.Grep, is Command.Path, is Command.CopyPath -> FileAction.READ
        is Command.Write, is Command.Mkdir -> FileAction.WRITE
        is Command.Edit -> FileAction.EDIT
    }

    fun check(command: Command, authorization: Authorization): Decision =
        authorization.decide(action(command))
}
