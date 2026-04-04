package com.wwt.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wwt.assistant.entity.base.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /**
     * 所属团队ID，关联sys_team.id。
     */
    @TableField("team_id")
    private Long teamId;

    /**
     * 登录用户名。
     */
    @TableField("username")
    private String username;

    /**
     * 密码哈希。
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 用户姓名或展示名。
     */
    @TableField("name")
    private String name;

    /**
     * 用户邮箱。
     */
    @TableField("email")
    private String email;

    /**
     * 用户角色：admin/operator/member。
     */
    @TableField("role")
    private String role;

    /**
     * 用户状态：active/disabled。
     */
    @TableField("status")
    private String status;

    /**
     * 最近登录时间。
     */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;
}
