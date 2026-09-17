package com.sorts.ai.service;

import com.sorts.ai.dto.ConversationSaveRequest;
import com.sorts.ai.dto.ConversationVO;
import com.sorts.common.result.PageData;

import java.util.List;

/**
 * AI 会话持久化：登录用户会话由后端保存，刷新 / 换设备不丢。
 *
 * <p>存储策略：全量快照（消息、草稿、勾选项、最近生成区间一次保存），
 * 个人场景数据量小、实现简单、无增量合并的一致性问题。</p>
 *
 * @author sorts
 */
public interface ConversationService {

    /** 分页查询当前用户的会话（按更新时间倒序） */
    PageData<ConversationVO> list(Long userId, int page, int pageSize);

    /** 新建会话（标题缺省用空串，前端随后台补首条消息） */
    ConversationVO create(Long userId, String title);

    /** 会话详情（含消息等快照） */
    ConversationVO get(Long userId, Long conversationId);

    /** 全量快照保存；messages 超过上限时按「保留最近 N 条」截断 */
    ConversationVO update(Long userId, Long conversationId, ConversationSaveRequest request);

    /** 逻辑删除单个会话 */
    void delete(Long userId, Long conversationId);

    /** 批量逻辑删除：事务内全部成功或全部失败 */
    void deleteBatch(Long userId, List<Long> conversationIds);
}
