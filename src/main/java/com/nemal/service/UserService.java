package com.nemal.service;

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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserSettingsService userSettingsService;

    public UserService(UserRepository userRepository, DepartmentRepository departmentRepository,
                       DesignationRepository designationRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthenticationManager authenticationManager,
                       UserSettingsService userSettingsService) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userSettingsService = userSettingsService;
    }

    public LoginResponse register(UserRegistrationDto dto) {
        User user = User.builder()
                .email(dto.email())
                .passwordHash(passwordEncoder.encode(dto.password()))
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .roles(dto.roles())
                .authProvider(AuthProvider.LOCAL)
                .build();
        userRepository.save(user);
        String token = jwtService.generateToken(user);
        return LoginResponse.from(token, user);
    }

    public LoginResponse authenticate(LoginDto dto, String browserTimezone) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.email(), dto.password())
        );
        User user = userRepository.findByEmail(dto.email()).orElseThrow();
        userSettingsService.ensureSettingsOnFirstLogin(user, browserTimezone);
        String token = jwtService.generateToken(user);
        return LoginResponse.from(token, user);
    }


    public UserDto createUser(UserDto dto, Role role) {
        User user = User.builder()
                .email(dto.email())
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .roles(Set.of(role))
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