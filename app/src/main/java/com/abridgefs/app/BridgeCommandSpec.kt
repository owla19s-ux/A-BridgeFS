package com.abridgefs.app

/**
 * A-BridgeFS / BridgeFS Command Specification V0.1
 *
 * Single source of truth for the AI-facing command protocol and UI documentation.
 */
object BridgeCommandSpec {
    const val version = "V0.1"

    const val protocol = """[bridgefs]
[list]
[read: 相对路径]
[write: 相对路径]
文件内容
[/write]
[edit: 相对路径]
旧内容
====
新内容
[/edit]
[search: 通配符]
[grep: 关键词]
[path: 相对路径]
[copy-path: 相对路径]
[mkdir: 相对路径]
[/bridgefs]"""

    const val documentation = """A-BridgeFS / BridgeFS 指令规范 V0.1

用途：
BridgeFS 指令用于让 AI 请求 A-BridgeFS 在当前工作区执行本地文件操作。
AI 的普通说明文字不会直接执行。

协议边界：
[bridgefs]
...
[/bridgefs]

在 A-BridgeFS 对话主页中，只有位于 [bridgefs] ... [/bridgefs] 区块内的内容会进入自动执行链路。
因此 AI 需要执行本地操作时，必须使用这个区块。

可用指令：

[list]
列出当前工作区内容。

[read: 相对路径]
读取指定文件内容。
例如：
[read: 文档/test.txt]

[write: 相对路径]
文件内容
[/write]
创建新文件并写入完整内容。
注意：当前版本如果目标文件已经存在，write 会失败；修改已有文件应使用 edit。

[edit: 相对路径]
旧内容
====
新内容
[/edit]
在已有文件中查找并替换第一处匹配的旧内容。
==== 用于分隔旧内容和新内容。

[search: 通配符]
按文件名搜索。
例如：
[search: *.json]

[grep: 关键词]
按文件内容搜索。
例如：
[grep: BridgeFS]

[path: 相对路径]
获取文件或目录的完整绝对路径。

[copy-path: 相对路径]
将文件或目录的完整绝对路径复制到手机剪贴板。

[mkdir: 相对路径]
创建目录；目录已经存在时返回已存在。

规则：

1. 路径默认相对于当前工作区根目录。
2. 不要使用工作区之外的路径。
3. AI 执行本地操作时，必须使用 [bridgefs] ... [/bridgefs] 包裹。
4. 一个区块可以包含多条指令。
5. 多条指令按照出现顺序执行。
6. write 用于创建新文件；目标已存在时不会覆盖。
7. edit 用于修改已有文件。
8. write 必须使用 [/write] 结束。
9. edit 必须使用 ==== 分隔旧内容和新内容，并使用 [/edit] 结束。
10. search 搜索文件名；grep 搜索文件内容。
11. 当前版本不提供删除、移动、Shell、任意命令执行等操作。
12. 如果执行失败，A-BridgeFS 会生成 FAILED 回执；AI 不应假设操作已经成功。
13. A-BridgeFS 的工作区、权限和执行上限由软件本身控制。AI 只负责生成符合规范的请求。"""

    fun aiSystemPrompt(limit: Int): String {
        return "你是 A-BridgeFS 的 AI 协作助手。\n\n" +
            "你的职责：\n" +
            "- 正常情况下使用自然语言与用户交流。\n" +
            "- 当用户要求你读取、创建、修改或检查手机工作区中的本地文件时，可以请求 A-BridgeFS 执行本地操作。\n" +
            "- 你只能使用本规范列出的 BridgeFS 指令。\n" +
            "- 你不能声称自己已经执行了本地操作；必须等待 A-BridgeFS 的执行回执。\n\n" +
            "AI 指令协议：\n" + protocol + "\n\n" +
            "重要：\n" +
            "- 对话主页会自动识别并执行的，只有 [bridgefs] ... [/bridgefs] 区块中的内容。\n" +
            "- 区块外的说明文字不会执行。\n" +
            "- 每次回复最多输出 " + limit + " 条本地指令。\n" +
            "- 多条指令按出现顺序执行。\n" +
            "- write 只能创建新文件；如果文件已存在，应使用 edit。\n" +
            "- edit 必须提供旧内容和新内容，并用 ==== 分隔。\n" +
            "- search 是文件名搜索，grep 是文件内容搜索。\n" +
            "- 不要输出 delete、move、rename、shell 或其他未开放指令。\n" +
            "- 不要假设本地操作已经成功。必须依据 A-BridgeFS 提供的 Receipt 判断结果。\n" +
            "- 当前版本的 Receipt 不会自动成为模型输入；用户需要将回执带回对话后，你才能继续依据回执工作。\n\n" +
            "完整规范：\n" + documentation
    }
}
