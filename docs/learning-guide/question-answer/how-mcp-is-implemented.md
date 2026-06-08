# ❓ MCP 是如何实现的？

> **问题来源**：学员在学习 Payment Agent 时的疑问
> **核心问题**：MCP 如何实现？Spring Boot API 如何变成 MCP 工具？
> **涉及概念**：MCP 协议、Spring AI、工具注解、自动发现机制

---

## 📋 问题描述

### 学员的疑问

1. **MCP 是如何实现的？** 需要一个 MCP Client 和 MCP Server 吗？
2. **Spring Boot 编写的 API 如何变成 MCP 工具？** 转换过程是什么？
3. **MCP Client 和 MCP Server 之间的通信协议是什么？**

---

## ✅ 回答

### 1. MCP 架构概述

**是的，MCP 需要 Client 和 Server 两端！**

```
┌─────────────────────────────────────────────────────┐
│         MCP Client (Agent 端)                        │
│         例如: AccountMCPAgent                        │
│         使用: Langchain4j McpClient                  │
└──────────────────┬──────────────────────────────────┘
                   │
                   │ MCP 协议 (SSE/HTTP)
                   │
┌──────────────────▼──────────────────────────────────┐
│         MCP Server (业务 API 端)                     │
│         例如: Account Service                        │
│         使用: Spring AI MCP Server                   │
└─────────────────────────────────────────────────────┘
```

---

### 2. 本项目的 MCP 实现

#### 2.1 MCP Server 端（业务 API）

**文件位置**：`app/business-api/account/`

**核心依赖**（pom.xml）：
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

**这个依赖的作用**：
- ✅ 自动配置 MCP Server
- ✅ 扫描 `@Tool` 注解
- ✅ 暴露 SSE 端点（`/sse`）
- ✅ 处理 MCP 协议

---

#### 2.2 定义 MCP 工具

**文件**：`AccountMCPService.java`

```java
package com.microsoft.openai.samples.assistant.business.mcp.server;

import com.microsoft.openai.samples.assistant.business.models.Account;
import com.microsoft.openai.samples.assistant.business.models.Beneficiary;
import com.microsoft.openai.samples.assistant.business.models.PaymentMethod;
import com.microsoft.openai.samples.assistant.business.service.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

@Service
public class AccountMCPService {

    private final AccountService accountService;
    
    public AccountMCPService(AccountService accountService) {
       this.accountService = accountService;
    }

    @Tool(description = "Get account details and available payment methods")
    public Account getAccountDetails(String accountId) {
        return this.accountService.getAccountDetails(accountId);
    }

    @Tool(description = "Get payment method detail with available balance")
    public PaymentMethod getPaymentMethodDetails(String paymentMethodId) {
       return this.accountService.getPaymentMethodDetails(paymentMethodId);
    }

    @Tool(description = "Get list of registered beneficiaries for a specific account")
    public List<Beneficiary> getRegisteredBeneficiary(String accountId) {
     return this.accountService.getRegisteredBeneficiary(accountId);
    }
}
```

**关键点**：

1. **`@Service`**：Spring 组件注解，让 Spring 管理这个类
2. **`@Tool`**：Spring AI 的注解，标记这个方法是一个 MCP 工具
3. **`description`**：工具的描述，Agent 会根据这个描述决定是否调用

---

#### 2.3 `@Tool` 注解的作用

**`@Tool` 是 Spring AI 提供的注解**，用于标记一个方法为 MCP 工具。

**语法**：
```java
@Tool(description = "工具的描述")
public ReturnType methodName(ParameterType parameter) {
    // 实现
}
```

**示例**：
```java
@Tool(description = "Get account details and available payment methods")
public Account getAccountDetails(String accountId) {
    return this.accountService.getAccountDetails(accountId);
}
```

**Spring AI 自动处理**：
1. 扫描所有 `@Tool` 注解的方法
2. 提取方法名、参数、返回类型
3. 生成工具规格（ToolSpecification）
4. 注册到 MCP Server

---

### 3. Spring Boot API 如何变成 MCP 工具？

#### 3.1 转换过程

**步骤 1：添加依赖**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

**步骤 2：创建 MCP Service**
```java
@Service
public class AccountMCPService {
    
    @Tool(description = "Get account details")
    public Account getAccountDetails(String accountId) {
        // 实现
    }
}
```

