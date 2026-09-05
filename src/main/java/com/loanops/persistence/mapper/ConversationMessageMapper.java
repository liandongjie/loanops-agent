package com.loanops.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loanops.persistence.entity.ConversationMessageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ConversationMessageMapper extends BaseMapper<ConversationMessageEntity> {

    @Select("""
            SELECT message_id,
                   conversation_id,
                   request_id,
                   sequence_no,
                   role,
                   content,
                   created_at
            FROM conversation_message
            WHERE conversation_id = #{conversationId}
              AND role IN ('USER', 'ASSISTANT')
            ORDER BY sequence_no DESC
            LIMIT #{limit}
            """)
    List<ConversationMessageEntity> selectRecentHistory(
            @Param("conversationId") String conversationId,
            @Param("limit") int limit
    );
}
