package com.wwt.assistant.dto.tool.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TicketStatusResponse {
    private String ticketId;
    private String title;
    private String status;
    private String priority;
    private String owner;
    private String updatedAt;
    private String summary;
}
