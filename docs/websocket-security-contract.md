# MediChannel — WebSocket Security Contract (Member 4 Integration)

This document specifies the security requirements, protocols, and architectural contract for WebSocket and STOMP messaging in MediChannel, owned by **Member 4 (Real-time Queue & Chat)**.

---

## 1. Connection Endpoint & Handshake

- **Transport**: STOMP 1.2 over SockJS with fallback to native WebSockets.
- **Handshake Endpoint**: `/ws` (allowed in `SecurityConfig` via `.requestMatchers("/ws/**").permitAll()`).
- **Allowed Origins**: Configured dynamically via `${medichannel.cors.allowed-origin}` (default: `http://localhost:3000`).

---

## 2. Authentication Contract

Because WebSocket handshakes initiated by browsers cannot always pass custom HTTP Authorization headers in standard WebSocket APIs, **STOMP frame authentication** is enforced during the `CONNECT` phase:

1. **Client Header**: The client must supply the JWT access token in the STOMP `CONNECT` frame headers:
   ```stomp
   CONNECT
   accept-version:1.2
   heart-beat:10000,10000
   Authorization:Bearer <jwt-access-token>
   passcode:<jwt-access-token>
   ```
2. **ChannelInterceptor Authentication**:
   - Member 4 must register a `ChannelInterceptor` on the `clientInboundChannel` in `WebSocketConfig`.
   - In `preSend(Message<?> message, MessageChannel channel)`:
     ```java
     StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
     if (StompCommand.CONNECT.equals(accessor.getCommand())) {
         String authHeader = accessor.getFirstNativeHeader("Authorization");
         String token = null;
         if (authHeader != null && authHeader.startsWith("Bearer ")) {
             token = authHeader.substring(7);
         } else {
             token = accessor.getPasscode(); // SockJS fallback
         }
         if (token == null || token.isBlank()) {
             throw new AuthenticationCredentialsNotFoundException("Missing JWT authentication token");
         }
         UUID userId = jwtService.extractUserId(token);
         UserAccount user = userAccountRepository.findById(userId)
                 .filter(u -> u.isActive() && !u.isLocked(Instant.now()))
                 .orElseThrow(() -> new BadCredentialsException("Invalid or inactive user"));

         AuthenticatedUser principal = new AuthenticatedUser(user);
         UsernamePasswordAuthenticationToken authentication =
                 new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
         accessor.setUser(authentication);
     }
     ```

---

## 3. Topic Destination Subscriptions & Permissions

### 3.1 Public / Session Queue Topic
- **Destination**: `/topic/session.{sessionId}.queue`
- **Purpose**: Broadcasts current queue number updates (e.g., `nowServing: 5`).
- **Privacy Restriction (GDPR / Medical Privacy)**:
  - **NEVER** broadcast patient names, NICs, diagnoses, contact numbers, or medical reasons over public topics.
  - The payload must contain solely non-sensitive tracking numbers:
    ```json
    {
      "sessionId": "b9687e1f-13a8-4bb9-bf1b-c74b126d4001",
      "nowServing": 4,
      "totalWaiting": 8,
      "estimatedDelayMinutes": 0,
      "timestamp": "2026-11-16T15:30:00Z"
    }
    ```

### 3.2 Private Consultation Chat
- **Destination**: `/user/queue/chat.{sessionId}` (or `/queue/session.{sessionId}.chat`)
- **Purpose**: Real-time encrypted text consultation between doctor and patient.
- **Subscription Access Control**:
  - In `ChannelInterceptor.preSend`, when `StompCommand.SUBSCRIBE` is received:
    - Member 4 **MUST verify** that the authenticated user (`accessor.getUser()`) is either:
      1. The Doctor assigned to this `sessionId`, OR
      2. The Patient who has an active appointment for this `sessionId`.
    - If any other user (including `ADMIN` or `MANAGER`) attempts to subscribe or send a message, throw an `AccessDeniedException` immediately.
  - Chat messages persisted to database table `chat_messages` must have `body_encrypted` encrypted using the AES-256-GCM converter (`body` JPA field).

---

## 4. Disconnection & Cleanup

- When a client disconnects (`DISCONNECT` frame or TCP close), session tracking records and active subscriptions must be cleaned up gracefully.
