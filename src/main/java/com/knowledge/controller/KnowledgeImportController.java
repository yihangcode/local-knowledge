package com.knowledge.controller;

import com.knowledge.common.Result;
import com.knowledge.model.vo.ImportTaskProgressVO;
import com.knowledge.service.ImportTaskService;
import com.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 知识导入 Controller
 */
@RestController
@RequestMapping("/api/knowledge-import")
@RequiredArgsConstructor
public class KnowledgeImportController {

    private final KnowledgeService knowledgeService;
    private final ImportTaskService importTaskService;

    /**
     * 导入知识文件
     * <p>
     * 异步处理：上传文件后返回 taskId，前端轮询进度
     * </p>
     *
     * @param file   上传的文件
     * @param baseId 目标知识库ID
     * @param format 文件格式（可选，自动识别）
     * @return 任务ID
     */
    @PostMapping("/upload")
    public Result<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("baseId") Long baseId,
            @RequestParam(value = "format", required = false) String format) {

        if (file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }

        try {
            String taskId = knowledgeService.importKnowledge(
                    baseId, file.getOriginalFilename(), file.getInputStream(), format);
            return Result.success(taskId);
        } catch (IOException e) {
            return Result.error("文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 查询导入任务进度
     *
     * @param taskId 任务ID
     * @return 进度信息
     */
    @GetMapping("/progress/{taskId}")
    public Result<ImportTaskProgressVO> getProgress(@PathVariable String taskId) {
        ImportTaskProgressVO progress = importTaskService.getProgress(taskId);
        if (progress == null) {
            return Result.error("任务不存在: " + taskId);
        }
        return Result.success(progress);
    }
}
