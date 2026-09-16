package com.sorts.notification.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sorts.common.exception.BizException;
import com.sorts.notification.dto.CreateNotificationCommand;
import com.sorts.notification.dto.NotificationPageVO;
import com.sorts.notification.dto.NotificationQuery;
import com.sorts.notification.entity.Notification;
import com.sorts.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知服务单元测试：列表归一与未读角标、已读幂等与属主校验、创建与批量容错。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplTest {

    private static final Long USER_ID = 9L;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Notification unread;

    @BeforeEach
    void setUp() {
        unread = new Notification();
        unread.setId(1L);
        unread.setUserId(USER_ID);
        unread.setType("REMINDER");
        unread.setTitle("「织一段代码」15 分钟后开始");
        unread.setContent("日程提醒");
        unread.setIsRead(0);
        unread.setRelatedId(100L);
    }

    @Test
    @DisplayName("list：页码与页大小归一，未读角标来自独立统计（不受筛选影响）")
    void listNormalizesPaging() {
        Page<Notification> page = new Page<>(1, 20);
        page.setRecords(List.of(unread));
        page.setTotal(1);
        when(notificationMapper.selectPage(any(), any())).thenReturn(page);
        when(notificationMapper.countUnread(USER_ID)).thenReturn(3L);

        NotificationQuery query = new NotificationQuery();
        query.setPage(0);
        query.setPageSize(0);
        NotificationPageVO result = notificationService.list(USER_ID, query);

        assertEquals(1, result.getList().size());
        assertEquals(1, result.getPage());
        assertEquals(20, result.getPageSize());
        assertEquals(3L, result.getUnreadCount());
        // 库表 TINYINT → 对外布尔
        assertFalse(result.getList().get(0).getIsRead());
    }

    @Test
    @DisplayName("list：pageSize 超过上限被夹紧，避免一次性拉爆")
    void listClampsPageSize() {
        Page<Notification> page = new Page<>(1, 200);
        page.setRecords(List.of());
        page.setTotal(0);
        when(notificationMapper.selectPage(any(), any())).thenReturn(page);

        NotificationQuery query = new NotificationQuery();
        query.setPageSize(9999);
        assertEquals(200, notificationService.list(USER_ID, query).getPageSize());
    }

    @Test
    @DisplayName("list：筛选「已读」时未读角标仍然是全部未读数")
    void listKeepsUnreadCountWhenFilteringRead() {
        Page<Notification> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(notificationMapper.selectPage(any(), any())).thenReturn(page);
        when(notificationMapper.countUnread(USER_ID)).thenReturn(5L);

        NotificationQuery query = new NotificationQuery();
        query.setIsRead(true);
        assertEquals(5L, notificationService.list(USER_ID, query).getUnreadCount());
    }

    @Test
    @DisplayName("list：非法通知类型直接报错，不静默返回全量")
    void listRejectsUnknownType() {
        NotificationQuery query = new NotificationQuery();
        query.setType("NOT_A_TYPE");
        assertThrows(BizException.class, () -> notificationService.list(USER_ID, query));
        verify(notificationMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("list：类型大小写不敏感")
    void listAcceptsLowercaseType() {
        Page<Notification> page = new Page<>(1, 20);
        page.setRecords(List.of(unread));
        page.setTotal(1);
        when(notificationMapper.selectPage(any(), any())).thenReturn(page);
        NotificationQuery query = new NotificationQuery();
        query.setType(" reminder ");
        assertEquals(1, notificationService.list(USER_ID, query).getList().size());
    }

    @Test
    @DisplayName("markRead：通知不存在或不属于本人时按「不存在」处理")
    void markReadRejectsForeignNotification() {
        when(notificationMapper.countOwned(1L, USER_ID)).thenReturn(0);
        assertThrows(BizException.class, () -> notificationService.markRead(USER_ID, 1L));
        verify(notificationMapper, never()).markRead(anyLong(), anyLong());
    }

    @Test
    @DisplayName("markRead：重复标记已读是幂等的，不因为 0 行更新而报错")
    void markReadIsIdempotent() {
        when(notificationMapper.countOwned(1L, USER_ID)).thenReturn(1);
        when(notificationMapper.markRead(1L, USER_ID)).thenReturn(0);
        notificationService.markRead(USER_ID, 1L);
        verify(notificationMapper).markRead(1L, USER_ID);
    }

    @Test
    @DisplayName("markRead：缺少 ID 时给出参数错误")
    void markReadRequiresId() {
        assertThrows(BizException.class, () -> notificationService.markRead(USER_ID, null));
    }

    @Test
    @DisplayName("markAllRead：返回本次实际变更条数")
    void markAllReadReturnsCount() {
        when(notificationMapper.markAllRead(USER_ID)).thenReturn(4);
        assertEquals(4, notificationService.markAllRead(USER_ID));
    }

    @Test
    @DisplayName("create：默认未读、未知类型降级为 SYSTEM、超长标题按列宽截断")
    void createNormalizesFields() {
        CreateNotificationCommand command = new CreateNotificationCommand();
        command.setUserId(USER_ID);
        command.setType("mystery");
        command.setTitle("标".repeat(200));
        command.setContent("正".repeat(600));

        notificationService.create(command);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        Notification saved = captor.getValue();
        assertEquals("SYSTEM", saved.getType());
        assertEquals(0, saved.getIsRead());
        assertEquals(128, saved.getTitle().length());
        assertEquals(512, saved.getContent().length());
        assertTrue(saved.getTitle().startsWith("标"));
    }

    @Test
    @DisplayName("create：标题为空或缺少接收人时拒绝")
    void createRejectsInvalidCommand() {
        CreateNotificationCommand noTitle = new CreateNotificationCommand();
        noTitle.setUserId(USER_ID);
        assertThrows(BizException.class, () -> notificationService.create(noTitle));

        CreateNotificationCommand noUser = new CreateNotificationCommand();
        noUser.setTitle("标题");
        assertThrows(BizException.class, () -> notificationService.create(noUser));

        assertThrows(BizException.class, () -> notificationService.create(null));
    }

    @Test
    @DisplayName("createBatch：空集合返回 0，不触发任何写库")
    void createBatchHandlesEmpty() {
        assertEquals(0, notificationService.createBatch(null));
        assertEquals(0, notificationService.createBatch(List.of()));
        verify(notificationMapper, never()).insert(ArgumentMatchers.<Notification>any());
    }

    @Test
    @DisplayName("createBatch：超过上限直接拒绝，避免一次压垮数据库")
    void createBatchRejectsOversize() {
        List<CreateNotificationCommand> commands = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            CreateNotificationCommand command = new CreateNotificationCommand();
            command.setUserId(USER_ID);
            command.setTitle("t" + i);
            commands.add(command);
        }
        assertThrows(BizException.class, () -> notificationService.createBatch(commands));
    }

    @Test
    @DisplayName("createBatch：个别非法项被跳过，其余照常入库")
    void createBatchSkipsInvalidItems() {
        CreateNotificationCommand valid = new CreateNotificationCommand();
        valid.setUserId(USER_ID);
        valid.setTitle("有效通知");
        CreateNotificationCommand invalid = new CreateNotificationCommand();

        assertEquals(1, notificationService.createBatch(List.of(valid, invalid)));
        verify(notificationMapper, times(1)).insert(ArgumentMatchers.<Notification>any());
    }

    @Test
    @DisplayName("list：查询条件为空时按默认分页返回，不 NPE")
    void listToleratesNullQuery() {
        Page<Notification> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(notificationMapper.selectPage(any(), any())).thenReturn(page);
        assertTrue(notificationService.list(USER_ID, null).getList().isEmpty());
    }
}
