package com.wwt.assistant.dto.tool.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateTicketResponse {
    private String ticketId;
    private String createStatus;
    private String submittedAt;
    private String assignee;
    private String processStatus;
}
