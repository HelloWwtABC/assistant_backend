package com.wwt.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wwt.assistant.entity.ChatMessage;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("""
            SELECT
                CAST(cm.id AS CHAR) AS messageId,
                cm.role AS role,
                COALESCE(cm.content, '') AS content,
                DATE_FORMAT(cm.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt,
                COALESCE(cm.citations_json, rl.citations_json) AS citationsJson
            FROM chat_message cm
            LEFT JOIN request_log rl
                ON rl.id = cm.request_log_id
               AND rl.deleted_at IS NULL
            WHERE cm.deleted_at IS NULL
              AND cm.session_id = #{sessionId}
            ORDER BY cm.created_at ASC, cm.id ASC
            """)
    List<QaMessageRecord> selectQaMessagesBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT
                cm.id AS messageId,
                cm.role AS role,
                COALESCE(cm.content, '') AS content,
                cm.created_at AS createdAt
            FROM chat_message cm
            WHERE cm.deleted_at IS NULL
              AND cm.session_id = #{sessionId}
            ORDER BY cm.created_at DESC, cm.id DESC
            LIMIT #{limit}
            """)
    List<RecentMessageRecord> selectRecentMessages(
            @Param("sessionId") Long sessionId,
            @Param("limit") Integer limit);

    class QaMessageRecord {
        private String messageId;
        private String role;
        private String content;
        private String createdAt;
        private String citationsJson;

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getCitationsJson() {
            return citationsJson;
        }

        public void setCitationsJson(String citationsJson) {
            this.citationsJson = citationsJson;
        }
    }

    class RecentMessageRecord {
        private Long messageId;
        private String role;
        private String content;
        private LocalDateTime createdAt;

        public Long getMessageId() {
            return messageId;
        }

        public void setMessageId(Long messageId) {
            this.messageId = messageId;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}
