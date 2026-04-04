package com.wwt.assistant.controller;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.dto.auth.request.LoginRequest;
import com.wwt.assistant.dto.auth.response.CurrentUserResponse;
import com.wwt.assistant.dto.auth.response.LoginResponse;
import com.wwt.assistant.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/current-user")
    public ApiResponse<CurrentUserResponse> getCurrentUser() {
        return unsupported("get current user");
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return unsupported("auth logout");
    }

    private <T> T unsupported(String operation) {
        throw new UnsupportedOperationException("TODO: implement controller endpoint: " + operation);
    }
}
