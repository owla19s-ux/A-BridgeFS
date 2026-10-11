# APS 第三方模块兼容性现状盘点

盘点基线：`main` commit `ab5e0c49bc76a5f97ce07ef8df41f6fe2909f930`

范围：AI、GitHub、Local 三条连接链及其现有测试。本文只记录可从当前源码确认的事实；“有接口”不等于“已验证可接入任意第三方模块”。

## 1. 对照表

| 能力域 | 当前源码事实 | 已有验证 | 主要缺口 / 风险 | 建议优先级 |
| --- | --- | --- | --- | --- |
| AI 接口 | `AIConnector` 提供 `send`、默认流式回退、取消接口；`AIConnectorRegistry` 按 Connection ID 解析 | Registry 单元测试覆盖注入、Profile 构造与未注册错误 | `AIConnectorFactory` 当前固定创建 `OpenAICompatibleConnector`；请求模型只有 USER / ASSISTANT，能力声明、统一错误分类、超时与服务能力差异尚未形成公共契约 | 高 |
| OpenAI 兼容 API | 通过 OkHttp 发起普通和 SSE 请求；模型列表和连通性测试在连接器中 | 测试覆盖请求地址、鉴权头、历史消息、流式片段等 | 地址规范化仅 trim 和去尾斜线；当前接受外部 HTTP，Bearer Token 有明文传输风险；需共享 URL 校验并补负向测试 | 最高 |
| GitHub API | Retrofit `GitHubApi` 声明用户、仓库、分支、文件、提交、Actions 查询、文件写入和 workflow dispatch | `GitHubConnectorTest` 覆盖地址转发、写权限拒绝与工厂装配 | API 层使用 `JsonObject` 等弱类型结果；`GitHubApiFactory` 固定 `https://api.github.com/`；Connector 暴露的 API 范围有限，尚不能等同完整 GitHub REST 覆盖；旧语义边界仍需继续盘点 | 中高 |
| GitHub 写操作 | `GitHubConnector` 通过默认关闭的 `GitHubWritePolicy` 门禁文件写入和 workflow dispatch | 测试验证未授权写操作不会调用 API | 权限策略目前只以一个 enabled 开关覆盖两类写操作，尚未细分文件修改、工作流触发等操作权限 | 高 |
| Local 文件 | `LocalFileConnector` 依赖 Android 文档树 URI 与 `LocalDocumentGateway`，可列目录、读写受支持文本文件，支持编辑冲突保护 | 测试覆盖读取、写授权、写入、并发内容变化和拒绝二进制文件 | 仅是用户授权文档树内的文本文件操作；不代表任意文件系统、二进制、大文件、批量操作、移动/复制/删除能力均已支持 | 中高 |
| 第三方模块装配 | AI 有 Factory / Registry，GitHub 有 Factory；Local 通过 Gateway 注入平台访问 | 各自存在单元测试 | 三条链没有统一的能力清单、版本契约、通用错误模型或可复用的 Connector 契约测试；没有模块动态发现/热加载证据 | 中 |
| 工程依赖 | 当前 Gradle 工程是单个 Android app 模块；已直接依赖 Retrofit、Gson、OkHttp、Coroutines | Android 单元测试由 workflow 运行 | 不能仅凭 Gradle 依赖推断第三方 Android 模块可直接集成；许可证、传递依赖、minSdk、Manifest 和生命周期仍需逐个审查 | 中 |

## 2. 目前可以明确说什么

- **可扩展基础已存在**：AI 有接口和 Registry；GitHub、Local 有分离的 Connector / Gateway 边界。
- **不属于通用插件系统**：当前源码未证明支持任意仓库自动导入、运行时发现或动态加载第三方代码。
- **接入通常需要适配**：普通 Kotlin / Java 库可能通过 Gradle 依赖使用，但仍要由 APS 适配器接入权限、生命周期和错误处理。
- **跨语言项目不能默认内嵌**：Python、Node.js、CLI 或完整独立应用需要独立评估运行环境和隔离方式。
- **API 兼容性与代码兼容性是两回事**：OpenAI-compatible endpoint 只代表一组 API 协议相似，不保证工具调用、多模态、模型列表、鉴权和错误响应完全一致。

## 3. 建议的最小公共契约

先不要把所有模块强行改成一个大接口。建议只统一必要元数据与执行边界：

1. 稳定的 Connection ID 和类型；
2. 能力声明（支持什么操作、可选能力是什么）；
3. 权限需求和调用前授权检查；
4. 统一可分类错误（认证、权限、网络、限流、输入、服务端、取消）；
5. 超时、取消和资源释放语义；
6. 可复用的契约测试；
7. 适配器和外部依赖版本记录。

AI 的消息与流式契约、GitHub 的 Repository / Workflow 操作、Local 的文件操作仍应保留各自类型安全的业务接口。

## 4. 推荐实施顺序

1. 先修复并测试 AI API URL 安全校验；这是当前有明确安全风险的缺口。
2. 为 GitHub 写操作拆分最小权限策略，至少区分内容修改与 workflow dispatch。
3. 为 Local 定义能力声明，明确已支持的文本文件操作与尚未支持的文件操作。
4. 再补跨 Connector 的错误分类和契约测试；不要为统一而牺牲具体 API 的类型信息。
5. 选一个实际开源库做试接入，用结果评估是否需要公共 Module Manifest 或 Gradle 多模块。

## 5. 验证限制

本文依据源码与现有测试文件进行静态盘点；没有在本次变更中运行 Android 构建或测试，也没有实际引入第三方库。任何“已通过”结论都应引用对应的工作流结果，不能由这份文档替代。