**步骤 3：Spring Boot 启动时自动注册**
```
Spring Boot 启动
    ↓
扫描 @Service 注解
    ↓
发现 AccountMCPService
    ↓
扫描 @Tool 注解
    ↓
发现 getAccountDetails 方法
    ↓
提取工具信息:
  - 名称: getAccountDetails
  - 描述: "Get account details"
  - 参数: accountId (String)
  - 返回: Account
    ↓
生成 ToolSpecification
    ↓
注册到 MCP Server
    ↓
暴露 SSE 端点: /sse
```

---

#### 3.2 对比：传统 REST API vs MCP 工具

**传统 REST API**：
```java
@RestController
@RequestMapping("/accounts")
public class AccountController {
    
    @GetMapping("/{accountId}")
    public Account getAccountDetails(@PathVariable String accountId) {
        return accountService.getAccountDetails(accountId);
    }
}
```

**MCP 工具**：
```java
@Service
public class AccountMCPService {
    
    @Tool(description = "Get account details")
    public Account getAccountDetails(String accountId) {
        return accountService.getAccountDetails(accountId);
    }
}
```

**关键区别**：

| 维度 | REST API | MCP 工具 |
|-----|---------|---------|
| **注解** | `@GetMapping` | `@Tool` |
| **端点** | `/accounts/{accountId}` | 无固定端点（通过 MCP 协议调用） |
| **调用方式** | HTTP GET | MCP 协议（SSE/HTTP） |
| **发现机制** | OpenAPI/Swagger | MCP 自动发现 |
| **描述** | OpenAPI 文档 | `@Tool` 的 description |
| **调用者** | 前端/其他服务 | AI Agent |

---

#### 3.3 两者可以共存吗？

**可以！本项目就是这样做的！**

```java
// REST API（供前端调用）
@RestController
@RequestMapping("/accounts")
public class AccountController {
    @GetMapping("/{accountId}")
    public Account getAccountDetails(@PathVariable String accountId) {
        return accountService.getAccountDetails(accountId);
    }
}

// MCP 工具（供 AI Agent 调用）
@Service
public class AccountMCPService {
    @Tool(description = "Get account details")
    public Account getAccountDetails(String accountId) {
        return accountService.getAccountDetails(accountId);
    }
}

// 业务逻辑（共享）
@Service
public class AccountService {
    public Account getAccountDetails(String accountId) {
        // 实际业务逻辑
    }
}
```

**架构**：
```
前端 → AccountController (REST API)
          ↓
       AccountService (业务逻辑)
          ↑
AI Agent → AccountMCPService (MCP 工具)
```

**优势**：
- ✅ REST API 和 MCP 工具共享业务逻辑
- ✅ 前端可以直接调用 REST API
- ✅ AI Agent 可以通过 MCP 调用
- ✅ 代码复用，避免重复

---

### 4. MCP Client 端（Agent 端）

#### 4.1 MCP Client 的实现

**文件位置**：`app/copilot/langchain4j-agents/.../MCPToolAgent.java`

```java
public abstract class MCPToolAgent extends AbstractReActAgent {
    
    protected List<ToolSpecification> toolSpecifications;
    protected Map<String, ToolExecutor> extendedExecutorMap;
    protected List<McpClient> mcpClients;
    protected Map<String, McpClient> tool2ClientMap;

    protected MCPToolAgent(ChatLanguageModel chatModel, 
                          List<MCPServerMetadata> mcpServerMetadata) {
        super(chatModel);
        
        this.mcpClients = new ArrayList<>();
        this.tool2ClientMap = new HashMap<>();
        this.toolSpecifications = new ArrayList<>();
        this.extendedExecutorMap = new HashMap<>();

        mcpServerMetadata.forEach(metadata -> {
            if(metadata.protocolType().equals(MCPProtocolType.SSE)){
                // ① 创建 MCP 传输层
                McpTransport transport = new HttpMcpTransport.Builder()
                        .sseUrl(metadata.url())  // 例如: "http://account-service:8080/sse"
                        .logRequests(true)
                        .logResponses(true)
                        .timeout(Duration.ofHours(3))
                        .build();

                // ② 创建 MCP 客户端
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
}
```

**关键步骤**：

