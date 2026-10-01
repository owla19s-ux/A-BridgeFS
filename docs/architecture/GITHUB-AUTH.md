# GitHub 授权架构

状态：正式施工基线（2026-10-02）

## 1. 第一版授权路线

Android App 使用 GitHub OAuth 设备流（Device Flow）完成用户授权。

流程：

```
A-BridgeFS
 ↓
请求 device_code
 ↓
展示 user_code
 ↓
用户在 GitHub /login/device 完成授权
 ↓
A-BridgeFS 按 interval 轮询
 ↓
获得 access token
 ↓
调用 GitHub API
 ↓
读取 /user 确认账号
```

设备流不需要把 client secret 放进 Android APK。

## 2. 为什么采用 Device Flow

移动端直接保存 OAuth client secret 不安全。

GitHub 官方 Device Flow 使用 client_id，不要求 client_secret；授权完成后返回 access token。授权码有有效期，轮询必须遵守 GitHub 返回的 interval。

因此第一版采用：

- GitHub App / OAuth Device Flow
- client_id 可进入 App 配置
- client_secret 不进入 App
- access token 本地安全存储
- GitHub API 统一经过 GitHubClient

## 3. Token 存储

access token 不进入：

- Repository 数据
- BridgeProject JSON
- GitHub UI
- 日志
- Commit
- Crash 日志

应使用 Android Keystore 保护本地凭据。

第一版至少保存：

- token
- token expiration（如果 GitHub 返回）
- refresh token（如果授权方式返回）
- GitHub login

## 4. 全局访问开关

`AccessPolicy.isGithubEnabled` 是 GitHub API 的第一道闸门。

```
GitHub API request
        ↓
isGithubEnabled?
  ├─ false → 拒绝请求
  └─ true
       ↓
token 是否存在
       ↓
GitHub API
```

## 5. 授权状态

App 内部至少区分：

- NOT_CONFIGURED
- AUTHORIZING
- AUTHORIZED
- EXPIRED
- REVOKED
- ERROR

UI 不得用“有 Repository 字符串”推断 AUTHORIZED。

## 6. 账号确认

授权成功后调用 GitHub user API。

以 GitHub 返回的 login 作为当前账号身份。

Repository 列表必须来自授权账号实际可访问的仓库。

## 7. Repository 权限

GitHub 本身的权限优先级高于 A-BridgeFS 本地权限。

例如：

```
A-BridgeFS 修改权限 = 开
GitHub token = read-only
        ↓
实际仍然不能修改
```

反过来也一样：

```
GitHub token = write
A-BridgeFS 修改权限 = 关
        ↓
A-BridgeFS 不允许发起写操作
```

最终写操作必须同时满足：

```
Global GitHub Enabled
AND
Workspace Read/Write Enabled
AND
GitHub Credential Allows Operation
AND
Repository Accessible
```

## 8. Device Flow 状态

GitHub Device Flow 可能返回：

- authorization_pending
- slow_down
- expired_token
- access_denied
- incorrect_device_code
- device_flow_disabled

这些状态需要映射成明确 UI，而不是显示通用失败。

## 9. Client ID

client_id 是 App 注册后获得的公开标识，不属于 client secret。

仓库中不提交 secret。

施工阶段使用配置入口：

```
GITHUB_CLIENT_ID
```

没有配置 client ID 时，UI 明确显示“GitHub 连接尚未配置”，而不是假装可以连接。

## 10. 后续迁移

如果以后需要更强的 Repository 范围控制，可以迁移到：

GitHub App
→ Installation
→ User Access Token / Installation Access Token
→ repository_ids / permissions

第一版接口应避免把 OAuth Device Flow 细节泄漏到 UI 和工作区数据结构。

## 11. 安全边界

禁止：

- 在日志输出 token
- 在异常信息输出 token
- 把 token 放进 Intent extras
- 把 token 写入 BridgeProject JSON
- 把 token 写进 GitHub repository
- 把 token 放进 APK 静态资源
- 将 client secret 放进 Android APK

