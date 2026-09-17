package com.sorts.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sorts.ai.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * AI 会话仓储。
 *
 * @author sorts
 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {

    /** 属主校验 + 软删除（带审计时间） */
    @Update("UPDATE t_ai_conversation SET deleted = 1, updated_at = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId} AND deleted = 0")
    int softDeleteOwned(@Param("userId") Long userId, @Param("id") Long id);

    /** 批量软删除：整体校验属主 */
    @Update("<script>"
            + "UPDATE t_ai_conversation SET deleted = 1, updated_at = NOW() "
            + "WHERE user_id = #{userId} AND deleted = 0 "
            + "AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int softDeleteBatchOwned(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
