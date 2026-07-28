package com.nemal.config;

import com.nemal.repository.UserRepository;
import com.nemal.security.JwtAuthenticationFilter;
import com.nemal.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final String allowedOrigins;

    public SecurityConfig(
            JwtService jwtService,
            UserRepository userRepository,
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}") String allowedOrigins
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/google").permitAll()
                        .requestMatchers("/api/integrations/google-calendar/callback").permitAll()
                        .requestMatchers("/api/auth/verify").authenticated()
                        .requestMatchers("/ws/**", "/ws").permitAll()

                        .requestMatchers("/api/debug/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Candidates: HR/ADMIN for lists, documents, mutations, close; single GET for interviewers
                        .requestMatchers(HttpMethod.GET, "/api/candidates/coordinated-hr-options").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/candidates").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/candidates/search").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/candidates/department/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/candidates/status/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/candidates/*/documents").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/candidates/*/documents/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/candidates/*/documents/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/candidates/*/documents", "/api/candidates/*/documents/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/candidates/*/close").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/candidates").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/candidates/*/technologies").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/candidates/*/technologies/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/candidates/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/candidates/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/candidates/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")

                        .requestMatchers("/api/candidateScreening/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("/api/hr/**").hasAnyRole("HR", "ADMIN")

                        .requestMatchers("/api/interviewer/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers("/api/interview-requests/upcoming").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers("/api/interview-requests/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")

                        .requestMatchers("/api/availability/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")

                        // Master data writes: ADMIN for updates/deletes; create allowed for interviewers
                        .requestMatchers(HttpMethod.POST, "/api/departments").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/designations", "/api/designations/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/designations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/designations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/designations/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/tiers", "/api/tiers/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/tiers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/tiers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/tiers/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/technologies", "/api/technologies/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/technologies/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/technologies/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/technologies/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/technology-categories", "/api/technology-categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/technology-categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/technology-categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/technology-categories/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/question-categories", "/api/question-categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/question-categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/question-categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/question-categories/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/domains", "/api/domains/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/domains/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/domains/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/domains/**").authenticated()

                        .requestMatchers("/api/notifications/**").authenticated()

                        .requestMatchers("/api/profile/**").authenticated()
                        .requestMatchers("/api/integrations/google-calendar/**").authenticated()
                        .requestMatchers("/api/departments/**").authenticated()
                        .requestMatchers("/api/department/**").authenticated()
                        .requestMatchers("/api/masterSteps/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers("/api/closing-reasons/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/candidatePipeline/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("/api/candidatePipeline/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/feedback/questions").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/feedback/forms", "/api/feedback/forms/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/feedback/candidateforms").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/feedback/obligatory-questions", "/api/feedback/obligatory-questions/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/feedback/obligatory-questions").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/feedback/obligatory-questions/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/feedback/obligatory-questions/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/feedback/responses").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/feedback/responses/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/feedback/forms", "/api/feedback/forms/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/feedback/forms/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/feedback/forms/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/feedback/forms/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/feedback/questions/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/feedback/questions/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/feedback/questions/**").hasAnyRole("HR", "ADMIN")

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Access denied\"}");
                        })
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(jwtAuthenticationFilter(userDetailsService()), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toList()
        );
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(UserDetailsService userDetailsService) {
        return new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(authProvider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
