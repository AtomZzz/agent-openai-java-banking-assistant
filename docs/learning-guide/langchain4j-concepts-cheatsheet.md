# 📖 Langchain4j 核心概念速查表

> **目标**：快速查阅 Langchain4j 框架的核心概念和 API
> **适合场景**：阅读代码时遇到不理解的类/方法，来这里查找解释

---

## 一、核心类速查

### 1.1 ChatLanguageModel - LLM 抽象

```java
ChatLanguageModel chatModel;
```

| 属性 | 说明 |
|-----|------|
| **类型** | 接口（Interface） |
| **作用** | 抽象大语言模型的调用 |
| **常见实现** | `AzureOpenAiChatModel`, `OpenAiChatModel` |
| **核心方法** | `ChatResponse chat(ChatRequest request)` |
| **类比** | 就像 JDBC 的 `Connection` 接口 |

**为什么用接口？**
- 解耦：代码不依赖具体 LLM 提供商
- 可替换：轻松切换 Azure OpenAI / OpenAI / 其他
- 可测试：可以用 Mock 实现进行单元测试

---

### 1.2 ChatRequest - 请求对象

```java
ChatRequest request = ChatRequest.builder()
    .messages(List.of(userMessage, systemMessage))
    .parameters(ChatRequestParameters.builder()
        .toolSpecifications(tools)
        .build())
    .build();
```

| 属性 | 说明 |
|-----|------|
| **messages** | 对话历史（System、User、AI 消息） |
| **parameters** | 请求参数（工具、温度、最大 tokens 等） |
| **类比** | HTTP 请求的 Request Body |

---

### 1.3 ChatResponse - 响应对象

```java
ChatResponse response = chatModel.chat(request);
AiMessage aiMessage = response.aiMessage();
```

| 属性 | 说明 |
|-----|------|
| **aiMessage()** | AI 的回复消息 |
| **tokens()** | 使用的 token 数量 |
| **finishReason()** | 完成原因（正常、长度限制、工具调用等） |

---

### 1.4 AiMessage - AI 消息

```java
AiMessage aiMessage = response.aiMessage();
String text = aiMessage.text();
boolean hasTools = aiMessage.hasToolExecutionRequests();
List<ToolExecutionRequest> tools = aiMessage.toolExecutionRequests();
```

| 方法 | 返回值 | 说明 |
|-----|--------|------|
| `text()` | String | AI 的文本回复（可能为 null） |
| `hasToolExecutionRequests()` | boolean | 是否需要调用工具 |
| `toolExecutionRequests()` | List | 工具调用请求列表 |

**两种响应模式**：

**模式 1：直接回复**
```java
aiMessage = {
    text: "您好！有什么可以帮助您的？",
    toolExecutionRequests: []
}
```

**模式 2：请求工具**
```java
aiMessage = {
    text: null,
    toolExecutionRequests: [
        ToolExecutionRequest(
            name: "getAccount",
            arguments: { username: "john" }
        )
    ]
}
```

---

### 1.5 ChatMemory - 对话记忆

```java
ChatMemory memory = MessageWindowChatMemory.builder()
    .id("default")
    .maxMessages(20)
    .build();
```

| 属性 | 说明 |
|-----|------|
| **类型** | 接口 |
| **常见实现** | `MessageWindowChatMemory`（窗口记忆） |
| **id** | 记忆的唯一标识（支持多用户会话） |
| **maxMessages** | 最多保留的消息数量 |

**核心方法**：
```java
memory.add(message);              // 添加消息
memory.messages();                // 获取所有消息
memory.clear();                   // 清空记忆
```

**为什么限制消息数量？**
1. LLM 上下文窗口有限（如 8K/128K tokens）
2. 节省成本（tokens 越少越便宜）
3. 提高响应速度

---

## 二、消息类型（ChatMessage）

### 2.1 SystemMessage - 系统消息

```java
SystemMessage.from("你是财务助手，帮助用户查询账户信息");
```

| 属性 | 说明 |
|-----|------|
| **作用** | 设定 AI 的角色、行为、约束 |
| **位置** | 通常是对话的第一条消息 |
| **可见性** | 用户看不到，但对 AI 有影响 |
| **类比** | 给演员的剧本和角色设定 |

