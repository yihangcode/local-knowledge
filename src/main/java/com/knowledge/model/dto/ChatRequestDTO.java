package com.knowledge.model.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 对话请求 DTO
 */
@Data
public class ChatRequestDTO {

    /**
     * 用户消息
     */
    @NotBlank(message = "消息不能为空")
    private String message;

    /**
     * 对话会话ID（不传则新建会话）
     */
    private String sessionId;

    /**
     * 指定知识库ID（不传则搜索全部知识库）
     */
    private String baseId;

    /**
     * 是否强制使用大模型（跳过知识库检索）
     */
    private Boolean forceLlm = false;
}
