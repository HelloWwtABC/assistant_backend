package com.wwt.assistant.service.impl;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.dto.auth.request.LoginRequest;
import com.wwt.assistant.dto.auth.response.CurrentUserResponse;
import com.wwt.assistant.dto.auth.response.LoginResponse;
import com.wwt.assistant.entity.SysUser;
import com.wwt.assistant.mapper.SysUserMapper;
import com.wwt.assistant.service.AuthService;
import com.wwt.assistant.utils.JWTUtil;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String STATUS_ACTIVE = "active";

    private final SysUserMapper sysUserMapper;
    private final JWTUtil jwtUtil;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public ApiResponse<LoginResponse> login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            return ApiResponse.fail(400, "用户名或密码不能为空");
        }

        String username = request.getUsername().trim();
        SysUser user = sysUserMapper.findActiveByUsername(username);
        if (user == null || !matchesPassword(request.getPassword(), user.getPasswordHash())) {
            return ApiResponse.fail(401, "用户名或密码错误");
        }

        if (!STATUS_ACTIVE.equalsIgnoreCase(user.getStatus())) {
            return ApiResponse.fail(423, "账号已被禁用");
        }

        LocalDateTime loginTime = LocalDateTime.now();
        sysUserMapper.updateLoginSuccess(user.getId(), loginTime);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getTeamId());
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setRefreshToken(token);
        response.setUser(buildCurrentUser(user));

        return ApiResponse.success(response);
    }

    private CurrentUserResponse buildCurrentUser(SysUser user) {
        CurrentUserResponse currentUser = new CurrentUserResponse();
        currentUser.setId(user.getId() == null ? null : String.valueOf(user.getId()));
        currentUser.setName(user.getName());
        currentUser.setEmail(user.getEmail());
        currentUser.setRole(user.getRole());
        return currentUser;
    }

    private boolean matchesPassword(String rawPassword, String storedPasswordHash) {
        if (!StringUtils.hasText(storedPasswordHash)) {
            return false;
        }
        String normalizedHash = storedPasswordHash.trim();
        return passwordEncoder.matches(rawPassword, normalizedHash);
    }

}
