# 🔍 第二阶段：Agent 代码深度解析

> **目标**：从零开始理解 Langchain4j 框架和 Agent 实现
> **前置知识**：已理解 AI 基本概念，对 Langchain4j 零基础
> **预计时间**：2-3 小时

---

## 📋 学习计划

我们将按照**依赖关系**和**从抽象到具体**的顺序阅读代码：

```
1. Agent.java (接口)              ← 定义 Agent 长什么样
2. AbstractReActAgent.java (抽象类) ← 实现 ReAct 模式的核心逻辑
3. MCPToolAgent.java (抽象类)      ← 专门处理 MCP 工具的 Agent
4. AccountMCPAgent.java (具体实现)  ← 第一个完整的 Agent
```

---

## 第一部分：Agent 接口 - 定义契约

### 📄 文件：`Agent.java`（13 行）

```java
package com.microsoft.langchain4j.agent;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

public interface Agent {

    String getName();                                    // ①
    AgentMetadata getMetadata();                         // ②
    List<ChatMessage> invoke(List<ChatMessage> chatHistory) throws AgentExecutionException;  // ③
}
```

### 🔍 逐行解析

#### ① `getName()` - Agent 的名称
```java
String getName();
```
**作用**：返回 Agent 的唯一标识符  
**示例**：`"AccountAgent"`, `"PaymentAgent"`, `"SupervisorAgent"`  
**为什么需要**：
- 日志记录：知道哪个 Agent 在处理请求
- 调试：追踪问题时有明确的标识
- Supervisor 路由：通过名称识别不同的 Agent

---

#### ② `getMetadata()` - Agent 的元数据
```java
AgentMetadata getMetadata();
```
**作用**：返回 Agent 的描述信息，**这是 Supervisor 路由的关键！**

**AgentMetadata 包含什么**（稍后你会看到）：
```java
new AgentMetadata(
    "Personal financial advisor for retrieving bank account information.",  // 描述
    List.of("RetrieveAccountInfo", "DisplayAccountDetails")                // 能力标签
);
```

**💡 核心理解**：
- Supervisor Agent 就是通过这个**描述**来决定路由到哪个 Agent
- 例如：用户问"我的余额是多少？"
  - Supervisor 看到 Account Agent 的描述包含 "bank account information"
  - 自动选择 Account Agent

---

#### ③ `invoke()` - 执行 Agent 的核心方法
```java
List<ChatMessage> invoke(List<ChatMessage> chatHistory) throws AgentExecutionException;
```
**作用**：接收对话历史，返回 Agent 的响应

**参数**：`chatHistory` - 用户和 AI 的对话历史  
**返回值**：`List<ChatMessage>` - Agent 处理后的完整对话  
**异常**：`AgentExecutionException` - Agent 执行失败时抛出

**类比理解**：
```
就像调用一个函数:
  输入: 用户的对话历史
  处理: Agent 理解意图 → 调用工具 → 生成回复
  输出: 包含回复的新对话历史
```

---

### ✅ 小结：Agent 接口的核心思想

一个 Agent 必须具备：
1. **名字**（我是谁）
2. **描述**（我能做什么）← Supervisor 靠这个路由
3. **执行方法**（干活的能力）

---

## 第二部分：AbstractReActAgent - ReAct 模式实现

### 📄 文件：`AbstractReActAgent.java`（116 行）

这是**整个项目的核心**，理解了它，你就理解了 AI Agent 的工作原理！

### 🎯 什么是 ReAct 模式？

**ReAct = Reasoning（推理）+ Acting（行动）**

传统编程：
```
输入 → 处理逻辑 → 输出
```

ReAct 模式：
```
输入 → 思考(Reasoning) → 决定用什么工具 → 执行工具(Acting) 
     → 观察结果 → 继续思考 → ... → 输出最终答案
```

**生活中的例子**：
```
问题: "北京明天的天气怎么样？"

传统程序: 无法回答（没有天气数据）

ReAct Agent:
1. 思考: 我不知道天气，但我有个"查询天气"的工具
2. 行动: 调用 weatherApi("北京", "明天")
3. 观察: 工具返回 "晴天，25°C"
4. 思考: 现在我有答案了
5. 输出: "北京明天是晴天，气温 25°C"
```

