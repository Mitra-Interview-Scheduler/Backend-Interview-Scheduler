package com.nemal.security;

import com.nemal.entity.User;
import com.nemal.repository.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class UserChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public UserChannelInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authToken = accessor.getFirstNativeHeader("Authorization");
            if (authToken == null || !authToken.startsWith("Bearer ")) {
                return null;
            }

            try {
                String jwt = authToken.substring(7).trim();
                String email = jwtService.extractUsername(jwt);
                if (email == null || email.isBlank()) {
                    return null;
                }
                User user = userRepository.findByEmail(email).orElse(null);
                if (user == null || !user.isEnabled() || !jwtService.isTokenValid(jwt, user)) {
                    return null;
                }

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        user.getAuthorities());
                accessor.setUser(auth);
            } catch (RuntimeException ex) {
                return null;
            }
        } else if (StompCommand.SEND.equals(accessor.getCommand())
                || StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                || StompCommand.UNSUBSCRIBE.equals(accessor.getCommand())) {
            if (accessor.getUser() == null) {
                return null;
            }
        }
        return message;
    }
}




