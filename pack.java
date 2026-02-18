package org.samsung.api.DeviceControl.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.samsung.api.DeviceControl.util.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        // 1. Extract JWT from Authorization header
        String token = extractTokenFromHeader(request);

        if (token == null) {
            log.warn("No JWT token in Authorization header");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // 2. Decode JWT and extract userId
        Optional<String> userIdOpt = jwtUtil.extractUserId(token);

        if (userIdOpt.isEmpty()) {
            log.warn("Could not extract userId from JWT");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // 3. Store userId in session attributes (will be available in afterConnectionEstablished)
        String userId = userIdOpt.get();
        attributes.put("userId", userId);

        log.info("JWT decoded successfully. UserId: {}", userId);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // Nothing needed here
    }

    private String extractTokenFromHeader(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String authHeader = servletRequest.getServletRequest().getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }
        return null;
    }
}