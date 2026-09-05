package com.loanops.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loanops.persistence.entity.ConversationEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface ConversationMapper extends BaseMapper<ConversationEntity> {

    @Update("""
            UPDATE conversation
            SET version = version + 1,
                last_message_sequence = last_message_sequence + 2,
                updated_at = #{updatedAt}
            WHERE conversation_id = #{conversationId}
              AND version = #{expectedVersion}
              AND last_message_sequence = #{expectedLastMessageSequence}
            """)
    int advance(
            @Param("conversationId") String conversationId,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedLastMessageSequence") int expectedLastMessageSequence,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
