# Learning
package org.samsung.api.DeviceControl.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.samsung.api.DeviceControl.models.dto.BasePayload;
import org.samsung.api.DeviceControl.service.WebSocketService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler {

    private final WebSocketService webSocketService;
    private final ObjectMapper objectMapper;
    private final WebSocketMessageDispatcher dispatcher;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Connection Established: {}", session.getId());

        // Auto-register user from JWT claims (set by interceptor)
        String userId = (String) session.getAttributes().get("userId");
        
        if (userId != null) {
            webSocketService.registerUser(session, userId);
            log.info("User auto-registered from JWT: {}", userId);
        } else {
            log.warn("No userId found in session attributes for session: {}", 
                    session.getId());
        }
    }

    @Override
    protected void handleTextMessage(
            @NonNull WebSocketSession session, 
            TextMessage message) throws Exception {
        
        String jsonPayload = message.getPayload();
        try {
            BasePayload payload = objectMapper.readValue(jsonPayload, BasePayload.class);
            dispatcher.dispatch(payload, session);
        } catch (Exception e) {
            log.error("Failed to process WebSocket message: {}", jsonPayload, e);
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session, 
            CloseStatus status) {
        
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            webSocketService.closeWebsocketConnection(userId);
            log.info("Connection closed for user: {}, status: {}", userId, status);
        }
    }

    @Override
    public void handleTransportError(
            WebSocketSession session, 
            Throwable exception) {
        
        log.error("Transport error for session {}: {}", 
                session.getId(), exception.getMessage());
        
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            webSocketService.closeWebsocketConnection(userId);
        }
    }
}
