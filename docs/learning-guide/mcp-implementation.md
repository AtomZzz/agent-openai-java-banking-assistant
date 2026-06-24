## MCP 化业务服务（让 LLM 能够直接调用）

本文档总结了本仓库中如何把传统业务服务（Spring Boot 微服务）“MCP 化”，以便被多智能体/LLM 通过 Model Context Protocol (MCP) 发现并调用。包含实现要点、代码位置、端到端调用流程、测试与排错建议。

### 核心目标

- 将已有的 REST 服务以“工具（tool）”的形式暴露出来，使得 Agent（基于 langchain4j / LLM）能够：
  - 自动发现服务（列出可用工具）
  - 将自然语言解析为工具调用参数并执行工具
  - 接收工具执行结果并继续对话流

### 关键技术栈

- 服务端（MCP Server）：Spring AI 的 MCP Server 支持（artifact: `spring-ai-starter-mcp-server-webmvc`）
- 客户端（MCP Client / Agent）：langchain4j 的 MCP 客户端（`dev.langchain4j.mcp.client`）
- 传输层：通过 SSE（Server-Sent Events）连接，Agent 订阅服务端的工具清单并执行工具调用

### 在本工程中的实现位置（快速索引）

- 业务微服务（示例）：
  - `app/business-api/account`：Account 服务
    - MCP 服务类：`src/main/java/.../business/mcp/server/AccountMCPService.java`
    - MCP 配置：`src/main/java/.../business/mcp/config/MCPServerConfiguration.java`
    - dev 配置（端口）：`src/main/resources/application-dev.properties`（server.port=8070）
  - `app/business-api/payment`：Payment 服务
    - MCP 服务类：`.../business/mcp/server/PaymentMCPService.java`
    - MCP 配置：`.../business/mcp/config/MCPServerConfiguration.java`
  - `app/business-api/transactions-history`：Transactions 服务
    - MCP 服务类：`.../business/mcp/server/TransactionMCPService.java`

- 依赖与 POM：各服务的 `pom.xml` 中引入了 `spring-ai-starter-mcp-server-webmvc`（并使用 `spring-ai-bom` 管理版本）

- Agent / MCP 客户端示例：
  - `app/copilot/langchain4j-agents` 包含 `MCPToolAgent.java`（构造 MCP client、列出工具并执行工具调用）以及 `AccountMCPAgent`, `PaymentMCPAgent` 等示例 agent。
  - 测试/演示：`app/copilot/langchain4j-agents/src/test/java/...` 中有若干 `*IntegrationTest.java` 演示如何创建 agent 并调用本地 MCP 服务（SSE 地址，例如 `http://localhost:8070/sse`）。

### 服务端（MCP 化）的实现步骤

下面展示的是本仓库采用的做法（与 Spring AI 的标准做法一致）：

1. 引入依赖
   - 在业务服务的 `pom.xml` 中添加 `spring-ai-bom`（dependencyManagement）以及 `spring-ai-starter-mcp-server-webmvc`。
   - 参考：`app/business-api/account/pom.xml` 中的依赖声明。

2. 在业务代码中定义“工具”对象（Tool Object）
   - 创建一个 Spring `@Service` 类，内部定义对业务服务的包装方法，并在方法上使用 `@Tool` 注解；若需要对参数做补充描述可使用 `@ToolParam`。
   - 这些方法的签名（参数与返回值）会被自动作为工具的参数类型与返回类型对外描述。
   - 示例（摘自 `AccountMCPService.java`）：

     - `@Service` public class AccountMCPService { ... }
     - `@Tool(description = "Get account details and available payment methods")`
       public Account getAccountDetails(String accountId) { ... }

3. 将工具对象注册到 `ToolCallbackProvider`
   - 使用 `MethodToolCallbackProvider.builder().toolObjects(...).build()` 来把一个或多个工具对象包装成 `ToolCallbackProvider` bean。
   - 在配置类中创建对应的 `@Bean`，这样 Spring AI 的 MCP Server 自动会读取这些 bean 并把工具通过 MCP 对外暴露。
   - 示例：`MCPServerConfiguration.java` 中为 `AccountMCPService` 和 `UserMCPService` 分别返回 `ToolCallbackProvider`。

4. MCP Server 端点（默认由 spring-ai 提供）
   - Spring AI 的 MCP Server starter 会提供若干端点（例如：SSE 端点用于 Agent 连接，和一个用于获取元数据的 `mcp` 端点）。可通过配置覆盖默认路径，常见配置项示例：
     - `spring.ai.mcp.server.name`、`spring.ai.mcp.server.version`
     - `spring.ai.mcp.server.sse.endpoint=/sse`
     - `spring.ai.mcp.server.mcp.endpoint=/mcp`
   - 在本项目中，服务启动后（例如 `account` 服务在 8070 端口）会有 SSE 地址类似 `http://localhost:8070/sse`，agent 通过该地址创建 `HttpMcpTransport` 并建立连接。

5. 可选：权限与认证
   - 如果生产环境需要鉴权，可在 MCP SSE / MCP 端点前加上 Spring Security 配置（例如验证 token、API key 或使用 Managed Identity）。Agent 端也需要相应传递认证信息（headers、query params 等）。

### 客户端（Agent）如何发现与调用工具（本仓库实现要点）

Agent 端的关键类是 `MCPToolAgent`（位于 `app/copilot/langchain4j-agents`）：

