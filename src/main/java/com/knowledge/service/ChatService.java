package com.knowledge.service;

import com.knowledge.common.Constants;
import com.knowledge.config.KnowledgeProperties;
import com.knowledge.enums.MessageSource;
import com.knowledge.model.document.ConversationMessage;
import com.knowledge.model.dto.ChatRequestDTO;
import com.knowledge.model.vo.ChatResponseVO;
import com.knowledge.model.vo.SearchResultVO;
import com.knowledge.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 对话服务
 * <p>
 * 核心流程：
 * 1. 接收用户消息
 * 2. 知识库检索（关键词 + 语义混合搜索）
 * 3. 如果知识库命中（置信度 >= 阈值），基于检索结果生成回答
 * 4. 如果知识库未命中，fallback 到 LLM 直接回答
 * 5. 保存对话历史到 MongoDB 和 Redis
 * <p>
 * LLM Provider 通过 application.yml 中 knowledge.llm.provider 配置切换，
 * 由 LlmProviderConfig 根据 @Primary 动态选择，无需改动本类代码。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatModel chatModel;
    private final RetrievalService retrievalService;
    private final ConversationRepository conversationRepository;
    private final StringRedisTemplate redisTemplate;
    private final KnowledgeProperties knowledgeProperties;
    private final ApplicationContext applicationContext;

    /**
     * 运行时获取当前 Provider 对应的 ChatModel
     * <p>
     * 修复：直接注入的 chatModel 是启动时固定的 @Primary Bean，
     * 运行时切换 provider 后不会生效。改为每次调用时从 ApplicationContext
     * 按 knowledgeProperties.llm.provider 动态获取对应的 ChatModel Bean。
     * </p>
     */
    private ChatModel resolveChatModel() {
        String provider = knowledgeProperties.getLlm().getProvider();
        String beanPrefix = resolveBeanPrefix(provider);
        String beanName = beanPrefix + "ChatModel";
        try {
            return applicationContext.getBean(beanName, ChatModel.class);
        } catch (Exception e) {
            log.warn("Failed to resolve ChatModel for provider={}, falling back to primary. Error: {}",
                    provider, e.getMessage());
            return chatModel; // 回退到启动时的 @Primary Bean
        }
    }

    private static String resolveBeanPrefix(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> "openAi";
            case "deepseek" -> "openAi";
            case "zhipuai", "zhipu", "glm" -> "zhiPuAi";
            case "ollama" -> "ollama";
            default -> "openAi";
        };
    }

    /** 系统提示词模板 */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一个专业的知识库助手。请基于以下参考资料回答用户的问题。
            如果参考资料中没有相关信息，请明确告知用户，不要编造内容。
            
            参考资料：
            %s
            
            回答要求：
            1. 优先使用参考资料中的信息
            2. 引用信息时注明来源
            3. 如果资料不足，坦诚说明
            4. 回答要准确、简洁、有条理
            """;

    /** Fallback 系统提示词 */
    private static final String FALLBACK_SYSTEM_PROMPT = """
            你是一个智能助手。用户的问题超出了当前知识库的范围，请基于你的知识尽力回答。
            请在回答开头说明"以下回答未参考知识库资料"，然后给出你的回答。
            回答要准确、有帮助，如果不确定，请坦诚说明。
            """;

    /**
     * 对话（非流式）
     */
    public ChatResponseVO chat(ChatRequestDTO request) {
        String sessionId = resolveSessionId(request.getSessionId());
        String baseId = request.getBaseId();
        String userMessage = request.getMessage();

        // 保存用户消息
        saveMessage(sessionId, "user", userMessage, null, null, baseId);

        // 检索知识库
        RetrievalService.RetrievalResult retrievalResult;
        List<SearchResultVO> references;
        String source;
        String answer;

        if (Boolean.TRUE.equals(request.getForceLlm())) {
            // 强制使用 LLM
            retrievalResult = null;
            references = List.of();
            source = MessageSource.AI.name();
            answer = callLlmDirectly(sessionId, userMessage);
        } else {
            retrievalResult = retrievalService.retrieveWithFallbackCheck(userMessage, baseId);
            references = retrievalResult.results();

            if (retrievalResult.needFallback()) {
                // Fallback 到 LLM
                source = MessageSource.AI.name();
                answer = callLlmFallback(sessionId, userMessage);
                log.info("Used LLM fallback for session={}, score={}", sessionId,
                        String.format("%.3f", retrievalResult.maxScore()));
            } else {
                // 基于知识库回答
                source = MessageSource.KNOWLEDGE.name();
                String context = buildContextFromResults(references);
                answer = callLlmWithContext(sessionId, userMessage, context);
                log.info("Used knowledge base for session={}, score={}", sessionId,
                        String.format("%.3f", retrievalResult.maxScore()));
            }
        }

        // 保存助手回复
        String[] matchedChunkIds = references.stream()
                .map(SearchResultVO::getChunkId)
                .toArray(String[]::new);

        saveMessage(sessionId, "assistant", answer, source, matchedChunkIds, baseId);

        // 构建响应
        ChatResponseVO response = new ChatResponseVO();
        response.setContent(answer);
        response.setSource(source);
        response.setSessionId(sessionId);
        response.setReferences(references);
        // knowledgeHit: 仅当真正命中知识库时为 true
        // fallback 到 AI 的回答不应标记为知识库命中
        response.setKnowledgeHit(MessageSource.KNOWLEDGE.name().equals(source));

        return response;
    }

    /**
     * 对话（SSE 流式）
     */
    public Flux<String> chatStream(ChatRequestDTO request) {
        String sessionId = resolveSessionId(request.getSessionId());
        String baseId = request.getBaseId();
        String userMessage = request.getMessage();

        // 保存用户消息
        saveMessage(sessionId, "user", userMessage, null, null, baseId);

        // 检索知识库
        RetrievalService.RetrievalResult retrievalResult;
        List<SearchResultVO> references;
        String systemPrompt;

        if (Boolean.TRUE.equals(request.getForceLlm())) {
            retrievalResult = null;
            references = List.of();
            systemPrompt = FALLBACK_SYSTEM_PROMPT;
        } else {
            retrievalResult = retrievalService.retrieveWithFallbackCheck(userMessage, baseId);
            references = retrievalResult.results();

            if (retrievalResult.needFallback()) {
                systemPrompt = FALLBACK_SYSTEM_PROMPT;
            } else {
                String context = buildContextFromResults(references);
                systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, context);
            }
        }

        // 构建对话历史
        List<Message> messages = buildMessageHistory(sessionId);
        List<Message> allMessages = new ArrayList<>();
        allMessages.add(new SystemMessage(systemPrompt));
        allMessages.addAll(messages);
        allMessages.add(new UserMessage(userMessage));

        // 使用动态解析的 ChatModel 进行流式调用
        ChatModel currentModel = resolveChatModel();
        Prompt prompt = new Prompt(allMessages);
        StringBuilder fullResponse = new StringBuilder();

        String finalSource = (retrievalResult != null && !retrievalResult.needFallback())
                ? MessageSource.KNOWLEDGE.name() : MessageSource.AI.name();
        List<SearchResultVO> finalReferences = references;

        return currentModel.stream(prompt)
                .map(chatResponse -> {
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                        String content = chatResponse.getResult().getOutput().getContent();
                        if (content != null && !content.isEmpty()) {
                            fullResponse.append(content);
                            return content;
                        }
                    }
                    return "";
                })
                .filter(content -> content != null && !content.isEmpty())
                .doOnComplete(() -> {
                    // 保存完整的助手回复
                    String[] matchedChunkIds = finalReferences.stream()
                            .map(SearchResultVO::getChunkId)
                            .toArray(String[]::new);
                    saveMessage(sessionId, "assistant", fullResponse.toString(), finalSource, matchedChunkIds, baseId);
                });
    }

    /**
     * 获取会话历史
     */
    public List<ConversationMessage> getChatHistory(String sessionId) {
        return conversationRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * 删除会话
     */
    public void deleteSession(String sessionId) {
        conversationRepository.deleteBySessionId(sessionId);
        redisTemplate.delete(Constants.CHAT_HISTORY_PREFIX + sessionId);
    }

    // ====================== 内部方法 ======================

    /**
     * 解析或创建 session ID
     */
    private String resolveSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 基于知识库上下文调用 LLM
     */
    private String callLlmWithContext(String sessionId, String userMessage, String context) {
        String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, context);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(buildMessageHistory(sessionId));
        messages.add(new UserMessage(userMessage));

        Prompt prompt = new Prompt(messages);
        ChatResponse response = resolveChatModel().call(prompt);

        return response.getResult() != null ? response.getResult().getOutput().getContent() : "";
    }

    /**
     * Fallback 直接调用 LLM
     */
    private String callLlmFallback(String sessionId, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(FALLBACK_SYSTEM_PROMPT));
        messages.addAll(buildMessageHistory(sessionId));
        messages.add(new UserMessage(userMessage));

        Prompt prompt = new Prompt(messages);
        ChatResponse response = resolveChatModel().call(prompt);

        return response.getResult() != null ? response.getResult().getOutput().getContent() : "";
    }

    /**
     * 强制 LLM 调用（无知识库）
     */
    private String callLlmDirectly(String sessionId, String userMessage) {
        return callLlmFallback(sessionId, userMessage);
    }

    /**
     * 从检索结果构建上下文文本
     */
    private String buildContextFromResults(List<SearchResultVO> results) {
        if (results == null || results.isEmpty()) {
            return "（无相关参考资料）";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResultVO r = results.get(i);
            sb.append("【资料 ").append(i + 1).append("】");
            if (r.getSourceFileName() != null) {
                sb.append("（来源：").append(r.getSourceFileName()).append("）");
            }
            sb.append("\n").append(r.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 构建对话历史消息列表
     */
    private List<Message> buildMessageHistory(String sessionId) {
        List<ConversationMessage> history = conversationRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId);

        // 限制历史长度，避免 context 过长
        int maxHistory = 20;
        if (history.size() > maxHistory) {
            history = history.subList(history.size() - maxHistory, history.size());
        }

        return history.stream()
                .map(msg -> {
                    if ("user".equals(msg.getRole())) {
                        return (Message) new UserMessage(msg.getContent());
                    } else {
                        return (Message) new AssistantMessage(msg.getContent());
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 保存消息到 MongoDB 和 Redis
     */
    private void saveMessage(String sessionId, String role, String content,
                             String source, String[] matchedChunkIds, String baseId) {
        // MongoDB 持久化
        ConversationMessage message = new ConversationMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSource(source);
        message.setMatchedChunkIds(matchedChunkIds);
        message.setBaseId(baseId);
        message.setCreatedAt(LocalDateTime.now());
        conversationRepository.save(message);

        // Redis 缓存（用于快速读取近期对话）
        try {
            String key = Constants.CHAT_HISTORY_PREFIX + sessionId;
            String value = role + ":" + content;
            redisTemplate.opsForList().rightPush(key, value);
            redisTemplate.expire(key, Constants.CHAT_HISTORY_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache message to Redis: {}", e.getMessage());
        }
    }
}