1. **创建传输层**：`HttpMcpTransport`（SSE 协议）
2. **创建客户端**：`DefaultMcpClient`
3. **获取工具列表**：`mcpClient.listTools()`
4. **注册工具**：添加到 `toolSpecifications` 和 `tool2ClientMap`

---

#### 4.2 MCP Client 如何调用工具？

**执行流程**：

```java
protected List<ToolExecutionResultMessage> executeToolRequests(
    List<ToolExecutionRequest> toolExecutionRequests) {
    
    List<ToolExecutionResultMessage> toolExecutionResultMessages = new ArrayList<>();
    
    for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
        String result = "ko";

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

**关键点**：
1. 根据工具名找到对应的 MCP Client
2. 调用 `mcpClient.executeTool(request)`
3. 返回工具执行结果

---

### 5. MCP 协议的通信细节

#### 5.1 MCP 协议是什么？

**MCP（Model Context Protocol）** 是一个标准协议，用于 AI Agent 和外部工具之间的通信。

**类比**：
```
HTTP 协议：浏览器 ↔ Web 服务器
MCP 协议：AI Agent ↔ 工具服务器
```

---

#### 5.2 MCP 支持的传输方式

**本项目使用**：SSE（Server-Sent Events）

**MCP 支持的传输方式**：
1. **SSE（HTTP）**：基于 HTTP 的服务端推送（本项目使用）
2. **STDIO**：标准输入输出（本地进程）
3. **WebSocket**：全双工通信（未来可能支持）

---

#### 5.3 SSE 传输层的工作流程

**SSE（Server-Sent Events）** 是一种服务端推送技术。

**工作流程**：

```
┌─────────────────────────────────────────────────────────┐
│ 1. MCP Client 连接到 MCP Server                          │
│    GET http://account-service:8080/sse                   │
│    Accept: text/event-stream                             │
└──────────────────┬──────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────┐
│ 2. MCP Server 返回工具列表（SSE 事件）                    │
│    Content-Type: text/event-stream                       │
│                                                          │
│    event: tools                                          │
│    data: [                                               │
│      {                                                   │
│        "name": "getAccountDetails",                      │
│        "description": "Get account details",             │
│        "parameters": {                                   │
│          "accountId": { "type": "string" }               │
│        }                                                 │
│      },                                                  │
│      ...                                                 │
│    ]                                                     │
└──────────────────┬──────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────┐
│ 3. MCP Client 解析工具列表                                │
│    toolSpecifications = [                                │
│      ToolSpecification(                                  │
│        name: "getAccountDetails",                        │
│        description: "Get account details",               │
│        parameters: {...}                                 │
│      ),                                                  │
│      ...                                                 │
│    ]                                                     │
└──────────────────┬──────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────┐
│ 4. Agent 决定调用工具                                     │
│    LLM 返回: ToolExecutionRequest(                       │
│      name: "getAccountDetails",                          │
│      arguments: { accountId: "ACC123456" }               │
│    )                                                     │
└──────────────────┬──────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────┐
│ 5. MCP Client 发送工具调用请求                            │
│    POST http://account-service:8080/mcp                  │
│    Content-Type: application/json                        │
│                                                          │
│    {                                                     │
│      "method": "tools/call",                             │
│      "params": {                                         │
│        "name": "getAccountDetails",                      │
│        "arguments": {                                    │
│          "accountId": "ACC123456"                        │
│        }                                                 │
│      }                                                   │
│    }                                                     │
└──────────────────┬──────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────┐
│ 6. MCP Server 执行工具                                    │
│    - 找到 @Tool 标注的 getAccountDetails 方法             │
│    - 调用 accountService.getAccountDetails("ACC123456")  │
│    - 返回 Account 对象                                   │
└──────────────────┬──────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────┐
│ 7. MCP Server 返回结果                                    │
│    HTTP 200 OK                                           │
│    Content-Type: application/json                        │
│                                                          │
│    {                                                     │
│      "result": {                                         │
│        "id": "ACC123456",                                │
│        "userName": "john.doe",                           │
│        "balance": 5000.00,                               │
│        "currency": "USD"                                 │
│      }                                                   │
│    }                                                     │
└──────────────────┬──────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────┐
│ 8. MCP Client 接收结果                                    │
│    result = "{id: ACC123456, balance: 5000}"             │
│                                                          │
│    封装为 ToolExecutionResultMessage                     │
│    添加到对话记忆                                         │
│                                                          │
│    继续 ReAct 循环                                       │
└─────────────────────────────────────────────────────────┘
```

---

### 6. Spring AI MCP Server 的自动配置

#### 6.1 Spring Boot 自动配置

**依赖**：
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

**自动配置的内容**：

1. **扫描 `@Tool` 注解**
   ```java
   @Bean
   public ToolCallbackProvider toolCallbackProvider() {
       // 扫描所有 @Tool 方法
       // 生成 ToolCallback
   }
   ```

2. **暴露 SSE 端点**
   ```java
   @RestController
   public class McpServerController {
       @GetMapping("/sse")
       public SseEmitter handleSse() {
           // 返回工具列表
       }
   }
   ```

3. **暴露 MCP 端点**
   ```java
   @PostMapping("/mcp")
   public ResponseEntity<?> handleMcpRequest(@RequestBody McpRequest request) {
       // 处理工具调用
   }
   ```

4. **生成 OpenAPI 文档**
   ```java
   // 可选：生成 OpenAPI 文档
   // 用于前端调用
   ```

---

#### 6.2 配置文件（可选）

**application.properties**（默认配置）：
```properties
# MCP Server 配置
spring.ai.mcp.server.name=account-service
spring.ai.mcp.server.version=1.0.0

