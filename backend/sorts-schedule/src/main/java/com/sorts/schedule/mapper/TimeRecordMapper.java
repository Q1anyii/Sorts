package com.sorts.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.schedule.entity.TimeRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计时片段数据访问。
 *
 * @author sorts
 */
@Mapper
public interface TimeRecordMapper extends BaseMapper<TimeRecord> {
}
