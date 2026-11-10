package com.gregomebije.ledger;

import com.gregomebije.ledger.service.*;

import com.gregomebije.ledger.dto.Models.*;
import com.gregomebije.ledger.model.Tenant;
import com.gregomebije.ledger.model.User;
import com.gregomebije.ledger.model.UserTenant;
import com.gregomebije.ledger.repository.TenantRepository;
import com.gregomebije.ledger.repository.UserRepository;
import com.gregomebije.ledger.repository.UserTenantRepository;
import com.gregomebije.ledger.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock TenantRepository tenantRepository; //fake stand-ins (mocks)
    @Mock UserRepository userRepository;
    @Mock UserTenantRepository userTenantRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @InjectMocks OnboardingService service;

    @Test @DisplayName("registerUser -> duplicate email -> IllegalArgumentException")
    void registerUser_duplicate() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.registerUser(new CreateUserRequest("a@b.com", "pw", "J", "D")));
        verify(userRepository, never()).save(any());
    }

    @Test @DisplayName("registerUser -> stores hashed password, never the plain one")
    void registerUser_success() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(passwordEncoder.encode("plain")).thenReturn("hashed");

        service.registerUser(new CreateUserRequest("a@b.com", "plain", "J", "D"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed", captor.getValue().getPasswordHash());
        assertEquals("a@b.com", captor.getValue().getEmail());
    }

   @Test @DisplayName("registerTenant -> unknown user -> IllegalArgumentException")
    void registerTenant_noUser() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.registerTenant(new CreateTenantRequest("a@b.com", "t-1", "T")));
        verify(tenantRepository, never()).save(any());
    }

    @Test @DisplayName("registerTenant -> tenant id taken -> IllegalArgumentException")
    void registerTenant_conflict() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);
        when(tenantRepository.existsById("t-1")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.registerTenant(new CreateTenantRequest("a@b.com", "t-1", "T")));
        verify(tenantRepository, never()).save(any());
    }

    @Test @DisplayName("registerTenant -> saves tenant and user-tenant link")
    void registerTenant_success() {
        Tenant saved = Tenant.builder().id("t-1").name("T").build();
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);
        when(tenantRepository.existsById("t-1")).thenReturn(false);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(new User()));
        when(tenantRepository.save(any(Tenant.class))).thenReturn(saved);

        service.registerTenant(new CreateTenantRequest("a@b.com", "t-1", "T"));

        verify(tenantRepository).save(any(Tenant.class));
        verify(userTenantRepository).save(any(UserTenant.class));
    }
    @Test @DisplayName("login -> no membership -> IllegalArgumentException")
    void login_noMembership() {
        when(userTenantRepository.findByTenantIdAndEmail("t-1", "a@b.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.login(new LoginRequest("t-1", "a@b.com", "pw")));
        verifyNoInteractions(jwtProvider);
    }

    @Test @DisplayName("login -> wrong password -> IllegalArgumentException, no token")
    void login_wrongPassword() {
        User user = User.builder().email("a@b.com").passwordHash("hashed").build();
        UserTenant ut = UserTenant.builder().user(user).tenant(Tenant.builder().id("t-1").build()).build();
        when(userTenantRepository.findByTenantIdAndEmail("t-1", "a@b.com")).thenReturn(Optional.of(ut));
        when(passwordEncoder.matches("bad", "hashed")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.login(new LoginRequest("t-1", "a@b.com", "bad")));
        verifyNoInteractions(jwtProvider);
    }

    @Test @DisplayName("login -> success -> returns JWT scoped to tenant")
    void login_success() {
        User user = User.builder().email("a@b.com").passwordHash("hashed").build();
        UserTenant ut = UserTenant.builder().user(user).tenant(Tenant.builder().id("t-1").build()).build();
        when(userTenantRepository.findByTenantIdAndEmail("t-1", "a@b.com")).thenReturn(Optional.of(ut));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(true);
        when(jwtProvider.generateToken("t-1", "a@b.com")).thenReturn("jwt");

        assertEquals("jwt", service.login(new LoginRequest("t-1", "a@b.com", "pw")));
    }
}