**示例**：
```java
SystemMessage.from("""
    你是个人财务顾问，帮助用户查询银行账户信息。
    使用 HTML 列表或表格展示账户信息。
    始终使用以下登录用户的信息：
    'john.doe'
""");
```

---

### 2.2 UserMessage - 用户消息

```java
UserMessage.from("我的账户余额是多少？");
```

| 属性 | 说明 |
|-----|------|
| **作用** | 用户的输入 |
| **来源** | 前端聊天界面、API 调用 |

---

### 2.3 AiMessage - AI 消息

```java
AiMessage.from("您的账户余额是 5000 美元");
```

| 属性 | 说明 |
|-----|------|
| **作用** | AI 的回复 |
| **可能包含** | 文本、工具调用请求 |

---

### 2.4 ToolExecutionResultMessage - 工具执行结果

```java
ToolExecutionResultMessage.from(
    toolExecutionRequest,
    "{\"balance\": 5000, \"currency\": \"USD\"}"
);
```

| 属性 | 说明 |
|-----|------|
| **作用** | 工具调用的结果 |
| **内容** | 工具名称、参数、返回结果 |
| **可见性** | AI 能看到，用户看不到 |

**在对话中的位置**：
```
1. UserMessage: "我的余额是多少？"
2. AiMessage: "我需要调用 getAccount 工具"
3. ToolExecutionResultMessage: "{balance: 5000}"  ← AI 看到的结果
4. AiMessage: "您的余额是 5000 美元"              ← 给用户的回复
```

---

## 三、工具系统（Tools）

### 3.1 ToolSpecification - 工具规格

```java
ToolSpecification tool = ToolSpecification.builder()
    .name("getAccount")
    .description("Get account details by username")
    .parameters(JsonObjectSchema.builder()
        .addStringProperty("username", "The username")
        .build())
    .build();
```

| 属性 | 说明 |
|-----|------|
| **name** | 工具名称（AI 调用时使用） |
| **description** | 工具描述（AI 判断是否使用） |
| **parameters** | 参数定义（JSON Schema 格式） |

**AI 如何使用**：
```
AI 思考: "用户要查余额，我需要查账户信息"
      ↓
查看可用工具: [getAccount, getPaymentMethods, ...]
      ↓
判断: getAccount 的描述是 "Get account details" ✅
      ↓
调用: getAccount(username: "john.doe")
```

---

### 3.2 ToolExecutionRequest - 工具执行请求

```java
ToolExecutionRequest request = ToolExecutionRequest.builder()
    .name("getAccount")
    .arguments("{\"username\": \"john.doe\"}")
    .build();
```

| 属性 | 说明 |
|-----|------|
| **name()** | 工具名称 |
| **arguments()** | 参数（JSON 字符串） |
| **id()** | 请求 ID（用于匹配结果） |

**来源**：由 LLM 生成，告诉 Agent "我要调用这个工具"

---

### 3.3 ToolExecutor - 工具执行器

```java
@FunctionalInterface
public interface ToolExecutor {
    String execute(ToolExecutionRequest request, Object memoryId);
}
```

| 属性 | 说明 |
|-----|------|
| **类型** | 函数式接口 |
| **输入** | 工具执行请求 |
| **输出** | 工具返回结果（字符串） |
| **类比** | REST Controller 的方法 |

**实现示例**：
```java
ToolExecutor executor = (request, memoryId) -> {
    String username = extractUsername(request.arguments());
    Account account = accountService.getAccount(username);
    return toJson(account);
};
```

---

## 四、MCP（Model Context Protocol）

### 4.1 McpClient - MCP 客户端

```java
McpClient mcpClient = new DefaultMcpClient.Builder()
    .transport(transport)
    .build();
```

| 属性 | 说明 |
|-----|------|
| **作用** | 连接到 MCP Server，发现和调用工具 |
| **核心方法** | `listTools()`, `executeTool()` |
| **类比** | HTTP Client（如 RestTemplate） |

---

### 4.2 McpTransport - MCP 传输层

```java
McpTransport transport = new HttpMcpTransport.Builder()
    .sseUrl("http://account-service:8080/sse")
    .logRequests(true)
    .logResponses(true)
    .timeout(Duration.ofHours(3))
    .build();
```

| 属性 | 说明 |
|-----|------|
| **sseUrl** | MCP Server 的 SSE 端点 |
| **logRequests/Responses** | 是否记录请求/响应日志 |
| **timeout** | 超时时间 |

