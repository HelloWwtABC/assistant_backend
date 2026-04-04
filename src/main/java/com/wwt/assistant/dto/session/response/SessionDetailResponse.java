package com.wwt.assistant.dto.session.response;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SessionDetailResponse extends SessionRecordItem {
    private List<SessionMessageItem> messages;
}
