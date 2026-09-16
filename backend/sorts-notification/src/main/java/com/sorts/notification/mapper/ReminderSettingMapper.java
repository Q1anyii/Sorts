package com.sorts.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.notification.entity.ReminderSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提醒设置数据访问。
 *
 * @author sorts
 */
@Mapper
public interface ReminderSettingMapper extends BaseMapper<ReminderSetting> {
}
