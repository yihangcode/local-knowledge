package com.knowledge.enums;

import lombok.Getter;

/**
 * 导入状态枚举
 */
@Getter
public enum ImportStatus {

    PENDING("PENDING", "待处理"),
    PROCESSING("PROCESSING", "处理中"),
    COMPLETED("COMPLETED", "已完成"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String description;

    ImportStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