---

### 🔍 逐段代码解析

#### 2.1 类的定义和成员变量

```java
public abstract class AbstractReActAgent implements Agent {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractReActAgent.class);

    protected final ChatLanguageModel chatModel;  // ①
```

**① `ChatLanguageModel`** - 这就是 **LLM（大语言模型）**！

**Langchain4j 的抽象**：
```
ChatLanguageModel (接口)
    ├── AzureOpenAiChatModel (Azure OpenAI 实现)
    ├── OpenAiChatModel (OpenAI 原生实现)
    └── 其他实现...
```

**为什么用接口**？
- 解耦：代码不依赖具体的 LLM 提供商
- 可替换：可以轻松从 Azure OpenAI 切换到其他模型

**类比**：就像 Spring 的 `DataSource` 接口，可以切换 MySQL/PostgreSQL

---

#### 2.2 构造函数

```java
protected AbstractReActAgent(ChatLanguageModel chatModel) {
    if (chatModel == null) {
        throw new IllegalArgumentException("chatModel cannot be null");
    }
    this.chatModel = chatModel;
}
```

**作用**：初始化 Agent 时必须提供 LLM  
**思考**：为什么是 `protected`？  
**答案**：因为这是抽象类，只能被子类调用

---

#### 2.3 核心方法：`invoke()` - Agent 的执行流程 ⭐⭐⭐

这是**最重要的方法**，请仔细阅读注释：

```java
@Override
public List<ChatMessage> invoke(List<ChatMessage> chatHistory) throws AgentExecutionException {
    LOGGER.info("------------- {} -------------", this.getName());

    try {
        // 步骤 1: 构建内部对话记忆
        var internalChatMemory = buildInternalChat(chatHistory);  // ①

        // 步骤 2: 配置工具参数
        ChatRequestParameters parameters = ChatRequestParameters.builder()
            .toolSpecifications(getToolSpecifications())  // ② 告诉 LLM 有哪些工具可用
            .build();

        // 步骤 3: 构建请求
        ChatRequest request = ChatRequest.builder()
            .messages(internalChatMemory.messages())  // ③ 对话历史
            .parameters(parameters)                   // ④ 工具列表
            .build();

        // 步骤 4: 第一次调用 LLM
        var aiMessage = chatModel.chat(request).aiMessage();  // ⑤

        // 步骤 5: ReAct 循环 - 处理工具调用
        while (aiMessage != null && aiMessage.hasToolExecutionRequests()) {  // ⑥
            // 执行工具
            List<ToolExecutionResultMessage> toolExecutionResultMessages = 
                executeToolRequests(aiMessage.toolExecutionRequests());  // ⑦

            // 将 AI 的思考和工具结果添加到记忆
            internalChatMemory.add(aiMessage);                           // ⑧
            toolExecutionResultMessages.forEach(internalChatMemory::add);

            // 再次请求 LLM，让它基于工具结果继续思考
            ChatRequest toolExecutionResultResponseRequest = ChatRequest.builder()
                .messages(internalChatMemory.messages())
                .parameters(parameters)
                .build();

            aiMessage = chatModel.chat(toolExecutionResultResponseRequest).aiMessage();  // ⑨
        }

        LOGGER.info("Agent response: {}", aiMessage.text());

        // 步骤 6: 添加最后的 AI 回复到记忆
        internalChatMemory.add(aiMessage);
        return buildResponse(chatHistory, internalChatMemory);  // ⑩
    } catch (Exception e) {
        throw new AgentExecutionException("Error during agent [%s] invocation".formatted(this.getName()), e);
    }
}
```

### 🎬 详细执行流程图解

假设用户问：**"我的账户余额是多少？"**

