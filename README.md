# 本地知识库系统（Local Knowledge Base）

> 基于 RAG（检索增强生成）架构的智能知识库系统，支持多格式文档导入、混合检索、多 LLM Provider 配置切换

---

## 目录

- [项目概述](#项目概述)
- [技术架构](#技术架构)
- [功能特性](#功能特性)
- [系统需求](#系统需求)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [核心模块详解](#核心模块详解)
- [API 接口文档](#api-接口文档)
- [配置说明](#配置说明)
- [前端界面](#前端界面)
- [数据库设计](#数据库设计)
- [测试](#测试)
- [部署指南](#部署指南)
- [后续规划](#后续规划)

---

## 项目概述

本地知识库系统是一个企业级的智能问答平台，核心流程为：

1. **文档导入**：上传 TXT / MD / PDF / DOCX / JSON 等格式文件
2. **智能分块**：解析文档内容并按策略分块
3. **向量化存储**：通过 Embedding 模型生成向量并存储到 MongoDB
4. **混合检索**：关键词检索 + 语义相似度检索的加权融合
5. **智能回答**：命中知识库则基于上下文生成回答，未命中则 Fallback 到 LLM 直接回答
6. **流式输出**：支持 SSE 流式响应，实时返回生成内容

### 核心亮点

- 🤖 **多大模型支持**：OpenAI / 智谱GLM / DeepSeek / Ollama，配置文件一键切换
- 🔍 **混合检索**：关键词 + 语义双路检索，加权融合提升准确率
- 📚 **多格式文档**：TXT、Markdown、PDF、Word、JSON 问答对
- ⚡ **异步导入**：文件上传后异步处理，前端轮询进度
- 💬 **SSE 流式**：对话支持 Server-Sent Events 实时流式输出
- 🎯 **Fallback 机制**：知识库置信度低于阈值时自动转交 LLM

---

## 技术架构

```
┌───────────────────────────────────────────────────────┐
│                     前端 (Vue3 + TDesign)               │
│              index.html (CDN 单页, 无需 npm 构建)        │
└───────────────────────┬───────────────────────────────┘
                        │ HTTP / SSE
┌───────────────────────▼───────────────────────────────┐
│               Spring Boot 3.3.5 (Java 17)              │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐ │
│  │Controller│ │  Service  │ │  Parser   │ │  Chunker  │ │
│  │  Layer   │ │   Layer   │ │  Strategy │ │  Strategy │ │
│  └────┬─────┘ └────┬─────┘ └──────────┘ └───────────┘ │
│       │            │                                    │
│  ┌────▼────────────▼──────────────────────────────┐    │
│  │          Spring AI 1.0.0-M4                     │    │
│  │  ChatModel (OpenAI/ZhiPuAI/Ollama)              │    │
│  │  EmbeddingModel (OpenAI/ZhiPuAI/Ollama)         │    │
│  │  LlmProviderConfig (@Primary 动态选择)           │    │
│  └─────────────────────────────────────────────────┘    │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────────┐    │
│  │ MyBatis+ │ │ MongoRepo │ │ MongoVectorStore     │    │
│  │  MySQL   │ │  MongoDB  │ │ (余弦相似度计算)      │    │
│  └──────────┘ └──────────┘ └──────────────────────┘    │
│  ┌──────────┐                                         │
│  │  Redis   │  对话历史缓存                             │
│  └──────────┘                                         │
└───────────────────────────────────────────────────────┘
```

### 技术栈总览

| 分类 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 框架 | Spring Boot | 3.3.5 | 应用框架 |
| AI | Spring AI | 1.0.0-M4 | LLM / Embedding 抽象层 |
| ORM | MyBatis-Plus | 3.5.7 | MySQL 数据访问 |
| 关系库 | MySQL | 8.x | 知识库元数据、任务、配置 |
| 文档库 | MongoDB | 7.x | 向量存储、对话消息 |
| 缓存 | Redis | 7.x | 对话历史缓存 |
| 工具 | Hutool | 5.8.27 | JSON 解析等工具 |
| PDF | Apache PDFBox | 3.0.3 | PDF 文档解析 |
| Word | Apache POI | 5.3.0 | Word 文档解析 |
| 前端 | Vue 3 + TDesign | CDN | 管理界面 |
| 构建 | Maven | 3.6+ | 项目构建 |

---

## 功能特性

### 1. 知识库管理

- 创建 / 删除知识库（支持逻辑删除）
- 查看知识库详情及统计（文件数、分块数）
- 知识库内条目管理

### 2. 文档导入

- **支持格式**：TXT、Markdown、PDF、Word（.docx）、JSON 问答对
- **自动识别**：根据文件扩展名自动匹配解析器
- **异步处理**：上传后立即返回任务 ID，后台异步执行
- **进度查询**：前端轮询获取实时进度（解析 → 分块 → 向量化 → 完成）
- **导入记录**：每次导入均有详细记录

### 3. 智能检索

- **关键词检索**：基于 TF 匹配度的文本搜索
- **语义检索**：基于 Embedding 向量的余弦相似度搜索
- **混合融合**：两种检索结果加权融合（默认关键词 30% + 语义 70%）
- **Fallback 判断**：最高置信度低于阈值（默认 0.6）时标记需 Fallback

### 4. 智能对话

- **知识库回答**：检索命中时，基于上下文 + LLM 生成回答
- **LLM Fallback**：知识库未命中时，转交 LLM 直接回答并标注来源
- **强制 LLM**：可跳过知识库检索直接使用 LLM
- **SSE 流式**：支持 Server-Sent Events 实时流式输出
- **会话管理**：自动维护 session，支持历史查询与删除
- **对话存储**：MongoDB 持久化 + Redis 缓存双写

### 5. 多 LLM Provider

| Provider | ChatModel Bean | EmbeddingModel Bean | 备注 |
|----------|---------------|---------------------|------|
| OpenAI | `openAiChatModel` | `openAiEmbeddingModel` | 原生支持 |
| DeepSeek | `openAiChatModel` | `openAiEmbeddingModel` | 兼容 OpenAI API |
| 智谱 GLM | `zhiPuAiChatModel` | `zhiPuAiEmbeddingModel` | 原生支持 |
| Ollama | `ollamaChatModel` | `ollamaEmbeddingModel` | 本地部署 |

> 切换 Provider 只需修改 `application.yml` 中 `knowledge.llm.provider` 和 `knowledge.embedding.provider`，由 `LlmProviderConfig` 通过 `@Primary` 动态选择，**无需改动任何代码**。

### 6. 系统配置

- 运行时动态修改：LLM Provider、Embedding Provider、Fallback 阈值、分块参数、检索参数
- 配置双写：更新 yml 属性 + 持久化到 MySQL

---

## 系统需求

| 组件 | 最低版本 | 说明 |
|------|---------|------|
| JDK | 17+ | 必须 |
| Maven | 3.6+ | 构建工具 |
| MySQL | 8.0+ | 元数据存储 |
| MongoDB | 7.0+ | 向量与对话存储 |
| Redis | 7.0+ | 缓存 |

---

## 快速开始

### 1. 初始化数据库

```bash
# 执行 MySQL 初始化脚本
mysql -u root -p < sql/init.sql
```

### 2. 修改配置

编辑 `src/main/resources/application-dev.yml`，配置数据库连接和 LLM API Key：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/knowledge_base?useUnicode=true&characterEncoding=utf8&useSSL=true&serverTimezone=GMT%2B8
    username: your_username
    password: your_password
  data:
    mongodb:
      uri: mongodb://localhost:27017/knowledge_base
    redis:
      host: localhost
      port: 6379
```

### 3. 选择 LLM Provider

编辑 `src/main/resources/application.yml`：

```yaml
knowledge:
  llm:
    provider: zhipuai    # openai / zhipuai / ollama / deepseek
  embedding:
    provider: zhipuai    # openai / zhipuai / ollama
```

### 4. 构建与运行

```bash
# 构建
mvn clean package -DskipTests

# 运行
java -jar target/local-knowledge-1.0.0.jar

# 或开发模式
mvn spring-boot:run
```

### 5. 访问系统

浏览器打开 `http://localhost:8080`，即可使用 Web 管理界面。

---

## 项目结构

```
local-knowledge/
├── pom.xml                                      # Maven 构建配置
├── sql/
│   └── init.sql                                 # MySQL 初始化脚本
├── docs/                                        # 文档目录
├── src/
│   ├── main/
│   │   ├── java/com/knowledge/
│   │   │   ├── KnowledgeBaseApplication.java    # 启动类
│   │   │   ├── chunker/                         # 文本分块
│   │   │   │   ├── TextChunker.java             #   分块器接口
│   │   │   │   ├── FixedSizeChunker.java        #   固定大小分块
│   │   │   │   ├── QaPairChunker.java           #   问答对分块
│   │   │   │   └── ChunkerFactory.java          #   分块器工厂
│   │   │   ├── common/                          # 通用工具
│   │   │   │   ├── Result.java                  #   统一响应包装
│   │   │   │   ├── Constants.java               #   系统常量
│   │   │   │   └── BusinessException.java       #   业务异常
│   │   │   ├── config/                          # 配置类
│   │   │   │   ├── LlmProviderConfig.java       #   ★ LLM Provider 动态选择
│   │   │   │   ├── KnowledgeProperties.java     #   知识库配置属性
│   │   │   │   ├── AsyncConfig.java             #   异步线程池
│   │   │   │   ├── CorsConfig.java              #   跨域配置
│   │   │   │   ├── GlobalExceptionHandler.java  #   全局异常处理
│   │   │   │   ├── MongoConfig.java             #   MongoDB 配置
│   │   │   │   ├── RedisConfig.java             #   Redis 配置
│   │   │   │   ├── VectorStoreConfig.java       #   VectorStore 注册
│   │   │   │   └── WebConfig.java               #   静态资源映射
│   │   │   ├── controller/                      # REST 控制器
│   │   │   │   ├── ChatController.java          #   对话接口
│   │   │   │   ├── KnowledgeBaseController.java #   知识库管理接口
│   │   │   │   ├── KnowledgeImportController.java#  知识导入接口
│   │   │   │   ├── RetrievalController.java     #   检索接口
│   │   │   │   └── SystemConfigController.java  #   系统配置接口
│   │   │   ├── enums/                           # 枚举类
│   │   │   │   ├── FileFormat.java              #   文件格式
│   │   │   │   ├── ImportStatus.java            #   导入状态
│   │   │   │   ├── LlmProvider.java             #   LLM 提供商
│   │   │   │   ├── EmbeddingProvider.java       #   Embedding 提供商
│   │   │   │   └── MessageSource.java           #   消息来源
│   │   │   ├── mapper/                          # MyBatis-Plus Mapper
│   │   │   │   ├── KnowledgeBaseMapper.java
│   │   │   │   ├── KnowledgeItemMapper.java
│   │   │   │   ├── ImportRecordMapper.java
│   │   │   │   ├── ImportTaskMapper.java
│   │   │   │   └── SystemConfigMapper.java
│   │   │   ├── model/
│   │   │   │   ├── document/                    # MongoDB 文档
│   │   │   │   │   ├── KnowledgeChunk.java      #   知识分块
│   │   │   │   │   └── ConversationMessage.java #   对话消息
│   │   │   │   ├── dto/                         # 请求 DTO
│   │   │   │   │   ├── ChatRequestDTO.java
│   │   │   │   │   ├── KnowledgeBaseCreateDTO.java
│   │   │   │   │   ├── KnowledgeImportDTO.java
│   │   │   │   │   └── ConfigUpdateDTO.java
│   │   │   │   ├── entity/                      # MySQL 实体
│   │   │   │   │   ├── KnowledgeBase.java
│   │   │   │   │   ├── KnowledgeItem.java
│   │   │   │   │   ├── ImportRecord.java
│   │   │   │   │   ├── ImportTask.java
│   │   │   │   │   └── SystemConfig.java
│   │   │   │   └── vo/                          # 响应 VO
│   │   │   │       ├── ChatResponseVO.java
│   │   │   │       ├── KnowledgeBaseVO.java
│   │   │   │       ├── KnowledgeItemVO.java
│   │   │   │       ├── SearchResultVO.java
│   │   │   │       ├── ImportRecordVO.java
│   │   │   │       └── ImportTaskProgressVO.java
│   │   │   ├── parser/                          # 文档解析
│   │   │   │   ├── DocumentParser.java          #   解析器接口
│   │   │   │   ├── ParserFactory.java           #   解析器工厂
│   │   │   │   └── impl/
│   │   │   │       ├── TxtParser.java           #   TXT 解析
│   │   │   │       ├── MarkdownParser.java      #   Markdown 解析
│   │   │   │       ├── PdfParser.java           #   PDF 解析
│   │   │   │       ├── WordParser.java          #   Word 解析
│   │   │   │       └── JsonQaParser.java        #   JSON QA 解析
│   │   │   ├── repository/                      # MongoDB Repository
│   │   │   │   ├── KnowledgeChunkRepository.java
│   │   │   │   └── ConversationRepository.java
│   │   │   ├── service/                         # 业务服务
│   │   │   │   ├── ChatService.java             #   ★ 对话服务（核心）
│   │   │   │   ├── KnowledgeService.java        #   ★ 知识库服务（核心）
│   │   │   │   ├── RetrievalService.java        #   ★ 混合检索服务
│   │   │   │   ├── EmbeddingService.java        #   Embedding 服务
│   │   │   │   ├── ImportTaskService.java       #   导入任务服务
│   │   │   │   └── SystemConfigService.java     #   系统配置服务
│   │   │   └── vectorstore/
│   │   │       └── MongoVectorStore.java        #   ★ 自定义向量存储
│   │   └── resources/
│   │       ├── application.yml                  # 主配置
│   │       ├── application-dev.yml              # 开发环境配置
│   │       ├── application-prod.yml             # 生产环境配置
│   │       └── static/
│   │           └── index.html                   # 前端单页面
│   └── test/java/com/knowledge/                 # 单元测试
│       ├── config/LlmProviderConfigTest.java
│       ├── controller/ControllerTest.java
│       └── service/
│           ├── ChatServiceTest.java
│           ├── EmbeddingServiceTest.java
│           ├── ImportTaskServiceTest.java
│           ├── KnowledgeServiceTest.java
│           └── RetrievalServiceTest.java
```

---

## 核心模块详解

### 1. LlmProviderConfig — 多 Provider 动态选择

这是系统的核心设计，解决 Spring AI 多 Provider 自动配置产生多个 Bean 的冲突问题。

**工作原理**：

```java
@Configuration
public class LlmProviderConfig {

    // Provider 名称 → Bean 前缀的映射
    private static String resolveBeanPrefix(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai"  -> "openAi";
            case "deepseek" -> "openAi";   // DeepSeek 兼容 OpenAI API
            case "zhipuai", "zhipu", "glm" -> "zhiPuAi";
            case "ollama"  -> "ollama";
            default -> throw new IllegalArgumentException(...);
        };
    }

    @Bean @Primary
    public ChatModel primaryChatModel(ApplicationContext ctx, @Value("${knowledge.llm.provider}") String provider) {
        String beanName = resolveBeanPrefix(provider) + "ChatModel";
        return ctx.getBean(beanName, ChatModel.class);
    }

    @Bean @Primary
    public EmbeddingModel primaryEmbeddingModel(ApplicationContext ctx, @Value("${knowledge.embedding.provider}") String provider) {
        String beanName = resolveBeanPrefix(provider) + "EmbeddingModel";
        return ctx.getBean(beanName, EmbeddingModel.class);
    }
}
```

**切换方式**：只需修改 `application.yml`：

```yaml
knowledge:
  llm:
    provider: deepseek    # 改这一行即可切换 LLM
  embedding:
    provider: zhipuai     # 改这一行即可切换 Embedding
```

### 2. ChatService — 对话核心流程

```
用户消息 → 知识库检索 → 置信度判断
                         ├── 命中（≥阈值）→ 构建上下文 Prompt → LLM 生成回答（来源：KNOWLEDGE）
                         └── 未命中（<阈值）→ Fallback Prompt → LLM 直接回答（来源：AI）
```

关键设计：
- **双 Prompt 策略**：知识库命中使用带参考资料的 System Prompt，Fallback 使用通用 Prompt
- **对话历史管理**：MongoDB 持久化 + Redis 缓存，最多保留 20 轮历史
- **流式支持**：`chatStream()` 方法返回 `Flux<String>`，通过 `chatModel.stream()` 实时输出

### 3. RetrievalService — 混合检索

```
查询文本 ──┬── 关键词检索（TF 匹配度）── keywordScore
           └── 语义检索（余弦相似度）── semanticScore
                         │
                         ▼
              finalScore = 0.3 × keywordScore + 0.7 × semanticScore
                         │
                         ▼
                   按 finalScore 降序，取 Top-K
```

### 4. MongoVectorStore — 向量存储

- 存储：分块文本 + Embedding 向量 + 元数据（baseId、itemId、chunkIndex、fileName）
- 检索：`semanticSearchWithEmbedding()` 精确余弦相似度计算
- 扩展性：实现 Spring AI `VectorStore` 接口，后续可无缝替换为 Milvus / Chroma

### 5. 文档解析 — 策略模式

| 格式 | 解析器 | 分段策略 |
|------|--------|---------|
| TXT | `TxtParser` | 按空行分段 |
| Markdown | `MarkdownParser` | 按标题层级分节 |
| PDF | `PdfParser` | PDFBox 按页提取 |
| Word | `WordParser` | Apache POI 按段落提取 |
| JSON | `JsonQaParser` | 解析 `[{"question":"...","answer":"..."}]` 格式 |

### 6. 文本分块 — 策略模式

| 类型 | 分块器 | 策略 |
|------|--------|------|
| 通用 | `FixedSizeChunker` | 固定字符数 + 重叠窗口 + 句子边界切分 |
| 问答对 | `QaPairChunker` | 每个 Q&A 对独立成块，保持语义完整 |

---

## API 接口文档

### 知识库管理

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/api/knowledge-base` | 创建知识库 | `KnowledgeBaseCreateDTO` | `KnowledgeBaseVO` |
| GET | `/api/knowledge-base` | 知识库列表 | - | `List<KnowledgeBaseVO>` |
| GET | `/api/knowledge-base/{id}` | 知识库详情 | - | `KnowledgeBaseVO` |
| DELETE | `/api/knowledge-base/{id}` | 删除知识库 | - | `void` |
| GET | `/api/knowledge-base/{id}/items` | 条目列表 | - | `List<KnowledgeItemVO>` |
| DELETE | `/api/knowledge-base/items/{itemId}` | 删除条目 | - | `void` |
| POST | `/api/knowledge-base/{id}/rebuild-vectors` | 重建向量 | - | `void` |

### 知识导入

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/api/knowledge-import/upload` | 上传文件 | `multipart/form-data: file, baseId, format(可选)` | `taskId` |
| GET | `/api/knowledge-import/progress/{taskId}` | 查询进度 | - | `ImportTaskProgressVO` |

### 智能对话

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/api/chat` | 对话（非流式） | `ChatRequestDTO` | `ChatResponseVO` |
| POST | `/api/chat/stream` | 对话（SSE 流式） | `ChatRequestDTO` | `text/event-stream` |
| GET | `/api/chat/history/{sessionId}` | 会话历史 | - | `List<ConversationMessage>` |
| DELETE | `/api/chat/session/{sessionId}` | 删除会话 | - | `void` |

### 知识检索

| 方法 | 路径 | 说明 | 参数 | 响应 |
|------|------|------|------|------|
| GET | `/api/retrieval/search` | 混合检索 | `query`, `baseId(可选)`, `topK(默认5)` | `List<SearchResultVO>` |

### 系统配置

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| GET | `/api/config` | 获取所有配置 | - | `Map<String, String>` |
| GET | `/api/config/{key}` | 获取单个配置 | - | `String` |
| PUT | `/api/config` | 更新配置 | `ConfigUpdateDTO` | `void` |

### DTO 定义

**ChatRequestDTO**
```json
{
  "message": "你好",           // 必填，用户消息
  "sessionId": "uuid",         // 可选，不传则新建会话
  "baseId": "1",              // 可选，指定知识库
  "forceLlm": false           // 可选，是否强制使用 LLM
}
```

**ChatResponseVO**
```json
{
  "content": "你好！",
  "source": "KNOWLEDGE",      // KNOWLEDGE / AI
  "sessionId": "uuid",
  "references": [...],        // 引用的知识条目
  "knowledgeHit": true        // 是否命中知识库
}
```

**ConfigUpdateDTO**
```json
{
  "llmProvider": "zhipuai",       // 可选
  "embeddingProvider": "zhipuai", // 可选
  "fallbackThreshold": 0.6,       // 可选，0-1
  "fallbackEnabled": true,        // 可选
  "chunkSize": 500,               // 可选
  "chunkOverlap": 50,             // 可选
  "topK": 5,                      // 可选
  "keywordWeight": 0.3            // 可选，语义权重 = 1 - 此值
}
```

---

## 配置说明

### application.yml（主配置）

```yaml
server:
  port: 8080

spring:
  application:
    name: local-knowledge
  profiles:
    active: dev
  servlet:
    multipart:
      max-file-size: 100MB      # 单文件最大 100MB
      max-request-size: 200MB   # 请求最大 200MB
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8
    default-property-inclusion: non_null

# MyBatis Plus
mybatis-plus:
  mapper-locations: classpath:mapper/**/*.xml
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 生产环境关闭

# 知识库系统配置
knowledge:
  vector-store:
    type: mongodb               # 向量存储类型
  retrieval:
    fallback-threshold: 0.6     # Fallback 阈值
    top-k: 5                    # 返回 Top-K
    keyword-weight: 0.3         # 关键词权重
    semantic-weight: 0.7        # 语义权重
  chunk:
    size: 500                   # 分块大小（字符）
    overlap: 50                 # 分块重叠（字符）
  llm:
    provider: zhipuai           # 当前 LLM Provider
  embedding:
    provider: zhipuai           # 当前 Embedding Provider
```

### application-dev.yml（开发环境）

包含 MySQL、MongoDB、Redis 连接配置及三个 LLM Provider 的详细配置：

- **OpenAI**：通过环境变量 `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`CHAT_MODEL`、`EMBEDDING_MODEL` 配置
- **DeepSeek**：复用 OpenAI 配置，修改 `base-url` 为 `https://api.deepseek.com`
- **智谱 GLM**：直接配置 `api-key`、`model: glm-4`、`embedding: embedding-3`
- **Ollama**：配置 `base-url`（默认 `http://localhost:11434`）、`model: llama3`

### application-prod.yml（生产环境）

所有敏感配置通过环境变量注入：

```yaml
spring:
  datasource:
    username: ${MYSQL_USERNAME}
    password: ${MYSQL_PASSWORD}
  data:
    mongodb:
      uri: ${MONGODB_URI}
    redis:
      password: ${REDIS_PASSWORD}
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
    zhipuai:
      api-key: ${ZHIPU_API_KEY}
```

---

## 前端界面

前端采用 **Vue 3 + TDesign CDN** 单页面方案，放在 `src/main/resources/static/index.html`，无需 npm 构建。

### 页面功能

| 页面 | 功能 |
|------|------|
| 📁 知识库管理 | 创建/删除知识库、查看条目、导入文件、重建向量 |
| 💬 智能对话 | 选择知识库对话、查看来源标签、会话管理 |
| ⚙️ 系统配置 | 修改 Provider、Fallback 阈值、分块参数、检索参数 |

### 特色交互

- 知识库卡片式布局，hover 高亮
- 文件上传 + 实时进度条
- 对话界面区分知识库/AI 来源
- 配置滑块实时预览

---

## 数据库设计

### MySQL 表结构（5 张表）

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `kb_knowledge_base` | 知识库元数据 | id, name, description, file_count, chunk_count, deleted |
| `kb_knowledge_item` | 知识条目元数据 | id, base_id, file_name, file_format, chunk_count, status |
| `kb_import_record` | 导入记录 | id, item_id, base_id, file_name, status, duration_ms |
| `kb_import_task` | 异步导入任务 | id, task_id(UUID), status, progress, current_stage |
| `kb_system_config` | 系统配置 | config_key, config_value, config_group |

### MongoDB 集合（2 个）

| 集合 | 说明 | 关键字段 |
|------|------|---------|
| `knowledge_chunk` | 知识分块 + 向量 | id, baseId, itemId, content, embedding(float[]), chunkIndex, metadata |
| `conversation_message` | 对话消息 | id, sessionId, role, content, source, matchedChunkIds |

### Redis Key

| Key 模式 | TTL | 用途 |
|----------|-----|------|
| `chat:history:{sessionId}` | 24h | 对话历史缓存 |
| `retrieval:cache:{hash}` | 30min | 检索结果缓存 |

---

## 测试

### 测试概览

| 测试类 | 测试数 | 覆盖范围 |
|--------|--------|---------|
| `LlmProviderConfigTest` | 11 | Provider 映射、@Primary Bean 选择、异常 Provider |
| `ControllerTest` | 7 | 知识库 CRUD、对话、检索 HTTP 请求/响应 |
| `ChatServiceTest` | 7 | 知识库命中/Fallback/强制 LLM/会话管理 |
| `EmbeddingServiceTest` | 10 | 单文本/批量/空输入/异常/部分失败/维度 |
| `ImportTaskServiceTest` | 11 | 任务创建/状态流转/进度/未找到 |
| `KnowledgeServiceTest` | 10 | 知识库 CRUD/条目管理/重复检查/级联删除 |
| `RetrievalServiceTest` | 8 | 混合检索/关键词/语义/Fallback 判断 |
| **合计** | **74** | — |

### 运行测试

```bash
mvn test
```

### 测试技术

- JUnit 5 + Mockito
- MockMvc（Controller 层）
- 不依赖 Spring 上下文，纯单元测试

---

## 部署指南

### 开发环境

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 生产环境

```bash
# 构建
mvn clean package -DskipTests

# 运行
java -jar target/local-knowledge-1.0.0.jar --spring.profiles.active=prod
```

### 环境变量（生产环境必须配置）

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `MYSQL_USERNAME` | MySQL 用户名 | root |
| `MYSQL_PASSWORD` | MySQL 密码 | your_password |
| `MONGODB_URI` | MongoDB 连接串 | mongodb://user:pass@host:27017/db |
| `REDIS_HOST` | Redis 地址 | localhost |
| `REDIS_PASSWORD` | Redis 密码 | your_password |
| `OPENAI_API_KEY` | OpenAI API Key | sk-xxx |
| `OPENAI_BASE_URL` | OpenAI Base URL | https://api.openai.com |
| `ZHIPU_API_KEY` | 智谱 API Key | xxx |
| `OLLAMA_BASE_URL` | Ollama 地址 | http://localhost:11434 |

---

## 后续规划

### 短期（Phase 2）

- [ ] 替换 MongoVectorStore 为 Milvus / Chroma 向量数据库
- [ ] 中文分词优化（接入 jieba / HanLP）
- [ ] 检索结果缓存（Redis）
- [ ] 对话历史分页查询

### 中期（Phase 3）

- [ ] 用户认证与权限管理
- [ ] 多租户知识库隔离
- [ ] 知识库分享与协作
- [ ] 对话评价与反馈机制

### 长期（Phase 4）

- [ ] Agent 工作流（多轮工具调用）
- [ ] 图谱知识库（Graph RAG）
- [ ] 多模态知识导入（图片、表格理解）
- [ ] Kubernetes 部署方案

---

## 许可证

本项目仅供学习和研究使用。
