package com.sorts.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.notification.config.NotificationProperties;
import com.sorts.notification.dto.ReminderSettingRequest;
import com.sorts.notification.dto.ReminderSettingVO;
import com.sorts.notification.entity.ReminderSetting;
import com.sorts.notification.enums.ReminderChannel;
import com.sorts.notification.mapper.ReminderSettingMapper;
import com.sorts.notification.service.ReminderSettingService;
import com.sorts.notification.support.QuietHours;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 提醒设置服务实现。
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderSettingServiceImpl implements ReminderSettingService {

    private final ReminderSettingMapper reminderSettingMapper;

    private final NotificationProperties properties;

    @Override
    public ReminderSettingVO get(Long userId) {
        ReminderSetting setting = findEffective(userId);
        return toVO(setting, setting.getId() != null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReminderSettingVO update(Long userId, ReminderSettingRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "提醒设置不能为空");
        }
        ReminderSetting setting = reminderSettingMapper.selectOne(
                new LambdaQueryWrapper<ReminderSetting>().eq(ReminderSetting::getUserId, userId));
        boolean isNew = setting == null;
        if (isNew) {
            setting = new ReminderSetting();
            setting.setUserId(userId);
            setting.setDefaultAdvanceMinutes(properties.getDefaultAdvanceMinutes());
            setting.setChannels(properties.getDefaultChannels());
            setting.setQuietHoursEnabled(0);
            setting.setDeleted(0);
        }

        if (request.getDefaultAdvanceMinutes() != null) {
            setting.setDefaultAdvanceMinutes(request.getDefaultAdvanceMinutes());
        }
        if (request.getChannels() != null) {
            setting.setChannels(ReminderChannel.join(ReminderChannel.parse(new LinkedHashSet<>(request.getChannels()))));
        }
        if (request.getQuietHoursEnabled() != null) {
            setting.setQuietHoursEnabled(Boolean.TRUE.equals(request.getQuietHoursEnabled()) ? 1 : 0);
        }
        if (StringUtils.hasText(request.getQuietStart())) {
            setting.setQuietStart(request.getQuietStart().trim());
        }
        if (StringUtils.hasText(request.getQuietEnd())) {
            setting.setQuietEnd(request.getQuietEnd().trim());
        }

        validateQuietHours(setting);

        LocalDateTime now = LocalDateTime.now();
        if (isNew) {
            setting.setCreatedAt(now);
            setting.setUpdatedAt(now);
            reminderSettingMapper.insert(setting);
        } else {
            setting.setUpdatedAt(now);
            reminderSettingMapper.updateById(setting);
        }
        return toVO(setting, true);
    }

    @Override
    public ReminderSetting findEffective(Long userId) {
        ReminderSetting setting = reminderSettingMapper.selectOne(
                new LambdaQueryWrapper<ReminderSetting>().eq(ReminderSetting::getUserId, userId));
        return setting != null ? setting : defaultSetting(userId);
    }

    @Override
    public Map<Long, ReminderSetting> findEffective(Collection<Long> userIds) {
        Map<Long, ReminderSetting> result = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<ReminderSetting> persisted = reminderSettingMapper.selectList(
                new LambdaQueryWrapper<ReminderSetting>().in(ReminderSetting::getUserId, userIds));
        persisted.forEach(setting -> result.put(setting.getUserId(), setting));

        // 没有自定义记录的用户用默认设置补齐：提醒扫描按「用户 -> 设置」取值，缺 key 会退化成 NPE
        userIds.forEach(userId -> result.computeIfAbsent(userId, this::defaultSetting));
        return result;
    }

    /** 未落库的默认设置（id 为 null 即代表「未自定义」） */
    private ReminderSetting defaultSetting(Long userId) {
        ReminderSetting setting = new ReminderSetting();
        setting.setUserId(userId);
        setting.setDefaultAdvanceMinutes(properties.getDefaultAdvanceMinutes());
        setting.setChannels(properties.getDefaultChannels());
        setting.setQuietHoursEnabled(0);
        return setting;
    }

    /**
     * 免打扰一致性校验。
     *
     * <p>启用免打扰却不给起止时间，等于让用户以为设置生效、实际不生效；
     * 宁可报错让前端把输入补全。</p>
     */
    private void validateQuietHours(ReminderSetting setting) {
        if (!Integer.valueOf(1).equals(setting.getQuietHoursEnabled())) {
            return;
        }
        if (QuietHours.parse(setting.getQuietStart()) == null || QuietHours.parse(setting.getQuietEnd()) == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "启用免打扰需同时设置起止时间（HH:mm）");
        }
        if (setting.getQuietStart().equals(setting.getQuietEnd())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "免打扰起止时间不能相同");
        }
    }

    private ReminderSettingVO toVO(ReminderSetting setting, boolean customized) {
        ReminderSettingVO vo = new ReminderSettingVO();
        vo.setDefaultAdvanceMinutes(setting.getDefaultAdvanceMinutes());
        Set<ReminderChannel> channels = ReminderChannel.split(setting.getChannels());
        vo.setChannels(channels.stream().map(Enum::name).toList());
        vo.setQuietHoursEnabled(Integer.valueOf(1).equals(setting.getQuietHoursEnabled()));
        vo.setQuietStart(setting.getQuietStart());
        vo.setQuietEnd(setting.getQuietEnd());
        vo.setCustomized(customized);
        return vo;
    }
}
