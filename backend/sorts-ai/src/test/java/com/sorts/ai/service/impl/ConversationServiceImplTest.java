package com.sorts.ai.service.impl;

import com.sorts.ai.dto.ConversationMessage;
import com.sorts.ai.dto.ConversationSaveRequest;
import com.sorts.ai.dto.ConversationVO;
import com.sorts.ai.entity.AiConversation;
import com.sorts.ai.mapper.AiConversationMapper;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 会话持久化单元测试：容量上限、消息清洗、删除语义（软删除 + 整体回滚）。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long CONV_ID = 1L;

    @Mock
    private AiConversationMapper conversationMapper;

    @InjectMocks
    private ConversationServiceImpl conversationService;

    @Spy
    private final ToolJsonCodec codec = new ToolJsonCodec();

    private AiConversation owned;

    @BeforeEach
    void setUp() {
        owned = new AiConversation();
        owned.setId(CONV_ID);
        owned.setUserId(USER_ID);
        owned.setTitle("旧会话");
        owned.setMessages("[]");
        owned.setDraft("");
        owned.setSelectedPlanItems("[]");
        owned.setLastGeneratedRange("{}");
        owned.setDeleted(0);
        owned.setCreatedAt(LocalDateTime.now());
        owned.setUpdatedAt(LocalDateTime.now());
        when(conversationMapper.selectOne(any())).thenReturn(owned);
    }

    @Test
    @DisplayName("create：标题为空时回落「新会话」，初始快照为空数组/空对象")
    void createAppliesDefaults() {
        var vo = conversationService.create(USER_ID, "   ");
        assertEquals("新会话", vo.getTitle());
        verify(conversationMapper).insert(any(AiConversation.class));
    }

    @Test
    @DisplayName("create：会话数达到上限时先软删最旧会话再新建")
    void createEvictsOldestAtCap() {
        when(conversationMapper.selectCount(any())).thenReturn(50L);
        AiConversation oldest = new AiConversation();
        oldest.setId(99L);
        when(conversationMapper.selectOne(any())).thenReturn(oldest);

        conversationService.create(USER_ID, "新会话");

        verify(conversationMapper).deleteById(99L);
        verify(conversationMapper).insert(any(AiConversation.class));
    }

    @Test
    @DisplayName("update：非法 role 被剔除、超长正文被截断、超出上限只保留最近 100 条")
    void updateSanitizesMessages() {
        List<ConversationMessage> messages = new ArrayList<>();
        // 101 条合法消息
        for (int i = 0; i < 101; i++) {
            messages.add(ConversationMessage.builder().role("user").content("m" + i).build());
        }
        // 2 条非法 role + 1 条超长正文
        messages.add(ConversationMessage.builder().role("system-admin").content("x").build());
        messages.add(ConversationMessage.builder().role(null).content("y").build());
        messages.add(ConversationMessage.builder().role("user").content("长".repeat(6000)).build());
        // 101 条消息队列（置底）再压入 2 条
        ConversationSaveRequest request = new ConversationSaveRequest();
        request.setMessages(messages);

        ConversationVO vo = conversationService.update(USER_ID, CONV_ID, request);

        List<ConversationMessage> saved = vo.getMessages();
        assertEquals(100, saved.size());
        assertTrue(saved.stream().noneMatch(m -> m.getRole() == null || !m.getRole().equals("user")),
                "只允许 user/assistant/system");
        assertTrue(saved.stream().noneMatch(m -> m.getContent().length() > 5000), "单条正文必须截断");
        // 保留最近 100 条：最后一条是超长截断的那条
        assertEquals(5000, saved.get(99).getContent().length());
    }

    @Test
    @DisplayName("update：草稿 / 勾选项 / 规划区间 / planId 快照落库")
    void updatePersistsSnapshotFields() {
        ConversationSaveRequest request = new ConversationSaveRequest();
        request.setDraft("未来一周草稿");
        request.setSelectedPlanItems(List.of("0", "2"));
        request.setLastGeneratedRange(java.util.Map.of("startDate", "2026-09-17", "endDate", "2026-09-23"));
        request.setPlanId("88");

        ConversationVO vo = conversationService.update(USER_ID, CONV_ID, request);

        assertEquals("未来一周草稿", vo.getDraft());
        assertEquals(List.of("0", "2"), vo.getSelectedPlanItems());
        verify(conversationMapper).updateById(any(AiConversation.class));
    }

    @Test
    @DisplayName("delete：不属于该用户时抛 NOT_FOUND")
    void deleteThrowsWhenNotOwned() {
        when(conversationMapper.softDeleteOwned(USER_ID, CONV_ID)).thenReturn(0);
        BizException ex = assertThrows(BizException.class,
                () -> conversationService.delete(USER_ID, CONV_ID));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("deleteBatch：空选择直接拒绝")
    void deleteBatchRejectsEmpty() {
        assertThrows(BizException.class, () -> conversationService.deleteBatch(USER_ID, List.of()));
        verify(conversationMapper, never()).softDeleteBatchOwned(eq(USER_ID), anyList());
    }

    @Test
    @DisplayName("deleteBatch：部分不存在时整体回滚")
    void deleteBatchRollsBackOnPartial() {
        when(conversationMapper.softDeleteBatchOwned(eq(USER_ID), anyList())).thenReturn(1);
        BizException ex = assertThrows(BizException.class,
                () -> conversationService.deleteBatch(USER_ID, List.of(1L, 2L)));
        assertTrue(ex.getMessage().contains("整体回滚"));
    }

    @Test
    @DisplayName("get：命中属主时返回完整会话")
    void getReturnsOwnedConversation() {
        ConversationVO vo = conversationService.get(USER_ID, CONV_ID);
        assertNotNull(vo);
        assertEquals(CONV_ID, vo.getId());
        assertEquals("旧会话", vo.getTitle());
    }
}
