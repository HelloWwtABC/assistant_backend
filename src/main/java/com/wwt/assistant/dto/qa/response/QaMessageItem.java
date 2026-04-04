package com.wwt.assistant.dto.qa.response;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class QaMessageItem {
    private String messageId;
    private String role;
    private String content;
    private String createdAt;
    private List<QaCitationItem> citations;
}
