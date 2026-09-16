package com.sorts.notification.service.impl;

import com.sorts.common.exception.BizException;
import com.sorts.notification.config.NotificationProperties;
import com.sorts.notification.dto.ReminderSettingRequest;
import com.sorts.notification.dto.ReminderSettingVO;
import com.sorts.notification.entity.ReminderSetting;
import com.sorts.notification.mapper.ReminderSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提醒设置服务单元测试：默认值回落、部分更新、免打扰一致性、批量补齐。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReminderSettingServiceImplTest {

    private static final Long USER_ID = 11L;

    @Mock
    private ReminderSettingMapper reminderSettingMapper;

    private NotificationProperties properties;

    private ReminderSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setDefaultAdvanceMinutes(15);
        properties.setDefaultChannels("APP");
        service = new ReminderSettingServiceImpl(reminderSettingMapper, properties);
    }

    @Test
    @DisplayName("get：从未自定义时返回服务端默认值，并明确标注 customized=false")
    void getFallsBackToDefaults() {
        when(reminderSettingMapper.selectOne(any())).thenReturn(null);

        ReminderSettingVO vo = service.get(USER_ID);

        assertEquals(15, vo.getDefaultAdvanceMinutes());
        assertEquals(List.of("APP"), vo.getChannels());
        assertFalse(vo.getQuietHoursEnabled());
        assertFalse(vo.getCustomized());
    }

    @Test
    @DisplayName("get：已有记录时按库中值返回，渠道按逗号拆分")
    void getReturnsPersistedSetting() {
        ReminderSetting persisted = new ReminderSetting();
        persisted.setId(1L);
        persisted.setUserId(USER_ID);
        persisted.setDefaultAdvanceMinutes(30);
        persisted.setChannels("APP,EMAIL");
        persisted.setQuietHoursEnabled(1);
        persisted.setQuietStart("23:00");
        persisted.setQuietEnd("07:00");
        when(reminderSettingMapper.selectOne(any())).thenReturn(persisted);

        ReminderSettingVO vo = service.get(USER_ID);

        assertTrue(vo.getCustomized());
        assertEquals(30, vo.getDefaultAdvanceMinutes());
        assertEquals(List.of("APP", "EMAIL"), vo.getChannels());
        assertTrue(vo.getQuietHoursEnabled());
        assertEquals("23:00", vo.getQuietStart());
    }

    @Test
    @DisplayName("update：首次设置走 insert，并补齐未传字段的默认值")
    void updateInsertsWhenAbsent() {
        when(reminderSettingMapper.selectOne(any())).thenReturn(null);

        ReminderSettingRequest request = new ReminderSettingRequest();
        request.setDefaultAdvanceMinutes(45);
        ReminderSettingVO vo = service.update(USER_ID, request);

        ArgumentCaptor<ReminderSetting> captor = ArgumentCaptor.forClass(ReminderSetting.class);
        verify(reminderSettingMapper).insert(captor.capture());
        ReminderSetting saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(45, saved.getDefaultAdvanceMinutes());
        // 未传渠道时用默认值，而不是落 null 让后续解析炸掉
        assertEquals("APP", saved.getChannels());
        assertEquals(0, saved.getQuietHoursEnabled());
        assertEquals(0, saved.getDeleted());
        assertNotNull(saved.getCreatedAt());
        verify(reminderSettingMapper, never()).updateById(ArgumentMatchers.<ReminderSetting>any());
        assertTrue(vo.getCustomized());
    }

    @Test
    @DisplayName("update：已有记录走 updateById，未传字段保持原值")
    void updateOnlyOverwritesProvidedFields() {
        ReminderSetting persisted = new ReminderSetting();
        persisted.setId(1L);
        persisted.setUserId(USER_ID);
        persisted.setDefaultAdvanceMinutes(10);
        persisted.setChannels("APP,SMS");
        persisted.setQuietHoursEnabled(1);
        persisted.setQuietStart("22:00");
        persisted.setQuietEnd("06:00");
        when(reminderSettingMapper.selectOne(any())).thenReturn(persisted);

        ReminderSettingRequest request = new ReminderSettingRequest();
        request.setDefaultAdvanceMinutes(5);
        ReminderSettingVO vo = service.update(USER_ID, request);

        verify(reminderSettingMapper).updateById(persisted);
        verify(reminderSettingMapper, never()).insert(ArgumentMatchers.<ReminderSetting>any());
        assertEquals(5, vo.getDefaultAdvanceMinutes());
        // 渠道与免打扰时段未被清空
        assertEquals(List.of("APP", "SMS"), vo.getChannels());
        assertEquals("22:00", vo.getQuietStart());
    }

    @Test
    @DisplayName("update：全未知渠道回落为 APP，不会把提醒渠道置空")
    void updateNormalizesUnknownChannels() {
        when(reminderSettingMapper.selectOne(any())).thenReturn(null);

        ReminderSettingRequest request = new ReminderSettingRequest();
        request.setChannels(List.of("WECHAT", "DINGTALK"));
        service.update(USER_ID, request);

        ArgumentCaptor<ReminderSetting> captor = ArgumentCaptor.forClass(ReminderSetting.class);
        verify(reminderSettingMapper).insert(captor.capture());
        assertEquals("APP", captor.getValue().getChannels());
    }

    @Test
    @DisplayName("update：启用免打扰却没给时间时直接报错，避免「以为生效实际不生效」")
    void updateRejectsQuietHoursWithoutBounds() {
        when(reminderSettingMapper.selectOne(any())).thenReturn(null);

        ReminderSettingRequest request = new ReminderSettingRequest();
        request.setQuietHoursEnabled(true);
        assertThrows(BizException.class, () -> service.update(USER_ID, request));
        verify(reminderSettingMapper, never()).insert(ArgumentMatchers.<ReminderSetting>any());
    }

    @Test
    @DisplayName("update：免打扰起止相同时报错（等于 24 小时静默的误配置）")
    void updateRejectsIdenticalQuietBounds() {
        when(reminderSettingMapper.selectOne(any())).thenReturn(null);

        ReminderSettingRequest request = new ReminderSettingRequest();
        request.setQuietHoursEnabled(true);
        request.setQuietStart("08:00");
        request.setQuietEnd("08:00");
        assertThrows(BizException.class, () -> service.update(USER_ID, request));
    }

    @Test
    @DisplayName("update：启用免打扰且时间合法时正常写入")
    void updateAcceptsValidQuietHours() {
        when(reminderSettingMapper.selectOne(any())).thenReturn(null);

        ReminderSettingRequest request = new ReminderSettingRequest();
        request.setQuietHoursEnabled(true);
        request.setQuietStart("23:00");
        request.setQuietEnd("07:00");
        ReminderSettingVO vo = service.update(USER_ID, request);

        assertTrue(vo.getQuietHoursEnabled());
        assertEquals("23:00", vo.getQuietStart());
        assertEquals("07:00", vo.getQuietEnd());
    }

    @Test
    @DisplayName("update：请求体缺失时报参数错误")
    void updateRejectsNullRequest() {
        assertThrows(BizException.class, () -> service.update(USER_ID, null));
    }

    @Test
    @DisplayName("findEffectiveAll：批量取设置时为无记录的用户补默认值，避免扫描时 NPE")
    void findEffectiveBatchFillsDefaults() {
        ReminderSetting persisted = new ReminderSetting();
        persisted.setId(2L);
        persisted.setUserId(USER_ID);
        persisted.setDefaultAdvanceMinutes(60);
        persisted.setChannels("APP");
        when(reminderSettingMapper.selectList(any())).thenReturn(List.of(persisted));

        Map<Long, ReminderSetting> result = service.findEffectiveAll(List.of(USER_ID, 99L));

        assertEquals(2, result.size());
        assertEquals(60, result.get(USER_ID).getDefaultAdvanceMinutes());
        // 未自定义的用户拿到默认设置，且 id 为空以标识「未落库」
        assertEquals(15, result.get(99L).getDefaultAdvanceMinutes());
        assertNull(result.get(99L).getId());
    }

    @Test
    @DisplayName("findEffectiveAll：空入参直接返回空 Map，不查库")
    void findEffectiveHandlesEmptyInput() {
        assertTrue(service.findEffectiveAll(List.of()).isEmpty());
        assertTrue(service.findEffectiveAll(null).isEmpty());
        verify(reminderSettingMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("findEffective：单用户查询也带默认值语义")
    void findEffectiveSingle() {
        when(reminderSettingMapper.selectOne(any())).thenReturn(null);
        ReminderSetting setting = service.findEffective(USER_ID);
        assertEquals(15, setting.getDefaultAdvanceMinutes());
        assertEquals(USER_ID, setting.getUserId());
        assertNull(setting.getId());
    }
}