---

### 4.3 MCP 工作流程

```
1. MCP Client 连接到 MCP Server (通过 SSE)
2. Client 调用 listTools() 获取可用工具列表
3. Server 返回工具规格 (ToolSpecification)
4. Client 注册工具到 Agent
5. Agent 执行时，通过 MCP Client 调用工具
6. MCP Client 发送 HTTP 请求到实际的 REST API
7. 返回结果给 Agent
```

---

## 五、Prompt 工程

### 5.1 PromptTemplate - 提示词模板

```java
PromptTemplate template = PromptTemplate.from(
    "你是财务顾问，帮助用户 '{{username}}' 查询账户信息"
);

Prompt prompt = template.apply(Map.of("username", "john.doe"));
String text = prompt.text();
// 结果: "你是财务顾问，帮助用户 'john.doe' 查询账户信息"
```

| 方法 | 说明 |
|-----|------|
| `from(String)` | 创建模板 |
| `apply(Map)` | 替换变量 |
| `text()` | 获取替换后的文本 |

**类比**：Thymeleaf、FreeMarker 的模板引擎

---

### 5.2 系统提示词设计原则

```java
String systemMessage = """
    角色设定: 你是个人财务顾问
    职责: 帮助用户查询银行账户信息
    格式要求: 使用 HTML 列表或表格展示
    约束条件: 始终使用当前登录用户 '{{username}}' 的信息
    语气: 专业、友好
    """;
```

| 要素 | 说明 | 示例 |
|-----|------|------|
| **角色** | AI 的身份 | "你是财务顾问" |
| **职责** | AI 能做什么 | "帮助查询账户信息" |
| **格式** | 输出格式要求 | "使用 HTML 列表" |
| **约束** | 限制条件 | "只使用当前用户数据" |
| **语气** | 交流风格 | "专业、友好" |

---

## 六、Agent 架构

### 6.1 继承关系图

```
Agent (接口)
  ↑
  |
AbstractReActAgent (抽象类)
  ↑
  |
MCPToolAgent (抽象类)
  ↑
  |
  ├── AccountMCPAgent (具体实现)
  ├── TransactionHistoryMCPAgent (具体实现)
  └── PaymentMCPAgent (具体实现)
```

---

### 6.2 核心方法对照表

| 方法 | 定义位置 | 实现位置 | 作用 |
|-----|---------|---------|------|
| `invoke()` | Agent 接口 | AbstractReActAgent | 执行 Agent 的核心逻辑 |
| `getName()` | Agent 接口 | 具体 Agent | 返回 Agent 名称 |
| `getMetadata()` | Agent 接口 | 具体 Agent | 返回 Agent 元数据（描述） |
| `getSystemMessage()` | AbstractReActAgent | 具体 Agent | 返回系统提示词 |
| `getToolSpecifications()` | AbstractReActAgent | MCPToolAgent | 返回工具列表 |
| `getToolExecutor()` | AbstractReActAgent | MCPToolAgent | 返回工具执行器（MCP 不需要） |

---

### 6.3 ReAct 循环伪代码

```
function invoke(chatHistory):
    // 1. 初始化记忆
    memory = buildMemory(chatHistory)
    
    // 2. 准备工具
    tools = getToolSpecifications()
    
    // 3. 第一次调用 LLM
    aiMessage = chatModel.chat(memory, tools)
    
    // 4. ReAct 循环
    while aiMessage.hasToolExecutionRequests():
        // 执行工具
        results = executeTools(aiMessage.toolExecutionRequests())
        
        // 添加到记忆
        memory.add(aiMessage)
        memory.add(results)
        
        // 再次调用 LLM
        aiMessage = chatModel.chat(memory, tools)
    
    // 5. 返回最终结果
    return memory.messages()
```

---

## 七、常见模式和最佳实践

### 7.1 模板方法模式

**抽象类定义流程**：
```java
public abstract class AbstractReActAgent {
    public List<ChatMessage> invoke(List<ChatMessage> chatHistory) {
        // 固定流程
        memory = buildInternalChat(chatHistory);
        tools = getToolSpecifications();
        response = chatModel.chat(memory, tools);
        
        while (response.hasToolExecutionRequests()) {
            results = executeToolRequests(response.toolExecutionRequests());
            memory.add(results);
            response = chatModel.chat(memory, tools);
        }
        
        return response;
    }
    
    // 抽象方法（子类实现）
    protected abstract String getSystemMessage();
    protected abstract List<ToolSpecification> getToolSpecifications();
    protected abstract ToolExecutor getToolExecutor(String toolName);
}
```

