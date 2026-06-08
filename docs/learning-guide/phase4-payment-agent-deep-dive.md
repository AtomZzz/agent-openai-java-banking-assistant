# 💳 Payment Agent 深度解析

> **目标**：理解最复杂的 Agent，包含 OCR、多工具协作、复杂业务流程
> **核心内容**：PaymentMCPAgent.java (128 行) 逐行解析 + OCR 集成
> **预计时间**：2-3 小时
> **难度**：⭐⭐⭐⭐⭐（项目中最复杂）

---

## 📋 学习目录

1. [Payment Agent 概述](#1-payment-agent-概述)
2. [核心架构：为什么最复杂](#2-核心架构为什么最复杂)
3. [完整业务流程](#3-完整业务流程)
4. [逐段代码解析](#4-逐段代码解析)
5. [OCR 集成详解](#5-ocr-集成详解)
6. [多工具协作流程](#6-多工具协作流程)
7. [与简单 Agent 的对比](#7-与简单-agent-的对比)
8. [学习检查点](#8-学习检查点)

---

## 1. Payment Agent 概述

### 1.1 Payment Agent 是什么？

**Payment Agent（支付 Agent）** 是项目中最复杂、功能最完整的 Agent，专门处理支付相关任务。

**核心能力**：
1. 🔥 **OCR 发票识别**：通过 Azure Document Intelligence 自动识别发票图片
2. 🔥 **多步骤支付流程**：查询账户 → 检查历史 → 提取数据 → 提交支付
3. 🔥 **多工具协作**：同时使用 6+ 个工具完成复杂任务
4. 🔥 **智能验证**：检查重复支付、验证支付方法、确认支付详情
5. 🔥 **错误处理**：优雅处理 OCR 失败、支付失败等情况

**类比**：就像一个完整的支付系统 + 智能助手

---

### 1.2 Payment Agent 的能力范围

**可以处理的任务**：
```
✅ "我想支付这张电费账单"（上传发票图片）
✅ "帮我支付上个月的房租"
✅ "查看我是否已经支付过 Contoso 的账单"
✅ "用信用卡支付 100 美元给 John"
✅ "检查我的支付方法有哪些"
```

**不能处理的任务**：
```
❌ "查询我的账户余额" → Account Agent
❌ "查看我的交易历史" → Transaction Agent
❌ "帮我投资股票" → 超出能力范围
```

---

## 2. 核心架构：为什么最复杂

### 2.1 复杂度来源

**对比：Account Agent vs Payment Agent**

| 维度 | Account Agent | Payment Agent |
|-----|--------------|---------------|
| **工具数量** | 3 个（getAccount, getPaymentMethods, getBeneficiaries） | 9 个（OCR + 账户 + 交易 + 支付） |
| **MCP Server** | 1 个（Account Service） | 3 个（Account + Transaction + Payment） |
| **自定义工具** | 0 个 | 1 个（scanInvoice） |
| **业务流程** | 简单（1 步） | 复杂（5+ 步） |
| **外部服务** | 无 | Azure Document Intelligence |
| **代码行数** | 55 行 | 128 行 + OCR 相关代码 |

**结论**：Payment Agent 的复杂度是 Account Agent 的 **3-4 倍**！

---

### 2.2 技术栈

```
Payment Agent
├── Langchain4j（Agent 框架）
├── MCP Protocol（工具调用）
│   ├── Account Service（账户信息）
│   ├── Transaction Service（交易历史）
│   └── Payment Service（支付提交）
├── Azure Document Intelligence（OCR）
├── Azure Blob Storage（图片存储）
└── Spring Boot（业务逻辑）
```

---

## 3. 完整业务流程

### 3.1 场景：用户上传发票图片并支付

```
┌─────────────────────────────────────────────────────────────┐
│ 用户: "我想支付这张电费账单" + 上传发票图片                   │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 步骤 1: Supervisor 路由                                     │
│ LLM 思考: "支付账单 → PaymentAgent"                          │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 步骤 2: Payment Agent 分析                                  │
│ LLM 思考: "用户要支付账单，上传了图片，需要 OCR 识别"         │
│ 决定: 调用 scanInvoice 工具                                  │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 步骤 3: OCR 识别发票                                        │
│ scanInvoice("invoice_001.jpg")                              │
│ → Azure Document Intelligence                               │
│ → 提取: VendorName, InvoiceId, Amount, ...                  │
│ 返回: {VendorName: "电力公司", Amount: "¥150.00", ...}       │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 步骤 4: 向用户确认提取的数据                                 │
│ LLM: "我从发票中提取了以下信息:                              │
│       供应商: 电力公司                                       │
│       发票号: 123456                                         │
│       金额: ¥150.00                                          │
│       请确认是否正确？"                                       │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 用户: "是的，确认"                                           │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 步骤 5: 检查是否已经支付过                                   │
│ LLM 思考: "需要先检查交易历史，避免重复支付"                  │
│ 调用: searchTransactions(VendorName: "电力公司")             │
│ 返回: [] (空，未支付过)                                      │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 步骤 6: 查询支付方法                                        │
│ LLM 思考: "需要知道用户有哪些支付方式"                        │
│ 调用: getPaymentMethods()                                   │
│ 返回: [信用卡, 银行转账, PayPal]                              │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 步骤 7: 询问用户选择支付方式                                 │
│ LLM: "您有以下支付方式:                                      │
│       1. 信用卡 (Visa ***477)                                │
│       2. 银行转账                                            │
│       3. PayPal                                              │
│       请选择一种支付方式？"                                   │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 用户: "用信用卡"                                             │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 步骤 8: 获取账户信息                                        │
│ LLM 思考: "需要 accountId 和 paymentMethodId"                │
│ 调用: getAccount(username)                                  │
│ 返回: {accountId: "ACC123456"}                              │
│ 调用: getPaymentMethods()                                   │
│ 返回: [{id: "PM001", type: "Visa"}]                         │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 步骤 9: 最终确认                                            │
│ LLM: "支付详情:                                              │
│       收款方: 电力公司                                       │
│       金额: ¥150.00                                          │
│       支付方式: Visa 信用卡 (***477)                          │
│       描述: Payment for invoice 123456                       │
│       确认支付吗？(是/否)"                                    │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 用户: "是，确认支付"                                         │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 步骤 10: 提交支付                                           │
│ LLM 调用: submitPayment(                                    │
│   accountId: "ACC123456",                                   │
│   amount: 150.00,                                           │
│   payee: "电力公司",                                         │
│   paymentMethodId: "PM001",                                 │
│   description: "Payment for invoice 123456"                 │
│ )                                                           │
│ 返回: {success: true, transactionId: "TXN987654"}           │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│ 步骤 11: 通知用户                                           │
│ LLM: "支付成功！✅                                           │
│       交易号: TXN987654                                      │
│       金额: ¥150.00 已从您的信用卡扣除                        │
│       感谢您的使用！"                                         │
└─────────────────────────────────────────────────────────────┘
```

**总共 11 步！** 这就是为什么 Payment Agent 最复杂。

---

## 4. 逐段代码解析

### 📄 文件：`PaymentMCPAgent.java`（128 行）

---

#### 4.1 系统提示词 - 最详细的提示词 ⭐⭐⭐

```java
private static final String PAYMENT_AGENT_SYSTEM_MESSAGE = """
    you are a personal financial advisor who help the user with their 
    recurrent bill payments. The user may want to pay the bill uploading 
    a photo of the bill, or it may start the payment checking transactions 
    history for a specific payee.
    
    For the bill payment you need to know the: 
    - bill id or invoice number
    - payee name
    - the total amount
    
    If you don't have enough information to pay the bill ask the user to 
    provide the missing information.
    
    If the user submit a photo, always ask the user to confirm the extracted 
    data from the photo.
    
    Always check if the bill has been paid already based on payment history 
    before asking to execute the bill payment.
    
    Ask for the payment method to use based on the available methods on the 
    user account.
    
    if the user wants to pay using bank transfer, check if the payee is in 
    account registered beneficiaries list. If not ask the user to provide 
    the payee bank code.
    
    Check if the payment method selected by the user has enough funds to pay 
    the bill. Don't use the account balance to evaluate the funds.
    
    Before submitting the payment to the system ask the user confirmation 
    providing the payment details.
    
    Include in the payment description the invoice id or bill id as 
    following: payment for invoice 1527248.
    
    When submitting payment always use the available functions to retrieve 
    accountId, paymentMethodId.
    
    If the payment succeeds provide the user with the payment confirmation. 
    If not provide the user with the error message.
    
    Use HTML list or table to display bill extracted data, payments, account 
    or transaction details.
    
    Always use the below logged user details to retrieve account info:
    '{{loggedUserName}}'
    Current timestamp:
    '{{currentDateTime}}'
    
    Don't try to guess accountId,paymentMethodId from the conversation.
    When submitting payment always use functions to retrieve accountId, 
    paymentMethodId.
    
    ### Output format
    - Example of showing Payment information:
        <table border="1">
          <tr>
            <th>Payee Name</th>
            <td>contoso</td>
          </tr>
          <tr>
            <th>Invoice ID</th>
            <td>9524011000817857</td>
          </tr>
          <tr>
            <th>Amount</th>
            <td>€85.20</td>
          </tr>
          <tr>
            <th>Payment Method</th>
            <td>Visa (Card Number: ***477)</td>
          </tr>
          <tr>
            <th>Description</th>
            <td>Payment for invoice 9524011000817857</td>
          </tr>
        </table>
        
    - Example of showing Payment methods:
        <ol>
          <li><strong>Bank Transfer</strong></li>
          <li><strong>Visa</strong> (Card Number: ***3667)</li>
        </ol>
""";
```

**这是项目中最详细的系统提示词！** 让我们逐段解析：

---

##### 4.1.1 角色和职责

```java
"you are a personal financial advisor who help the user with their 
recurrent bill payments."
```

**解读**：
- 角色：个人财务顾问
- 专长：定期账单支付
- 隐含：需要处理重复性支付（如水电费、房租）

---

##### 4.1.2 两种支付场景

```java
"The user may want to pay the bill uploading a photo of the bill, 
or it may start the payment checking transactions history for a 
specific payee."
```

**场景 1：上传发票图片**
```
用户: "我想支付这张电费账单" + 上传图片
→ 需要 OCR 识别 → 提取数据 → 支付
```

**场景 2：查询历史支付**
```
用户: "帮我支付上个月的房租"
→ 查询交易历史 → 找到收款方 → 支付
```

---

##### 4.1.3 支付所需信息

```java
"For the bill payment you need to know the: 
- bill id or invoice number
- payee name
- the total amount"
```

**三个必要信息**：
1. **账单/发票号**：唯一标识
2. **收款方名称**：支付给谁
3. **总金额**：支付多少

**如果缺少信息**：
```java
"If you don't have enough information to pay the bill ask the user 
to provide the missing information."
```

**示例**：
```
用户: "我想支付账单"
Agent: "请问:
       1. 账单号是多少？
       2. 收款方是谁？
       3. 金额是多少？"
```

---

##### 4.1.4 OCR 确认机制

```java
"If the user submit a photo, always ask the user to confirm the 
extracted data from the photo."
```

**为什么需要确认？**

**原因 1：OCR 可能出错**
```
发票图片: 金额 "¥150.00"
OCR 识别: 金额 "¥1500.00"  ← 多了一个 0！

如果直接支付 → 用户损失 ¥1350 ❌
要求确认 → 用户发现错误 ✅
```

**原因 2：用户责任**
```
Agent: "我从发票中提取了以下信息:
       金额: ¥1500.00
       请确认是否正确？"

用户: "不对，应该是 ¥150.00"

→ 用户修正，避免错误支付 ✅
```

**最佳实践**：涉及金钱，必须确认！

---

##### 4.1.5 防止重复支付

```java
"Always check if the bill has been paid already based on payment 
history before asking to execute the bill payment."
```

**为什么重要？**

**场景**：
```
用户: "我想支付这张电费账单"

如果 Agent 不检查:
→ 直接支付
→ 但这张账单上个月已经支付过了！
→ 用户重复支付 ❌

如果 Agent 检查:
→ 查询交易历史
→ 发现已支付记录
→ 提醒用户: "这张账单已经在 2024-01-15 支付过了" ✅
```

**实现**：
```java
// Agent 会自动调用
searchTransactions(
    payee: "电力公司",
    invoiceId: "123456"
)

// 如果找到匹配的记录
// 提醒用户已支付
```

---

##### 4.1.6 支付方法选择

```java
"Ask for the payment method to use based on the available methods 
on the user account."
```

**流程**：
```
1. 查询用户的支付方法
   getPaymentMethods()
   → [信用卡, 银行转账, PayPal]

2. 询问用户选择
   "您有以下支付方式:
    1. 信用卡 (Visa ***477)
    2. 银行转账
    3. PayPal
    请选择一种？"

3. 用户选择
   "用信用卡"

4. 继续支付流程
```

---

##### 4.1.7 银行转账特殊处理

```java
"if the user wants to pay using bank transfer, check if the payee 
is in account registered beneficiaries list. If not ask the user 
to provide the payee bank code."
```

**为什么银行转账需要特殊处理？**

**场景 1：收款方在受益人列表中**
```
用户: "用银行转账支付给 John"
Agent: 检查受益人列表 → 找到 John → 直接转账 ✅
```

**场景 2：收款方不在列表中**
```
用户: "用银行转账支付给 NewCompany"
Agent: 检查受益人列表 → 未找到 NewCompany
Agent: "NewCompany 不在您的受益人列表中，请提供银行代码"
用户: "银行代码是 ABC123"
Agent: 添加到受益人列表 → 转账 ✅
```

**安全措施**：防止转错账户

---

##### 4.1.8 验证支付方法余额

```java
"Check if the payment method selected by the user has enough funds 
to pay the bill. Don't use the account balance to evaluate the 
funds."
```

**为什么不用账户余额？**

**错误做法**：
```
账户余额: ¥5000
支付金额: ¥100
Agent: "余额充足，可以支付" ❌

但信用卡可能已经透支！
```

**正确做法**：
```
信用卡可用额度: ¥50
支付金额: ¥100
Agent: "信用卡额度不足，请选择其他支付方式" ✅
```

**关键**：检查**支付方法的可用额度**，不是账户余额！

---

##### 4.1.9 最终确认

```java
"Before submitting the payment to the system ask the user 
confirmation providing the payment details."
```

**最终确认的重要性**：

```
Agent: "支付详情:
       收款方: 电力公司
       金额: ¥150.00
       支付方式: Visa 信用卡 (***477)
       描述: Payment for invoice 123456
       
       确认支付吗？(是/否)"

用户: "是，确认"

→ 用户最后确认，避免错误 ✅
```

**最佳实践**：涉及金钱，**双重确认**！

---

##### 4.1.10 支付描述格式

```java
"Include in the payment description the invoice id or bill id as 
following: payment for invoice 1527248."
```

**为什么需要特定格式？**

**好处 1：易于追踪**
```
交易记录: "Payment for invoice 123456"
→ 一眼看出是哪个账单的支付 ✅
```

**好处 2：防止重复**
```
查询历史: invoiceId = "123456"
→ 可以找到匹配的交易 ✅
```

---

##### 4.1.11 不要猜测 ID

```java
"Don't try to guess accountId,paymentMethodId from the conversation.
When submitting payment always use functions to retrieve accountId, 
paymentMethodId."
```

**为什么不能猜测？**

**错误做法**：
```
对话中: "我的账户是 ACC123456"
Agent: 直接使用 accountId = "ACC123456" ❌

但这可能是用户之前提到的，不是当前登录用户的账户！
```

**正确做法**：
```
Agent: 调用 getAccount(username) → 获取最新的 accountId
       调用 getPaymentMethods() → 获取最新的 paymentMethodId
       
→ 确保数据准确 ✅
```

**关键**：**始终从系统获取最新数据**，不要依赖对话历史！

---

##### 4.1.12 错误处理

```java
"If the payment succeeds provide the user with the payment 
confirmation. If not provide the user with the error message."
```

**成功**：
```
"支付成功！✅
交易号: TXN987654
金额: ¥150.00 已从您的信用卡扣除"
```

**失败**：
```
"支付失败 ❌
错误: 信用卡额度不足
建议: 请选择其他支付方式或联系银行"
```

---

##### 4.1.13 输出格式

```java
"Use HTML list or table to display bill extracted data, payments, 
account or transaction details."
```

**示例**：
```html
<table border="1">
  <tr>
    <th>Payee Name</th>
    <td>contoso</td>
  </tr>
  <tr>
    <th>Amount</th>
    <td>€85.20</td>
  </tr>
</table>
```

**好处**：
- 前端可以直接渲染 HTML
- 表格展示清晰易读
- 统一格式，易于维护

---

##### 4.1.14 动态变量

```java
"Always use the below logged user details to retrieve account info:
'{{loggedUserName}}'
Current timestamp:
'{{currentDateTime}}'"
```

**变量替换**：
```java
// 模板
"Logged user: '{{loggedUserName}}'"

// 替换后
"Logged user: 'john.doe'"
```

**作用**：
- `loggedUserName`: 确保只访问当前用户的数据
- `currentDateTime`: 提供当前时间（用于时间敏感的查询）

---

#### 4.2 构造函数 - 连接多个 MCP Server

```java
public PaymentMCPAgent(ChatLanguageModel chatModel, 
                      DocumentIntelligenceInvoiceScanHelper documentIntelligenceInvoiceScanHelper, 
                      String loggedUserName, 
                      String transactionMCPServerURL, 
                      String accountMCPServerUrl, 
                      String paymentsMCPServerUrl) {
    
    // ① 连接 3 个 MCP Server
    super(chatModel, List.of(
        new MCPServerMetadata("payment", paymentsMCPServerUrl, MCPProtocolType.SSE),
        new MCPServerMetadata("transaction", transactionMCPServerURL, MCPProtocolType.SSE),
        new MCPServerMetadata("account", accountMCPServerUrl, MCPProtocolType.SSE)
    ));

    if (loggedUserName == null || loggedUserName.isEmpty()) {
        throw new IllegalArgumentException("loggedUserName cannot be null or empty");
    }

    // ② 添加自定义工具（OCR）
    extendToolMap(documentIntelligenceInvoiceScanHelper);

    // ③ 应用模板变量
    PromptTemplate promptTemplate = PromptTemplate.from(PAYMENT_AGENT_SYSTEM_MESSAGE);
    var datetimeIso8601 = ZonedDateTime.now(ZoneId.of("UTC")).toInstant().toString();

    this.agentPrompt = promptTemplate.apply(Map.of(
            "loggedUserName", loggedUserName,
            "currentDateTime", datetimeIso8601
    ));
}
```

**① 连接 3 个 MCP Server**

```java
// Account Agent 只连接 1 个 Server
super(chatModel, List.of(
    new MCPServerMetadata("account", accountMCPServerUrl, MCPProtocolType.SSE)
));

// Payment Agent 连接 3 个 Server
super(chatModel, List.of(
    new MCPServerMetadata("payment", paymentsMCPServerUrl, MCPProtocolType.SSE),
    new MCPServerMetadata("transaction", transactionMCPServerURL, MCPProtocolType.SSE),
    new MCPServerMetadata("account", accountMCPServerUrl, MCPProtocolType.SSE)
));
```

**为什么需要 3 个 Server？**

```
Payment Service:
- submitPayment (提交支付)
- notifyTransaction (通知交易)

Transaction Service:
- searchTransactions (查询交易历史)
- getTransactionsByRecipient (按收款人查询)
- checkDuplicatePayment (检查重复支付)

Account Service:
- getAccount (获取账户信息)
- getPaymentMethods (获取支付方法)
- getBeneficiaries (获取受益人列表)
- getCreditBalance (获取信用额度)
```

**总共 9 个工具！** 这就是为什么需要 3 个 Server。

---

**② 添加自定义工具（OCR）**

```java
extendToolMap(documentIntelligenceInvoiceScanHelper);
```

**作用**：
- 添加 `scanInvoice` 工具（非 MCP 工具）
- 通过 Azure Document Intelligence 识别发票图片

**稍后详细讲解**

---

**③ 应用模板变量**

```java
// 当前时间（ISO 8601 格式）
var datetimeIso8601 = ZonedDateTime.now(ZoneId.of("UTC")).toInstant().toString();
// 例如: "2024-01-15T10:30:45Z"

// 替换变量
this.agentPrompt = promptTemplate.apply(Map.of(
    "loggedUserName", loggedUserName,      // "john.doe"
    "currentDateTime", datetimeIso8601     // "2024-01-15T10:30:45Z"
));
```

**为什么需要当前时间？**

```
用户: "我上个月支付了多少钱？"
Agent: 需要知道"上个月"是什么时间
      → 使用 currentDateTime 计算 ✅
```

---

#### 4.3 实现抽象方法

```java
@Override
public String getName() {
    return "PaymentAgent";
}

@Override
public AgentMetadata getMetadata() {
    return new AgentMetadata(
        "Personal financial advisor for submitting payment request.",
        List.of("RetrievePaymentInfo", "DisplayPaymentDetails", "SubmitPayment")
    );
}

@Override
protected String getSystemMessage() {
    return agentPrompt.text();
}
```

**关键点**：
- `getName()`: 返回 "PaymentAgent"
- `getMetadata()`: 描述清晰（Supervisor 靠这个路由）
- `getSystemMessage()`: 返回替换变量后的提示词

---

#### 4.4 自定义工具注册 - 扩展 MCP 工具 ⭐⭐⭐

```java
protected void extendToolMap(DocumentIntelligenceInvoiceScanHelper documentIntelligenceInvoiceScanHelper) {
    try {
        // ① 获取 scanInvoice 方法
        Method scanInvoiceMethod = InvoiceScanTool.class.getMethod("scanInvoice", String.class);
        
        // ② 创建工具实例
        InvoiceScanTool invoiceScanTool = new InvoiceScanTool(documentIntelligenceInvoiceScanHelper);

        // ③ 添加工具规格（从注解生成）
        this.toolSpecifications.addAll(
            ToolSpecifications.toolSpecificationsFrom(InvoiceScanTool.class)
        );
        
        // ④ 添加工具执行器
        this.extendedExecutorMap.put("scanInvoice", 
            new DefaultToolExecutor(invoiceScanTool, scanInvoiceMethod)
        );
    } catch (NoSuchMethodException e) {
        throw new AgentExecutionException(
            "scanInvoice method not found in InvoiceScanTool class", e
        );
    }
}
```

**这是扩展 MCP 工具的关键！** 让我们逐行解析：

---

**① 获取 scanInvoice 方法**

```java
Method scanInvoiceMethod = InvoiceScanTool.class.getMethod("scanInvoice", String.class);
```

**作用**：
- 通过反射获取方法
- 方法签名：`String scanInvoice(String filePath)`

---

**② 创建工具实例**

```java
InvoiceScanTool invoiceScanTool = new InvoiceScanTool(documentIntelligenceInvoiceScanHelper);
```

**作用**：
- 创建 InvoiceScanTool 实例
- 注入 DocumentIntelligenceInvoiceScanHelper（OCR 服务）

---

**③ 添加工具规格**

```java
this.toolSpecifications.addAll(
    ToolSpecifications.toolSpecificationsFrom(InvoiceScanTool.class)
);
```

**作用**：
- 从 `@Tool` 注解自动生成工具规格
- 添加到 `toolSpecifications` 列表

**InvoiceScanTool 的定义**：
```java
public class InvoiceScanTool {
    @Tool("Extract the invoice or bill data scanning a photo or image")
    public String scanInvoice(
        @P("the path to the file containing the image or photo") String filePath
    ) {
        // 实现...
    }
}
```

**自动生成的工具规格**：
```java
ToolSpecification {
    name: "scanInvoice",
    description: "Extract the invoice or bill data scanning a photo or image",
    parameters: {
        filePath: {
            type: "string",
            description: "the path to the file containing the image or photo"
        }
    }
}
```

---

**④ 添加工具执行器**

```java
this.extendedExecutorMap.put("scanInvoice", 
    new DefaultToolExecutor(invoiceScanTool, scanInvoiceMethod)
);
```

**作用**：
- 创建工具执行器（封装反射调用）
- 添加到 `extendedExecutorMap`

**执行流程**：
```
LLM 决定调用 scanInvoice
    ↓
MCPToolAgent.executeToolRequests()
    ↓
检查 extendedExecutorMap.get("scanInvoice")
    ↓
找到执行器 → 调用 invoiceScanTool.scanInvoice(filePath)
    ↓
返回 OCR 识别结果
```

---

**为什么需要自定义工具？**

**MCP 工具的局限**：
- 只能通过 HTTP 调用 REST API
- 无法调用本地服务（如 Azure Document Intelligence）

**自定义工具的优势**：
- 可以调用任何 Java 服务
- 可以集成 Azure SDK
- 可以处理复杂逻辑

**本例**：
- `scanInvoice` 不是 REST API
- 是直接调用 Azure Document Intelligence SDK
- 所以需要用自定义工具

---

## 5. OCR 集成详解

### 5.1 InvoiceScanTool - 工具封装

```java
public class InvoiceScanTool {
    private final DocumentIntelligenceInvoiceScanHelper documentIntelligenceInvoiceScanHelper;
    
    public InvoiceScanTool(DocumentIntelligenceInvoiceScanHelper documentIntelligenceInvoiceScanHelper) {
        this.documentIntelligenceInvoiceScanHelper = documentIntelligenceInvoiceScanHelper;
    }
    
    @Tool("Extract the invoice or bill data scanning a photo or image")
    public String scanInvoice(
        @P("the path to the file containing the image or photo") String filePath
    ) {
        Map<String,String> scanData = null;
        
        try {
            scanData = documentIntelligenceInvoiceScanHelper.scan(filePath);
        } catch (Exception e) {
            LOGGER.warn("Error extracting data from invoice {}:", filePath, e);
            scanData = new HashMap<>();  // 返回空 Map，不抛异常
        }
        
        LOGGER.info("scanInvoice tool: Data extracted {}:{}", filePath, scanData);
        return scanData.toString();
    }
}
```

**关键点**：

1. **`@Tool` 注解**：告诉 Langchain4j 这是一个工具
2. **`@P` 注解**：描述参数
3. **错误处理**：捕获异常，返回空 Map（不崩溃）
4. **返回值**：`Map.toString()` 格式（简单但不优雅）

---

### 5.2 DocumentIntelligenceInvoiceScanHelper - OCR 核心

```java
public class DocumentIntelligenceInvoiceScanHelper {
    private final DocumentIntelligenceClient client;
    private final BlobStorageProxy blobStorageProxy;
    private final String modelId;
    
    public DocumentIntelligenceInvoiceScanHelper(
        DocumentIntelligenceClient client, 
        BlobStorageProxy blobStorageProxy
    ) {
        this.client = client;
        this.modelId = "prebuilt-invoice";  // 使用预构建发票模型
        this.blobStorageProxy = blobStorageProxy;
    }
    
    public Map<String, String> scan(String blobName) throws IOException {
        LOGGER.info("Retrieving blob file with name [{}]", blobName);
        
        // ① 从 Azure Blob Storage 下载图片
        byte[] blobData = blobStorageProxy.getFileAsBytes(blobName);
        
        LOGGER.debug("Found blob file with name [{}] and size [{}]", 
            blobName, blobData.length);
        
        // ② 调用 Azure Document Intelligence
        SyncPoller<AnalyzeOperationDetails, AnalyzeResult> analyzeInvoicePoller =
            client.beginAnalyzeDocument(
                "prebuilt-invoice",
                new AnalyzeDocumentOptions(blobData)
            );
        
        return internalScan(analyzeInvoicePoller);
    }
    
    private Map<String, String> internalScan(
        SyncPoller<AnalyzeOperationDetails, AnalyzeResult> analyzeInvoicePoller
    ) {
        // ③ 等待 OCR 完成
        AnalyzeResult analyzeInvoiceResult = analyzeInvoicePoller.getFinalResult();
        
        LOGGER.debug("Document intelligence: start extracting data..");
        
        Map<String,String> scanData = new HashMap<>();
        
        // ④ 提取发票字段
        for (int i = 0; i < analyzeInvoiceResult.getDocuments().size(); i++) {
            AnalyzedDocument analyzedInvoice = analyzeInvoiceResult.getDocuments().get(i);
            Map<String, DocumentField> invoiceFields = analyzedInvoice.getFields();
            
            // 提取 VendorName（供应商名称）
            DocumentField vendorNameField = invoiceFields.get("VendorName");
            if (vendorNameField != null) {
                if (DocumentFieldType.STRING == vendorNameField.getType()) {
                    scanData.put("VendorName", vendorNameField.getValueString());
                }
            }
            
            // 提取 InvoiceId（发票号）
            DocumentField invoiceIdField = invoiceFields.get("InvoiceId");
            if (invoiceIdField != null) {
                scanData.put("InvoiceId", invoiceIdField.getValueString());
            }
            
            // 提取 InvoiceTotal（总金额）
            DocumentField invoiceTotalField = invoiceFields.get("InvoiceTotal");
            if (invoiceTotalField != null) {
                scanData.put("InvoiceTotal", invoiceTotalField.getContent());
            }
            
            // 提取其他字段...
            // VendorAddress, CustomerName, InvoiceDate, etc.
        }
        
        return scanData;
    }
}
```

**核心流程**：

```
1. 从 Blob Storage 下载图片
   blobStorageProxy.getFileAsBytes(blobName)
   → byte[] (图片二进制数据)

2. 调用 Azure Document Intelligence
   client.beginAnalyzeDocument("prebuilt-invoice", ...)
   → SyncPoller (异步轮询器)

3. 等待 OCR 完成
   analyzeInvoicePoller.getFinalResult()
   → AnalyzeResult

4. 提取发票字段
   analyzeInvoiceResult.getDocuments()
   → 提取 VendorName, InvoiceId, InvoiceTotal, ...

5. 返回结构化数据
   → Map<String, String>
```

---

### 5.3 Azure Document Intelligence 简介

**什么是 Azure Document Intelligence？**

Azure Document Intelligence（原 Form Recognizer）是微软的 OCR 服务，专门用于文档数据提取。

**预构建模型**：
- `prebuilt-invoice`：发票识别
- `prebuilt-receipt`：收据识别
- `prebuilt-idDocument`：身份证识别
- `prebuilt-businessCard`：名片识别

**本项目使用**：`prebuilt-invoice`（发票识别）

**识别的字段**：

| 字段 | 说明 | 示例 |
|-----|------|------|
| `VendorName` | 供应商名称 | "电力公司" |
| `VendorAddress` | 供应商地址 | "北京市朝阳区..." |
| `CustomerName` | 客户名称 | "John Doe" |
| `InvoiceId` | 发票号 | "123456" |
| `InvoiceDate` | 发票日期 | "2024-01-15" |
| `InvoiceTotal` | 总金额 | "¥150.00" |
| `Items` | 商品列表 | [...] |

---

### 5.4 图片上传流程

**前端 → Blob Storage → Agent**

```
┌─────────────────────────────────────────────────────────┐
│ 1. 前端上传图片                                          │
│    POST /api/upload                                     │
│    Content-Type: multipart/form-data                    │
│    Body: [binary image data]                            │
└──────────────────┬──────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────┐
│ 2. 后端接收图片                                         │
│    ContentController.uploadImage()                      │
│    → 上传到 Azure Blob Storage                           │
│    → 返回 blobName: "invoice_001.jpg"                   │
└──────────────────┬──────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────┐
│ 3. 前端发送聊天请求                                      │
│    POST /api/chat                                       │
│    {                                                    │
│      "messages": [                                      │
│        {                                                │
│          "role": "user",                                │
│          "content": "我想支付这张账单",                   │
│          "attachments": ["invoice_001.jpg"]             │
│        }                                                │
│      ]                                                  │
│    }                                                    │
└──────────────────┬──────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────┐
│ 4. Payment Agent 处理                                   │
│    LLM 看到 attachments: ["invoice_001.jpg"]             │
│    决定调用: scanInvoice("invoice_001.jpg")              │
└──────────────────┬──────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────┐
│ 5. OCR 识别                                             │
│    InvoiceScanTool.scanInvoice("invoice_001.jpg")        │
│    → BlobStorageProxy.getFileAsBytes("invoice_001.jpg")  │
│    → Azure Document Intelligence                        │
│    → 返回: {VendorName: "电力公司", Amount: "¥150.00"}    │
└─────────────────────────────────────────────────────────┘
```

---

## 6. 多工具协作流程

### 6.1 完整工具调用链

```
用户: "我想支付这张电费账单" + 上传发票图片
         │
         ▼
┌─────────────────────────────────────┐
│ 1. scanInvoice("invoice_001.jpg")    │
│    → OCR 识别发票                    │
│    → 返回: {VendorName, Amount, ...} │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│ 2. searchTransactions(VendorName)    │
│    → 检查是否已经支付过              │
│    → 返回: [] (未支付)                │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│ 3. getPaymentMethods()               │
│    → 获取用户的支付方法              │
│    → 返回: [信用卡, 银行转账]        │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│ 4. getAccount(username)              │
│    → 获取账户信息                    │
│    → 返回: {accountId: "ACC123456"}  │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│ 5. getBeneficiaries()                │
│    → 检查收款方是否在受益人列表      │
│    → 返回: [John, Alice, ...]        │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│ 6. submitPayment(...)                │
│    → 提交支付                        │
│    → 返回: {success: true, txnId}    │
└─────────────────────────────────────┘
```

**总共调用 6 个工具！** 这就是多工具协作。

---

### 6.2 工具调用的智能决策

**LLM 如何决定调用哪个工具？**

**场景 1：用户上传发票**
```
用户: "我想支付这张电费账单" + 图片

LLM 思考:
- 有图片 → 需要 OCR
- 调用 scanInvoice ✅
```

**场景 2：用户查询历史**
```
用户: "我上个月支付了多少钱给电力公司？"

LLM 思考:
- 查询历史 → 需要交易记录
- 调用 searchTransactions ✅
```

**场景 3：用户选择支付方式**
```
用户: "用信用卡支付"

LLM 思考:
- 需要知道有哪些支付方法
- 调用 getPaymentMethods ✅
```

**场景 4：用户确认支付**
```
用户: "确认支付"

LLM 思考:
- 需要 accountId 和 paymentMethodId
- 调用 getAccount ✅
- 调用 getPaymentMethods ✅
- 调用 submitPayment ✅
```

**关键**：LLM **自主决定**调用顺序，不需要人工编排！

---

## 7. 与简单 Agent 的对比

### 7.1 代码行数对比

| Agent | 代码行数 | 工具数量 | 复杂度 |
|-----|---------|---------|--------|
| Account Agent | 55 行 | 3 个 | ⭐ |
| Transaction Agent | 64 行 | 4 个 | ⭐⭐ |
| **Payment Agent** | **128 行** | **9 个** | **⭐⭐⭐⭐⭐** |

**Payment Agent 是 Account Agent 的 2.3 倍！**

---

### 7.2 系统提示词对比

**Account Agent**（23 行）：
```java
"""
you are a personal financial advisor who help the user to retrieve 
information about their bank accounts.
Use html list or table to display the account information.
Always use the below logged user details to retrieve account info:
'{{loggedUserName}}'
"""
```

**Payment Agent**（120 行）：
```java
"""
you are a personal financial advisor who help the user with their 
recurrent bill payments. The user may want to pay the bill uploading 
a photo of the bill, or it may start the payment checking transactions 
history for a specific payee.

For the bill payment you need to know the: 
- bill id or invoice number
- payee name
- the total amount

If you don't have enough information to pay the bill ask the user to 
provide the missing information.

If the user submit a photo, always ask the user to confirm the 
extracted data from the photo.

Always check if the bill has been paid already based on payment 
history before asking to execute the bill payment.

... (还有 100 行)
"""
```

**对比**：
- Account Agent：简单（查询账户信息）
- Payment Agent：复杂（多步骤支付流程）

---

### 7.3 工具数量对比

**Account Agent**：
```
1. getAccount(username)
2. getPaymentMethods()
3. getBeneficiaries()
```

**Payment Agent**：
```
MCP 工具（来自 3 个 Server）:
1. getAccount(username)                    [Account Service]
2. getPaymentMethods()                     [Account Service]
3. getBeneficiaries()                      [Account Service]
4. getCreditBalance()                      [Account Service]
5. searchTransactions(...)                 [Transaction Service]
6. getTransactionsByRecipient(...)         [Transaction Service]
7. checkDuplicatePayment(...)              [Transaction Service]
8. submitPayment(...)                      [Payment Service]
9. notifyTransaction(...)                  [Payment Service]

自定义工具:
10. scanInvoice(filePath)                  [Azure Document Intelligence]
```

**总共 10 个工具！** 这就是为什么 Payment Agent 最复杂。

---

## 8. 学习检查点

### 基础理解

#### ✅ 问题 1：Payment Agent 的核心能力是什么？

**答案**：
1. OCR 发票识别（Azure Document Intelligence）
2. 多步骤支付流程（查询 → 验证 → 确认 → 支付）
3. 多工具协作（10 个工具）
4. 智能验证（防止重复支付、验证余额）
5. 错误处理（优雅处理失败情况）

---

#### ✅ 问题 2：为什么 Payment Agent 需要连接 3 个 MCP Server？

**答案**：

**Account Service（账户服务）**：
- getAccount - 获取账户信息
- getPaymentMethods - 获取支付方法
- getBeneficiaries - 获取受益人列表
- getCreditBalance - 获取信用额度

**Transaction Service（交易服务）**：
- searchTransactions - 查询交易历史
- getTransactionsByRecipient - 按收款人查询
- checkDuplicatePayment - 检查重复支付

**Payment Service（支付服务）**：
- submitPayment - 提交支付
- notifyTransaction - 通知交易

**总共 9 个 MCP 工具 + 1 个自定义工具 = 10 个工具**

---

#### ✅ 问题 3：`extendToolMap()` 的作用是什么？

**答案**：

**作用**：添加自定义工具（非 MCP 工具）到 Agent

**代码**：
```java
protected void extendToolMap(DocumentIntelligenceInvoiceScanHelper helper) {
    // 1. 获取方法
    Method scanInvoiceMethod = InvoiceScanTool.class.getMethod("scanInvoice", String.class);
    
    // 2. 创建工具实例
    InvoiceScanTool invoiceScanTool = new InvoiceScanTool(helper);
    
    // 3. 添加工具规格
    this.toolSpecifications.addAll(
        ToolSpecifications.toolSpecificationsFrom(InvoiceScanTool.class)
    );
    
    // 4. 添加工具执行器
    this.extendedExecutorMap.put("scanInvoice", 
        new DefaultToolExecutor(invoiceScanTool, scanInvoiceMethod)
    );
}
```

**为什么需要自定义工具？**
- `scanInvoice` 不是 REST API
- 是直接调用 Azure Document Intelligence SDK
- 无法通过 MCP 协议调用

---

### 架构理解

#### ✅ 问题 4：为什么系统提示词要求"确认 OCR 提取的数据"？

**答案**：

**原因 1：OCR 可能出错**
```
发票图片: 金额 "¥150.00"
OCR 识别: 金额 "¥1500.00"  ← 多了一个 0！

如果直接支付 → 用户损失 ¥1350 ❌
要求确认 → 用户发现错误 ✅
```

**原因 2：用户责任**
```
涉及金钱的操作，必须让用户确认
这是最佳实践，也是法律要求
```

**实现**：
```
Agent: "我从发票中提取了以下信息:
       金额: ¥1500.00
       请确认是否正确？"

用户: "不对，应该是 ¥150.00"

→ 用户修正，避免错误 ✅
```

---

#### ✅ 问题 5：Payment Agent 如何处理图片上传？

**答案**：

**完整流程**：

```
1. 前端上传图片到 Azure Blob Storage
   POST /api/upload
   → 返回 blobName: "invoice_001.jpg"

2. 前端发送聊天请求，包含附件
   POST /api/chat
   {
     "messages": [
       {
         "role": "user",
         "content": "我想支付这张账单",
         "attachments": ["invoice_001.jpg"]
       }
     ]
   }

3. Payment Agent 看到附件
   LLM: "用户提到了支付，并上传了图片"
   LLM: "需要 OCR 识别发票"
   LLM: 决定调用 scanInvoice("invoice_001.jpg")

4. InvoiceScanTool 执行
   - 从 Blob Storage 下载图片
   - 调用 Azure Document Intelligence
   - 返回识别结果: {VendorName, Amount, ...}

5. Agent 向用户确认
   "我从发票中提取了以下信息:
    供应商: 电力公司
    金额: ¥150.00
    请确认是否正确？"

6. 用户确认后继续支付流程
```

---

#### ✅ 问题 6：Payment Agent 如何防止重复支付？

**答案**：

**系统提示词要求**：
```java
"Always check if the bill has been paid already based on payment 
history before asking to execute the bill payment."
```

**实现**：
```
1. Agent 提取发票信息
   → InvoiceId: "123456"
   → VendorName: "电力公司"

2. Agent 查询交易历史
   searchTransactions(
       payee: "电力公司",
       invoiceId: "123456"
   )

3. 如果找到匹配的记录
   Agent: "这张账单已经在 2024-01-15 支付过了
          交易号: TXN123456
          您确定要再次支付吗？"

4. 如果未找到
   Agent: 继续支付流程
```

**好处**：
- 防止重复支付
- 保护用户资金
- 提高用户体验

---

## 🎓 总结

### 你已经掌握了

1. **Payment Agent 的复杂性**：10 个工具、3 个 MCP Server、OCR 集成
2. **完整业务流程**：11 个步骤的支付流程
3. **OCR 集成**：Azure Document Intelligence 的使用
4. **多工具协作**：智能决策调用顺序
5. **最佳实践**：确认机制、防止重复、错误处理

### 核心要点

| 要点 | 说明 |
|-----|------|
| **OCR 集成** | Azure Document Intelligence + 自定义工具 |
| **多 Server** | Account + Transaction + Payment |
| **智能决策** | LLM 自主决定调用顺序 |
| **确认机制** | 涉及金钱，必须确认 |
| **防止重复** | 查询历史，避免重复支付 |
| **错误处理** | 优雅处理失败情况 |

---

## 🚀 下一步

恭喜你完成了 Payment Agent 的学习！现在你已经理解了项目中最复杂的 Agent。

**接下来**：
1. **实战：修改 Account Agent** - 动手添加新功能
2. **总结回顾** - 回顾整个学习过程

**准备好了吗？告诉我你想做什么！** 🎓
