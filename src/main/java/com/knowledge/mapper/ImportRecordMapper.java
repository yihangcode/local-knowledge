package com.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledge.model.entity.ImportRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导入记录 Mapper
 */
@Mapper
public interface ImportRecordMapper extends BaseMapper<ImportRecord> {
}