**子类实现细节**：
```java
public class AccountMCPAgent extends AbstractReActAgent {
    @Override
    protected String getSystemMessage() {
        return "你是财务顾问...";
    }
    
    // 其他方法...
}
```

---

### 7.2 建造者模式

```java
// 复杂对象的构建
ChatRequest request = ChatRequest.builder()
    .messages(messages)
    .parameters(parameters)
    .build();

ChatMemory memory = MessageWindowChatMemory.builder()
    .id("default")
    .maxMessages(20)
    .build();
```

**优点**：
- 链式调用，代码可读性好
- 可选参数，不需要构造函数重载
- 不可变对象（build 后不能修改）

---

### 7.3 策略模式

```java
// 不同的 MCP 传输策略
McpTransport transport = switch (protocolType) {
    case SSE -> new HttpMcpTransport.Builder()
        .sseUrl(url)
        .build();
    case STDIO -> new StdioMcpTransport.Builder()
        .command(command)
        .build();
};
```

---

## 八、调试技巧

### 8.1 启用 MCP 日志

```java
McpTransport transport = new HttpMcpTransport.Builder()
    .sseUrl(url)
    .logRequests(true)   // 记录请求
    .logResponses(true)  // 记录响应
    .build();
```

**日志输出**：
```
Executing getAccount with params {"username": "john.doe"}
Response from getAccount: {"balance": 5000, "currency": "USD"}
```

---

### 8.2 查看 Agent 执行流程

```java
LOGGER.info("------------- {} -------------", this.getName());
LOGGER.info("Agent response: {}", aiMessage.text());
```

**日志输出**：
```
------------- AccountAgent -------------
Agent response: 您的账户余额是 5000 美元
```

---

### 8.3 常见问题排查

| 问题 | 可能原因 | 排查方法 |
|-----|---------|---------|
| 工具未调用 | 工具描述不清晰 | 检查 `ToolSpecification.description` |
| 参数错误 | 参数提取失败 | 查看 LLM 返回的 `arguments` |
| 空结果 | API 返回空 | 检查 `result == null` 的处理 |
| 超时 | API 响应慢 | 调整 `timeout(Duration)` |

---

## 九、关键术语对照表

| 术语 | 中文 | 解释 |
|-----|------|------|
| **LLM** | 大语言模型 | GPT-4、Claude 等 |
| **Agent** | 智能体 | 能使用工具解决任务的 AI |
| **Tool** | 工具 | Agent 可调用的功能 |
| **Prompt** | 提示词 | 给 AI 的指令 |
| **Token** | 词元 | AI 处理文本的基本单位 |
| **MCP** | 模型上下文协议 | 暴露 API 为 AI 工具的标准 |
| **ReAct** | 推理+行动 | Agent 的工作模式 |
| **SSE** | 服务端推送事件 | 实时通信协议 |
| **ChatMemory** | 对话记忆 | 保存对话历史 |
| **Function Calling** | 函数调用 | OpenAI 的工具调用机制 |

---

## 十、快速参考：代码位置

| 功能 | 文件位置 |
|-----|---------|
| Agent 接口 | `langchain4j-agents/.../Agent.java` |
| ReAct 实现 | `langchain4j-agents/.../AbstractReActAgent.java` |
| MCP Agent | `langchain4j-agents/.../MCPToolAgent.java` |
| Account Agent | `langchain4j-agents/.../mcp/AccountMCPAgent.java` |
| Transaction Agent | `langchain4j-agents/.../mcp/TransactionHistoryMCPAgent.java` |
| Payment Agent | `langchain4j-agents/.../mcp/PaymentMCPAgent.java` |
| Supervisor Agent | `langchain4j-agents/.../SupervisorAgent.java` |

---

## 💡 使用建议

1. **阅读代码时**：遇到不理解的类，回来查阅
2. **编写代码时**：参考最佳实践和模式
3. **调试问题时**：查看调试技巧和常见问题
4. **复习概念时**：浏览术语对照表

---

**这份速查表会随着你的学习不断更新，随时添加新的内容！** 📚
