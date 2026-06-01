# 🎯 第一阶段学习指南：项目认知与架构理解

> **目标**：从全局到细节，建立对项目的完整认知框架
> **预计时间**：1-2 周
> **适合人群**：有 Java/Spring Boot 经验的开发者

---

## 📋 目录

1. [项目全景概览](#1-项目全景概览)
2. [核心架构解析](#2-核心架构解析)
3. [技术栈映射](#3-技术栈映射)
4. [代码阅读路线图](#4-代码阅读路线图)
5. [AI 开发核心术语表](#5-ai-开发核心术语表)
6. [第一阶段实战任务](#6-第一阶段实战任务)
7. [学习检查清单](#7-学习检查清单)

---

## 1. 项目全景概览

### 1.1 这是什么？

这是一个**基于多智能体架构的银行个人助手**，用户可以通过对话方式：
- 查询账户余额和支付方法
- 查看交易历史
- 发起支付（支持上传发票图片，OCR 自动识别）

### 1.2 为什么这样设计？

**传统方式**：用户需要登录网银 → 导航到不同菜单 → 手动操作
**AI 助手方式**：用户直接对话 → AI 理解意图 → 自动调用对应服务

**核心价值**：将传统微服务架构与 AI Agent 技术结合，提供自然的交互体验。

### 1.3 系统组成（5 个核心服务）

```
┌─────────────────────────────────────────────────┐
│              Frontend (React)                    │
│         端口: 80 (Docker) / 8081 (本地)          │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│         Copilot Backend (Spring Boot)            │
│     AI Agent 编排中心 - 端口: 8080               │
│  包含: Supervisor + 3 个领域 Agent               │
└──────┬──────────────┬──────────────┬────────────┘
       │              │              │
┌──────▼──────┐ ┌─────▼──────┐ ┌────▼──────────┐
│  Account    │ │  Payment   │ │ Transaction   │
│  Service    │ │  Service   │ │ History       │
│  端口:8082  │ │  端口:8083 │ │ Service       │
│             │ │            │ │ 端口:8084     │
└─────────────┘ └────────────┘ └───────────────┘
```

---

## 2. 核心架构解析

### 2.1 垂直多智能体监督者模式

这是本项目**最重要的架构概念**。

```
用户请求: "我上个月给 John 转了多少钱？"
         │
         ▼
┌─────────────────────────────────────┐
│   Supervisor Agent (监督者)          │
│   职责: 理解意图，路由到正确的 Agent  │
│   判断: 这是查询交易 → 选 Transactions Agent │
└─────────────┬───────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│   Transactions Agent                │
│   职责: 处理交易历史查询             │
│   工具: searchTransactions API      │
│   提取参数: recipient="John",       │
│          timeframe="上个月"          │
└─────────────┬───────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│   MCP Tool (Spring AI MCP)          │
│   将 REST API 暴露为 Agent 可调用的  │
│   工具                              │
└─────────────┬───────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│   Transaction History Service       │
│   实际执行业务逻辑                   │
│   返回: 交易列表                     │
└─────────────────────────────────────┘
```

### 2.2 三个领域 Agent

| Agent 名称 | 职责 | 复杂度 | 涉及技术 |
|-----------|------|--------|---------|
| **Account Agent** | 查询账户信息、余额、支付方法 | ⭐ 简单 | MCP 工具调用 |
| **Transactions Agent** | 查询交易历史、按收款人搜索 | ⭐⭐ 中等 | 对话记忆、参数提取 |
| **Payments Agent** | 提交支付、OCR 发票识别 | ⭐⭐⭐ 复杂 | Azure Document Intelligence、多工具链 |

### 2.3 MCP（Model Context Protocol）

**核心概念**：将你的 Spring Boot REST API 自动暴露为 AI Agent 可以调用的"工具"。

```
传统 REST API:
  GET /api/accounts/{username}
  
通过 MCP 暴露后:
  Agent 可以这样调用:
  Tool: getAccountDetails
  参数: { username: "john.doe" }
  
优势:
  ✅ 自动从 OpenAPI 规范生成工具定义
  ✅ 类型安全的参数绑定
  ✅ 无需手动编写工具适配代码
```

---

## 3. 技术栈映射

### 3.1 你已掌握的技术 vs 需要学习的技术

#### ✅ 你已经懂的（舒适区）
- Java 17
- Spring Boot 3.3.6
- Maven 构建
- REST API 设计
- Docker 容器化
- 微服务架构

#### 🔥 需要重点学习的（成长区）

| 技术 | 用途 | 学习优先级 | 项目中的位置 |
|-----|------|-----------|-------------|
| **Langchain4j** | Agent 编排框架 | ⭐⭐⭐⭐⭐ | `app/copilot/langchain4j-agents/` |
| **Spring AI MCP** | API → Agent 工具 | ⭐⭐⭐⭐⭐ | `app/business-api/*/` |
| **Azure OpenAI** | LLM 提供者 | ⭐⭐⭐⭐ | 配置在环境变量 |
| **Prompt Engineering** | 提示词设计 | ⭐⭐⭐⭐ | Agent 的系统提示词 |
| **Azure Document Intelligence** | OCR 发票识别 | ⭐⭐⭐ | `PaymentMCPAgent.java` |

### 3.2 依赖关系图

```
copilot-backend (主服务)
    ├── langchain4j-agents (Agent 实现)
    │       ├── Langchain4j 核心库
    │       └── Azure OpenAI SDK
    └── copilot-common (共享模型)
    
business-api/account
    └── Spring AI MCP Server (暴露工具)
    
business-api/payment
    └── Spring AI MCP Server + Azure Document Intelligence
    
business-api/transactions-history
    └── Spring AI MCP Server
```

---

## 4. 代码阅读路线图

### 📍 第一站：理解项目入口（30 分钟）

#### 4.1 启动脚本
**文件**: `app/start-compose.ps1`
**学习目标**:
- 理解如何本地启动所有服务
- 了解环境变量配置
- 知道 Service Principal 的作用（Azure 认证）

**阅读重点**:
```powershell
# 第 1-7 行: 从 azd 获取环境变量
# 第 16-36 行: 创建/查找 Service Principal
# 第 51 行: docker compose 启动所有容器
```

#### 4.2 Docker Compose 配置
**文件**: `app/compose.yaml`
**学习目标**:
- 5 个服务的依赖关系
- 端口映射
- 环境变量传递

**关键配置**:
```yaml
copilot:
  environment:
    - ACCOUNTS_API_SERVER_URL=http://account:8080
    - PAYMENTS_API_SERVER_URL=http://payment:8080
    - TRANSACTIONS_API_SERVER_URL=http://transaction:8080
```

---

### 📍 第二站：探索 Agent 核心代码（2-3 小时）

按照**从简单到复杂**的顺序阅读：

#### 4.3 Agent 接口定义
**文件**: `app/copilot/langchain4j-agents/src/main/java/com/microsoft/langchain4j/agent/Agent.java`
```java
// 只有 12 行！理解 Agent 的基本抽象
```
**学习目标**: Agent 的通用接口定义

#### 4.4 AbstractReAct Agent
**文件**: `AbstractReActAgent.java`
**行数**: 116 行
**学习目标**:
- ReAct 模式是什么？（Reasoning + Acting）
- Agent 的循环机制：思考 → 行动 → 观察
- 如何绑定工具

**阅读提示**:
```
重点关注:
- 第 30-50 行: Agent 的构建器模式
- 第 60-80 行: 工具绑定逻辑
- 第 90-110 行: 执行流程
```

#### 4.5 AccountMCPAgent（最简单的 Agent）⭐ 从这里开始
**文件**: `app/copilot/langchain4j-agents/.../mcp/AccountMCPAgent.java`
**行数**: 54 行
**学习目标**:
- 如何定义一个 Agent
- 系统提示词怎么写
- 如何绑定 MCP 工具

**关键代码片段**:
```java
// 注意看:
// 1. Agent 的名称和描述
// 2. 系统提示词（告诉 Agent 它能做什么）
// 3. MCP 工具的 URL 配置
```

**思考题**:
1. 这个 Agent 能回答哪些问题？
2. 它使用了哪些工具？
3. 系统提示词中包含了什么信息？

#### 4.6 TransactionHistoryMCPAgent
**文件**: `TransactionHistoryMCPAgent.java`
**行数**: 64 行
**学习目标**:
- 与 Account Agent 的异同
- 如何支持更复杂的查询

#### 4.7 PaymentMCPAgent（最复杂的 Agent）
**文件**: `PaymentMCPAgent.java`
**行数**: 128 行
**学习目标**:
- 多工具协作（OCR + 账户查询 + 支付提交）
- 如何处理图片上传
- 错误处理机制

#### 4.8 SupervisorAgent（大脑）
**文件**: `SupervisorAgent.java`
**行数**: 122 行
**学习目标**:
- 如何路由到不同的 Agent
- 意图识别逻辑
- 工具注册机制

**关键问题**:
```
Supervisor 如何知道该选哪个 Agent？
答案: 通过每个 Agent 的描述（description）
OpenAI 的 Function Calling 机制会自动选择
```

---

### 📍 第三站：理解 MCP 工具暴露（1-2 小时）

#### 4.9 Account Service 的 MCP 配置
**目录**: `app/business-api/account/src/main/java/...`
**查找**:
- OpenAPI/Swagger 配置
- MCP Server 配置
- REST Controller

**学习目标**:
```
一个普通的 Spring Boot Controller:
  @GetMapping("/api/accounts/{username}")
  public Account getAccount(@PathVariable String username)
  
通过 Spring AI MCP 自动变成:
  Tool: getAccount
  参数: username (String, required)
  描述: Get account details by username
```

#### 4.10 Payment Service 的 OCR 集成
**目录**: `app/business-api/payment/`
**学习目标**:
- Azure Document Intelligence 的调用方式
- 发票数据结构
- 错误处理

---

### 📍 第四站：前端交互（选读，1 小时）

#### 4.11 前端 API 调用
**文件**: `app/frontend/src/api/api.ts`
**学习目标**:
- 前端如何调用 Copilot Backend
- 消息格式
- 流式响应处理

---

## 5. AI 开发核心术语表

### 5.1 基础概念

| 术语 | 英文 | 解释 | 类比 |
|-----|------|------|------|
| **LLM** | Large Language Model | 大语言模型，如 GPT-4 | "聪明的文本处理器" |
| **Agent** | Agent | 能使用工具解决任务的 AI 实体 | "会使用软件的助手" |
| **Tool** | Tool | Agent 可以调用的功能 | "助手的工具箱" |
| **Prompt** | Prompt | 给 AI 的指令 | "给助手的任务说明" |
| **Token** | Token | AI 处理文本的基本单位 | "单词的子片段" |

### 5.2 本项目特定术语

| 术语 | 解释 | 项目位置 |
|-----|------|---------|
| **Supervisor Agent** | 监督者 Agent，负责路由请求 | `SupervisorAgent.java` |
| **Domain Agent** | 领域 Agent，处理特定业务 | `AccountMCPAgent.java` 等 |
| **MCP Tool** | 通过 MCP 协议暴露的工具 | `business-api/*/` |
| **ReAct Pattern** | Reasoning + Acting 模式 | `AbstractReActAgent.java` |
| **Function Calling** | OpenAI 的工具调用机制 | Langchain4j 内部使用 |

### 5.3 架构模式

| 模式 | 解释 | 本项目应用 |
|-----|------|-----------|
| **垂直多智能体** | 一个监督者 + 多个专业 Agent | 核心架构 |
| **水平多智能体** | 多个 Agent 协作，无明确监督者 | 未使用 |
| **MicroAgents** | 按功能域划分的微 Agent | Account/Payment/Transaction |
| **RAG** | 检索增强生成 | 本项目未使用，但文档有提及 |

---

## 6. 第一阶段实战任务

### 任务 1：本地运行项目 ✅

**步骤**:
```powershell
# 1. 确保已安装依赖
# - Java 17+
# - Docker Desktop
# - Azure CLI (az)
# - Azure Developer CLI (azd)

# 2. 登录 Azure
az login

# 3. 启动服务
cd app
./start-compose.ps1

# 4. 等待所有容器启动（约 2-3 分钟）
# 观察 Docker Desktop 中的容器状态

# 5. 访问前端
# http://localhost:80 或 http://localhost:8081
```

**验证点**:
- [ ] 所有 5 个容器状态为 Running
- [ ] 前端页面正常加载
- [ ] 可以发送消息并收到回复

### 任务 2：绘制你的第一张架构图

**要求**:
- 用任何工具（Draw.io、Excalidraw、甚至手绘）
- 包含 5 个核心服务
- 标注端口号和依赖关系
- 标注数据流向

**提示**: 参考本文档第 1.3 节的 ASCII 图

### 任务 3：代码走读 - Account Agent

**步骤**:
1. 打开 `AccountMCPAgent.java`
2. 逐行阅读，理解每一行代码的作用
3. 回答以下问题：

**问题清单**:
- [ ] 这个 Agent 的名称是什么？
- [ ] 它的系统提示词包含了哪些信息？
- [ ] 它绑定了哪些 MCP 工具？
- [ ] MCP 工具的 URL 从哪里来？
- [ ] 如果用户问"我的余额是多少"，流程是什么？

### 任务 4：体验并记录 Agent 行为

**测试场景**:
1. 访问前端，尝试以下对话：
   - "What's my account balance?"
   - "Show my recent transactions"
   - "I want to pay an invoice"（上传 `data/` 目录下的示例发票）

2. 记录你的观察：
   - 响应速度如何？
   - Agent 是否正确理解了你的意图？
   - 有没有出现"幻觉"（胡说八道）？

### 任务 5：阅读 Langchain4j 官方文档

**链接**: https://docs.langchain4j.dev/

**重点阅读**:
1. Getting Started（快速开始）
2. AI Services（AI 服务）
3. Tools（工具系统）
4. Memory（记忆管理）

**时间**: 2-3 小时

---

## 7. 学习检查清单

完成以下检查项，确认你已掌握第一阶段内容：

### 架构理解
- [ ] 能画出完整的系统架构图
- [ ] 能解释 Supervisor Agent 的作用
- [ ] 能说明 MCP 协议的价值
- [ ] 能描述一个用户请求的完整流转过程

### 代码理解
- [ ] 能找到所有 Agent 的代码文件
- [ ] 能解释 AccountMCPAgent 的工作原理
- [ ] 能说出 3 个领域 Agent 的区别
- [ ] 能理解 Docker Compose 的服务编排

### 技术栈
- [ ] 能列出项目使用的核心技术
- [ ] 能区分"已掌握"和"需学习"的技术
- [ ] 能解释 Langchain4j 的作用
- [ ] 能说明 Azure OpenAI 的集成方式

### 实战能力
- [ ] 能本地启动所有服务
- [ ] 能通过前端与 Agent 对话
- [ ] 能使用 Docker 命令查看容器状态
- [ ] 能查看服务日志排查问题

---

## 📚 推荐学习资源

### 官方文档
1. **Langchain4j**: https://docs.langchain4j.dev/
2. **Spring AI MCP**: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
3. **Azure OpenAI**: https://learn.microsoft.com/azure/ai-services/openai/

### 概念学习
1. **Generative AI For Beginners**: https://github.com/microsoft/generative-ai-for-beginners
2. **OpenAI Function Calling**: https://platform.openai.com/docs/guides/function-calling
3. **ReAct Pattern 论文**: https://arxiv.org/abs/2210.03629

### 本项目文档
1. `README.md` - 项目概览
2. `docs/multi-agents/introduction.md` - 多智能体架构说明
3. `AGENTS.md` - 技术文档（你正在看的）
4. `docs/faq.md` - 常见问题
5. `docs/troubleshooting.md` - 故障排查

---

## 🎯 下一步预告

完成第一阶段后，我们将进入**第二阶段：深入核心 - Agent 实现**

你会学习到：
- 🔍 深入阅读 4 个 Agent 的完整代码
- 🛠️ 修改现有 Agent，添加新功能
- 📝 编写你自己的第一个 Agent
- 🧪 调试 Agent 的工具调用流程

---

## 💡 学习建议

1. **不要急于写代码**：先理解，再动手
2. **多问"为什么"**：为什么用这个架构？为什么选这个框架？
3. **画图辅助理解**：流程图、架构图、时序图
4. **记录问题**：把疑问记下来，我们逐步解决
5. **实践驱动**：每学一个概念，立刻在代码中验证

---

## ❓ 遇到问题？

常见问题排查：

**Q1: Docker 容器启动失败？**
```powershell
# 查看容器日志
docker logs <container-name>

# 查看容器状态
docker ps -a
```

**Q2: 前端无法连接后端？**
- 检查 `compose.yaml` 中的服务名称和端口
- 确认所有容器状态为 Running
- 查看浏览器控制台的网络请求

**Q3: 代码看不懂？**
- 先关注整体流程，不要纠结细节
- 查阅 Langchain4j 官方文档
- 记录下来，我们一起讨论

---

**准备好了吗？开始你的第一阶段学习吧！** 🚀

有任何问题，随时告诉我。我会根据你的进度调整教学计划。
