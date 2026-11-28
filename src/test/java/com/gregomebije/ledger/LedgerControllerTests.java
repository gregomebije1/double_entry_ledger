package com.gregomebije.ledger;

import com.gregomebije.ledger.controller.LedgerController;
import com.gregomebije.ledger.exception.UnbalancedLedgerException;
import com.gregomebije.ledger.security.JwtProvider;
import com.gregomebije.ledger.service.LedgerService;
import com.gregomebije.ledger.service.OnboardingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LedgerController.class)
@AutoConfigureMockMvc(addFilters = false)
class LedgerControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean OnboardingService onboardingService;
    @MockitoBean LedgerService ledgerService;
    @MockitoBean JwtProvider jwtProvider; // in case your security filter bean needs it

    private static final String USER_JSON = """
            {"email":"johndoe@mangital.com","password":"password","firstName":"John","lastName":"Doe"}""";
    private static final String TENANT_JSON = """
            {"tenantId":"tenant-1","tenantName":"Mangital ltd","email":"johndoe@mangital.com"}""";
    private static final String LOGIN_JSON = """
            {"tenantId":"tenant-1","email":"johndoe@mangital.com","password":"password"}""";
    private static final String ACCOUNT_JSON = """
            {"accountCode":"1001","name":"Alice wallet","type":"ASSET","currency":"USD"}""";
    private static final String TX_JSON = """
            {"memo":"Fund Alice","idempotencyKey":"fund-alice-1","lines":[
              {"accountCode":"1001","subRef":"","debit":100000,"credit":0},
              {"accountCode":"1001","subRef":"","debit":0,"credit":100000}]}""";

    @Test @DisplayName("POST /onboarding/user -> 201")
    void registerUser_created() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/user").contentType(MediaType.APPLICATION_JSON).content(USER_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().string("User organization enrolled successfully"));
        verify(onboardingService).registerUser(any());
    }

    @Test @DisplayName("POST /onboarding/user -> duplicate email -> 400")
    void registerUser_duplicate() throws Exception {
        doThrow(new IllegalArgumentException("Email already exist.")).when(onboardingService).registerUser(any());
        mockMvc.perform(post("/api/v1/onboarding/user").contentType(MediaType.APPLICATION_JSON).content(USER_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already exist."));
    }

    @Test @DisplayName("POST /onboarding/tenant -> 201")
    void registerTenant_created() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/tenant").contentType(MediaType.APPLICATION_JSON).content(TENANT_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().string("Tenant added to user organization"));
    }

    @Test @DisplayName("POST /onboarding/login -> 200 + token")
    void login_ok() throws Exception {
        when(onboardingService.login(any())).thenReturn("jwt.token");
        mockMvc.perform(post("/api/v1/onboarding/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt.token"));
    }

    @Test @DisplayName("POST /ledger/accounts -> no / malformed Authorization -> 401")
    void createAccount_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/ledger/accounts").contentType(MediaType.APPLICATION_JSON).content(ACCOUNT_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/ledger/accounts").header("Authorization", "Basic abc")
                        .contentType(MediaType.APPLICATION_JSON).content(ACCOUNT_JSON))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(ledgerService); //asserts that the controller never called any method on LedgerService.
    }

    @Test @DisplayName("POST /ledger/accounts -> Bearer -> 201")
    void createAccount_created() throws Exception {
        when(ledgerService.createAccount(any())).thenReturn("ACC-1");
        mockMvc.perform(post("/api/v1/ledger/accounts").header("Authorization", "Bearer x")
                        .contentType(MediaType.APPLICATION_JSON).content(ACCOUNT_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().string("Account created: ACC-1"));
    }

    @Test @DisplayName("POST /ledger/entries -> no token -> 401")
    void postTx_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/ledger/entries").contentType(MediaType.APPLICATION_JSON).content(TX_JSON))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(ledgerService);
    }

    @Test @DisplayName("POST /ledger/entries -> Bearer -> 201")
    void postTx_created() throws Exception {
        when(ledgerService.postLedger(any())).thenReturn("TX-1");
        mockMvc.perform(post("/api/v1/ledger/entries").header("Authorization", "Bearer x")
                        .contentType(MediaType.APPLICATION_JSON).content(TX_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().string("Ledger entry balance block recorded successfully: TX-1"));
    }

    @Test @DisplayName("POST /ledger/entries -> unbalanced -> 400")
    void postTx_unbalanced() throws Exception {
        when(ledgerService.postLedger(any())).thenThrow(new UnbalancedLedgerException("unbalanced"));
        mockMvc.perform(post("/api/v1/ledger/entries").header("Authorization", "Bearer x")
                        .contentType(MediaType.APPLICATION_JSON).content(TX_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("unbalanced"));
    }

    @Test @DisplayName("POST /ledger/entries -> idempotency breach -> 409")
    void postTx_conflict() throws Exception {
        when(ledgerService.postLedger(any())).thenThrow(new IllegalStateException("dup"));
        mockMvc.perform(post("/api/v1/ledger/entries").header("Authorization", "Bearer x")
                        .contentType(MediaType.APPLICATION_JSON).content(TX_JSON))
                .andExpect(status().isConflict());
    }
}