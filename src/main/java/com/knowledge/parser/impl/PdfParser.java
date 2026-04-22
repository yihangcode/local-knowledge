package com.knowledge.parser.impl;

import com.knowledge.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 文档解析器
 * <p>
 * 使用 PDFBox 提取文本，按页分段
 * </p>
 */
@Slf4j
@Component
public class PdfParser implements DocumentParser {

    @Override
    public String supportedFormat() {
        return "PDF";
    }

    @Override
    public List<String> parse(InputStream inputStream, String fileName) {
        try {
            // PDFBox 3.x 需要先将流保存为临时文件再加载
            Path tempFile = Files.createTempFile("knowledge-pdf-", ".pdf");
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            try (PDDocument document = Loader.loadPDF(tempFile.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                int totalPages = document.getNumberOfPages();
                List<String> pages = new ArrayList<>();

                for (int i = 1; i <= totalPages; i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String pageText = stripper.getText(document).trim();
                    if (!pageText.isEmpty()) {
                        pages.add(pageText);
                    }
                }

                log.info("Parsed PDF file '{}': {} pages, {} non-empty pages", fileName, totalPages, pages.size());
                return pages;
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            log.error("Failed to parse PDF file: {}", fileName, e);
            throw new RuntimeException("PDF文件解析失败: " + e.getMessage(), e);
        }
    }
}
