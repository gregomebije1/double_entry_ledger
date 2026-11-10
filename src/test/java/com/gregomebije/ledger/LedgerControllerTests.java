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
            {"accountNumber":"1001","name":"Alice wallet","type":"ASSET","currency":"USD"}""";
    private static final String TX_JSON = """
            {"description":"Fund Alice","idempotencyKey":"fund-alice-1","lines":[
              {"accountId":"d880a0fe-ce74-4877-98f9-73dbb2ffe46b","ledgerLineType":"DEBIT","debit":100000,"credit":0},
              {"accountId":"40eb54c1-291b-4986-a51e-00bf2e99a40e","ledgerLineType":"CREDIT","debit":0,"credit":100000}]}""";

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

    @Test @DisplayName("POST /ledgers/accounts -> no / malformed Authorization -> 401")
    void createAccount_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/ledgers/accounts").contentType(MediaType.APPLICATION_JSON).content(ACCOUNT_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/ledgers/accounts").header("Authorization", "Basic abc")
                        .contentType(MediaType.APPLICATION_JSON).content(ACCOUNT_JSON))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(ledgerService);
    }

    @Test @DisplayName("POST /ledgers/accounts -> Bearer -> 201")
    void createAccount_created() throws Exception {
        when(ledgerService.createAccount(any())).thenReturn("ACC-1");
        mockMvc.perform(post("/api/v1/ledgers/accounts").header("Authorization", "Bearer x")
                        .contentType(MediaType.APPLICATION_JSON).content(ACCOUNT_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().string("Account created: ACC-1"));
    }

    @Test @DisplayName("POST /ledgers/transactions -> no token -> 401")
    void postTx_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/ledgers/transactions").contentType(MediaType.APPLICATION_JSON).content(TX_JSON))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(ledgerService);
    }

    @Test @DisplayName("POST /ledgers/transactions -> Bearer -> 201")
    void postTx_created() throws Exception {
        when(ledgerService.postTransaction(any())).thenReturn("TX-1");
        mockMvc.perform(post("/api/v1/ledgers/transactions").header("Authorization", "Bearer x")
                        .contentType(MediaType.APPLICATION_JSON).content(TX_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().string("Ledger entry balance block recorded successfully: TX-1"));
    }

    @Test @DisplayName("POST /ledgers/transactions -> unbalanced -> 400")
    void postTx_unbalanced() throws Exception {
        when(ledgerService.postTransaction(any())).thenThrow(new UnbalancedLedgerException("unbalanced"));
        mockMvc.perform(post("/api/v1/ledgers/transactions").header("Authorization", "Bearer x")
                        .contentType(MediaType.APPLICATION_JSON).content(TX_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("unbalanced"));
    }

    @Test @DisplayName("POST /ledgers/transactions -> idempotency breach -> 409")
    void postTx_conflict() throws Exception {
        when(ledgerService.postTransaction(any())).thenThrow(new IllegalStateException("dup"));
        mockMvc.perform(post("/api/v1/ledgers/transactions").header("Authorization", "Bearer x")
                        .contentType(MediaType.APPLICATION_JSON).content(TX_JSON))
                .andExpect(status().isConflict());
    }
}