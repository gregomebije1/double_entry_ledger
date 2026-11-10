package com.gregomebije.ledger.service;

import com.gregomebije.ledger.dto.Models.*;
import com.gregomebije.ledger.security.JwtProvider;
import com.gregomebije.ledger.model.*;
import com.gregomebije.ledger.repository.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OnboardingService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserTenantRepository userTenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public OnboardingService(TenantRepository tenantRepository, UserRepository userRepository, 
                             UserTenantRepository userTenantRepository,
                             PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.userTenantRepository = userTenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public void registerUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exist.");
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .build();
        userRepository.save(user);   
    }

    @Transactional
    public void registerTenant(CreateTenantRequest request) {
        if (!userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("No user associated with the email.");
        }
        if (tenantRepository.existsById(request.tenantId())) {
            throw new IllegalArgumentException("Tenant ID registration identifier conflict.");
        }

        Optional<User> user = userRepository.findByEmail(request.email());
        Tenant tenant = Tenant.builder()
                .id(request.tenantId())
                .name(request.tenantName())
                .build();
        // Capture the managed instance returned by JPA
        Tenant savedTenant = tenantRepository.save(tenant);
        
        UserTenant userTenant = UserTenant.builder()
                .user(user.get())
                .tenant(savedTenant)
                .build();
        userTenantRepository.save(userTenant);
    }

    @Transactional(readOnly = true)
    public String login(LoginRequest request) {
        // Enforce safe isolation constraints directly at authentication boundaries
        
        UserTenant userTenant = userTenantRepository.findByTenantIdAndEmail(request.tenantId(), request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials or tenant mismatch."));

        if (!passwordEncoder.matches(request.password(), userTenant.getUser().getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials or tenant mismatch.");
        }

        return jwtProvider.generateToken(userTenant.getTenant().getId(), userTenant.getUser().getEmail());
    }
}
