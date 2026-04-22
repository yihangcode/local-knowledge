package com.knowledge.parser.impl;

import com.knowledge.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Markdown 文档解析器
 * <p>
 * 按 Markdown 标题层级分节，每节作为一个独立段落
 * </p>
 */
@Slf4j
@Component
public class MarkdownParser implements DocumentParser {

    @Override
    public String supportedFormat() {
        return "MD";
    }

    @Override
    public List<String> parse(InputStream inputStream, String fileName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            List<String> sections = new ArrayList<>();
            StringBuilder currentSection = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                // 遇到标题（# 开头），且当前节有内容，则结束当前节
                if (line.trim().startsWith("#") && currentSection.length() > 0) {
                    sections.add(currentSection.toString().trim());
                    currentSection = new StringBuilder();
                }
                if (currentSection.length() > 0) {
                    currentSection.append("\n");
                }
                currentSection.append(line);
            }
            if (currentSection.length() > 0) {
                sections.add(currentSection.toString().trim());
            }

            sections.removeIf(String::isEmpty);

            log.info("Parsed Markdown file '{}': {} sections extracted", fileName, sections.size());
            return sections;
        } catch (Exception e) {
            log.error("Failed to parse Markdown file: {}", fileName, e);
            throw new RuntimeException("Markdown文件解析失败: " + e.getMessage(), e);
        }
    }
}
