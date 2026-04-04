package com.wwt.assistant.dto.session.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SessionMessageCitationItem {
    private String documentId;
    private String documentName;
    private Integer chunkIndex;
    private String snippet;
}
