# GitHub 授权架构

状态：正式施工基线（2026-10-02）

## 1. 第一版授权路线

APS 第一版支持两种 GitHub 连接方式：

1. **Fine-grained Personal Access Token（PAT）**：第一版主路径。
2. **OAuth Device Flow**：保留为后续/可选路径；需要配置 `GITHUB_CLIENT_ID`。

PAT 适合个人 Android 工作站：用户可以在 GitHub 侧限制 Repository 与权限，并可选择较长的凭据生命周期。

PAT 流程：

```
GitHub
 ↓
Fine-grained personal access token
 ↓
限制 Repository / 权限
 ↓
复制 Token
 ↓
APS
 ↓
/user 验证账号
 ↓
/user/repos 验证 Repository 访问
 ↓
Android Keystore 加密保存
```

## 2. PAT 权限边界

APS 不自行扩大 GitHub 权限。

GitHub 是 Connection 类型之一；Context 使用 GitHub Connection 时的最终实际操作权限：

```
GitHub 全局允许
AND
Context 允许
AND
GitHub 凭据实际权限
AND
Repository 可访问
```

例如：

```
Project允许修改 = 开
GitHub PAT = Contents Read-only
        ↓
实际仍然不能写入
```

## 3. PAT 连接

UI 提供：

- 连接：Personal Access Token
- Token 输入框使用密码显示
- 先验证 `/user`
- 再验证 Repository 列表可读取
- 验证成功后才写入本地凭据

Token 不进入：

- Repository 数据
- BridgeProject JSON
- GitHub UI
- 日志
- Commit
- Crash 日志
- Intent extras

## 4. Token 存储

使用 Android Keystore 保护 Token。

SharedPreferences 只保存：

- 加密后的 token
- GitHub login
- credential type

明文 Token 不持久化。

断开 GitHub 时清除本地凭据。

## 5. OAuth Device Flow

Device Flow 不需要把 client secret 放进 Android APK。

需要：

```
GITHUB_CLIENT_ID
```

授权成功后仍统一经过 `GitHubTokenStore` 与 `GitHubClient`。

## 6. 全局访问开关

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

## 7. 账号确认

凭据验证成功后调用 GitHub user API，以 GitHub 返回的 login 作为当前账号身份。

Repository 列表必须来自授权账号实际可访问的仓库。

## 8. Repository 与 Branch

Context / GitHub Connection 关联状态保存：

- GitHub account login
- Repository
- Branch
- APS 读取权限
- APS 修改权限

Token 本身不写入 Context 数据。

## 9. 授权状态

后续应继续完善：

- NOT_CONFIGURED
- AUTHORIZING
- AUTHORIZED
- EXPIRED
- REVOKED
- ERROR

当前 UI 已区分“未连接 / 已连接”，但 Token 过期、撤销等状态仍待进一步实现。

## 10. 安全边界

禁止：

- 在日志输出 token
- 在异常信息输出 token
- 把 token 放进 Intent extras
- 把 token 写入 BridgeProject JSON
- 把 token 写进 GitHub repository
- 把 token 放进 APK 静态资源
- 将 client secret 放进 Android APK
