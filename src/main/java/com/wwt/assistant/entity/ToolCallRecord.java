package com.wwt.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wwt.assistant.entity.base.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具调用记录表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tool_call_record")
public class ToolCallRecord extends BaseEntity {

    /**
     * 关联请求日志ID，关联request_log.id。
     */
    @TableField("request_log_id")
    private Long requestLogId;

    /**
     * 关联会话ID，关联chat_session.id。
     */
    @TableField("session_id")
    private Long sessionId;

    /**
     * 工具名称：ticket_status/create_ticket/service_health。
     */
    @TableField("tool_name")
    private String toolName;

    /**
     * 工具输入摘要。
     */
    @TableField("request_summary")
    private String requestSummary;

    /**
     * 工具输出摘要。
     */
    @TableField("result_summary")
    private String resultSummary;

    /**
     * 调用状态：success/failed。
     */
    @TableField("status")
    private String status;

    /**
     * 调用耗时，单位毫秒。
     */
    @TableField("duration_ms")
    private Integer durationMs;

    /**
     * 调用发生时间。
     */
    @TableField("called_at")
    private LocalDateTime calledAt;

    /**
     * 发起用户ID，关联sys_user.id。
     */
    @TableField("created_by")
    private Long createdBy;
}
