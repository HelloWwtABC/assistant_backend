package com.wwt.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wwt.assistant.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 问答会话主表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_session")
public class ChatSession extends BaseEntity {

    /**
     * 所属团队ID，关联sys_team.id。
     */
    @TableField("team_id")
    private Long teamId;

    /**
     * 发起用户ID，关联sys_user.id。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 关联知识库ID，关联kb_knowledge_base.id。
     */
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    /**
     * 会话标题。
     */
    @TableField("title")
    private String title;

    /**
     * 会话状态：active/archived/deleted。
     */
    @TableField("status")
    private String status;

    /**
     * 消息总数冗余字段。
     */
    @TableField("message_count")
    private Integer messageCount;

    /**
     * 用户提问次数冗余字段。
     */
    @TableField("question_count")
    private Integer questionCount;

    /**
     * 最后一条消息摘要。
     */
    @TableField("last_message_preview")
    private String lastMessagePreview;

    /**
     * 最近问题摘要。
     */
    @TableField("last_question_snippet")
    private String lastQuestionSnippet;
}
