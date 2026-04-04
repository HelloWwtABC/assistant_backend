package com.wwt.assistant.dto.auth.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CurrentUserResponse {
    private String id;
    private String name;
    private String email;
    private String role;
}
