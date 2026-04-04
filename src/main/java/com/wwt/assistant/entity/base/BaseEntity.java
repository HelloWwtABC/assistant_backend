package com.wwt.assistant.entity.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公共基础实体。
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BaseEntity {

    /**
     * 主键ID。
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 创建时间。
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 软删除时间，NULL表示未删除。
     */
    @TableField("deleted_at")
    @TableLogic(value = "null", delval = "now(3)")
    private LocalDateTime deletedAt;

    /**
     * 软删除操作人ID，关联sys_user.id。
     */
    @TableField("deleted_by")
    private Long deletedBy;
}
