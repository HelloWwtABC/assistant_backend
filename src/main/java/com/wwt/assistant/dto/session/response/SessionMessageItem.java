package com.wwt.assistant.dto.session.response;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SessionMessageItem {
    private String messageId;
    private String role;
    private String content;
    private String createdAt;
    private List<SessionMessageCitationItem> citations;
}
