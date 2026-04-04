package com.wwt.assistant.service.impl;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.dto.auth.request.LoginRequest;
import com.wwt.assistant.dto.auth.response.CurrentUserResponse;
import com.wwt.assistant.dto.auth.response.LoginResponse;
import com.wwt.assistant.entity.SysUser;
import com.wwt.assistant.mapper.SysUserMapper;
import com.wwt.assistant.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String STATUS_ACTIVE = "active";

    private final SysUserMapper sysUserMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public ApiResponse<LoginResponse> login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            return ApiResponse.fail(400, "用户名或密码不能为空");
        }

        SysUser user = sysUserMapper.findActiveByUsername(request.getUsername().trim());
        if (user == null || !matchesPassword(request.getPassword(), user.getPasswordHash())) {
            return ApiResponse.fail(401, "用户名或密码错误");
        }

        if (!STATUS_ACTIVE.equalsIgnoreCase(user.getStatus())) {
            return ApiResponse.fail(423, "账号已被禁用");
        }

        LocalDateTime loginTime = LocalDateTime.now();
        sysUserMapper.updateLoginSuccess(user.getId(), loginTime);

        LoginResponse response = new LoginResponse();
        response.setToken(generateToken());
        response.setRefreshToken(generateToken());

        CurrentUserResponse currentUser = new CurrentUserResponse();
        BeanUtils.copyProperties(user, currentUser);
        response.setUser(currentUser);

        return ApiResponse.success(response);
    }


    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean matchesPassword(String rawPassword, String storedPasswordHash) {
        if (!StringUtils.hasText(storedPasswordHash)) {
            return false;
        }

        String normalizedHash = storedPasswordHash.trim();
        if (normalizedHash.startsWith("{noop}")) {
            return rawPassword.equals(normalizedHash.substring("{noop}".length()));
        }
        if (normalizedHash.startsWith("{bcrypt}")) {
            return passwordEncoder.matches(rawPassword, normalizedHash.substring("{bcrypt}".length()));
        }
        if (isBcryptHash(normalizedHash)) {
            return passwordEncoder.matches(rawPassword, normalizedHash);
        }
        if (isHexHash(normalizedHash, 64)) {
            return sha256Hex(rawPassword).equalsIgnoreCase(normalizedHash);
        }
        if (isHexHash(normalizedHash, 32)) {
            return DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8))
                    .equalsIgnoreCase(normalizedHash);
        }
        return rawPassword.equals(normalizedHash);
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private boolean isHexHash(String value, int expectedLength) {
        if (value.length() != expectedLength) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char current = lower.charAt(i);
            if (!((current >= '0' && current <= '9') || (current >= 'a' && current <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}
