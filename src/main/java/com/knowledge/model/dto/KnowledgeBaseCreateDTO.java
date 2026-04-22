package com.knowledge.model.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建知识库请求 DTO
 */
@Data
public class KnowledgeBaseCreateDTO {

    /**
     * 知识库名称
     */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "名称长度不超过100")
    private String name;

    /**
     * 知识库描述
     */
    @Size(max = 500, message = "描述长度不超过500")
    private String description;
}
