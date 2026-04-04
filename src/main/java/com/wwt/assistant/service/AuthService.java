package com.wwt.assistant.service;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.dto.auth.request.LoginRequest;
import com.wwt.assistant.dto.auth.response.LoginResponse;

public interface AuthService {

    ApiResponse<LoginResponse> login(LoginRequest request);
}