```
┌──────────────────────────────────────────────────┐
│ 步骤 1: 构建对话记忆                              │
│ internalChatMemory = [                            │
│   SystemMessage("你是财务助手..."),               │
│   UserMessage("我的账户余额是多少？")              │
│ ]                                                 │
└──────────────────┬───────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────┐
│ 步骤 2-3: 构建请求                                │
│ ChatRequest = {                                   │
│   messages: [...上面的对话...],                   │
│   parameters: {                                   │
│     tools: [                                      │
│       getAccount(username),                       │
│       getPaymentMethods(),                        │
│       getBeneficiaries()                          │
│     ]                                             │
│   }                                               │
│ }                                                 │
└──────────────────┬───────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────┐
│ 步骤 4: 第一次调用 LLM (Azure OpenAI)             │
│                                                   │
│ LLM 思考: "用户要查余额，我应该调用 getAccount"   │
│                                                   │
│ LLM 返回 aiMessage: {                             │
│   text: null,  // 还没有最终答案                  │
│   toolExecutionRequests: [                        │
│     ToolExecutionRequest(                         │
│       name: "getAccount",                         │
│       arguments: { username: "john.doe" }         │
│     )                                             │
│   ]                                               │
│ }                                                 │
└──────────────────┬───────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────┐
│ 步骤 5: 进入 while 循环                           │
│ 条件: aiMessage.hasToolExecutionRequests() = true │
│                                                   │
│ 步骤 5a: 执行工具                                 │
│ executeToolRequests([getAccount 请求])            │
│   → 调用实际的 REST API                           │
│   → 返回: { balance: 5000, currency: "USD" }     │
│                                                   │
│ 步骤 5b: 将结果添加到记忆                         │
│ internalChatMemory 现在是:                        │
│ [                                                 │
│   SystemMessage("你是财务助手..."),               │
│   UserMessage("我的账户余额是多少？"),            │
│   AIMessage("调用 getAccount..."),    ← LLM的思考 │
│   ToolExecutionResultMessage(         ← 工具结果  │
│     result: "{balance: 5000}"                     │
│   )                                               │
│ ]                                                 │
│                                                   │
│ 步骤 5c: 再次调用 LLM                             │
│ LLM 看到工具结果后思考:                           │
│ "好的，余额是 5000 美元，我可以回答用户了"        │
│                                                   │
│ LLM 返回新的 aiMessage: {                         │
│   text: "您的账户余额是 5000 美元",               │
│   toolExecutionRequests: []  // 空！不需要工具了  │
│ }                                                 │
└──────────────────┬───────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────┐
│ 步骤 6: 退出 while 循环                           │
│ 条件: aiMessage.hasToolExecutionRequests() = false│
│                                                   │
│ 返回最终的对话历史给用户                          │
└──────────────────────────────────────────────────┘
```

---

### 🔍 关键方法详解

#### ⑥ `hasToolExecutionRequests()` - LLM 是否在请求工具？

**LLM 的两种响应**：

**情况 1：需要调用工具**
```java
aiMessage = {
    text: null,  // 没有直接回复
    toolExecutionRequests: [
        ToolExecutionRequest(
            name: "getAccount",
            arguments: { username: "john" }
        )
    ]
}
hasToolExecutionRequests() → true → 继续循环
```

**情况 2：不需要工具，直接回复**
```java
aiMessage = {
    text: "您的余额是 5000 美元",
    toolExecutionRequests: []  // 空数组
}
hasToolExecutionRequests() → false → 退出循环
```

**💡 核心理解**：
- LLM **自主决定**是否需要调用工具
- 不需要人工写 if-else 判断逻辑
- 这就是 AI 的"智能"所在！

---

#### ⑦ `executeToolRequests()` - 执行工具

```java
protected List<ToolExecutionResultMessage> executeToolRequests(
    List<ToolExecutionRequest> toolExecutionRequests) {
    
    List<ToolExecutionResultMessage> toolExecutionResultMessages = new ArrayList<>();
    
    for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
        var toolExecutor = getToolExecutor(toolExecutionRequest.name());  // ①
        
        LOGGER.info("Executing {} with params {}", 
            toolExecutionRequest.name(), 
            toolExecutionRequest.arguments());
        
        String result = toolExecutor.execute(toolExecutionRequest, null);  // ②
        
        LOGGER.info("Response from {}: {}", 
            toolExecutionRequest.name(), 
            result);
        
        if (result == null || result.isEmpty()) {
            LOGGER.warn("Tool {} returned empty result...");
            result = "ok";
        }
        
        toolExecutionResultMessages.add(
            ToolExecutionResultMessage.from(toolExecutionRequest, result)  // ③
        );
    }
    return toolExecutionResultMessages;
}
```

