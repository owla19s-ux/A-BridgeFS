package com.abridgefs.app

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors

class GitHubActivity : android.app.Activity() {
    private val authStore by lazy { GitHubTokenStore(this) }
    private val projectStore by lazy { BridgeProjectStore(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private var projects = mutableListOf<BridgeProject>()
    private var workspace: BridgeProject? = null
    private lateinit var root: LinearLayout

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        projects = projectStore.load()
        if (projects.isEmpty()) projects += projectStore.newProject("默认工作区")
        workspace = projects.first()
        build()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun build() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(color(R.color.bridgefs_surface))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(scroll)
        render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(TextView(this).apply {
            text = "GitHub"
            textSize = 26f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "真实 GitHub 协作资源"
            textSize = 13f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(4), 0, dp(16))
        })

        if (!AccessPolicy.isGithubEnabled(this)) {
            root.addView(info("GitHub 全局访问已关闭", "请先在“配置 → 连接与访问”开启。"))
            return
        }

        val auth = authStore.state()
        if (auth.accessToken.isNullOrBlank()) {
            root.addView(info("○ 未连接", "第一版支持 Fine-grained Personal Access Token；Device Flow 保留为后续授权方式。"))
            root.addView(button("连接：Personal Access Token") { showPatDialog() })
            if (BuildConfig.GITHUB_CLIENT_ID.isNotBlank()) {
                root.addView(button("连接：GitHub Device Flow") { startDeviceFlow() })
            }
            return
        }

        root.addView(info("● 已连接", auth.login ?: "GitHub 账号"))
        root.addView(info("凭据", auth.credentialType ?: "GitHub Token"))
        root.addView(section("Repository"))
        root.addView(info(workspace?.githubRepository ?: "未选择 Repository", "当前工作区 Repository"))
        root.addView(button("切换 Repository") { chooseRepository() })

        root.addView(section("Branch"))
        root.addView(info(workspace?.githubBranch ?: "未选择 Branch", "当前工作区 Branch"))
        root.addView(button("切换 Branch") { chooseBranch() })

        root.addView(section("访问权限"))
        root.addView(CheckBox(this).apply {
            text = "允许读取"
            isChecked = workspace?.githubReadEnabled ?: true
            setOnCheckedChangeListener { _, checked ->
                workspace?.githubReadEnabled = checked
                save()
            }
        })
        root.addView(CheckBox(this).apply {
            text = "允许修改"
            isChecked = workspace?.githubWriteEnabled ?: false
            setOnCheckedChangeListener { _, checked ->
                workspace?.githubWriteEnabled = checked
                save()
            }
        })

        root.addView(section("GitHub 工作区"))
        listOf("文件", "Commit", "Issue", "PR", "Actions", "Release").forEach { name ->
            root.addView(info(name, "真实模块入口；具体能力按版本逐步开放。"))
        })

