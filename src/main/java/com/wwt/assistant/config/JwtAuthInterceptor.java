package com.wwt.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.common.CurrentUser;
import com.wwt.assistant.common.UserContextHolder;
import com.wwt.assistant.utils.JWTUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    private final JWTUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod) || HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String requestPath = getRequestPath(request);
        if (isWhitelisted(requestPath)) {
            return true;
        }

        String token = jwtUtil.normalizeToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "未登录或登录已过期");
            return false;
        }

        if (!jwtUtil.validateToken(token) || jwtUtil.isTokenExpired(token)) {
            writeUnauthorized(response, "未登录或登录已过期");
            return false;
        }

        Long userId = jwtUtil.getUserId(token);
        Long teamId = jwtUtil.getTeamId(token);
        String username = jwtUtil.getUsername(token);
        if (userId == null || !StringUtils.hasText(username)) {
            writeUnauthorized(response, "登录凭证无效");
            return false;
        }

        UserContextHolder.setCurrentUser(new CurrentUser(userId, username, teamId));
        refreshTokenIfNecessary(response, token, userId, teamId, username);
        return true;
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex) {
        UserContextHolder.clear();
    }

    private boolean isWhitelisted(String requestPath) {
        List<String> whitelist = jwtProperties.getWhitelist();
        if (CollectionUtils.isEmpty(whitelist)) {
            return false;
        }
        for (String pattern : whitelist) {
            if (StringUtils.hasText(pattern) && antPathMatcher.match(pattern, requestPath)) {
                return true;
            }
        }
        return false;
    }

    private String getRequestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private void refreshTokenIfNecessary(
            HttpServletResponse response,
            String token,
            Long userId,
            Long teamId,
            String username) {
        Date expiration = jwtUtil.getExpiration(token);
        if (expiration == null) {
            return;
        }

        long remainingMillis = expiration.getTime() - System.currentTimeMillis();
        long refreshThresholdMillis = jwtProperties.getRefreshThresholdSeconds() * 1000L;
        if (remainingMillis <= 0 || refreshThresholdMillis <= 0 || remainingMillis > refreshThresholdMillis) {
            return;
        }

        String refreshedToken = jwtUtil.generateToken(userId, username, teamId);
        response.setHeader(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + refreshedToken);
        response.setHeader(EXPOSE_HEADERS, HttpHeaders.AUTHORIZATION);
        log.debug("JWT token refreshed for userId={}", userId);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(HttpServletResponse.SC_UNAUTHORIZED, message));
    }
}
