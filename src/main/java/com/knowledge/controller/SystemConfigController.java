package com.knowledge.controller;

import com.knowledge.common.Result;
import com.knowledge.model.dto.ConfigUpdateDTO;
import com.knowledge.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统配置 Controller
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * 获取所有配置
     */
    @GetMapping
    public Result<Map<String, String>> getAllConfigs() {
        return Result.success(systemConfigService.getAllConfigs());
    }

    /**
     * 获取单个配置
     */
    @GetMapping("/{key}")
    public Result<String> getConfig(@PathVariable String key) {
        String value = systemConfigService.getConfig(key);
        return Result.success(value);
    }

    /**
     * 更新配置
     */
    @PutMapping
    public Result<Void> updateConfig(@RequestBody @Valid ConfigUpdateDTO dto) {
        systemConfigService.updateConfig(dto);
        return Result.success(null);
    }
}
