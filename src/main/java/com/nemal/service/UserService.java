package com.nemal.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.nemal.dto.LoginDto;
import com.nemal.dto.LoginResponse;
import com.nemal.dto.ProfileUpdateDto;
import com.nemal.dto.UserDto;
import com.nemal.dto.UserRegistrationDto;
import com.nemal.entity.Department;
import com.nemal.entity.Designation;
import com.nemal.entity.User;
import com.nemal.enums.AuthProvider;
import com.nemal.enums.Role;
import com.nemal.repository.DepartmentRepository;
import com.nemal.repository.DesignationRepository;
import com.nemal.repository.UserRepository;
import com.nemal.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${google.client.id}")
    private String googleClientId;

    public UserService(UserRepository userRepository, DepartmentRepository departmentRepository,
                       DesignationRepository designationRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public LoginResponse register(UserRegistrationDto dto) {
        User user = User.builder()
                .email(dto.email())
                .passwordHash(passwordEncoder.encode(dto.password()))
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .role(dto.role())
                .build();
        userRepository.save(user);
        String token = jwtService.generateToken((UserDetails) user);
        return LoginResponse.from(token, user);
    }

    public LoginResponse authenticate(LoginDto dto) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.email(), dto.password())
        );
        User user = userRepository.findByEmail(dto.email()).orElseThrow();
        String token = jwtService.generateToken((UserDetails) user);
        return LoginResponse.from(token, user);
    }

    public LoginResponse authenticateGoogleUser(String idTokenString) {
        // 1. Validate the token with Google
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                String email = payload.getEmail();

                // 2. Check if user exists, if not, create them
                User user = userRepository.findByEmail(email)
                        .orElseGet(() -> registerNewGoogleUser(payload));

                // 3. Generate YOUR local JWT token (just like your normal login)
                String jwtToken = jwtService.generateToken(user);

                return new LoginResponse(jwtToken, user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole(),user.getProfilePictureUrl());
            }
        } catch (Exception e) {
            throw new RuntimeException("Invalid Google Token");
        }
        throw new RuntimeException("Authentication failed");
    }

    private User registerNewGoogleUser(GoogleIdToken.Payload payload) {
        // Extract info from Google payload
        String email = payload.getEmail();
        String firstName = (String) payload.get("given_name");
        String lastName = (String) payload.get("family_name");
        String pictureUrl = (String) payload.get("picture");

        User user = User.builder()
                .email(email)
                .firstName(firstName != null ? firstName : "GoogleUser")
                .lastName(lastName != null ? lastName : "")
                .profilePictureUrl(pictureUrl)
                .role(Role.INTERVIEWER) // Default role for new signups
                .isActive(true)
                .authProvider(AuthProvider.GOOGLE)
                .passwordHash(null) // Important: Google users have no initial password
                .build();

        return userRepository.save(user);
    }

    public UserDto createUser(UserDto dto, Role role) {
        User user = User.builder()
                .email(dto.email())
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .role(role)
                .build();
        userRepository.save(user);
        return UserDto.from(user);
    }

    public void deactivateUser(Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setIsActive(false);
        userRepository.save(user);
    }

    public User updateProfile(User user, ProfileUpdateDto dto) {
        user.setPhone(dto.phone());
        user.setProfilePictureUrl(dto.profilePictureUrl());
        if (dto.departmentId() != null) {
            Department dept = departmentRepository.findById(dto.departmentId()).orElseThrow();
            user.setDepartment(dept);
        }
        if (dto.designationId() != null) {
            Designation des = designationRepository.findById(dto.designationId()).orElseThrow();
            user.setCurrentDesignation(des);
        }
        return userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}