        root.addView(section("账号"))
        root.addView(button("断开 GitHub") {
            authStore.clear()
            workspace?.githubAccountLogin = null
            workspace?.githubRepository = null
            workspace?.githubBranch = null
            workspace?.githubWriteEnabled = false
            save()
            render()
        })
    }

    private fun showPatDialog() {
        val input = EditText(this).apply {
            hint = "github_pat_…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
            addView(TextView(this@GitHubActivity).apply {
                text = "建议使用 Fine-grained PAT，并仅授权需要的 Repository。Token 只保存在本机，不进入工作区数据、日志或 GitHub。"
                textSize = 12f
                setTextColor(color(R.color.bridgefs_text_secondary))
                setPadding(0, 0, 0, dp(10))
            })
            addView(input, LinearLayout.LayoutParams(-1, dp(52)))
        }

        AlertDialog.Builder(this)
            .setTitle("连接 Personal Access Token")
            .setView(box)
            .setPositiveButton("验证并保存") { _, _ -> verifyAndSavePat(input.text.toString().trim()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun verifyAndSavePat(token: String) {
        if (token.isBlank()) {
            Toast.makeText(this, "Token 不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        executor.execute {
            runCatching {
                val client = GitHubClient(token)
                val login = client.getLogin()
                client.listRepositories()
                login
            }.onSuccess { login ->
                authStore.save(login, token, "Personal Access Token")
                runOnUiThread {
                    Toast.makeText(this, "GitHub 已连接：" + login, Toast.LENGTH_SHORT).show()
                    render()
                }
            }.onFailure {
                runOnUiThread {
                    Toast.makeText(this, "Token 验证失败：" + (it.message ?: "未知错误"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startDeviceFlow() {
        val clientId = BuildConfig.GITHUB_CLIENT_ID
        if (clientId.isBlank()) {
            Toast.makeText(this, "尚未配置 GitHub Client ID", Toast.LENGTH_LONG).show()
            return
        }

        executor.execute {
            runCatching {
                val client = GitHubClient()
                val code = client.requestDeviceCode(clientId)
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("连接 GitHub")
                        .setMessage(
                            "请在浏览器打开：\n" + code.verificationUri +
                                "\n\n验证码：" + code.userCode +
                                "\n\n授权后返回 App，系统会自动完成连接。"
                        )
                        .setPositiveButton("打开 GitHub") { _, _ ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri)))
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }

                var interval = code.intervalSeconds
                val deadline = System.currentTimeMillis() + code.expiresIn * 1000
                var token: String? = null
                while (token == null && System.currentTimeMillis() < deadline) {
                    Thread.sleep(interval * 1000)
                    try {
                        token = client.pollDeviceToken(clientId, code.deviceCode)
                    } catch (e: GitHubPendingException) {
                        interval = e.nextIntervalSeconds
                    }
                }
                require(!token.isNullOrBlank()) { "GitHub 授权超时" }

                val authenticated = GitHubClient(token)
                val login = authenticated.getLogin()
                authStore.save(login, token, "GitHub Device Flow")
                runOnUiThread {
                    Toast.makeText(this, "GitHub 已连接：" + login, Toast.LENGTH_SHORT).show()
                    render()
                }
            }.onFailure {
                runOnUiThread {
                    Toast.makeText(this, "GitHub 连接失败：" + (it.message ?: "未知错误"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun chooseRepository() {
        val token = authStore.state().accessToken ?: return
        executor.execute {
            runCatching { GitHubClient(token).listRepositories() }
                .onSuccess { repos ->
                    runOnUiThread {
                        if (repos.isEmpty()) {
                            Toast.makeText(this, "没有可访问的 Repository", Toast.LENGTH_SHORT).show()
                            return@runOnUiThread
                        }
                        AlertDialog.Builder(this)
                            .setTitle("选择 Repository")
                            .setItems(repos.map { it.fullName }.toTypedArray()) { _, which ->
                                val selected = repos[which]
                                workspace?.githubRepository = selected.fullName
                                workspace?.githubBranch = selected.defaultBranch
                                workspace?.githubAccountLogin = authStore.state().login
                                save()
                                render()
                            }.show()
                    }
                }
                .onFailure {
                    runOnUiThread {
                        Toast.makeText(this, "读取 Repository 失败：" + (it.message ?: "未知错误"), Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun chooseBranch() {
        val token = authStore.state().accessToken ?: return
        val repo = workspace?.githubRepository
        if (repo.isNullOrBlank()) {
            Toast.makeText(this, "请先选择 Repository", Toast.LENGTH_SHORT).show()
            return
        }
        executor.execute {
            runCatching { GitHubClient(token).listBranches(repo) }
                .onSuccess { branches ->
                    runOnUiThread {
                        AlertDialog.Builder(this)
                            .setTitle("选择 Branch")
                            .setItems(branches.toTypedArray()) { _, which ->
                                workspace?.githubBranch = branches[which]
                                save()
                                render()
                            }.show()
                    }
                }
                .onFailure {
                    runOnUiThread {
                        Toast.makeText(this, "读取 Branch 失败：" + (it.message ?: "未知错误"), Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun save() { projectStore.save(projects) }

    private fun section(title: String) = TextView(this).apply {
        text = title
        textSize = 14f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(color(R.color.bridgefs_text_secondary))
        setPadding(0, dp(18), 0, dp(8))
    }

    private fun info(title: String, subtitle: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setBackgroundColor(color(R.color.bridgefs_input_surface))
        addView(TextView(this@GitHubActivity).apply {
            text = title
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(TextView(this@GitHubActivity).apply {
            text = subtitle
            textSize = 12f
            setTextColor(color(R.color.bridgefs_text_secondary))
            setPadding(0, dp(4), 0, 0)
        })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    }

    private fun button(text: String, action: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(12))
        setTextColor(color(R.color.bridgefs_button_text))
        setBackgroundColor(color(R.color.bridgefs_button_bg))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) }
    }

    private fun color(id: Int) = resources.getColor(id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