**① `getToolExecutor()`** - 获取工具执行器  
- 这是一个**抽象方法**，由子类实现
- 不同的 Agent 有不同的工具

**② `toolExecutor.execute()`** - 实际执行  
- 调用 REST API、数据库查询等
- 返回字符串结果（通常是 JSON）

**③ `ToolExecutionResultMessage.from()`** - 封装结果  
- 将工具调用和结果打包成消息
- 这样 LLM 能看到"调用了什么工具"和"返回了什么"

---

#### ⑩ `buildInternalChat()` - 构建对话记忆

```java
protected ChatMemory buildInternalChat(List<ChatMessage> chatHistory) {
    var internalChatMemory = MessageWindowChatMemory.builder()
        .id("default")
        .maxMessages(20)  // ① 最多保留 20 条消息
        .build();

    internalChatMemory.add(SystemMessage.from(getSystemMessage()));  // ②
    chatHistory.forEach(internalChatMemory::add);                    // ③
    return internalChatMemory;
}
```

**① `maxMessages(20)`** - 对话窗口大小  
**为什么限制**？
- LLM 的上下文窗口有限（如 8K tokens）
- 节省成本（tokens 越少越便宜）
- 提高响应速度

**类比**：就像人的短期记忆，只能记住最近的事情

**② 添加系统提示词**  
- `getSystemMessage()` 是抽象方法，由子类实现
- 告诉 Agent "你是谁"、"你能做什么"

**③ 添加对话历史**  
- 用户之前说的话
- AI 之前的回复

---

### 🔑 三个抽象方法（子类必须实现）

```java
// 1. 系统提示词
protected abstract String getSystemMessage();

// 2. 工具规格（告诉 LLM 有哪些工具）
protected abstract List<ToolSpecification> getToolSpecifications();

// 3. 工具执行器（实际调用工具）
protected abstract ToolExecutor getToolExecutor(String toolName);
```

**这就是模板方法模式**：
- 抽象类定义流程（`invoke()` 方法）
- 子类实现细节（三个抽象方法）

---

## 第三部分：MCPToolAgent - MCP 工具的桥梁

### 📄 文件：`MCPToolAgent.java`（112 行）

这个类实现了 `AbstractReActAgent` 的三个抽象方法，专门处理 **MCP 协议**的工具。

### 🎯 什么是 MCP？

**MCP = Model Context Protocol（模型上下文协议）**

**作用**：将 REST API 自动暴露为 AI Agent 可调用的工具

**没有 MCP 时**：
```java
// 你需要手动写代码调用 API
public String getAccount(String username) {
    RestTemplate rest = new RestTemplate();
    ResponseEntity<String> response = rest.getForEntity(
        "http://account-service/api/accounts/" + username,
        String.class
    );
    return response.getBody();
}

// 然后包装成 Tool 给 Agent 用
@Tool("Get account details")
public String getAccountTool(@P("username") String username) {
    return getAccount(username);
}
```

**有了 MCP 后**：
```java
// 自动从 MCP Server 发现工具
McpClient mcpClient = new DefaultMcpClient.Builder()
    .transport(transport)
    .build();

// 工具自动注册，无需手动编写
// Agent 可以直接调用 getAccount, getPaymentMethods 等
```

---

### 🔍 逐段解析

#### 3.1 成员变量

```java
public abstract class MCPToolAgent extends AbstractReActAgent {
    
    protected List<ToolSpecification> toolSpecifications;      // ① 工具列表
    protected Map<String, ToolExecutor> extendedExecutorMap;   // ② 扩展工具执行器
    protected List<McpClient> mcpClients;                      // ③ MCP 客户端列表
    protected Map<String, McpClient> tool2ClientMap;           // ④ 工具名 → MCP 客户端映射
```

**① `toolSpecifications`** - 告诉 LLM 有哪些工具  
**② `extendedExecutorMap`** - 非 MCP 工具的执行器（本项目未使用）  
**③ `mcpClients`** - 连接到 MCP Server 的客户端  
**④ `tool2ClientMap`** - 根据工具名快速找到对应的 MCP 客户端