# SSE 端点
spring.ai.mcp.server.sse.endpoint=/sse

# MCP 端点
spring.ai.mcp.server.mcp.endpoint=/mcp
```

**本项目没有显式配置**，使用默认值。

---

### 7. 完整示例：从 REST API 到 MCP 工具

#### 7.1 传统 REST API

**AccountController.java**：
```java
@RestController
@RequestMapping("/accounts")
public class AccountController {
    
    private final AccountService accountService;
    
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }
    
    @GetMapping("/{accountId}")
    public Account getAccountDetails(@PathVariable String accountId) {
        return accountService.getAccountDetails(accountId);
    }
}
```

**调用方式**：
```
GET http://account-service:8080/accounts/ACC123456
```

**返回**：
```json
{
  "id": "ACC123456",
  "userName": "john.doe",
  "balance": 5000.00
}
```

---

#### 7.2 转换为 MCP 工具

**步骤 1：添加依赖**
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

**步骤 2：创建 MCP Service**
```java
@Service
public class AccountMCPService {
    
    private final AccountService accountService;
    
    public AccountMCPService(AccountService accountService) {
        this.accountService = accountService;
    }
    
    @Tool(description = "Get account details and available payment methods")
    public Account getAccountDetails(String accountId) {
        return this.accountService.getAccountDetails(accountId);
    }
}
```

**步骤 3：启动 Spring Boot**
```java
@SpringBootApplication
public class AccountApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
```

**自动完成**：
- ✅ 扫描 `@Tool` 注解
- ✅ 注册工具到 MCP Server
- ✅ 暴露 SSE 端点（`/sse`）
- ✅ 暴露 MCP 端点（`/mcp`）

---

#### 7.3 Agent 调用 MCP 工具

**MCPToolAgent.java**：
```java
// 创建 MCP Client
McpTransport transport = new HttpMcpTransport.Builder()
    .sseUrl("http://account-service:8080/sse")
    .build();

McpClient mcpClient = new DefaultMcpClient.Builder()
    .transport(transport)
    .build();

// 获取工具列表
List<ToolSpecification> tools = mcpClient.listTools();
// 返回: [getAccountDetails, getPaymentMethodDetails, ...]

// Agent 决定调用
ToolExecutionRequest request = ToolExecutionRequest.builder()
    .name("getAccountDetails")
    .arguments("{ \"accountId\": \"ACC123456\" }")
    .build();

// 执行工具
String result = mcpClient.executeTool(request);
// 返回: "{id: ACC123456, balance: 5000}"
```

---

### 8. MCP vs OpenAPI/Swagger

#### 8.1 对比

| 维度 | OpenAPI/Swagger | MCP |
|-----|----------------|-----|
| **目的** | API 文档和测试 | AI Agent 工具调用 |
| **调用者** | 人类（前端/测试） | AI Agent |
| **发现机制** | 手动查看文档 | 自动发现（`listTools()`） |
| **调用方式** | HTTP REST | MCP 协议（SSE/HTTP） |
| **注解** | `@GetMapping`、`@PostMapping` | `@Tool` |
| **描述** | OpenAPI YAML | `@Tool(description)` |
| **工具数量** | 所有 API | 只有 `@Tool` 标注的 |

---

#### 8.2 两者可以共存

**本项目就是这样做的！**

```java
// REST API（OpenAPI）
@RestController
@RequestMapping("/accounts")
public class AccountController {
    @GetMapping("/{accountId}")
    public Account getAccountDetails(@PathVariable String accountId) {
        return accountService.getAccountDetails(accountId);
    }
}

