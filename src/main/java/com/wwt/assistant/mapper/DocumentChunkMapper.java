package com.wwt.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wwt.assistant.entity.KbDocumentChunk;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DocumentChunkMapper extends BaseMapper<KbDocumentChunk> {

    @Delete("""
            DELETE FROM kb_document_chunk
            WHERE document_id = #{documentId}
            """)
    int deleteByDocumentId(@Param("documentId") Long documentId);

    @Update("""
            <script>
            UPDATE kb_document_chunk
            SET deleted_at = #{deletedAt},
                deleted_by = #{deletedBy},
                updated_at = #{updatedAt}
            WHERE deleted_at IS NULL
              AND document_id IN
              <foreach collection="documentIds" item="documentId" open="(" separator="," close=")">
                  #{documentId}
              </foreach>
            </script>
            """)
    int softDeleteByDocumentIds(
            @Param("documentIds") List<Long> documentIds,
            @Param("deletedAt") LocalDateTime deletedAt,
            @Param("deletedBy") Long deletedBy,
            @Param("updatedAt") LocalDateTime updatedAt);

    int batchInsert(@Param("chunks") List<KbDocumentChunk> chunks);

    @Update("""
            <script>
            UPDATE kb_document_chunk
            SET vector_status = 'completed',
                embedding_ref = CAST(id AS CHAR),
                updated_at = NOW()
            WHERE id IN
            <foreach collection="chunkIds" item="chunkId" open="(" separator="," close=")">
                #{chunkId}
            </foreach>
            </script>
            """)
    int markVectorCompleted(@Param("chunkIds") List<Long> chunkIds);

    @Update("""
            <script>
            UPDATE kb_document_chunk
            SET vector_status = 'failed',
                updated_at = NOW()
            WHERE id IN
            <foreach collection="chunkIds" item="chunkId" open="(" separator="," close=")">
                #{chunkId}
            </foreach>
            </script>
            """)
    int markVectorFailed(@Param("chunkIds") List<Long> chunkIds);

    @Select("""
            <script>
            SELECT
                c.id AS chunkId,
                c.document_id AS documentId,
                d.name AS documentName,
                c.chunk_index AS chunkIndex,
                c.content AS content,
                c.summary AS summary,
                c.page_no AS pageNo,
                c.section_title AS sectionTitle
            FROM kb_document_chunk c
            INNER JOIN kb_document d
                ON d.id = c.document_id
               AND d.deleted_at IS NULL
            INNER JOIN kb_knowledge_base kb
                ON kb.id = c.knowledge_base_id
               AND kb.deleted_at IS NULL
            WHERE c.deleted_at IS NULL
              AND kb.team_id = #{teamId}
              AND c.id IN
              <foreach collection="chunkIds" item="chunkId" open="(" separator="," close=")">
                  #{chunkId}
              </foreach>
            </script>
            """)
    List<QaChunkRecord> selectQaChunkRecords(
            @Param("teamId") Long teamId,
            @Param("chunkIds") List<Long> chunkIds);

    class QaChunkRecord {
        private Long chunkId;
        private Long documentId;
        private String documentName;
        private Integer chunkIndex;
        private String content;
        private String summary;
        private Integer pageNo;
        private String sectionTitle;

        public Long getChunkId() {
            return chunkId;
        }

        public void setChunkId(Long chunkId) {
            this.chunkId = chunkId;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }

        public String getDocumentName() {
            return documentName;
        }

        public void setDocumentName(String documentName) {
            this.documentName = documentName;
        }

        public Integer getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public Integer getPageNo() {
            return pageNo;
        }

        public void setPageNo(Integer pageNo) {
            this.pageNo = pageNo;
        }

        public String getSectionTitle() {
            return sectionTitle;
        }

        public void setSectionTitle(String sectionTitle) {
            this.sectionTitle = sectionTitle;
        }
    }
}