---

#### 3.2 构造函数 - 连接 MCP Server

```java
protected MCPToolAgent(ChatLanguageModel chatModel, 
                      List<MCPServerMetadata> mcpServerMetadata) {
    super(chatModel);  // 调用父类构造函数
    
    this.mcpClients = new ArrayList<>();
    this.tool2ClientMap = new HashMap<>();
    this.toolSpecifications = new ArrayList<>();
    this.extendedExecutorMap = new HashMap<>();

    // 遍历所有 MCP Server 配置
    mcpServerMetadata.forEach(metadata -> {
        if(metadata.protocolType().equals(MCPProtocolType.SSE)){  // ①
            // 创建 HTTP 传输层
            McpTransport transport = new HttpMcpTransport.Builder()
                    .sseUrl(metadata.url())  // ② MCP Server 的 URL
                    .logRequests(true)
                    .logResponses(true)
                    .timeout(Duration.ofHours(3))
                    .build();

            // 创建 MCP 客户端
            McpClient mcpClient = new DefaultMcpClient.Builder()
                    .transport(transport)
                    .build();
            
            // ③ 从 MCP Server 获取所有工具
            mcpClient.listTools().forEach(toolSpecification -> {
                this.tool2ClientMap.put(toolSpecification.name(), mcpClient);
                this.toolSpecifications.add(toolSpecification);
            });
            
            this.mcpClients.add(mcpClient);
        }
    });
}
```

**① `MCPProtocolType.SSE`**  
- SSE = Server-Sent Events
- 一种服务端推送技术
- MCP Server 通过 SSE 广播工具列表

**② `sseUrl`**  
- 例如：`http://account-service:8080/sse`
- MCP Server 的端点

**③ `mcpClient.listTools()`** - **这是魔法发生的地方！**

**自动发现工具的过程**：
```
1. MCP Client 连接到 MCP Server
2. Server 返回所有可用工具：
   [
     { name: "getAccount", description: "...", parameters: {...} },
     { name: "getPaymentMethods", description: "...", parameters: {...} },
     { name: "getBeneficiaries", description: "...", parameters: {...} }
   ]
3. Client 自动注册这些工具
4. Agent 可以直接调用，无需手动编码！
```

**对比手动 vs 自动**：
```java
// 手动方式（传统）:
@Tool("Get account")
public String getAccount(String username) { ... }

@Tool("Get payment methods")
public String getPaymentMethods() { ... }

@Tool("Get beneficiaries")
public String getBeneficiaries() { ... }

// MCP 自动方式（本项目）:
// 只需提供 MCP Server URL，所有工具自动发现！
new AccountMCPAgent(chatModel, username, "http://account-service:8080");
```

---

#### 3.3 实现抽象方法

```java
@Override
protected List<ToolSpecification> getToolSpecifications() {
    return this.toolSpecifications;  // 返回自动发现的工具列表
}

@Override
protected ToolExecutor getToolExecutor(String toolName) {
    // MCP 不需要这个！工具由 MCP Client 执行
    throw new AgentExecutionException(
        "getToolExecutor not required when using MCP. if you landed here please review your agent code"
    );
}
```

**💡 重要理解**：
- `AbstractReActAgent` 定义了 `getToolExecutor()` 抽象方法
- 但 `MCPToolAgent` 不需要它，因为 MCP Client 自己执行工具
- 如果误调用，会抛出异常提醒你

---

#### 3.4 重写 `executeToolRequests()` - 执行 MCP 工具

