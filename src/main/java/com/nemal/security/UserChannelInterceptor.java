package com.nemal.security;

import com.nemal.entity.User;
import com.nemal.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class UserChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(UserChannelInterceptor.class);
    private static final String NOTIFICATIONS_QUEUE = "/user/queue/notifications";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public UserChannelInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(command)) {
            return authenticateConnect(accessor) ? message : null;
        }

        Principal user = accessor.getUser();
        if (user == null || user.getName() == null || user.getName().isBlank()) {
            logger.warn("STOMP {} rejected: missing authenticated user", command);
            return null;
        }

        if (StompCommand.SUBSCRIBE.equals(command) && !isAllowedSubscribe(accessor.getDestination(), user.getName())) {
            logger.warn("STOMP SUBSCRIBE rejected for {} to {}", user.getName(), accessor.getDestination());
            return null;
        }

        if (StompCommand.SEND.equals(command) && !isAllowedSend(accessor.getDestination())) {
            logger.warn("STOMP SEND rejected for {} to {}", user.getName(), accessor.getDestination());
            return null;
        }

        return message;
    }

    private boolean authenticateConnect(StompHeaderAccessor accessor) {
        String authToken = accessor.getFirstNativeHeader("Authorization");
        if (authToken == null || !authToken.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }

        String jwt = authToken.substring(7).trim();
        if (jwt.isEmpty()) {
            return false;
        }

        try {
            String email = jwtService.extractUsername(jwt);
            if (email == null || email.isBlank()) {
                return false;
            }
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null || !user.isEnabled() || !jwtService.isTokenValid(jwt, user)) {
                return false;
            }
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    email,
                    null,
                    user.getAuthorities());
            accessor.setUser(auth);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            logger.warn("STOMP CONNECT JWT rejected: {}", ex.getMessage());
            return false;
        }
    }

    private boolean isAllowedSubscribe(String destination, String username) {
        if (destination == null || username == null) {
            return false;
        }
        if (NOTIFICATIONS_QUEUE.equals(destination)) {
            return true;
        }
        String personalQueue = "/user/" + username + "/queue/notifications";
        return personalQueue.equals(destination);
    }

    private boolean isAllowedSend(String destination) {
        return destination != null && destination.startsWith("/app/");
    }
}
