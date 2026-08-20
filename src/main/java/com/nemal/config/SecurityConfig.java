package com.nemal.config;

import com.nemal.repository.UserRepository;
import com.nemal.security.CsrfCookieFilter;
import com.nemal.security.JwtAuthenticationFilter;
import com.nemal.security.JwtService;
import com.nemal.security.RateLimitFilter;
import com.nemal.security.SpaCsrfTokenRequestHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
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
    private final RateLimitFilter rateLimitFilter;
    private final String allowedOrigins;
    private final String cspPolicy;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public SecurityConfig(
            JwtService jwtService,
            UserRepository userRepository,
            RateLimitFilter rateLimitFilter,
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}") String allowedOrigins,
            @Value("${security.headers.csp:default-src 'self'}") String cspPolicy,
            @Value("${auth.refresh-token.secure:false}") boolean cookieSecure,
            @Value("${auth.refresh-token.same-site:Strict}") String cookieSameSite
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.rateLimitFilter = rateLimitFilter;
        this.allowedOrigins = allowedOrigins;
        this.cspPolicy = cspPolicy;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers("/ws/**", "/ws")
                )
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(cspPolicy))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=()"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Avoid 401-on-missing-route: error dispatch has no JWT and would mask 404 as auth failure.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/google",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/csrf"
                        ).permitAll()
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
                        .requestMatchers(HttpMethod.PUT, "/api/departments/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/departments/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/departments", "/api/departments/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/designations", "/api/designations/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/designations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/designations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/designations/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/tiers", "/api/tiers/**").hasAnyRole("INTERVIEWER", "HR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/tiers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/tiers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/tiers/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/interview-types", "/api/interview-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/interview-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/interview-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/interview-types", "/api/interview-types/**").authenticated()

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

                        .requestMatchers(HttpMethod.POST, "/api/document-types", "/api/document-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/document-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/document-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/document-types", "/api/document-types/**").hasAnyRole("HR", "ADMIN", "INTERVIEWER")

                        .requestMatchers(HttpMethod.POST, "/api/resource-types", "/api/resource-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/resource-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/resource-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/resource-types", "/api/resource-types/**").hasAnyRole("HR", "ADMIN", "INTERVIEWER")

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
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(jwtAuthenticationFilter(userDetailsService()), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .sameSite(cookieSameSite)
                .secure(cookieSecure)
                .path("/"));
        return repository;
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
        // Authorization must be listed explicitly — "*" does not cover it in credentialed CORS.
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "X-Timezone",
                "X-XSRF-TOKEN"
        ));
        configuration.setExposedHeaders(List.of("Authorization"));
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
