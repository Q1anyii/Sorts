package com.sorts.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.schedule.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日程数据访问。
 *
 * <p>统计类查询（按天分组、标签分布）在服务层基于列表聚合，
 * 数据量在个人日程场景下可控，同时便于单元测试与口径统一。</p>
 *
 * @author sorts
 */
@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {
}