- 发现工具：
  - 为每个业务服务创建一个 `MCPServerMetadata`（包含 URL 与 ProtocolType=SSE）。
  - 使用 `HttpMcpTransport`（设置 `sseUrl(...)`）构建 transport，再把 transport 交给 `DefaultMcpClient`。
  - 调用 `mcpClient.listTools()` 获取该服务器上暴露的 `ToolSpecification` 列表（工具名、参数、描述等元数据）。

- 执行工具：
  - 当 Agent 决定要调用某个工具时，会构建 `ToolExecutionRequest`（包含工具名与参数），然后：
    - 如果有本地扩展 executor（extendedExecutorMap），优先用本地执行器；否则调用 `mcpClient.executeTool(toolExecutionRequest)`。
  - `mcpClient.executeTool(...)` 底层会把请求以 MCP 协议发送到服务端，服务端调用对应的 `@Tool` 方法并把结果序列化回客户端。

代码参考：`MCPToolAgent.java` 中的工具发现（listTools）与工具执行（executeTool）逻辑。

### 参数与数据类型注意事项

- 方法参数与返回值会被序列化/反序列化为 JSON。复杂类型（POJO）应当是可序列化的（通常是 Jackson 支持的 Java Bean）。
- `@ToolParam` 可以用于给参数添加描述，这些描述会出现在工具的元数据中，方便 LLM 将自然语言参数映射为具体字段。
- 如果方法返回 `void`，MCP 会将执行结果（通常为空字符串或一个成功信号）返回给 Agent；Agent 需处理空响应情况（示例 code 中若返回空，会把结果设置为 "ok"）。

### 端到端交互示例（简要）

1. 服务端 `account` 在 8070 端口启动并通过 `MethodToolCallbackProvider` 暴露 `AccountMCPService` 中带 `@Tool` 注解的方法。
2. Agent（PaymentMCPAgent / AccountMCPAgent）构造 `HttpMcpTransport` 指向 `http://localhost:8070/sse`，并创建 `DefaultMcpClient`。
3. Agent 调用 `listTools()`，获得工具元数据（名字、参数、描述）。
4. Agent 在对话中决定执行工具 `getAccountDetails`，构造 `ToolExecutionRequest` 并调用 `mcpClient.executeTool(...)`。
5. 服务端收到请求，反射调用 `AccountMCPService.getAccountDetails(accountId)`，结果序列化后通过 MCP 返回给 Agent。
6. Agent 将返回值转换为对话消息，继续与用户交互。

实际示例代码请查看：
- Agent side: `app/copilot/langchain4j-agents/src/main/java/com/microsoft/langchain4j/agent/mcp/MCPToolAgent.java`
- Service side: `app/business-api/account/src/main/java/.../AccountMCPService.java` 与 `MCPServerConfiguration.java`

### 本地测试（快速入门）

1. 在本项目根目录，可以使用仓内的启动脚本启动全部服务（基于 Docker Compose）：

   - Windows PowerShell：

     powershell -ExecutionPolicy Bypass -File .\\app\\start-compose.ps1

   - 或逐个服务启动（示例：在 cmd.exe 中运行 account 服务）

     cd app\\business-api\\account
     .\\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev

2. 启动 Agent 的示例（运行示例 main）：

   - 直接运行 Java main（示例在 `app/copilot/langchain4j-agents/src/test/java/.../PaymentMCPAgentIntegrationTest.java`）
   - 或使用 IDE 直接运行相应的类（这些测试类包含示例 agent 的构造并连接到 `http://localhost:8070/sse` 等）

3. 验证：Agent 日志中会显示列出的工具与每次 execute 的请求/响应；服务端也会记录被调用的 @Tool 方法。

### 常见问题与排错指引

- Agent 列不到任何工具：
  - 确认服务已启动并监听正确端口；检查 `spring.ai.mcp.server.sse.endpoint` 是否为 `/sse` 或你期望的路径。
  - 检查 `ToolCallbackProvider` bean 是否被 Spring 注入（配置类是否生效）。

- 工具调用返回序列化错误：
  - 检查方法返回类型与参数类型是否为 Jackson 可序列化/反序列化的类。
  - 为复杂参数添加无参构造与 getter/setter，或添加 Jackson 注解以控制序列化。

- 认证/跨域问题：
  - 如果对 SSE 或 /mcp 端点启用了 Spring Security，确保 Agent 客户端在建立连接时传递正确的认证头或 token。

- 超时与连接断开：
  - MCP 客户端（本项目使用的 `HttpMcpTransport`）允许设置超时时间（在 `MCPToolAgent` 构造时可见）。在长时间运行操作中适当增大超时。

### 设计考量与扩展点

- 保持 REST API 与 MCP 工具“双轨并行”有助于重用服务：既可被传统客户端调用，也可被 Agent 调用。
- 使用 `MethodToolCallbackProvider` 可以快速把现有的 service bean 方法暴露为 MCP 工具；如果需要更细粒度控制，可以实现自定义 `ToolCallbackProvider`。
- 对于需要异步或长时间运行的任务，可在工具实现内返回一个“任务 ID”，并让 Agent 通过另外的工具查询任务状态（避免阻塞 SSE 通道）。

---

附：若需我把现有 `docs/learning-guide/question-answer/how-mcp-is-implemented.md` 的关键信息合并或生成更详细的示例（包含完整的可运行演示、构建与运行脚本），我可以继续扩展并在仓库中添加运行指南与示例脚本。

文件位置： `docs/learning-guide/mcp-implementation.md`