```java
protected List<ToolExecutionResultMessage> executeToolRequests(
    List<ToolExecutionRequest> toolExecutionRequests) {
    
    List<ToolExecutionResultMessage> toolExecutionResultMessages = new ArrayList<>();
    
    for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
        String result = "ko";

        // 先尝试扩展执行器（本项目未使用）
        var toolExecutor = extendedExecutorMap.get(toolExecutionRequest.name());
        if(toolExecutor != null){
            result = toolExecutor.execute(toolExecutionRequest, null);
        } else {
            // ① 从映射中找到 MCP Client
            var mcpClient = tool2ClientMap.get(toolExecutionRequest.name());
            if (mcpClient == null) {
                throw new IllegalArgumentException(
                    "No MCP executor found for tool name: " + toolExecutionRequest.name()
                );
            }
            
            LOGGER.info("Executing {} with params {}", 
                toolExecutionRequest.name(), 
                toolExecutionRequest.arguments());
            
            // ② 通过 MCP Client 执行工具
            result = mcpClient.executeTool(toolExecutionRequest);
            
            LOGGER.info("Response from {}: {}", 
                toolExecutionRequest.name(), 
                result);
        }

        if (result == null || result.isEmpty()) {
            result = "ok";
        }
        
        toolExecutionResultMessages.add(
            ToolExecutionResultMessage.from(toolExecutionRequest, result)
        );
    }
    return toolExecutionResultMessages;
}
```

**① `tool2ClientMap.get()`**  
- 根据工具名找到对应的 MCP Client
- 例如：`"getAccount"` → `accountMcpClient`

**② `mcpClient.executeTool()`**  
- MCP Client 通过 HTTP 调用实际的 REST API
- 返回结果给 Agent

---

## 第四部分：AccountMCPAgent - 第一个完整 Agent

### 📄 文件：`AccountMCPAgent.java`（55 行）

这是一个**具体的 Agent 实现**，继承自 `MCPToolAgent`。

### 🔍 逐段解析

#### 4.1 系统提示词模板

```java
public class AccountMCPAgent extends MCPToolAgent {

    private final Prompt agentPrompt;

    private static final String ACCOUNT_AGENT_SYSTEM_MESSAGE = """
         you are a personal financial advisor who help the user to retrieve 
         information about their bank accounts.
         Use html list or table to display the account information.
         Always use the below logged user details to retrieve account info:
         '{{loggedUserName}}'
        """;  // ①
```

**① 系统提示词** - **这是 Prompt Engineering 的核心！**

**逐句解读**：

| 句子 | 作用 |
|-----|------|
| `you are a personal financial advisor` | 定义角色（身份设定） |
| `who help the user to retrieve information about their bank accounts` | 说明职责 |
| `Use html list or table to display the account information` | 格式化输出要求 |
| `Always use the below logged user details` | 约束条件 |
| `'{{loggedUserName}}'` | 动态变量（会被替换） |

**💡 Prompt Engineering 技巧**：
- 明确角色："你是财务顾问"
- 明确职责："帮助查询账户信息"
- 明确格式："使用 HTML 列表或表格"
- 明确约束："始终使用当前登录用户"

---

#### 4.2 构造函数

```java
public AccountMCPAgent(ChatLanguageModel chatModel, 
                      String loggedUserName, 
                      String accountMCPServerUrl) {
    
    // ① 调用父类构造函数，传入 MCP Server 配置
    super(chatModel, 
          List.of(new MCPServerMetadata("account", accountMCPServerUrl, MCPProtocolType.SSE)));

    if (loggedUserName == null || loggedUserName.isEmpty()) {
        throw new IllegalArgumentException("loggedUserName cannot be null or empty");
    }

    // ② 使用 PromptTemplate 替换变量
    PromptTemplate promptTemplate = PromptTemplate.from(ACCOUNT_AGENT_SYSTEM_MESSAGE);
    this.agentPrompt = promptTemplate.apply(Map.of("loggedUserName", loggedUserName));
}
```

**① `MCPServerMetadata`**  
```java
new MCPServerMetadata(
    "account",                          // Server 名称
    accountMCPServerUrl,                // URL，如 "http://account:8080"
    MCPProtocolType.SSE                 // 协议类型
)
```

**② `PromptTemplate`** - 模板引擎  
```java
// 模板:
"Always use '{{loggedUserName}}'"

// 替换后:
"Always use 'john.doe'"
```

**类比**：就像 Thymeleaf 或 FreeMarker 的模板

---

#### 4.3 实现抽象方法

