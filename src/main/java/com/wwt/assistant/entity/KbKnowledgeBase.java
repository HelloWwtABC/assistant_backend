package com.wwt.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wwt.assistant.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库主表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_knowledge_base")
public class KbKnowledgeBase extends BaseEntity {

    /**
     * 所属团队ID，关联sys_team.id。
     */
    @TableField("team_id")
    private Long teamId;

    /**
     * 知识库名称。
     */
    @TableField("name")
    private String name;

    /**
     * 知识库描述。
     */
    @TableField("description")
    private String description;

    /**
     * 知识库状态：active/inactive。
     */
    @TableField("status")
    private String status;

    /**
     * 文档数量冗余字段。
     */
    @TableField("document_count")
    private Integer documentCount;

    /**
     * 创建人用户ID，关联sys_user.id。
     */
    @TableField("created_by")
    private Long createdBy;
}
