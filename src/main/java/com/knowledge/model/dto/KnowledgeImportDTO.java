package com.knowledge.model.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 知识导入请求 DTO
 */
@Data
public class KnowledgeImportDTO {

    /**
     * 目标知识库ID
     */
    @NotBlank(message = "知识库ID不能为空")
    private String baseId;

    /**
     * 文件格式（不传则自动识别）
     */
    private String format;
}
