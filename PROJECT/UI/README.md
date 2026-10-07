# UI

正式 UI 资料入口。

当前 APS 第一阶段正式 UI 为三页：

- **项目页**：核心项目工作面；
- **对话页**：独立 AI 对话能力；
- **设置页**：全局连接、权限与系统配置。

其中，**项目页**是 Project 面向用户的主要工作页面。

## 三页总览

```
APS
├─ 项目页
│  └─ Project 的主要人机工作面
├─ 对话页
│  └─ 独立 AI 对话 / 查询 / 测试
└─ 设置页
   └─ 全局资源与系统设置
```

## 1. 项目页

### 定位

**项目页 = Project 的主要人机工作面。**

它不是完整 Agent Runtime，也不是复杂 Project Engine。

项目页主要展示和操作：

- 项目信息；
- Project Address；
- Default AI / Project Members；
- 项目主要对话；
- 请求 AI 协助；
- 当前任务；
- 施工、Commit、Verify 等状态。

### Project Address

项目页可以使用：

- Local Project；
- GitHub Project。

对应底层能力：

```
Local → Local Connector → Android 本地目录
GitHub → GitHub Connector → GitHub API → 远程 Repository
```

### 项目配置

项目配置通过独立入口展开 / 收起，不与主要对话混成一个长面板。

### 项目内对话

项目主要对话是项目页的核心工作区域。

任务与对话是不同对象：

- 任务：当前需要处理的工作事项；
- 对话：项目历史工作记录。

### AI 协助

项目页可以提供“请求 AI 协助”入口。

当前只确定：

```
当前 AI
 ↓
请求 AI 协助
 ↓
其他 AI Member / 临时 AI
 ↓
协助结果
 ↓
当前 AI 继续
```

多个 AI 如何真正协作施工尚未定型，项目页不得提前固定 Decision / Worker 或其他角色 UI。

### ↑ / ↓

底部 ↑ / ↓ 只控制屏幕显示空间：

- 不自动展开 / 收起项目配置；
- 不改变任务状态；
- 不改变对话状态；
- 不改变项目业务状态。

## 2. 对话页

**定位：直接使用 API 进行普通 AI 对话、查询和测试。**

可以：

- 选择 / 切换 API Profile；
- 与单个 AI 对话；
- 在授权范围内读取当前 Project 的项目资源。

对话页不自动获得 Project 施工权限，也不自动启动多 AI 协作。

## 3. 设置页

**定位：全局资源与系统设置。**

主要包括：

- API Profiles；
- GitHub；
- 系统级权限；
- 其他全局配置。

模块自己的权限继续由模块管理；Project 级访问范围由 Project / Project Address 管理。

## 4. 页面边界

```
                 APS
                  │
       ┌──────────┼──────────┐
       ↓          ↓          ↓
     项目页      对话页      设置页
       │          │           │
    项目工作     AI 对话      全局资源
       │          │           │
   任务/状态    API/查询      连接/权限
       │
   AI 协助
```

三页不得重新互相吞并职责。

## 5. 已废弃的 UI 模型

- Decision AI 固定分类；
- Worker AI 固定分类；
- 固定 AI A / AI B；
- 工作区作为独立一级业务页面；
- 通过 AI 角色直接定义决策 / 施工职责；
- 复杂 Project Dashboard。

本文件是 APS 项目页 / 对话页 / 设置页的 UI 总入口。具体页面细节继续由本目录对应文档维护。
