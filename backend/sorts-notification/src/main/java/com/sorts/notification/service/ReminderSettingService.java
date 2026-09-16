package com.sorts.notification.service;

import com.sorts.notification.dto.ReminderSettingRequest;
import com.sorts.notification.dto.ReminderSettingVO;
import com.sorts.notification.entity.ReminderSetting;

import java.util.Collection;
import java.util.Map;

/**
 * 提醒设置服务。
 *
 * @author sorts
 */
public interface ReminderSettingService {

    /** 查询设置；用户从未自定义过时返回服务端默认值（customized=false） */
    ReminderSettingVO get(Long userId);

    /** 更新设置（仅覆盖传入的非空字段），返回更新后的结果 */
    ReminderSettingVO update(Long userId, ReminderSettingRequest request);

    /**
     * 取「生效设置」：已有记录直接返回，没有则用默认值补齐。
     *
     * <p>返回的对象在未落库时 {@code id} 为 null，调用方据此判断是否已自定义。</p>
     */
    ReminderSetting findEffective(Long userId);

    /** 批量取生效设置，供提醒扫描一次查完，避免按用户逐个查库 */
    Map<Long, ReminderSetting> findEffective(Collection<Long> userIds);
}
