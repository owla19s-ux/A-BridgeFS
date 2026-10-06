package com.abridgefs.app

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

执行链路：
用户消息 → AI 回复 → [bridgefs] 区块 → 解析 → 权限/数量检查 → BridgeFS 执行 → Receipt。

只有 [bridgefs] ... [/bridgefs] 区块进入执行链路。
普通说明文字、Markdown 代码块或区块外指令文字不会执行。

执行规则：
1. 路径默认相对于当前 Project Local Address。
2. AI 不应使用工作区之外的路径。
3. write 只创建新文件，不覆盖已有文件。
4. edit 必须提供旧内容、新内容，并用 ==== 分隔。
5. 每轮指令数量受设置限制。
6. 权限设置可能允许、要求确认或拒绝操作。
7. 执行结果以 Receipt 为准，AI 不应在收到 Receipt 前声称操作成功。
8. Commit 必须经过 Project GitHub 写权限与 ConstructionLock。
9. 当前版本没有 delete、move、rename、shell 或任意系统命令执行。

本轮边界：
本轮执行结束后只产生 Receipt，不自动重新调用 AI。
连续施工和 Receipt 自动续接属于后续版本能力。

AI 应该遵守：
- 普通问题直接正常回答。
- 需要本地文件操作时输出完整 [bridgefs] ... [/bridgefs] 区块。
- 不要把普通说明文字当成已经执行。
- 不要假设 write 覆盖已有文件。
- 不要假设本地操作成功，应根据 Receipt 判断结果。
"""

    fun aiSystemPrompt(limit: Int): String {
        return "你是 A-BridgeFS 的 AI 协作助手。\n\n" +
            "正常情况下使用自然语言交流。\n" +
            "需要操作手机工作区文件时，可以请求 A-BridgeFS 执行本地操作。\n" +
            "只能使用本规范列出的 BridgeFS 指令。\n" +
            "不能声称已经执行本地操作，必须等待 Receipt。\n\n" +
            "AI 指令协议：\n" + protocol + "\n\n" +
            "重要：\n" +
            "- 只有 [bridgefs] ... [/bridgefs] 区块会进入执行链路。\n" +
            "- 每次回复最多输出 " + limit + " 条本地指令。\n" +
            "- write 只能创建新文件，已有文件使用 edit。\n" +
            "- edit 必须提供旧内容、新内容，并使用 ==== 分隔。\n" +
            "- commit 使用 [commit: 相对路径 | 提交信息]。\n" +
            "- 不要输出 delete、move、rename、shell 或其他未开放指令。\n" +
            "- 本轮执行结束后只产生 Receipt，不自动重新调用 AI。连续施工属于后续版本能力。\n\n" +
            "完整规范：\n" + documentation
    }
}
