package com.loanops.persistence.mapper;

import com.loanops.persistence.entity.AgentToolAuditLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Explicit SQL mapper for the audit Tool table.
 *
 * The table uses the composite primary key (request_id, sequence_no). MyBatis-Plus BaseMapper
 * models a single @TableId, so using BaseMapper here would misrepresent the key and expose unsafe
 * *ById methods. Keep every write/read predicate explicit instead.
 */
public interface AgentToolAuditLogMapper {

    @Insert("""
            INSERT INTO agent_tool_audit_log (
                request_id, sequence_no, tool_name, loan_no, started_at, status
            ) VALUES (
                #{requestId}, #{sequenceNo}, #{toolName}, #{loanNo}, #{startedAt}, #{status}
            )
            """)
    int insert(AgentToolAuditLogEntity entity);

    @Select("""
            SELECT request_id, sequence_no, tool_name, loan_no,
                   started_at, completed_at, status, duration_ms, error_type
            FROM agent_tool_audit_log
            WHERE request_id = #{requestId}
            ORDER BY sequence_no ASC
            """)
    List<AgentToolAuditLogEntity> selectByRequestId(@Param("requestId") String requestId);

    @Update("""
            UPDATE agent_tool_audit_log
            SET completed_at = #{completedAt},
                status = #{status},
                duration_ms = #{durationMs},
                error_type = #{errorType}
            WHERE request_id = #{requestId}
              AND sequence_no = #{sequenceNo}
              AND status = 'STARTED'
            """)
    int complete(
            @Param("requestId") String requestId,
            @Param("sequenceNo") int sequenceNo,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("status") String status,
            @Param("durationMs") long durationMs,
            @Param("errorType") String errorType);
}
