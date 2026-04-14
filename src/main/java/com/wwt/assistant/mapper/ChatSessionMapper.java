package com.wwt.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wwt.assistant.dto.qa.response.QaSessionDetailResponse;
import com.wwt.assistant.dto.qa.response.QaSessionItem;
import com.wwt.assistant.entity.ChatSession;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("""
            SELECT
                CAST(id AS CHAR) AS sessionId,
                COALESCE(NULLIF(TRIM(title), ''), '新建问答会话') AS title,
                DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt,
                DATE_FORMAT(COALESCE(updated_at, created_at), '%Y-%m-%d %H:%i:%s') AS updatedAt,
                COALESCE(last_message_preview, '') AS lastMessagePreview,
                COALESCE(message_count, 0) AS messageCount
            FROM chat_session
            WHERE deleted_at IS NULL
              AND team_id = #{teamId}
              AND user_id = #{userId}
              AND status != 'deleted'
            ORDER BY COALESCE(updated_at, created_at) DESC, id DESC
            """)
    List<QaSessionItem> selectQaSessions(@Param("teamId") Long teamId, @Param("userId") Long userId);

    @Select("""
            SELECT
                CAST(cs.id AS CHAR) AS sessionId,
                COALESCE(NULLIF(TRIM(cs.title), ''), '新建问答会话') AS title,
                DATE_FORMAT(cs.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt,
                DATE_FORMAT(COALESCE(cs.updated_at, cs.created_at), '%Y-%m-%d %H:%i:%s') AS updatedAt,
                COALESCE(cs.last_message_preview, '') AS lastMessagePreview,
                COALESCE(cs.message_count, 0) AS messageCount
            FROM chat_session cs
            WHERE cs.deleted_at IS NULL
              AND cs.team_id = #{teamId}
              AND cs.user_id = #{userId}
              AND cs.status != 'deleted'
              AND cs.id = #{sessionId}
            LIMIT 1
            """)
    QaSessionDetailResponse selectQaSessionDetail(
            @Param("teamId") Long teamId,
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId);

    @Update("""
            UPDATE chat_session
            SET status = 'deleted',
                deleted_at = #{deletedAt},
                deleted_by = #{deletedBy},
                updated_at = #{updatedAt}
            WHERE id = #{sessionId}
              AND team_id = #{teamId}
              AND user_id = #{userId}
              AND deleted_at IS NULL
              AND status != 'deleted'
            """)
    int softDeleteQaSession(
            @Param("teamId") Long teamId,
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId,
            @Param("deletedAt") LocalDateTime deletedAt,
            @Param("deletedBy") Long deletedBy,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Select("""
            SELECT id, team_id, user_id, knowledge_base_id, title, status,
                   message_count, question_count, last_message_preview, last_question_snippet,
                   created_at, updated_at, deleted_at, deleted_by
            FROM chat_session
            WHERE id = #{sessionId}
              AND team_id = #{teamId}
              AND user_id = #{userId}
              AND deleted_at IS NULL
              AND status != 'deleted'
            LIMIT 1
            """)
    ChatSession selectOwnedActiveSession(
            @Param("teamId") Long teamId,
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId);

    @Update("""
            <script>
            UPDATE chat_session
            SET message_count = COALESCE(message_count, 0) + 1,
                question_count = COALESCE(question_count, 0) + 1,
                last_message_preview = #{lastMessagePreview},
                last_question_snippet = #{lastQuestionSnippet},
                updated_at = #{updatedAt}
                <if test="title != null and title != ''">
                    , title = #{title}
                </if>
            WHERE id = #{sessionId}
              AND team_id = #{teamId}
              AND user_id = #{userId}
              AND deleted_at IS NULL
              AND status != 'deleted'
            </script>
            """)
    int updateSessionAfterUserQuestion(
            @Param("teamId") Long teamId,
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId,
            @Param("title") String title,
            @Param("lastMessagePreview") String lastMessagePreview,
            @Param("lastQuestionSnippet") String lastQuestionSnippet,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE chat_session
            SET message_count = COALESCE(message_count, 0) + 1,
                last_message_preview = #{lastMessagePreview},
                updated_at = #{updatedAt}
            WHERE id = #{sessionId}
              AND team_id = #{teamId}
              AND user_id = #{userId}
              AND deleted_at IS NULL
              AND status != 'deleted'
            """)
    int updateSessionAfterAssistantReply(
            @Param("teamId") Long teamId,
            @Param("userId") Long userId,
            @Param("sessionId") Long sessionId,
            @Param("lastMessagePreview") String lastMessagePreview,
            @Param("updatedAt") LocalDateTime updatedAt);
}
