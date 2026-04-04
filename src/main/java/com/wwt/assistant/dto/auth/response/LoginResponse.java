package com.wwt.assistant.dto.auth.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginResponse {
    private String token;
    private String refreshToken;
    private CurrentUserResponse user;
}
