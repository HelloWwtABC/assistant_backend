package com.wwt.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wwt.assistant.dto.document.request.DocumentQuery;
import com.wwt.assistant.dto.document.response.DocumentChunkItem;
import com.wwt.assistant.dto.document.response.DocumentDetailResponse;
import com.wwt.assistant.dto.document.response.DocumentListItem;
import com.wwt.assistant.dto.document.response.OptionItem;
import com.wwt.assistant.entity.KbDocument;
import com.wwt.assistant.entity.KbKnowledgeBase;
import java.time.LocalDateTime;
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
            WHERE d.id = #{documentId}
              AND d.deleted_at IS NULL
              AND kb.team_id = #{teamId}
            LIMIT 1
            """)
    DocumentDetailResponse selectDocumentDetail(
            @Param("teamId") Long teamId,
            @Param("documentId") Long documentId);

    @Select("""
            SELECT
                CAST(id AS CHAR) AS chunkId,
                chunk_index AS chunkIndex,
                summary AS summary,
                token_count AS tokenCount,
                vector_status AS vectorStatus
            FROM kb_document_chunk
            WHERE document_id = #{documentId}
              AND knowledge_base_id = #{knowledgeBaseId}
              AND deleted_at IS NULL
            ORDER BY chunk_index ASC, id ASC
            """)
    List<DocumentChunkItem> selectDocumentChunks(
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("documentId") Long documentId);

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

    @Update("""
            UPDATE kb_knowledge_base
            SET document_count = GREATEST(COALESCE(document_count, 0) - #{count}, 0)
            WHERE id = #{knowledgeBaseId}
              AND team_id = #{teamId}
              AND deleted_at IS NULL
            """)
    int decreaseDocumentCount(
            @Param("teamId") Long teamId,
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("count") Integer count);

    @Select("""
            SELECT d.id, d.knowledge_base_id, d.name, d.file_type, d.file_url,
                   d.file_size, d.status, d.chunk_count, d.remark, d.created_by,
                   d.uploaded_at, d.parse_error_message, d.parse_started_at,
                   d.parse_finished_at, d.parsed_at, d.parser_version,
                   d.created_at, d.updated_at, d.deleted_at, d.deleted_by
            FROM kb_document d
            INNER JOIN kb_knowledge_base kb
                ON kb.id = d.knowledge_base_id
               AND kb.deleted_at IS NULL
            WHERE d.id = #{documentId}
              AND kb.team_id = #{teamId}
              AND d.deleted_at IS NULL
            LIMIT 1
            """)
    KbDocument selectDocumentForProcessing(
            @Param("teamId") Long teamId,
            @Param("documentId") Long documentId);

    @Select("""
            SELECT d.id, d.knowledge_base_id, d.name, d.file_type, d.file_url,
                   d.file_size, d.status, d.chunk_count, d.remark, d.created_by,
                   d.uploaded_at, d.parse_error_message, d.parse_started_at,
                   d.parse_finished_at, d.parsed_at, d.parser_version,
                   d.created_at, d.updated_at, d.deleted_at, d.deleted_by
            FROM kb_document d
            INNER JOIN kb_knowledge_base kb
                ON kb.id = d.knowledge_base_id
               AND kb.deleted_at IS NULL
            WHERE d.id = #{documentId}
              AND d.knowledge_base_id = #{knowledgeBaseId}
              AND kb.team_id = #{teamId}
              AND d.deleted_at IS NULL
            LIMIT 1
            """)
    KbDocument selectVectorizationDocument(
            @Param("teamId") Long teamId,
            @Param("documentId") Long documentId,
            @Param("knowledgeBaseId") Long knowledgeBaseId);

    @Select("""
            <script>
            SELECT d.id, d.knowledge_base_id, d.name, d.file_type, d.file_url,
                   d.file_size, d.status, d.chunk_count, d.remark, d.created_by,
                   d.uploaded_at, d.parse_error_message, d.parse_started_at,
                   d.parse_finished_at, d.parsed_at, d.parser_version,
                   d.created_at, d.updated_at, d.deleted_at, d.deleted_by
            FROM kb_document d
            INNER JOIN kb_knowledge_base kb
                ON kb.id = d.knowledge_base_id
               AND kb.deleted_at IS NULL
            WHERE kb.team_id = #{teamId}
              AND d.deleted_at IS NULL
              AND d.id IN
              <foreach collection="documentIds" item="documentId" open="(" separator="," close=")">
                  #{documentId}
              </foreach>
            ORDER BY d.id ASC
            </script>
            """)
    List<KbDocument> selectDocumentsForDeletion(
            @Param("teamId") Long teamId,
            @Param("documentIds") List<Long> documentIds);

    @Update("""
            <script>
            UPDATE kb_document
            SET status = 'deleting',
                updated_at = #{updatedAt}
            WHERE deleted_at IS NULL
              AND status IN
              <foreach collection="allowedStatuses" item="status" open="(" separator="," close=")">
                  #{status}
              </foreach>
              AND id IN
              <foreach collection="documentIds" item="documentId" open="(" separator="," close=")">
                  #{documentId}
              </foreach>
            </script>
            """)
    int markDocumentsDeleting(
            @Param("documentIds") List<Long> documentIds,
            @Param("allowedStatuses") List<String> allowedStatuses,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            <script>
            UPDATE kb_document
            SET deleted_at = #{deletedAt},
                deleted_by = #{deletedBy},
                updated_at = #{updatedAt}
            WHERE deleted_at IS NULL
              AND id IN
              <foreach collection="documentIds" item="documentId" open="(" separator="," close=")">
                  #{documentId}
              </foreach>
            </script>
            """)
    int softDeleteDocuments(
            @Param("documentIds") List<Long> documentIds,
            @Param("deletedAt") LocalDateTime deletedAt,
            @Param("deletedBy") Long deletedBy,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            <script>
            UPDATE kb_document
            SET status = 'parsing',
                parse_error_message = NULL,
                parse_started_at = #{parseStartedAt},
                parser_version = #{parserVersion},
                updated_at = #{updatedAt}
            WHERE id = #{documentId}
              AND deleted_at IS NULL
              AND status IN
              <foreach collection="allowedStatuses" item="status" open="(" separator="," close=")">
                  #{status}
              </foreach>
            </script>
            """)
    int tryMarkParsingStarted(
            @Param("documentId") Long documentId,
            @Param("allowedStatuses") List<String> allowedStatuses,
            @Param("parserVersion") String parserVersion,
            @Param("parseStartedAt") LocalDateTime parseStartedAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE kb_document
            SET status = #{status},
                updated_at = #{updatedAt}
            WHERE id = #{documentId}
              AND deleted_at IS NULL
            """)
    int updateDocumentStatus(
            @Param("documentId") Long documentId,
            @Param("status") String status,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE kb_document
            SET chunk_count = #{chunkCount},
                status = #{status},
                parse_error_message = #{parseErrorMessage},
                parse_finished_at = #{parseFinishedAt},
                parsed_at = #{parsedAt},
                updated_at = #{updatedAt}
            WHERE id = #{documentId}
              AND deleted_at IS NULL
            """)
    int updateChunkingResult(
            @Param("documentId") Long documentId,
            @Param("chunkCount") Integer chunkCount,
            @Param("status") String status,
            @Param("parseErrorMessage") String parseErrorMessage,
            @Param("parseFinishedAt") LocalDateTime parseFinishedAt,
            @Param("parsedAt") LocalDateTime parsedAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE kb_document
            SET status = 'failed',
                parse_error_message = #{parseErrorMessage},
                parse_finished_at = #{parseFinishedAt},
                updated_at = #{updatedAt}
            WHERE id = #{documentId}
              AND deleted_at IS NULL
            """)
    int markProcessingFailed(
            @Param("documentId") Long documentId,
            @Param("parseErrorMessage") String parseErrorMessage,
            @Param("parseFinishedAt") LocalDateTime parseFinishedAt,
            @Param("updatedAt") LocalDateTime updatedAt);
}
