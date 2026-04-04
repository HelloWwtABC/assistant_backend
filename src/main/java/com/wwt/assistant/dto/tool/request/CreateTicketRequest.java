package com.wwt.assistant.dto.tool.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateTicketRequest {
    private String title;
    private String priority;
    private String description;
}
