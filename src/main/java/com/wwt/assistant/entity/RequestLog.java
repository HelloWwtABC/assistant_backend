package com.wwt.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wwt.assistant.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 请求日志观测快照表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("request_log")
public class RequestLog extends BaseEntity {

    /**
     * 关联会话ID，关联chat_session.id。
     */
    @TableField("session_id")
    private Long sessionId;

    /**
     * 请求用户ID，关联sys_user.id。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 模型名称。
     */
    @TableField("model_name")
    private String modelName;

    /**
     * 请求状态：success/failed/partial_success。
     */
    @TableField("status")
    private String status;

    /**
     * 整体响应耗时，单位毫秒。
     */
    @TableField("latency_ms")
    private Integer latencyMs;

    /**
     * 用户问题全文。
     */
    @TableField("question")
    private String question;

    /**
     * 问题摘要。
     */
    @TableField("question_summary")
    private String questionSummary;

    /**
     * 模型回答全文。
     */
    @TableField("answer")
    private String answer;

    /**
     * 命中片段数。
     */
    @TableField("hit_chunk_count")
    private Integer hitChunkCount;

    /**
     * 引用数量。
     */
    @TableField("citation_count")
    private Integer citationCount;

    /**
     * 是否调用工具：0否1是。
     */
    @TableField("used_tool")
    private Integer usedTool;

    /**
     * 请求级引用来源快照JSON。
     */
    @TableField("citations_json")
    private String citationsJson;
}
