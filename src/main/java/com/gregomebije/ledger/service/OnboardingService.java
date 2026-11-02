package com.gregomebije.ledger.service;

import com.gregomebije.ledger.dto.Models.LoginRequestDto;
import com.gregomebije.ledger.dto.Models.TenantRegistrationDto;
import com.gregomebije.ledger.security.JwtProvider;
import com.gregomebije.ledger.model.Tenant;
import com.gregomebije.ledger.model.User;
import com.gregomebije.ledger.repository.TenantRepository;
import com.gregomebije.ledger.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public OnboardingService(TenantRepository tenantRepository, UserRepository userRepository, 
                             PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public void registerTenant(TenantRegistrationDto dto) {
        if (tenantRepository.existsById(dto.tenantId())) {
            throw new IllegalArgumentException("Tenant ID registration identifier conflict.");
        }

        Tenant tenant = Tenant.builder()
                .id(dto.tenantId())
                .name(dto.tenantName())
                .build();
        // Capture the managed instance returned by JPA
        Tenant savedTenant = tenantRepository.save(tenant);
        
        User admin = User.builder()
                .tenant(savedTenant)
                .email(dto.adminEmail())
                .passwordHash(passwordEncoder.encode(dto.adminPassword()))
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .build();
        userRepository.save(admin);
        
    }

    @Transactional(readOnly = true)
    public String login(LoginRequestDto dto) {
        // Enforce safe isolation constraints directly at authentication boundaries
        User user = userRepository.findByTenantIdAndEmail(dto.tenantId(), dto.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials or tenant mismatch."));

        if (!passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials or tenant mismatch.");
        }

        return jwtProvider.generateToken(user.getTenant().getId(), user.getEmail());
    }
}
