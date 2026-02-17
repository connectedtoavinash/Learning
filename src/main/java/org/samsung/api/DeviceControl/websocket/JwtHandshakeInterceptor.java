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

        String token = extractToken(request);

        if (token == null) {
            log.warn("No JWT token provided in WebSocket handshake");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        if (!jwtUtil.isTokenValid(token)) {
            log.warn("Invalid or expired JWT token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // Extract userId and store in session attributes
        return jwtUtil.extractUserId(token)
                .map(userId -> {
                    attributes.put("userId", userId);
                    attributes.put("token", token);
                    log.info("JWT validated for user: {}", userId);
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("userId not found in JWT claims");
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                });
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // Can add post-handshake logic here if needed
    }

    private String extractToken(ServerHttpRequest request) {
        // Method 1: From query parameter
        // ws://localhost:8080/ws?token=xxx
        String query = request.getURI().getQuery();
        if (query != null && query.contains("token=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }

        // Method 2: From Authorization header (if using ServletServerHttpRequest)
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String authHeader = servletRequest.getServletRequest()
                    .getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
        }

        // Method 3: From custom header
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getHeader("X-Auth-Token");
        }

        return null;
    }
}
