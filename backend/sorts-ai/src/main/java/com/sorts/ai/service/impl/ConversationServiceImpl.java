package com.sorts.ai.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sorts.ai.dto.ConversationMessage;
import com.sorts.ai.dto.ConversationSaveRequest;
import com.sorts.ai.dto.ConversationVO;
import com.sorts.ai.entity.AiConversation;
import com.sorts.ai.mapper.AiConversationMapper;
import com.sorts.ai.service.ConversationService;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AI 会话持久化实现。
 *
 * <p>容量治理（防止快照式存储无限膨胀）：</p>
 * <ul>
 *   <li>单会话消息上限 {@link #MAX_MESSAGES}（超出保留最近 N 条）；</li>
 *   <li>单条消息正文上限 5000 字（超出截断）；</li>
 *   <li>每用户会话总数上限 {@link #MAX_SESSIONS}（新建时超出删除最久未更新的）；</li>
 *   <li>role 白名单：非 user/assistant/system 一律剔除，防止脏数据入库。</li>
 * </ul>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    /** 单会话消息上限（保留最近 N 条） */
    private static final int MAX_MESSAGES = 100;

    /** 单条消息正文上限（字符） */
    private static final int MAX_MESSAGE_LENGTH = 5000;

    /** 每用户会话总数上限 */
    private static final int MAX_SESSIONS = 50;

    /** 单页上限 */
    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_ROLES = Set.of("user", "assistant", "system");

    private final AiConversationMapper conversationMapper;

    private final ToolJsonCodec jsonCodec;

    @Override
    public PageData<ConversationVO> list(Long userId, int page, int pageSize) {
        long current = Math.max(page, 1);
        long size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Page<AiConversation> result = conversationMapper.selectPage(new Page<>(current, size),
                Wrappers.<AiConversation>lambdaQuery()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getUpdatedAt));
        List<ConversationVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageData.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO create(Long userId, String title) {
        // 容量上限：超出时删除最久未更新的会话（软删除），腾出配额
        Long count = conversationMapper.selectCount(Wrappers.<AiConversation>lambdaQuery()
                .eq(AiConversation::getUserId, userId));
        if (count != null && count >= MAX_SESSIONS) {
            AiConversation oldest = conversationMapper.selectOne(Wrappers.<AiConversation>lambdaQuery()
                    .eq(AiConversation::getUserId, userId)
                    .orderByAsc(AiConversation::getUpdatedAt)
                    .last("LIMIT 1"));
            if (oldest != null) {
                conversationMapper.deleteById(oldest.getId());
                log.info("会话数达上限，清理最旧会话 userId={}, conversationId={}", userId, oldest.getId());
            }
        }
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(StringUtils.hasText(title) ? title.strip() : "新会话");
        conversation.setMessages("[]");
        conversation.setDraft("");
        conversation.setSelectedPlanItems("[]");
        conversation.setLastGeneratedRange("{}");
        conversation.setPlanId(null);
        conversation.setPlanSuggestions(null);
        conversation.setDeleted(0);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return toVO(conversation);
    }

    @Override
    public ConversationVO get(Long userId, Long conversationId) {
        return toVO(requireOwned(userId, conversationId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO update(Long userId, Long conversationId, ConversationSaveRequest request) {
        AiConversation conversation = requireOwned(userId, conversationId);
        if (StringUtils.hasText(request.getTitle())) {
            conversation.setTitle(request.getTitle().strip());
        }
        if (request.getMessages() != null) {
            conversation.setMessages(writeMessages(sanitizeMessages(request.getMessages())));
        }
        if (request.getDraft() != null) {
            conversation.setDraft(request.getDraft());
        }
        if (request.getSelectedPlanItems() != null) {
            conversation.setSelectedPlanItems(writeObject(request.getSelectedPlanItems()));
        }
        if (request.getLastGeneratedRange() != null) {
            conversation.setLastGeneratedRange(writeObject(request.getLastGeneratedRange()));
        }
        if (request.getPlanId() != null) {
            conversation.setPlanId(request.getPlanId());
        }
        if (request.getPlanSuggestions() != null) {
            conversation.setPlanSuggestions(writeObject(request.getPlanSuggestions()));
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        return toVO(conversation);
    }

    @Override
    public void delete(Long userId, Long conversationId) {
        int affected = conversationMapper.softDeleteOwned(userId, conversationId);
        if (affected != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在或无权删除");
        }
        log.info("删除 AI 会话 userId={}, conversationId={}", userId, conversationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(Long userId, List<Long> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要删除的会话");
        }
        List<Long> distinct = conversationIds.stream().distinct().toList();
        if (distinct.size() > 200) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单次最多删除 200 个会话");
        }
        int affected = conversationMapper.softDeleteBatchOwned(userId, distinct);
        if (affected != distinct.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "部分会话不存在或无权删除，已整体回滚");
        }
        log.info("批量删除 AI 会话 userId={}, count={}", userId, distinct.size());
    }

    private AiConversation requireOwned(Long userId, Long conversationId) {
        AiConversation conversation = conversationMapper.selectOne(Wrappers.<AiConversation>lambdaQuery()
                .eq(AiConversation::getId, conversationId)
                .eq(AiConversation::getUserId, userId));
        if (conversation == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    /** 清洗消息：role 白名单、正文截断、保留最近 N 条 */
    private List<ConversationMessage> sanitizeMessages(List<ConversationMessage> messages) {
        List<ConversationMessage> cleaned = new ArrayList<>();
        for (ConversationMessage message : messages) {
            if (message == null || message.getRole() == null || !ALLOWED_ROLES.contains(message.getRole())) {
                continue;
            }
            if (message.getContent() != null && message.getContent().length() > MAX_MESSAGE_LENGTH) {
                message.setContent(message.getContent().substring(0, MAX_MESSAGE_LENGTH));
            }
            cleaned.add(message);
        }
        int size = cleaned.size();
        return size > MAX_MESSAGES ? cleaned.subList(size - MAX_MESSAGES, size) : cleaned;
    }

    private ConversationVO toVO(AiConversation conversation) {
        return ConversationVO.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .messages(readMessages(conversation.getMessages()))
                .draft(conversation.getDraft() == null ? "" : conversation.getDraft())
                .selectedPlanItems(readObject(conversation.getSelectedPlanItems()))
                .lastGeneratedRange(readObject(conversation.getLastGeneratedRange()))
                .planId(conversation.getPlanId())
                .planSuggestions(readObject(conversation.getPlanSuggestions()))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private String writeMessages(List<ConversationMessage> messages) {
        return writeObject(messages);
    }

    private String writeObject(Object value) {
        try {
            return jsonCodec.mapper().writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "会话数据序列化失败");
        }
    }

    private List<ConversationMessage> readMessages(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return jsonCodec.mapper().readValue(json, new TypeReference<List<ConversationMessage>>() {
            });
        } catch (Exception e) {
            log.warn("会话消息 JSON 解析失败，按空会话处理");
            return List.of();
        }
    }

    private Object readObject(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return jsonCodec.mapper().readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
