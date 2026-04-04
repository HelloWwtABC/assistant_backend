package com.wwt.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wwt.assistant.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统团队表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_team")
public class SysTeam extends BaseEntity {

    /**
     * 团队名称。
     */
    @TableField("name")
    private String name;

    /**
     * 团队编码。
     */
    @TableField("code")
    private String code;

    /**
     * 团队状态：active/inactive。
     */
    @TableField("status")
    private String status;

    /**
     * 团队负责人用户ID，关联sys_user.id。
     */
    @TableField("owner_user_id")
    private Long ownerUserId;

    /**
     * 团队备注。
     */
    @TableField("remark")
    private String remark;
}
