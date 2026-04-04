package com.wwt.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wwt.assistant.entity.base.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document")
public class KbDocument extends BaseEntity {

    /**
     * 所属知识库ID，关联kb_knowledge_base.id。
     */
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    /**
     * 文档名称。
     */
    @TableField("name")
    private String name;

    /**
     * 文件类型：pdf/docx/md/txt。
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 原始文件存储地址。
     */
    @TableField("file_url")
    private String fileUrl;

    /**
     * 文件大小，单位字节。
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 文档解析状态：unparsed/parsing/completed/failed。
     */
    @TableField("status")
    private String status;

    /**
     * 文档切片数量冗余字段。
     */
    @TableField("chunk_count")
    private Integer chunkCount;

    /**
     * 文档备注。
     */
    @TableField("remark")
    private String remark;

    /**
     * 上传人用户ID，关联sys_user.id。
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 上传时间。
     */
    @TableField("uploaded_at")
    private LocalDateTime uploadedAt;

    /**
     * 解析失败错误信息。
     */
    @TableField("parse_error_message")
    private String parseErrorMessage;

    /**
     * 解析开始时间。
     */
    @TableField("parse_started_at")
    private LocalDateTime parseStartedAt;

    /**
     * 解析结束时间。
     */
    @TableField("parse_finished_at")
    private LocalDateTime parseFinishedAt;

    /**
     * 解析成功时间。
     */
    @TableField("parsed_at")
    private LocalDateTime parsedAt;

    /**
     * 解析器版本号。
     */
    @TableField("parser_version")
    private String parserVersion;
}