```java
@Override
public String getName() {
    return "AccountAgent";  // ① Agent 名称
}

@Override
public AgentMetadata getMetadata() {
    return new AgentMetadata(
        "Personal financial advisor for retrieving bank account information.",  // ② 描述
        List.of("RetrieveAccountInfo", "DisplayAccountDetails")                 // ③ 能力标签
    );
}

@Override
protected String getSystemMessage() {
    return agentPrompt.text();  // ④ 返回系统提示词
}
```

**① `getName()`**  
- 返回 `"AccountAgent"`
- 用于日志和调试

**② `AgentMetadata` 的描述** - **Supervisor 路由的关键！**

**Supervisor 如何工作**：
```java
// 用户问: "我的余额是多少？"

// Supervisor 查看所有 Agent 的描述:
AccountAgent: "Personal financial advisor for retrieving bank account information."
TransactionAgent: "Agent for querying transaction history."
PaymentAgent: "Agent for processing payments."

// LLM 判断: "余额" 和 "bank account information" 最匹配
// 选择: AccountAgent ✅
```

**③ 能力标签**  
- 人类可读的标签
- 目前本项目未使用，但可以用于：
  - 文档生成
  - 前端展示
  - 权限控制

**④ `getSystemMessage()`**  
- 返回替换变量后的提示词
- 例如: `"Always use 'john.doe'"`

---

## 🎬 完整执行流程演示

### 场景：用户问 "What's my account balance?"

```
┌──────────────────────────────────────────────────────┐
│ 1. 前端发送请求到 Copilot Backend                     │
│    POST /api/chat                                    │
│    {                                                 │
│      "message": "What's my account balance?",        │
│      "username": "john.doe"                          │
│    }                                                 │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 2. Supervisor Agent 分析意图                          │
│                                                       │
│    LLM 思考: "用户要查询余额 → 这需要账户信息"        │
│    查看 Agent 描述:                                   │
│    - AccountAgent: "bank account information" ✅      │
│    - TransactionAgent: "transaction history"          │
│    - PaymentAgent: "processing payments"              │
│                                                       │
│    决定: 路由到 AccountAgent                          │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 3. AccountMCPAgent.invoke() 执行                      │
│                                                       │
│    3a. 构建对话记忆                                    │
│    internalChatMemory = [                             │
│      SystemMessage("you are a personal financial...   │
│                    Always use 'john.doe'"),           │
│      UserMessage("What's my account balance?")        │
│    ]                                                  │
│                                                       │
│    3b. 获取可用工具（从 MCP Server 自动发现）          │
│    tools = [                                          │
│      getAccount(username),                            │
│      getPaymentMethods(),                             │
│      getBeneficiaries()                               │
│    ]                                                  │
│                                                       │
│    3c. 第一次调用 LLM (Azure OpenAI)                   │
│    请求: {                                            │
│      messages: [...对话历史...],                      │
│      tools: [...工具列表...]                          │
│    }                                                  │
│                                                       │
│    LLM 返回:                                          │
│    {                                                  │
│      text: null,                                      │
│      toolExecutionRequests: [                         │
│        ToolExecutionRequest(                          │
│          name: "getAccount",                          │
│          arguments: { username: "john.doe" }          │
│        )                                              │
│      ]                                                │
│    }                                                  │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 4. 执行工具调用                                       │
│                                                       │
│    4a. 找到 MCP Client: tool2ClientMap.get("getAccount") │
│                                                       │
│    4b. MCP Client 发送 HTTP 请求                      │
│    GET http://account-service:8080/api/accounts/john.doe │
│                                                       │
│    4c. Account Service 返回:                          │
│    {                                                  │
│      "accountId": "ACC123456",                        │
│      "balance": 5000.00,                              │
│      "currency": "USD",                               │
│      "accountType": "Savings"                         │
│    }                                                  │
│                                                       │
│    4d. 封装结果:                                      │
│    ToolExecutionResultMessage(                        │
│      result: "{"accountId":"ACC123456",...}"          │
│    )                                                  │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 5. 第二次调用 LLM                                     │
│                                                       │
│    请求: {                                            │
│      messages: [                                      │
│        ...之前的对话...,                              │
│        AIMessage("调用 getAccount..."),               │
│        ToolExecutionResultMessage(                    │
│          result: "{balance: 5000}"                    │
│        )                                              │
│      ]                                                │
│    }                                                  │
│                                                       │
│    LLM 看到工具结果后思考:                            │
│    "好的，余额是 5000 美元，按提示词要求用 HTML 展示" │
│                                                       │
│    LLM 返回:                                          │
│    {                                                  │
│      text: "您的账户余额信息如下:<br>                 │
│             <ul>                                      │
│               <li>账户: ACC123456</li>                │
│               <li>余额: $5,000.00</li>                │
│               <li>类型: Savings</li>                  │
│             </ul>",                                   │
│      toolExecutionRequests: []  // 不需要工具了        │
│    }                                                  │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 6. 退出 while 循环，返回结果                          │
│                                                       │
│    while 条件: hasToolExecutionRequests() = false     │
│    退出循环                                           │
│                                                       │
│    返回: [                                            │
│      UserMessage("What's my account balance?"),       │
│      AIMessage("您的账户余额信息如下:...")            │
│    ]                                                  │
└──────────────────┬───────────────────────────────────┘
                   │
┌──────────────────▼───────────────────────────────────┐
│ 7. 前端展示给用户                                     │
│                                                       │
│    用户看到:                                          │
│    ╔═══════════════════════════════════════╗          │
│    ║ 您的账户余额信息如下:                  ║          │
│    ║ • 账户: ACC123456                     ║          │
│    ║ • 余额: $5,000.00                     ║          │
│    ║ • 类型: Savings                       ║          │
│    ╚═══════════════════════════════════════╝          │
└──────────────────────────────────────────────────────┘
```

