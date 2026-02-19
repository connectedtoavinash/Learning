@Override
public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
        WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

    String token = extractToken(request);

    if (token == null) {
        log.warn("No JWT token provided");
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    try {
        // Single call - validates AND extracts
        JwtClaims claims = jwtUtil.validateAndExtractClaims(token);

        attributes.put("userId", claims.getUserId());
        attributes.put("email", claims.getEmail());
        attributes.put("roles", claims.getRoles());
        attributes.put("orgId", claims.getOrgId());
        attributes.put("token", token);

        log.info("JWT validated for user: {}", claims.getUserId());
        return true;

    } catch (Exception e) {
        log.warn("JWT validation failed: {}", e.getMessage());
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }
}