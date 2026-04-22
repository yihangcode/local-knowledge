package com.knowledge.controller;

import com.knowledge.common.Result;
import com.knowledge.model.document.ConversationMessage;
import com.knowledge.model.dto.ChatRequestDTO;
import com.knowledge.model.vo.ChatResponseVO;
import com.knowledge.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话 Controller
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 对话（非流式）
     */
    @PostMapping
    public Result<ChatResponseVO> chat(@RequestBody @Valid ChatRequestDTO request) {
        return Result.success(chatService.chat(request));
    }

    /**
     * 对话（SSE 流式）
     * <p>
     * 返回 text/event-stream 格式的流式响应
     * </p>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody @Valid ChatRequestDTO request) {
        return chatService.chatStream(request);
    }

    /**
     * 获取会话历史
     */
    @GetMapping("/history/{sessionId}")
    public Result<List<ConversationMessage>> getHistory(@PathVariable String sessionId) {
        return Result.success(chatService.getChatHistory(sessionId));
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        chatService.deleteSession(sessionId);
        return Result.success(null);
    }
}
