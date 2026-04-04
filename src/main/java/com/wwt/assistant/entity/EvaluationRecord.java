package com.wwt.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wwt.assistant.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 请求评测记录表，当前阶段按一次请求一条最终评测记录设计，后续可扩展为多来源评测。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_record")
public class EvaluationRecord extends BaseEntity {

    /**
     * 关联请求日志ID，关联request_log.id；当前阶段按一次请求一条最终评测记录设计。
     */
    @TableField("request_log_id")
    private Long requestLogId;

    /**
     * 关联会话ID，关联chat_session.id。
     */
    @TableField("session_id")
    private Long sessionId;

    /**
     * 评价用户ID，关联sys_user.id。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 用户评分，建议范围1到5。
     */
    @TableField("user_score")
    private Integer userScore;

    /**
     * 是否有帮助：0否1是。
     */
    @TableField("helpful")
    private Integer helpful;

    /**
     * 回答质量等级：high/medium/low。
     */
    @TableField("quality_level")
    private String qualityLevel;

    /**
     * 评测备注。
     */
    @TableField("remark")
    private String remark;
}
