package com.wwt.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wwt.assistant.dto.document.request.DocumentQuery;
import com.wwt.assistant.dto.document.response.DocumentListItem;
import com.wwt.assistant.dto.document.response.OptionItem;
import com.wwt.assistant.entity.KbDocument;
import com.wwt.assistant.entity.KbKnowledgeBase;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DocumentMapper extends BaseMapper<KbDocument> {

    @Select("""
            <script>
            SELECT
                CAST(d.id AS CHAR) AS documentId,
                d.name AS documentName,
                CAST(d.knowledge_base_id AS CHAR) AS knowledgeBaseId,
                kb.name AS knowledgeBaseName,
                d.file_type AS fileType,
                d.status AS status,
                COALESCE(d.chunk_count, 0) AS chunkCount,
                COALESCE(NULLIF(TRIM(u.name), ''), u.username, '') AS uploader,
                DATE_FORMAT(d.uploaded_at, '%Y-%m-%d %H:%i:%s') AS uploadedAt,
                d.remark AS remark
            FROM kb_document d
            INNER JOIN kb_knowledge_base kb
                ON kb.id = d.knowledge_base_id
               AND kb.deleted_at IS NULL
            LEFT JOIN sys_user u
                ON u.id = d.created_by
               AND u.deleted_at IS NULL
            WHERE d.deleted_at IS NULL
              AND kb.team_id = #{teamId}
            <if test="query != null and query.keyword != null and query.keyword != ''">
              AND (
                    LOWER(d.name) LIKE CONCAT('%', LOWER(#{query.keyword}), '%')
                    OR CAST(d.id AS CHAR) LIKE CONCAT('%', #{query.keyword}, '%')
                  )
            </if>
            <if test="query != null and query.status != null and query.status != ''">
              AND d.status = #{query.status}
            </if>
            <if test="query != null and query.uploader != null and query.uploader != ''">
              AND LOWER(COALESCE(NULLIF(TRIM(u.name), ''), u.username, ''))
                    LIKE CONCAT('%', LOWER(#{query.uploader}), '%')
            </if>
            ORDER BY COALESCE(d.uploaded_at, d.created_at) DESC, d.id DESC
            </script>
            """)
    IPage<DocumentListItem> selectDocumentPage(
            Page<DocumentListItem> page,
            @Param("teamId") Long teamId,
            @Param("query") DocumentQuery query);

    @Select("""
            SELECT id, team_id, name, description, status, document_count,
                   created_by, created_at, updated_at, deleted_at, deleted_by
            FROM kb_knowledge_base
            WHERE id = #{knowledgeBaseId}
              AND team_id = #{teamId}
              AND status = 'active'
              AND deleted_at IS NULL
            LIMIT 1
            """)
    KbKnowledgeBase selectActiveKnowledgeBase(
            @Param("teamId") Long teamId,
            @Param("knowledgeBaseId") Long knowledgeBaseId);

    @Select("""
            SELECT
                CAST(id AS CHAR) AS value,
                name AS label
            FROM kb_knowledge_base
            WHERE deleted_at IS NULL
              AND team_id = #{teamId}
              AND status = 'active'
            ORDER BY name ASC, id ASC
            """)
    List<OptionItem> selectKnowledgeBaseOptions(@Param("teamId") Long teamId);

    @Update("""
            UPDATE kb_knowledge_base
            SET document_count = COALESCE(document_count, 0) + 1
            WHERE id = #{knowledgeBaseId}
              AND team_id = #{teamId}
              AND deleted_at IS NULL
            """)
    int increaseDocumentCount(@Param("teamId") Long teamId, @Param("knowledgeBaseId") Long knowledgeBaseId);
}
