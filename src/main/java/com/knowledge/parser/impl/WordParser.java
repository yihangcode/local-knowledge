package com.knowledge.parser.impl;

import com.knowledge.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Word 文档解析器
 * <p>
 * 使用 Apache POI 提取 .docx 文本，按段落分段
 * </p>
 */
@Slf4j
@Component
public class WordParser implements DocumentParser {

    @Override
    public String supportedFormat() {
        return "DOCX";
    }

    @Override
    public List<String> parse(InputStream inputStream, String fileName) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<String> paragraphs = new ArrayList<>();
            StringBuilder currentPara = new StringBuilder();

            for (XWPFParagraph para : document.getParagraphs()) {
                String text = para.getText();
                if (text == null || text.trim().isEmpty()) {
                    // 空段落作为分段标志
                    if (currentPara.length() > 0) {
                        paragraphs.add(currentPara.toString().trim());
                        currentPara = new StringBuilder();
                    }
                } else {
                    if (currentPara.length() > 0) {
                        currentPara.append("\n");
                    }
                    currentPara.append(text.trim());
                }
            }
            if (currentPara.length() > 0) {
                paragraphs.add(currentPara.toString().trim());
            }

            paragraphs.removeIf(String::isEmpty);

            log.info("Parsed Word file '{}': {} paragraphs extracted", fileName, paragraphs.size());
            return paragraphs;
        } catch (Exception e) {
            log.error("Failed to parse Word file: {}", fileName, e);
            throw new RuntimeException("Word文件解析失败: " + e.getMessage(), e);
        }
    }
}
