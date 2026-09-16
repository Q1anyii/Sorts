package com.sorts.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.user.entity.PointsLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 积分流水数据访问。
 *
 * @author sorts
 */
@Mapper
public interface PointsLogMapper extends BaseMapper<PointsLog> {
}
