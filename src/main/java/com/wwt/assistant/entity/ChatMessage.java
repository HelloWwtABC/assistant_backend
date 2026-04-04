package com.wwt.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wwt.assistant.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话消息事实表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_message")
public class ChatMessage extends BaseEntity {

    /**
     * 所属会话ID，关联chat_session.id。
     */
    @TableField("session_id")
    private Long sessionId;

    /**
     * 关联请求日志ID，关联request_log.id。
     */
    @TableField("request_log_id")
    private Long requestLogId;

    /**
     * 消息角色：user/assistant。
     */
    @TableField("role")
    private String role;

    /**
     * 消息内容。
     */
    @TableField("content")
    private String content;

    /**
     * 消息级引用来源JSON，主要用于assistant消息。
     */
    @TableField("citations_json")
    private String citationsJson;

    /**
     * 消息对应模型名称，assistant消息可用。
     */
    @TableField("model_name")
    private String modelName;

    /**
     * 提示词token用量。
     */
    @TableField("token_usage_prompt")
    private Integer tokenUsagePrompt;

    /**
     * 回答token用量。
     */
    @TableField("token_usage_completion")
    private Integer tokenUsageCompletion;
}
