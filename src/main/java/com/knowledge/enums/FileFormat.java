package com.knowledge.enums;

import lombok.Getter;

/**
 * 文件格式枚举
 */
@Getter
public enum FileFormat {

    TXT("txt", "纯文本"),
    MD("md", "Markdown"),
    PDF("pdf", "PDF文档"),
    DOCX("docx", "Word文档"),
    JSON("json", "JSON数据"),
    QA("qa", "问答对");

    private final String extension;
    private final String description;

    FileFormat(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }

    /**
     * 根据文件扩展名获取格式
     */
    public static FileFormat fromExtension(String ext) {
        if (ext == null) {
            return TXT;
        }
        String lower = ext.toLowerCase().replace(".", "");
        for (FileFormat format : values()) {
            if (format.extension.equals(lower)) {
                return format;
            }
        }
        return TXT;
    }
}