---

## 🎯 核心概念总结

### 1. ReAct 模式

```
思考(Reasoning) → 行动(Acting) → 观察(Observation) → 循环
```

**代码体现**：
```java
while (aiMessage.hasToolExecutionRequests()) {
    // 行动: 执行工具
    executeToolRequests(...);
    
    // 观察: 将结果添加到记忆
    internalChatMemory.add(result);
    
    // 思考: 再次调用 LLM
    aiMessage = chatModel.chat(...);
}
```

---

### 2. 模板方法模式

```java
// 抽象类定义流程
AbstractReActAgent.invoke() {
    buildInternalChat();           // 抽象
    getToolSpecifications();       // 抽象
    executeToolRequests();         // 可重写
    getSystemMessage();            // 抽象
}

// 子类实现细节
AccountMCPAgent {
    getSystemMessage() → "你是财务顾问..."
    getToolSpecifications() → [getAccount, getPaymentMethods, ...]
}
```

---

### 3. MCP 协议的价值

| 维度 | 传统方式 | MCP 方式 |
|-----|---------|---------|
| **工具注册** | 手动写 `@Tool` 注解 | 自动从 Server 发现 |
| **参数绑定** | 手动解析 JSON | 自动类型转换 |
| **API 变更** | 修改代码 + 重新部署 | Server 更新，自动同步 |
| **代码量** | 每个工具 10-20 行 | 0 行（自动） |

---

## ✅ 学习检查点

完成以下问题，确认你理解了代码：

### 基础理解
- [ ] 能解释 ReAct 模式的三个步骤
- [ ] 能说明 `invoke()` 方法的执行流程
- [ ] 能解释为什么需要 while 循环
- [ ] 能说出 `hasToolExecutionRequests()` 的作用

### 架构理解
- [ ] 能画出 Agent 的继承关系图
- [ ] 能解释 MCP 协议解决了什么问题
- [ ] 能说明 Supervisor 如何路由请求
- [ ] 能说出系统提示词的作用

### 代码细节
- [ ] 能解释 `MessageWindowChatMemory` 为什么限制 20 条消息
- [ ] 能说明 `PromptTemplate` 的作用
- [ ] 能解释 `tool2ClientMap` 的用途
- [ ] 能说出 `getMetadata()` 返回什么

---

## 📝 下一步

接下来我们将：
1. 阅读 `SupervisorAgent.java` - 理解路由机制
2. 实战：修改 Account Agent 添加新功能
3. 创建 Langchain4j 核心概念速查表

**准备好了吗？告诉我你的疑问，我们继续深入！** 🚀
