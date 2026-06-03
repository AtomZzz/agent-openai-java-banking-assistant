# 🎯 Supervisor Agent 深度解析

> **目标**：理解监督者 Agent 的路由机制和多 Agent 协作模式
> **核心内容**：SupervisorAgent.java (123 行) 逐行解析
> **预计时间**：1-2 小时

---

## 📋 学习目录

1. [Supervisor Agent 概述](#1-supervisor-agent-概述)
2. [核心架构：为什么需要 Supervisor](#2-核心架构为什么需要-supervisor)
3. [逐段代码解析](#3-逐段代码解析)
4. [完整请求处理流程](#4-完整请求处理流程)
5. [与领域 Agent 的对比](#5-与领域-agent-的对比)
6. [学习检查点](#6-学习检查点)

---

## 1. Supervisor Agent 概述

### 1.1 Supervisor 是什么？

**Supervisor Agent（监督者 Agent）** 是整个多 Agent 系统的"大脑"和"路由器"。

**核心职责**：
1. **意图识别**：理解用户想要什么
2. **Agent 路由**：决定由哪个领域 Agent 处理
3. **请求转发**：将请求交给合适的 Agent

**类比**：就像医院的前台导诊
```
病人: "我肚子疼"
前台: 判断是内科问题 → 转给内科医生

病人: "我牙疼"
前台: 判断是牙科问题 → 转给牙科医生

病人: "我腿骨折了"
前台: 判断是骨科问题 → 转给骨科医生
```

---

### 1.2 Supervisor 在系统中的位置

```
┌─────────────────────────────────────────┐
│         前端 (React App)                 │
│  用户输入: "我的账户余额是多少？"         │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│   ChatController (REST API)              │
│   POST /api/chat                        │
└──────────────────┬──────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────┐
│   SupervisorAgent (监督者) ⭐ 核心       │
│   1. 分析用户意图                         │
│   2. 选择合适的 Agent                    │
│   3. 转发请求                           │
└──────────────────┬──────────────────────┘
                   │
         ┌─────────┼─────────┐
         │         │         │
         ▼         ▼         ▼
   ┌─────────┐ ┌─────────┐ ┌─────────┐
   │Account  │ │Payment  │ │Transact │
   │ Agent   │ │ Agent   │ │ Agent   │
   └─────────┘ └─────────┘ └─────────┘
```

---

## 2. 核心架构：为什么需要 Supervisor

### 2.1 没有 Supervisor 的世界（问题）

**假设**：如果前端直接调用领域 Agent

```java
// 前端代码
String userMessage = "我的账户余额是多少？";

// 前端需要自己决定调用哪个 Agent
// 这很困难！因为用户可能用不同的方式表达
if (userMessage.contains("余额") || userMessage.contains("账户")) {
    accountAgent.invoke(...);
} else if (userMessage.contains("交易") || userMessage.contains("历史")) {
    transactionAgent.invoke(...);
} else if (userMessage.contains("支付") || userMessage.contains("付款")) {
    paymentAgent.invoke(...);
} else {
    // 无法处理
    throw new Exception("Unknown request");
}
```

**问题**：
- ❌ 前端需要硬编码所有判断逻辑
- ❌ 关键词匹配不准确（"balance" vs "账户余额"）
- ❌ 无法处理模糊意图（"我的钱怎么样了？"）
- ❌ 每加一个 Agent，前端代码就要改
- ❌ 不支持语义理解

---

### 2.2 有 Supervisor 的世界（优势）

```java
// 前端代码（简化）
ChatMessage userMessage = UserMessage.from("我的账户余额是多少？");
List<ChatMessage> response = supervisorAgent.invoke(List.of(userMessage));

// 或者
List<ChatMessage> response = supervisorAgent.invoke(List.of(
    UserMessage.from("我的钱怎么样了？")  // 模糊表达也能处理
));
```

**Supervisor 的优势**：

| 优势 | 说明 | 示例 |
|-----|------|------|
| **智能路由** | 基于语义理解，不是关键词匹配 | "balance" 和 "余额" 都能路由到 Account |
| **解耦** | 前端无需知道有哪些 Agent | 添加新 Agent，前端代码不用改 |
| **灵活性** | 可以处理模糊意图 | "我的钱" → 可能是账户、交易、支付 |
| **扩展性** | 添加 Agent 很容易 | 只需注册新 Agent，Supervisor 自动发现 |
| **维护性** | 路由逻辑集中管理 | 修改路由，只改一处 |

---

## 3. 逐段代码解析

### 📄 文件：`SupervisorAgent.java`（123 行）

---

#### 3.1 类定义和成员变量

```java
public class SupervisorAgent {

    private final Logger LOGGER = LoggerFactory.getLogger(SupervisorAgent.class);
    private final ChatLanguageModel chatLanguageModel;  // ① LLM 模型
    private final List<Agent> agents;                   // ② 所有领域 Agent 列表
    private final Map<String, AgentMetadata> agentsMetadata;  // ③ Agent 元数据映射
    private final Prompt agentPrompt;                   // ④ 系统提示词
    //When false only detect the next agent but doesn't route to it. It will answer with the agent name.
    private Boolean routing = true;                     // ⑤ 是否执行路由
```

**① `chatLanguageModel`** - LLM 模型
- 和领域 Agent 一样，使用 Azure OpenAI 或 OpenAI
- 用于理解用户意图和选择 Agent

**② `agents`** - 所有领域 Agent 列表
```java
List<Agent> agents = List.of(
    accountAgent,       // 账户 Agent
    transactionAgent,   // 交易 Agent
    paymentAgent        // 支付 Agent
);
```

**③ `agentsMetadata`** - Agent 元数据映射
```java
Map<String, AgentMetadata> agentsMetadata = {
    "AccountAgent" → AgentMetadata("Personal financial advisor for..."),
    "TransactionHistoryAgent" → AgentMetadata("Agent for querying transaction..."),
    "PaymentAgent" → AgentMetadata("Agent for processing payments...")
};
```

**为什么用 Map？**
- 快速查找：根据 Agent 名称快速找到元数据
- 用于路由决策

**④ `agentPrompt`** - 系统提示词
- 告诉 LLM "你是监督者，负责选择合适的 Agent"
- 包含所有 Agent 的描述

**⑤ `routing`** - 是否执行路由
- `true`（默认）：找到 Agent 后直接调用
- `false`：只返回 Agent 名称，不调用（用于测试或调试）

---

#### 3.2 系统提示词 - 路由的关键 ⭐⭐⭐

```java
private final String SUPERVISOR_AGENT_SINGLETURN_SYSTEM_MESSAGE = """
    You are a banking customer support agent triaging conversation and 
    select the best agent name that can solve the customer need.
    Use the below list of agents metadata to select the best one for 
    the customer request:
    {{agentsMetadata}}
    Answer only with the agent name.
    if you are not able to select an agent answer with none.
    """;
```

**逐句解读**：

| 句子 | 作用 | 重要性 |
|-----|------|--------|
| `You are a banking customer support agent` | 定义角色：银行客服 | ⭐⭐⭐ |
| `triaging conversation and select the best agent name` | 定义职责：分类对话，选择最佳 Agent | ⭐⭐⭐⭐ |
| `Use the below list of agents metadata` | 说明依据：使用下方的 Agent 元数据 | ⭐⭐⭐ |
| `Answer only with the agent name` | **约束输出：只返回 Agent 名称** | ⭐⭐⭐⭐⭐ |
| `if you are not able to select an agent answer with none` | 处理无法路由的情况 | ⭐⭐⭐ |

**为什么 "Answer only with the agent name" 很重要？**

**好的提示词**：
```
Answer only with the agent name.

LLM 返回: "AccountAgent"
代码可以直接使用 ✅
```

**不好的提示词**（假设）：
```
Please select the best agent and explain why.

LLM 返回: "I think the AccountAgent is the best choice because 
           the user is asking about account balance, and the 
           AccountAgent specializes in account information."
代码需要解析才能使用 ❌
```

**约束输出**可以：
- ✅ 简化代码逻辑
- ✅ 提高准确性
- ✅ 减少解析错误

---

#### 3.3 构造函数 - 组装 Supervisor

```java
public SupervisorAgent(ChatLanguageModel chatLanguageModel, 
                      List<Agent> agents, 
                      Boolean routing) {
    this.chatLanguageModel = chatLanguageModel;
    this.agents = agents;
    this.routing = routing;

    // ① 构建 Agent 元数据映射
    this.agentsMetadata = agents.stream()
            .collect(Collectors.toMap(Agent::getName, Agent::getMetadata));

    // ② 使用模板替换变量
    PromptTemplate promptTemplate = PromptTemplate.from(SUPERVISOR_AGENT_SINGLETURN_SYSTEM_MESSAGE);
    agentPrompt = promptTemplate.apply(Map.of("agentsMetadata", this.agentsMetadata));
}

// 简化构造函数（默认 routing = true）
public SupervisorAgent(ChatLanguageModel chatLanguageModel, List<Agent> agents) {
   this(chatLanguageModel, agents, true);
}
```

**① 构建元数据映射**

```java
// 输入: List<Agent>
[
    AccountMCPAgent { name: "AccountAgent", metadata: {...} },
    TransactionHistoryMCPAgent { name: "TransactionHistoryAgent", metadata: {...} },
    PaymentMCPAgent { name: "PaymentAgent", metadata: {...} }
]

// 输出: Map<String, AgentMetadata>
{
    "AccountAgent" → { description: "Personal financial advisor...", capabilities: [...] },
    "TransactionHistoryAgent" → { description: "Agent for querying transaction...", capabilities: [...] },
    "PaymentAgent" → { description: "Agent for processing payments...", capabilities: [...] }
}
```

**② 应用模板**

```java
// 模板:
"""
Use the below list of agents metadata:
{{agentsMetadata}}
"""

// 替换后:
"""
Use the below list of agents metadata:
{
  AccountAgent=AgentMetadata{...},
  TransactionHistoryAgent=AgentMetadata{...},
  PaymentAgent=AgentMetadata{...}
}
"""
```

---

#### 3.4 核心方法：`invoke()` - 处理用户请求

```java
public List<ChatMessage> invoke(List<ChatMessage> chatHistory) {
    LOGGER.info("------------- SupervisorAgent -------------");

    // ① 构建内部对话记忆
    var internalChatMemory = buildInternalChat(chatHistory);

    // ② 构建请求
    ChatRequest request = ChatRequest.builder()
            .messages(internalChatMemory.messages())
            .build();

    // ③ 调用 LLM，让它选择 Agent
    AiMessage aiMessage = chatLanguageModel.chat(request).aiMessage();
    String nextAgent = aiMessage.text();  // 例如: "AccountAgent"
    LOGGER.info("Supervisor Agent handoff to [{}]", nextAgent);

    // ④ 根据配置决定是否路由
    if (routing) {
       return singleTurnRouting(nextAgent, chatHistory);
    }

    return new ArrayList<>();
}
```

**① `buildInternalChat()`** - 构建对话记忆
- 稍后详细讲解
- 关键：过滤掉工具调用相关的消息

**② 构建请求**
- 只包含对话历史，**不包含工具**
- 为什么？因为 Supervisor 不需要工具，只需要选择 Agent

**③ 调用 LLM**
```java
// 请求:
ChatRequest = {
    messages: [
        SystemMessage("You are a banking customer support..."),
        UserMessage("我的账户余额是多少？")
    ]
}

// LLM 返回:
AiMessage = {
    text: "AccountAgent",  // ← 选择的 Agent
    toolExecutionRequests: []
}
```

**④ 路由决策**
- `routing = true`：调用选中的 Agent
- `routing = false`：只返回 Agent 名称（不执行）

---

#### 3.5 `singleTurnRouting()` - 执行路由

```java
protected List<ChatMessage> singleTurnRouting(String nextAgent, 
                                             List<ChatMessage> chatHistory) {
    // ① 处理 "none" 的情况（无法路由）
    if("none".equalsIgnoreCase(nextAgent)){
        LOGGER.info("Gracefully handle clarification.. ");
        AiMessage clarificationMessage = AiMessage.builder().
                text(" I'm not sure about your request. Can you please clarify?")
                .build();
        chatHistory.add(clarificationMessage);
        return chatHistory;
    }

    // ② 根据 Agent 名称找到对应的 Agent
    Agent agent = agents.stream()
            .filter(a -> a.getName().equals(nextAgent))
            .findFirst()
            .orElseThrow(() -> new AgentExecutionException("Agent not found: " + nextAgent));

    // ③ 调用选中的 Agent
    return agent.invoke(chatHistory);
}
```

**① 处理 "none"**

**场景**：用户的问题超出系统能力范围

```
用户: "你能帮我买股票吗？"

Supervisor 思考: 
- AccountAgent: 查询账户信息 ❌
- TransactionAgent: 查询交易历史 ❌
- PaymentAgent: 处理支付 ❌
- 没有合适的 Agent → 返回 "none"

系统响应: "I'm not sure about your request. Can you please clarify?"
```

**好处**：
- 优雅处理无法处理的请求
- 不崩溃，给用户友好的提示

**② 查找 Agent**

```java
// agents = [AccountAgent, TransactionAgent, PaymentAgent]
// nextAgent = "AccountAgent"

Agent agent = agents.stream()
    .filter(a -> a.getName().equals("AccountAgent"))  // 过滤
    .findFirst()                                        // 找到第一个
    .orElseThrow(...);                                  // 找不到抛异常

// 结果: AccountAgent 实例
```

**③ 调用 Agent**

```java
// 将用户对话历史交给选中的 Agent
List<ChatMessage> response = accountAgent.invoke(chatHistory);

// AccountAgent 开始执行:
// 1. 分析意图: "用户要查余额"
// 2. 调用工具: getAccount(username)
// 3. 生成回复: "您的账户余额是 5000 美元"
// 4. 返回 response
```

---

#### 3.6 `buildInternalChat()` - 构建对话记忆 ⭐

```java
private ChatMemory buildInternalChat(List<ChatMessage> chatHistory) {
    //build a new chat memory to preserve order of messages otherwise 
    //the model hallucinate.
    var internalChatMemory = MessageWindowChatMemory.builder()
            .id("default")
            .maxMessages(20)
            .build();

    // ① 添加系统提示词
    internalChatMemory.add(
        dev.langchain4j.data.message.SystemMessage.from(agentPrompt.text())
    );
    
    // ② 过滤掉工具相关的消息
    chatHistory.stream()
            .filter(chatMessage -> {
                // 过滤掉工具执行结果
                if (chatMessage instanceof ToolExecutionResultMessage) {
                    return false;
                }
                // 过滤掉 AI 的工具请求（但保留文本回复）
                if (chatMessage instanceof AiMessage) {
                    return !((AiMessage) chatMessage).hasToolExecutionRequests();
                }
                return true;
            })
            .forEach(internalChatMemory::add);
    
    return internalChatMemory;
}
```

**① 添加系统提示词**
- 告诉 LLM "你是监督者，负责选择 Agent"
- 包含所有 Agent 的描述

**② 过滤工具相关消息** - **这是关键！**

**为什么需要过滤？**

**场景**：用户和 AI 已经对话了几轮

```
对话历史:
1. User: "我的账户余额是多少？"
2. AI: "调用 getAccount 工具"              ← ToolExecutionRequest
3. ToolResult: "{balance: 5000}"           ← ToolExecutionResultMessage
4. AI: "您的余额是 5000 美元"              ← AiMessage (text)
5. User: "我上个月给 John 转了多少钱？"
```

**如果不过滤**（给 Supervisor 看所有消息）：
```
1. User: "我的账户余额是多少？"
2. AI: "调用 getAccount 工具"              ← Supervisor 可能困惑
3. ToolResult: "{balance: 5000}"           ← Supervisor 可能困惑
4. AI: "您的余额是 5000 美元"
5. User: "我上个月给 John 转了多少钱？"

Supervisor 思考: "这些工具调用结果是什么意思？我应该选哪个 Agent？"
→ 可能做出错误决策 ❌
```

**过滤后**（只给 Supervisor 看关键消息）：
```
1. User: "我的账户余额是多少？"
2. AI: "您的余额是 5000 美元"              ← 保留文本回复
3. User: "我上个月给 John 转了多少钱？"    ← 新问题

Supervisor 思考: "用户在询问转账历史，这是 TransactionAgent 的职责"
→ 准确选择 TransactionAgent ✅
```

**过滤规则**：

| 消息类型 | 是否保留 | 原因 |
|---------|---------|------|
| `SystemMessage` | 保留 | 系统提示词 |
| `UserMessage` | 保留 | 用户输入 |
| `AiMessage`（有工具请求） | **过滤** | 工具调用细节 |
| `AiMessage`（只有文本） | 保留 | AI 的回复 |
| `ToolExecutionResultMessage` | **过滤** | 工具执行结果 |

**核心价值**：
- ✅ 让 Supervisor 专注于"理解用户意图"
- ✅ 不被工具调用细节干扰
- ✅ 提高路由准确性

---

## 4. 完整请求处理流程

### 4.1 场景：用户问 "What's my account balance?"

```
┌──────────────────────────────────────────────────────┐
│ 1. 前端发送请求                                       │
│    POST /api/chat                                    │
│    {                                                 │
│      "messages": [                                   │
│        { role: "user", content: "What's my account   │
│                                   balance?" }        │
│      ]                                               │
│    }                                                 │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 2. ChatController 接收请求                            │
│                                                       │
│    chatHistory = [                                    │
│      UserMessage("What's my account balance?")        │
│    ]                                                  │
│                                                       │
│    调用: supervisorAgent.invoke(chatHistory)          │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 3. SupervisorAgent.invoke() 执行                      │
│                                                       │
│    3a. 构建对话记忆                                    │
│    internalChatMemory = [                             │
│      SystemMessage("You are a banking customer...    │
│                    Use the below list of agents...   │
│                    Answer only with the agent name"), │
│      UserMessage("What's my account balance?")        │
│    ]                                                  │
│                                                       │
│    3b. 调用 LLM (Azure OpenAI)                         │
│    请求: {                                            │
│      messages: [...对话历史...]                       │
│    }                                                  │
│                                                       │
│    LLM 思考: "用户要查余额 → 这需要账户信息"          │
│    LLM 查看 Agent 描述:                               │
│    - AccountAgent: "bank account information" ✅      │
│    - TransactionAgent: "transaction history"          │
│    - PaymentAgent: "processing payments"              │
│                                                       │
│    LLM 返回: { text: "AccountAgent" }                │
│                                                       │
│    3c. 解析选择的 Agent                                │
│    nextAgent = "AccountAgent"                         │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 4. singleTurnRouting() 执行路由                       │
│                                                       │
│    4a. 检查是否为 "none"                               │
│    "AccountAgent" ≠ "none" → 继续                     │
│                                                       │
│    4b. 查找 Agent                                     │
│    agents.stream()                                    │
│      .filter(a -> a.getName().equals("AccountAgent")) │
│      .findFirst()                                     │
│      → AccountAgent 实例                              │
│                                                       │
│    4c. 调用 AccountAgent                              │
│    accountAgent.invoke(chatHistory)                   │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 5. AccountAgent 执行                                  │
│                                                       │
│    5a. 构建对话记忆                                    │
│    5b. 获取工具列表                                    │
│    5c. 调用 LLM → 决定调用 getAccount 工具             │
│    5d. 执行工具，获取结果                              │
│    5e. 再次调用 LLM → 生成回复                         │
│    5f. 返回: "您的账户余额是 5000 美元"                │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 6. 返回给前端                                         │
│                                                       │
│    response = [                                       │
│      UserMessage("What's my account balance?"),       │
│      AiMessage("您的账户余额是 5000 美元")             │
│    ]                                                  │
│                                                       │
│    前端显示给用户                                      │
└──────────────────────────────────────────────────────┘
```

---

### 4.2 场景：用户问 "你能帮我买股票吗？"（无法路由）

```
┌──────────────────────────────────────────────────────┐
│ 1. 用户输入: "Can you help me buy stocks?"            │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 2. SupervisorAgent 分析                               │
│                                                       │
│    LLM 思考:                                          │
│    - 买股票需要什么 Agent？                            │
│    - AccountAgent: 查询账户 ❌                         │
│    - TransactionAgent: 查询交易 ❌                     │
│    - PaymentAgent: 处理支付 ❌                         │
│    - 没有合适的 Agent → 返回 "none"                    │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 3. singleTurnRouting() 处理                           │
│                                                       │
│    if("none".equalsIgnoreCase(nextAgent)){            │
│      // 添加澄清消息                                   │
│      chatHistory.add(AiMessage.from(                  │
│        "I'm not sure about your request.              │
│         Can you please clarify?"                      │
│      ));                                              │
│      return chatHistory;                              │
│    }                                                  │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 4. 返回给用户                                         │
│                                                       │
│    "I'm not sure about your request.                  │
│     Can you please clarify?"                          │
│                                                       │
│    用户可以重新表述问题                                │
└──────────────────────────────────────────────────────┘
```

---

## 5. 与领域 Agent 的对比

### 5.1 核心差异

| 维度 | Supervisor Agent | Domain Agent (Account/Payment/Transaction) |
|-----|------------------|-------------------------------------------|
| **职责** | 理解意图，选择 Agent | 解决特定领域的问题 |
| **工具** | ❌ 不使用工具 | ✅ 使用 MCP 工具 |
| **输出** | Agent 名称 | 用户的最终回复 |
| **循环** | ❌ 单次调用 LLM | ✅ ReAct 循环（多次调用） |
| **系统提示词** | "你是监督者，选择最佳 Agent" | "你是财务顾问/支付专家..." |
| **代码位置** | `SupervisorAgent.java` | `AccountMCPAgent.java` 等 |

---

### 5.2 执行流程对比

**Supervisor Agent**:
```java
invoke(chatHistory) {
    memory = buildInternalChat(chatHistory);
    aiMessage = chatModel.chat(memory);  // ← 只调用一次
    
    if ("none".equals(aiMessage.text())) {
        return clarificationMessage;
    }
    
    agent = findAgent(aiMessage.text());
    return agent.invoke(chatHistory);  // ← 转发给领域 Agent
}
```

**Domain Agent (AccountMCPAgent)**:
```java
invoke(chatHistory) {
    memory = buildInternalChat(chatHistory);
    tools = getToolSpecifications();
    
    aiMessage = chatModel.chat(memory, tools);  // ← 第一次调用
    
    while (aiMessage.hasToolExecutionRequests()) {  // ← 循环
        results = executeToolRequests(aiMessage.toolExecutionRequests());
        memory.add(results);
        aiMessage = chatModel.chat(memory, tools);  // ← 多次调用
    }
    
    return aiMessage;
}
```

**关键区别**：
- Supervisor: **单次调用**，只做路由
- Domain Agent: **ReAct 循环**，调用工具，生成回复

---

### 5.3 系统提示词对比

**Supervisor**:
```java
"""
You are a banking customer support agent triaging conversation and 
select the best agent name that can solve the customer need.
Use the below list of agents metadata to select the best one for 
the customer request:
{{agentsMetadata}}
Answer only with the agent name.
if you are not able to select an agent answer with none.
"""
```

**AccountAgent**:
```java
"""
you are a personal financial advisor who help the user to retrieve 
information about their bank accounts.
Use html list or table to display the account information.
Always use the below logged user details to retrieve account info:
'{{loggedUserName}}'
"""
```

**对比**：

| 维度 | Supervisor | AccountAgent |
|-----|-----------|--------------|
| **角色** | 银行客服（路由器） | 财务顾问（专家） |
| **职责** | 选择最佳 Agent | 帮助查询账户信息 |
| **输出约束** | 只返回 Agent 名称 | 用 HTML 格式展示信息 |
| **输入** | Agent 元数据列表 | 登录用户名 |

---

## 6. 学习检查点

### 基础理解

#### ✅ 问题 1：Supervisor Agent 的核心职责是什么？

**答案**：
1. **意图识别**：理解用户想要什么
2. **Agent 选择**：从多个 Agent 中选择最合适的
3. **请求路由**：将请求转发给选中的 Agent

---

#### ✅ 问题 2：为什么系统提示词要求 "Answer only with the agent name"？

**答案**：

**原因 1：简化代码**
```java
// 好的提示词
String nextAgent = aiMessage.text();  // "AccountAgent"
agent = findAgent(nextAgent);

// 不好的提示词
String response = aiMessage.text();  
// "I think the AccountAgent is the best choice because..."
// 需要复杂的解析逻辑 ❌
```

**原因 2：提高准确性**
- LLM 可能会"解释"为什么选择这个 Agent
- 但代码只需要 Agent 名称
- 约束输出可以避免不必要的解释

**原因 3：减少错误**
```java
// 如果 LLM 返回:
"The AccountAgent would be the most appropriate choice for 
 this request because it specializes in account information."

// 代码需要:
String agentName = extractAgentName(response);  // 复杂且容易出错
```

---

#### ✅ 问题 3：`buildInternalChat()` 为什么要过滤工具相关的消息？

**答案**：

**场景**：
```
对话历史:
1. User: "我的余额是多少？"
2. AI: "调用 getAccount"              ← 工具请求
3. ToolResult: "{balance: 5000}"      ← 工具结果
4. AI: "您的余额是 5000 美元"         ← 文本回复
5. User: "我上个月给 John 转了多少钱？"
```

**如果不过滤**：
```
Supervisor 看到:
1. User: "我的余额是多少？"
2. AI: "调用 getAccount"
3. ToolResult: "{balance: 5000}"
4. AI: "您的余额是 5000 美元"
5. User: "我上个月给 John 转了多少钱？"

Supervisor 可能困惑:
- "这些工具调用是什么意思？"
- "用户是不是在问工具相关的东西？"
→ 可能选择错误的 Agent ❌
```

**过滤后**：
```
Supervisor 看到:
1. User: "我的余额是多少？"
2. AI: "您的余额是 5000 美元"
3. User: "我上个月给 John 转了多少钱？"

Supervisor 清晰理解:
- "用户之前在问账户余额"
- "现在在问转账历史"
- "这是 TransactionAgent 的职责"
→ 准确选择 TransactionAgent ✅
```

**核心价值**：
- 让 Supervisor 专注于"理解用户意图"
- 不被工具调用细节干扰
- 提高路由准确性

---

### 架构理解

#### ✅ 问题 4：Supervisor 和 Domain Agent 的执行流程有什么区别？

**答案**：

**Supervisor Agent**:
```
单次调用 LLM → 选择 Agent → 转发请求
```

**特点**：
- ❌ 不调用工具
- ❌ 不循环
- ✅ 单次决策
- ✅ 快速路由

**Domain Agent**:
```
调用 LLM → 决定工具 → 执行工具 → 观察结果 
→ 继续循环 → 生成最终回复
```

**特点**：
- ✅ 调用 MCP 工具
- ✅ ReAct 循环（可能多次）
- ✅ 生成最终回复
- ✅ 处理复杂任务

---

#### ✅ 问题 5：如果用户的问题超出了系统能力范围，Supervisor 如何处理？

**答案**：

**流程**：
```
用户: "你能帮我买股票吗？"

Supervisor 思考:
- AccountAgent: 查询账户 ❌
- TransactionAgent: 查询交易 ❌
- PaymentAgent: 处理支付 ❌
- 没有合适的 Agent

LLM 返回: "none"

Supervisor 处理:
if("none".equalsIgnoreCase(nextAgent)){
    chatHistory.add(AiMessage.from(
        "I'm not sure about your request. Can you please clarify?"
    ));
    return chatHistory;
}
```

**结果**：
- 不崩溃
- 给用户友好的提示
- 用户可以重新表述问题

---

#### ✅ 问题 6：`routing` 参数的作用是什么？什么时候会设为 false？

**答案**：

**`routing = true`（默认）**：
```java
// 找到 Agent 后直接调用
if (routing) {
    return singleTurnRouting(nextAgent, chatHistory);
    // 实际执行: accountAgent.invoke(chatHistory)
}
```

**`routing = false`**：
```java
// 只返回 Agent 名称，不调用
if (routing) {
    return singleTurnRouting(nextAgent, chatHistory);
}
return new ArrayList<>();  // 返回空列表
// 实际效果: 不执行任何 Agent
```

**使用场景**：
- **测试**：验证 Supervisor 能否正确选择 Agent
- **调试**：查看 Supervisor 的选择，但不实际执行
- **性能优化**：在某些情况下只需要知道 Agent 名称

**测试示例**：
```java
@Test
public void supervisorShouldSelectCorrectAgent() {
    SupervisorAgent supervisor = new SupervisorAgent(
        chatModel, 
        List.of(accountAgent, transactionAgent, paymentAgent),
        false  // ← 不执行路由
    );
    
    List<ChatMessage> response = supervisor.invoke(
        List.of(UserMessage.from("What's my balance?"))
    );
    
    // 验证 Supervisor 选择了 AccountAgent
    // 但不实际执行 AccountAgent
}
```

---

## 7. 实战：追踪一个完整请求

让我们手动追踪一个请求，加深理解。

### 场景：用户问 "Show my recent transactions"

**步骤 1：前端发送请求**
```javascript
POST /api/chat
{
  "messages": [
    { role: "user", content: "Show my recent transactions" }
  ]
}
```

**步骤 2：ChatController 接收**
```java
chatHistory = [UserMessage.from("Show my recent transactions")];
supervisorAgent.invoke(chatHistory);
```

**步骤 3：Supervisor 分析**
```java
// 构建记忆
internalChatMemory = [
    SystemMessage("You are a banking customer... Answer only with the agent name"),
    UserMessage("Show my recent transactions")
]

// 调用 LLM
aiMessage = chatModel.chat(internalChatMemory).aiMessage();
// LLM 返回: "TransactionHistoryAgent"

// 解析
nextAgent = "TransactionHistoryAgent"
```

**步骤 4：路由**
```java
agent = agents.stream()
    .filter(a -> a.getName().equals("TransactionHistoryAgent"))
    .findFirst()
    .get();
// 找到 TransactionHistoryMCPAgent

// 调用
agent.invoke(chatHistory);
```

**步骤 5：TransactionHistoryMCPAgent 执行**
```java
// 构建记忆
memory = [
    SystemMessage("you are a transaction history specialist..."),
    UserMessage("Show my recent transactions")
]

// 获取工具
tools = [searchTransactions, getTransactionsByRecipient, ...]

// 第一次调用 LLM
aiMessage = chatModel.chat(memory, tools);
// LLM 返回: ToolExecutionRequest(searchTransactions)

// 执行工具
result = executeTool("searchTransactions");
// 返回: [{ amount: 100, recipient: "Amazon", ... }]

// 第二次调用 LLM
aiMessage = chatModel.chat(memory + result);
// LLM 返回: "Here are your recent transactions: ..."

// 返回
```

**步骤 6：返回给前端**
```java
response = [
    UserMessage("Show my recent transactions"),
    AiMessage("Here are your recent transactions:\n- Amazon: $100\n- ...")
]
```

---

## 🎓 总结

### 你已经掌握了

1. **Supervisor 的核心职责**：意图识别 + Agent 路由
2. **系统提示词设计**：约束输出，提高准确性
3. **对话记忆过滤**：只给 Supervisor 看关键信息
4. **路由机制**：单次调用 vs ReAct 循环
5. **错误处理**：优雅处理无法路由的情况

### 核心要点

| 要点 | 说明 |
|-----|------|
| **智能路由** | 基于语义理解，不是关键词匹配 |
| **解耦设计** | 前端无需知道有哪些 Agent |
| **约束输出** | "Answer only with the agent name" |
| **消息过滤** | 过滤工具相关消息，提高准确性 |
| **优雅降级** | 无法路由时返回 "none" 并提示用户 |

---

## 🚀 下一步

恭喜你完成了 Supervisor Agent 的学习！现在你已经理解了整个多 Agent 架构的核心。

**接下来我们将**：
1. **实战：修改 Account Agent** - 添加新功能
2. **深入 Payment Agent** - 学习最复杂的 Agent（包含 OCR）

**准备好了吗？告诉我你想从哪里开始！** 📚
