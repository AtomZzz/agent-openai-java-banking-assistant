# 🛠️ 实战：修改 Account Agent 添加新功能

> **目标**：动手实践，为 Account Agent 添加"查询账户类型"功能
> **预计时间**：1-2 小时
> **难度**：⭐⭐（中级）

---

## 📋 实战目录

1. [需求分析](#1-需求分析)
2. [实现计划](#2-实现计划)
3. [步骤 1：修改 Account 模型](#3-步骤-1修改-account-模型)
4. [步骤 2：修改 AccountService](#4-步骤-2修改-accountservice)
5. [步骤 3：添加 REST API](#5-步骤-3添加-rest-api)
6. [步骤 4：添加 MCP 工具](#6-步骤-4添加-mcp-工具)
7. [步骤 5：更新 OpenAPI 文档](#7-步骤-5更新-openapi-文档)
8. [步骤 6：测试验证](#8-步骤-6测试验证)
9. [常见问题排查](#9-常见问题排查)
10. [学习总结](#10-学习总结)

---

## 1. 需求分析

### 1.1 业务场景

**用户故事**：
```
作为银行客户，
我希望能够查询我的账户类型（储蓄账户、支票账户、信用账户等），
以便了解我的账户功能和限制。
```

**示例对话**：
```
用户: "我的账户是什么类型的？"
Agent: "您的账户 1000 是储蓄账户（Savings Account），
        特点：
        - 年利率 2.5%
        - 每月最多取款 6 次
        - 最低余额要求 $100"
```

---

### 1.2 功能需求

**必须实现**：
- ✅ 查询账户类型（Savings、Checking、Credit）
- ✅ 返回账户类型描述
- ✅ 通过 MCP 暴露给 Agent

**可选实现**：
- ⭐ 账户类型特点和限制
- ⭐ 账户类型的费用和利率

---

### 1.3 技术要求

**API 设计**：
```
GET /accounts/{accountId}/type
返回: {
  "type": "Savings",
  "description": "储蓄账户",
  "features": ["年利率 2.5%", "每月最多取款 6 次"],
  "limits": {
    "minimumBalance": 100,
    "maxWithdrawalsPerMonth": 6
  }
}
```

**MCP 工具**：
```java
@Tool(description = "Get account type with features and limits")
public AccountType getAccountType(String accountId) {
    // 实现
}
```

---

## 2. 实现计划

### 2.1 文件修改清单

```
app/business-api/account/
├── src/main/java/.../models/
│   └── AccountType.java                    ← 新建（模型类）
├── src/main/java/.../service/
│   └── AccountService.java                 ← 修改（添加方法）
├── src/main/java/.../controller/
│   └── AccountController.java              ← 修改（添加端点）
├── src/main/java/.../mcp/server/
│   └── AccountMCPService.java              ← 修改（添加工具）
└── src/main/resources/
    └── account.yaml                        ← 修改（OpenAPI 文档）
```

---

### 2.2 实现步骤

```
步骤 1: 创建 AccountType 模型类
         ↓
步骤 2: 修改 AccountService（添加业务逻辑）
         ↓
步骤 3: 修改 AccountController（添加 REST API）
         ↓
步骤 4: 修改 AccountMCPService（添加 MCP 工具）
         ↓
步骤 5: 更新 OpenAPI 文档
         ↓
步骤 6: 测试验证
```

---

## 3. 步骤 1：修改 Account 模型

### 3.1 创建 AccountType 模型

**文件**：`AccountType.java`

**位置**：`app/business-api/account/src/main/java/com/microsoft/openai/samples/assistant/business/models/AccountType.java`

```java
package com.microsoft.openai.samples.assistant.business.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountType(
    @JsonProperty("type") String type,
    @JsonProperty("description") String description,
    @JsonProperty("features") List<String> features,
    @JsonProperty("limits") AccountLimits limits
) {
    // 嵌套类：账户限制
    public record AccountLimits(
        @JsonProperty("minimumBalance") Double minimumBalance,
        @JsonProperty("maxWithdrawalsPerMonth") Integer maxWithdrawalsPerMonth,
        @JsonProperty("monthlyFee") Double monthlyFee,
        @JsonProperty("interestRate") Double interestRate
    ) {}
}
```

**解释**：
- `type`：账户类型（Savings、Checking、Credit）
- `description`：类型描述
- `features`：特点列表
- `limits`：限制（最低余额、取款次数、月费、利率）

---

## 4. 步骤 2：修改 AccountService

### 4.1 添加账户类型数据

**文件**：`AccountService.java`

**修改**：在构造函数中添加账户类型映射

```java
@Service
public class AccountService {

    private final Map<String, Account> accounts;
    private final Map<String, PaymentMethod> paymentMethods;
    private final Map<String, AccountType> accountTypes;  // ← 新增

    public AccountService() {
        this.accounts = new HashMap<>();
        this.paymentMethods = new HashMap<>();
        this.accountTypes = new HashMap<>();  // ← 新增
        
        // ... 现有代码 ...
        
        // 添加账户类型数据
        this.accountTypes.put("1000", new AccountType(
            "Savings",
            "储蓄账户 - 适合储蓄和积累利息",
            Arrays.asList(
                "年利率 2.5%",
                "每月最多取款 6 次",
                "免费提供在线银行服务",
                "自动转账功能"
            ),
            new AccountType.AccountLimits(100.0, 6, 0.0, 2.5)
        ));
        
        this.accountTypes.put("1010", new AccountType(
            "Checking",
            "支票账户 - 适合日常交易",
            Arrays.asList(
                "无限取款",
                "免费支票簿",
                "借记卡",
                "在线账单支付"
            ),
            new AccountType.AccountLimits(50.0, null, 5.0, 0.5)
        ));
        
        this.accountTypes.put("1020", new AccountType(
            "Credit",
            "信用账户 - 适合大额消费",
            Arrays.asList(
                "信用额度 $5000",
                "积分奖励",
                "旅行保险",
                "购物保护"
            ),
            new AccountType.AccountLimits(0.0, null, 10.0, 18.9)
        ));
    }
    
    // ... 现有方法 ...
}
```

---

### 4.2 添加查询方法

**修改**：添加 `getAccountType` 方法

```java
public AccountType getAccountType(String accountId) {
    if (accountId == null || accountId.isEmpty())
        throw new IllegalArgumentException("AccountId is empty or null");
    try {
        Integer.parseInt(accountId);
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("AccountId is not a valid number");
    }
    // Return account type from the map
    return this.accountTypes.get(accountId);
}
```

---

## 5. 步骤 3：添加 REST API

### 5.1 修改 AccountController

**文件**：`AccountController.java`

**修改**：添加新的端点

```java
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // ... 现有方法 ...

    @GetMapping("/{accountId}/type")
    public AccountType getAccountType(@PathVariable String accountId) {
        logger.info("Received request to get account type for account id: {}", accountId);
        return accountService.getAccountType(accountId);
    }
}
```

---

### 5.2 测试 REST API

**使用 curl 测试**：
```bash
curl http://localhost:8082/accounts/1000/type
```

**期望响应**：
```json
{
  "type": "Savings",
  "description": "储蓄账户 - 适合储蓄和积累利息",
  "features": [
    "年利率 2.5%",
    "每月最多取款 6 次",
    "免费提供在线银行服务",
    "自动转账功能"
  ],
  "limits": {
    "minimumBalance": 100.0,
    "maxWithdrawalsPerMonth": 6,
    "monthlyFee": 0.0,
    "interestRate": 2.5
  }
}
```

---

## 6. 步骤 4：添加 MCP 工具

### 6.1 修改 AccountMCPService

**文件**：`AccountMCPService.java`

**修改**：添加 MCP 工具

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

    @Tool(description = "Get payment method detail with available balance")
    public PaymentMethod getPaymentMethodDetails(String paymentMethodId) {
       return this.accountService.getPaymentMethodDetails(paymentMethodId);
    }

    @Tool(description = "Get list of registered beneficiaries for a specific account")
    public List<Beneficiary> getRegisteredBeneficiary(String accountId) {
     return this.accountService.getRegisteredBeneficiary(accountId);
    }
    
    // ← 新增 MCP 工具
    @Tool(description = "Get account type with features and limits")
    public AccountType getAccountType(String accountId) {
        return this.accountService.getAccountType(accountId);
    }
}
```

**关键点**：
- `@Tool` 注解：标记为 MCP 工具
- `description`：清晰的描述，Agent 会根据这个描述决定是否调用

---

### 6.2 验证 MCP 工具注册

**检查日志**：
```
INFO  [main] o.s.a.m.s.McpServerAutoConfiguration : 
  Registered MCP tool: getAccountType
  Description: Get account type with features and limits
```

---

## 7. 步骤 5：更新 OpenAPI 文档

### 7.1 修改 account.yaml

**文件**：`account.yaml`

**修改**：添加新的 API 定义

```yaml
paths:
  # ... 现有路径 ...
  
  /accounts/{accountid}/type:
    get:
      summary: Get account type with features and limits
      description: Get account type information including features and limits
      operationId: getAccountType
      parameters:
        - name: accountid
          description: id of specific account
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: Account type details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/AccountType'

components:
  schemas:
    # ... 现有 schemas ...
    
    AccountType:
      type: object
      properties:
        type:
          type: string
          description: The type of account (Savings, Checking, Credit)
        description:
          type: string
          description: Description of the account type
        features:
          type: array
          items:
            type: string
          description: List of features for this account type
        limits:
          $ref: '#/components/schemas/AccountLimits'
    
    AccountLimits:
      type: object
      properties:
        minimumBalance:
          type: number
          format: double
          description: Minimum balance required
        maxWithdrawalsPerMonth:
          type: integer
          description: Maximum number of withdrawals per month (null for unlimited)
        monthlyFee:
          type: number
          format: double
          description: Monthly maintenance fee
        interestRate:
          type: number
          format: double
          description: Annual interest rate (percentage)
```

---

## 8. 步骤 6：测试验证

### 8.1 启动服务

**启动 Account Service**：
```bash
cd app/business-api/account
./mvnw spring-boot:run
```

**检查日志**：
```
INFO  [main] c.m.o.s.a.b.AccountApplication : 
  Started AccountApplication in 2.345 seconds
INFO  [main] o.s.a.m.s.McpServerAutoConfiguration : 
  Registered 4 MCP tools
  - getAccountDetails
  - getPaymentMethodDetails
  - getRegisteredBeneficiary
  - getAccountType  ← 新工具
```

---

### 8.2 测试 REST API

**测试 1：查询储蓄账户类型**
```bash
curl http://localhost:8082/accounts/1000/type
```

**期望响应**：
```json
{
  "type": "Savings",
  "description": "储蓄账户 - 适合储蓄和积累利息",
  "features": [
    "年利率 2.5%",
    "每月最多取款 6 次",
    "免费提供在线银行服务",
    "自动转账功能"
  ],
  "limits": {
    "minimumBalance": 100.0,
    "maxWithdrawalsPerMonth": 6,
    "monthlyFee": 0.0,
    "interestRate": 2.5
  }
}
```

**测试 2：查询支票账户类型**
```bash
curl http://localhost:8082/accounts/1010/type
```

**测试 3：查询信用账户类型**
```bash
curl http://localhost:8082/accounts/1020/type
```

---

### 8.3 测试 MCP 工具

**使用 Postman 测试 MCP 端点**：

**请求 1：获取工具列表**
```
GET http://localhost:8082/sse
Accept: text/event-stream
```

**期望响应**：
```
event: tools
data: [
  {
    "name": "getAccountDetails",
    "description": "Get account details and available payment methods",
    ...
  },
  {
    "name": "getAccountType",
    "description": "Get account type with features and limits",
    "parameters": {
      "accountId": { "type": "string" }
    }
  },
  ...
]
```

**请求 2：调用工具**
```
POST http://localhost:8082/mcp
Content-Type: application/json

{
  "method": "tools/call",
  "params": {
    "name": "getAccountType",
    "arguments": {
      "accountId": "1000"
    }
  }
}
```

**期望响应**：
```json
{
  "result": {
    "type": "Savings",
    "description": "储蓄账户 - 适合储蓄和积累利息",
    "features": ["年利率 2.5%", ...],
    "limits": { ... }
  }
}
```

---

### 8.4 测试 Agent 集成

**启动 Copilot Backend**：
```bash
cd app/copilot
./mvnw spring-boot:run
```

**启动前端**：
```bash
cd app/frontend
npm start
```

**在聊天界面测试**：
```
用户: "我的账户是什么类型的？"

期望 Agent 响应:
"您的账户 1000 是储蓄账户（Savings Account），
 特点：
 • 年利率 2.5%
 • 每月最多取款 6 次
 • 免费提供在线银行服务
 • 自动转账功能
 
 限制：
 • 最低余额：$100
 • 月费：$0
 • 利率：2.5%"
```

---

## 9. 常见问题排查

### 9.1 问题 1：编译错误

**错误**：
```
cannot find symbol
  symbol:   class AccountType
  location: class AccountService
```

**原因**：没有导入 `AccountType` 类

**解决**：
```java
// 在 AccountService.java 顶部添加导入
import com.microsoft.openai.samples.assistant.business.models.AccountType;
```

---

### 9.2 问题 2：MCP 工具未注册

**现象**：Agent 无法调用 `getAccountType`

**检查**：
1. 查看日志，确认工具已注册
2. 检查 `@Tool` 注解是否正确
3. 确认 `@Service` 注解存在

**解决**：
```java
@Service  // ← 必须有 @Service
public class AccountMCPService {
    
    @Tool(description = "...")  // ← 必须有 @Tool
    public AccountType getAccountType(String accountId) {
        // ...
    }
}
```

---

### 9.3 问题 3：REST API 返回 404

**错误**：
```
curl http://localhost:8082/accounts/1000/type
404 Not Found
```

**检查**：
1. 确认 AccountController 有 `@GetMapping("/{accountId}/type")`
2. 确认服务已重启
3. 检查端口是否正确（8082）

**解决**：
```java
@RestController
@RequestMapping("/accounts")  // ← 必须有 @RequestMapping
public class AccountController {
    
    @GetMapping("/{accountId}/type")  // ← 必须有 @GetMapping
    public AccountType getAccountType(@PathVariable String accountId) {
        // ...
    }
}
```

---

### 9.4 问题 4：Agent 返回错误信息

**现象**：
```
用户: "我的账户是什么类型的？"
Agent: "抱歉，我无法查询账户类型"
```

**可能原因**：
1. MCP 工具未注册
2. Supervisor 未路由到 Account Agent
3. 工具描述不清晰

**解决**：
1. 检查 MCP 工具日志
2. 优化 `@Tool` 描述：
   ```java
   @Tool(description = "Get account type information including features, limits, and description")
   ```

---

## 10. 学习总结

### 10.1 你学到了什么？

**技术技能**：
- ✅ 如何添加新的 REST API
- ✅ 如何添加新的 MCP 工具
- ✅ 如何更新 OpenAPI 文档
- ✅ 如何测试 REST API 和 MCP 工具
- ✅ 如何排查常见问题

**设计思维**：
- ✅ 需求分析的重要性
- ✅ 分层架构的理解（Controller → Service → Model）
- ✅ REST API 和 MCP 工具的共存
- ✅ 测试验证的必要性

---

### 10.2 关键要点

| 要点 | 说明 |
|-----|------|
| **分层架构** | Controller（API）→ Service（业务）→ Model（数据） |
| **代码复用** | REST API 和 MCP 工具共享 Service 层 |
| **注解驱动** | `@Tool`、`@GetMapping`、`@Service` |
| **测试验证** | REST API 测试 + MCP 测试 + Agent 测试 |
| **问题排查** | 查看日志、检查注解、验证配置 |

---

### 10.3 下一步建议

**完成本次实战后**：

1. **尝试添加更多功能**：
   - 查询账户交易摘要
   - 查询账户信用评分
   - 查询账户奖励积分

2. **优化现有功能**：
   - 添加错误处理
   - 添加参数验证
   - 添加缓存

3. **学习其他 Agent**：
   - 深入 Transaction Agent
   - 优化 Payment Agent
   - 创建新的 Agent

---

## 🎓 恭喜你完成实战！

你已经成功：
- ✅ 添加了新的 AccountType 模型
- ✅ 修改了 AccountService（业务逻辑）
- ✅ 添加了 REST API（AccountController）
- ✅ 添加了 MCP 工具（AccountMCPService）
- ✅ 更新了 OpenAPI 文档
- ✅ 进行了完整测试

**这是你 AI 开发之旅的重要一步！** 🚀

---

**告诉我：你完成了吗？有什么问题吗？准备好继续前进了吗？** 📚
