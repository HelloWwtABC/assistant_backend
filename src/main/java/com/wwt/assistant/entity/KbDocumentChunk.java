package com.wwt.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wwt.assistant.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档切片表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document_chunk")
public class KbDocumentChunk extends BaseEntity {

    /**
     * 所属文档ID，关联kb_document.id。
     */
    @TableField("document_id")
    private Long documentId;

    /**
     * 所属知识库ID，关联kb_knowledge_base.id。
     */
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    /**
     * 文档内切片序号，从1开始。
     */
    @TableField("chunk_index")
    private Integer chunkIndex;

    /**
     * 切片原始内容。
     */
    @TableField("content")
    private String content;

    /**
     * 切片摘要内容。
     */
    @TableField("summary")
    private String summary;

    /**
     * 切片token数量。
     */
    @TableField("token_count")
    private Integer tokenCount;

    /**
     * 向量化状态：pending/completed/failed。
     */
    @TableField("vector_status")
    private String vectorStatus;

    /**
     * 向量索引引用ID或外部向量存储标识。
     */
    @TableField("embedding_ref")
    private String embeddingRef;

    /**
     * 页码
     */
    @TableField("page_no")
    private Integer pageNo;

    /**
     * 章节标题
     */
    @TableField("section_title")
    private String sectionTitle;

    /**
     * 字符数
     */
    @TableField("char_count")
    private Integer charCount;
}