// MCP 工具
@Service
public class AccountMCPService {
    @Tool(description = "Get account details")
    public Account getAccountDetails(String accountId) {
        return accountService.getAccountDetails(accountId);
    }
}

// 共享业务逻辑
@Service
public class AccountService {
    public Account getAccountDetails(String accountId) {
        // 实际业务逻辑
    }
}
```

**优势**：
- ✅ 前端使用 REST API（OpenAPI）
- ✅ AI Agent 使用 MCP 工具
- ✅ 代码复用，避免重复

---

### 9. MCP 的优势

#### 9.1 对比手动调用 REST API

**手动方式**：
```java
// 需要手动编写代码
@Tool("Get account details")
public String getAccountTool(@P("accountId") String accountId) {
    RestTemplate rest = new RestTemplate();
    ResponseEntity<String> response = rest.getForEntity(
        "http://account-service:8080/accounts/" + accountId,
        String.class
    );
    return response.getBody();
}

// 每个 API 都要写一遍
@Tool("Get payment methods")
public String getPaymentMethodsTool(@P("accountId") String accountId) {
    // 又写一遍...
}

@Tool("Get beneficiaries")
public String getBeneficiariesTool(@P("accountId") String accountId) {
    // 再写一遍...
}
```

**问题**：
- ❌ 每个 API 都要写 10-20 行代码
- ❌ 参数解析、错误处理要重复写
- ❌ API 变更时，代码也要改
- ❌ 维护成本高

---

**MCP 方式**：
```java
// 只需要提供 MCP Server URL
new AccountMCPAgent(
    chatModel, 
    "john.doe", 
    "http://account-service:8080"  // ← 就这一行！
);

// MCP 自动：
// 1. 从 MCP Server 发现所有工具
// 2. 自动提取参数（JSON Schema）
// 3. 自动调用 REST API
// 4. 自动处理类型转换
```

**优势**：
- ✅ 零代码：无需手动写 `@Tool` 注解
- ✅ 自动发现：Server 有什么工具，Agent 就能用什么
- ✅ 自动更新：Server 改了工具，Agent 自动同步
- ✅ 类型安全：自动生成参数绑定和验证

---

#### 9.2 核心价值

```
让 AI Agent 像使用 Swagger UI 一样简单：
1. 连接到 API Server
2. 自动发现所有可用接口
3. 根据描述决定调用哪个
4. 自动构造参数并执行
```

---

### 10. 实际案例：Account Service

#### 10.1 项目结构

```
app/business-api/account/
├── src/main/java/.../
│   ├── controller/
│   │   └── AccountController.java      # REST API
│   ├── mcp/server/
│   │   └── AccountMCPService.java      # MCP 工具
│   └── service/
│       └── AccountService.java         # 业务逻辑
├── src/main/resources/
│   └── account.yaml                    # OpenAPI 文档
└── pom.xml                             # 依赖
```

---

#### 10.2 REST API（供前端调用）

**AccountController.java**：
```java
@RestController
@RequestMapping("/accounts")
public class AccountController {
    
    @GetMapping("/{accountId}")
    public Account getAccountDetails(@PathVariable String accountId) {
        return accountService.getAccountDetails(accountId);
    }
    
    @GetMapping("/{accountId}/paymentmethods/{methodId}")
    public PaymentMethod getPaymentMethodDetails(
        @PathVariable String accountId, 
        @PathVariable String methodId
    ) {
        return accountService.getPaymentMethodDetails(methodId);
    }
    
    @GetMapping("/{accountId}/registeredBeneficiaries")
    public List<Beneficiary> getBeneficiaryDetails(@PathVariable String accountId) {
        return accountService.getRegisteredBeneficiary(accountId);
    }
}
```

**端点**：
```
GET /accounts/{accountId}
GET /accounts/{accountId}/paymentmethods/{methodId}
GET /accounts/{accountId}/registeredBeneficiaries
```

---

#### 10.3 MCP 工具（供 AI Agent 调用）

**AccountMCPService.java**：
```java
@Service
public class AccountMCPService {
    
