package com.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.model.entity.ImportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导入任务 Mapper
 */
@Mapper
public interface ImportTaskMapper extends BaseMapper<ImportTask> {
}