    @Tool(description = "Get account details and available payment methods")
    public Account getAccountDetails(String accountId) {
        return this.accountService.getAccountDetails(accountId);
    }
    
    @Tool(description = "Get payment method detail with available balance")
    public PaymentMethod getPaymentMethodDetails(String paymentMethodId) {
       return this.accountService.getPaymentMethodDetails(paymentMethodId);
    }
    
    @Tool(description = "Get list of registered beneficiaries for a specific account")
    public List<Beneficiary> getRegisteredBeneficiary(String accountId) {
     return this.accountService.getRegisteredBeneficiary(accountId);
    }
}
```

**MCP 端点**：
```
SSE: /sse
MCP: /mcp
```

---

#### 10.4 业务逻辑（共享）

**AccountService.java**：
```java
@Service
public class AccountService {
    
    public Account getAccountDetails(String accountId) {
        // 实际业务逻辑
        // 从数据库查询账户信息
        return new Account(...);
    }
    
    public PaymentMethod getPaymentMethodDetails(String paymentMethodId) {
        // 实际业务逻辑
        return new PaymentMethod(...);
    }
    
    public List<Beneficiary> getRegisteredBeneficiary(String accountId) {
        // 实际业务逻辑
        return List.of(...);
    }
}
```

---

#### 10.5 架构图

```
┌─────────────────────────────────────────────────────┐
│         前端 (React App)                              │
│         调用 REST API                                 │
└──────────────────┬──────────────────────────────────┘
                   │
                   │ HTTP REST
                   │ GET /accounts/ACC123456
                   │
┌──────────────────▼──────────────────────────────────┐
│         AccountController (REST API)                 │
│         @GetMapping("/{accountId}")                  │
└──────────────────┬──────────────────────────────────┘
                   │
                   │ 调用
                   │
┌──────────────────▼──────────────────────────────────┐
│         AccountService (业务逻辑)                     │
│         getAccountDetails(accountId)                 │
└──────────────────┬──────────────────────────────────┘
                   │
                   │ 调用
                   │
┌──────────────────▼──────────────────────────────────┐
│         AccountMCPService (MCP 工具)                 │
│         @Tool(description = "...")                   │
└──────────────────┬──────────────────────────────────┘
                   │
                   │ MCP 协议 (SSE)
                   │
┌──────────────────▼──────────────────────────────────┐
│         AI Agent (PaymentMCPAgent)                   │
│         调用 MCP 工具                                 │
└─────────────────────────────────────────────────────┘
```

---

## 🎓 总结

### 核心要点

| 要点 | 说明 |
|-----|------|
| **MCP 架构** | Client（Agent 端）+ Server（业务 API 端） |
| **MCP 协议** | SSE（HTTP）传输，自动发现工具 |
| **Spring AI** | `spring-ai-starter-mcp-server-webmvc` 自动配置 |
| **`@Tool` 注解** | 标记方法为 MCP 工具 |
| **自动发现** | `mcpClient.listTools()` 获取所有工具 |
| **代码复用** | REST API 和 MCP 工具共享业务逻辑 |

---

### MCP 的优势

| 优势 | 说明 |
|-----|------|
| **零代码** | 无需手动写工具调用代码 |
| **自动发现** | Server 有什么工具，Agent 就能用什么 |
| **自动更新** | Server 改了工具，Agent 自动同步 |
| **类型安全** | 自动生成参数绑定和验证 |
| **易于维护** | 修改工具，只需改 Server 端 |

---

### 实际案例

**Account Service**：
- REST API：`AccountController`（供前端调用）
- MCP 工具：`AccountMCPService`（供 AI Agent 调用）
- 业务逻辑：`AccountService`（共享）

**调用方式**：
- 前端：`GET /accounts/ACC123456`
- AI Agent：MCP 协议调用 `getAccountDetails`

---

## 🚀 下一步

恭喜你理解了 MCP 的实现原理！现在你可以：

1. **动手实战**：修改 Account Agent，添加新功能
2. **总结回顾**：回顾整个学习过程

**准备好了吗？告诉我你想做什么！** 🎓
