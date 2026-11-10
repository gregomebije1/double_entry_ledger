package com.gregomebije.ledger;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Requires Docker. Runs Flyway migrations on Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LedgerSystemIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired MockMvc mockMvc;

    static String token1, token2;
    static String alice, bob, bWallet;

    // ---------- helpers ----------
    private void register(String email, String tenantId, String tenantName) throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/user").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s","password":"password","firstName":"F","lastName":"L"}""".formatted(email)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/onboarding/tenant").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tenantId":"%s","tenantName":"%s","email":"%s"}""".formatted(tenantId, tenantName, email)))
                .andExpect(status().isCreated());
    }

    private String login(String tenantId, String email) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/onboarding/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tenantId":"%s","email":"%s","password":"password"}""".formatted(tenantId, email)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").exists()).andReturn();
        return "Bearer " + JsonPath.<String>read(r.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createAccount(String token, String number, String name, String type, String ccy) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/ledgers/accounts").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"accountNumber":"%s","name":"%s","type":"%s","currency":"%s"}""".formatted(number, name, type, ccy)))
                .andExpect(status().isCreated()).andReturn();
        return r.getResponse().getContentAsString().replace("Account created: ", "").trim();
    }

    private static String tx(String key, String desc, String debitAcc, long debit, String creditAcc, long credit) {
        return """
            {"description":"%s","idempotencyKey":"%s","lines":[
              {"accountId":"%s","ledgerLineType":"DEBIT","debit":%d,"credit":0},
              {"accountId":"%s","ledgerLineType":"CREDIT","debit":0,"credit":%d}]}""".formatted(desc, key, debitAcc, debit, creditAcc, credit);
    }

    private ResultActions postTx(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/ledgers/transactions").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---------- tests ----------
    @Test @Order(1) @DisplayName("Tenant 1: register user, tenant, login, create accounts")
    void tenant1Onboarding() throws Exception {
        register("johndoe@mangital.com", "tenant-1", "Mangital ltd");
        token1 = login("tenant-1", "johndoe@mangital.com");
        alice = createAccount(token1, "1001", "Alice wallet", "ASSET", "USD");
        bob = createAccount(token1, "1002", "Bob wallet", "ASSET", "USD");
        createAccount(token1, "3001", "Equity (USD)", "EQUITY", "USD");
    }

    @Test @Order(2) @DisplayName("Auth: missing bearer token -> 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/ledgers/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"accountNumber":"9","name":"x","type":"ASSET","currency":"USD"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(3) @DisplayName("Invariant 1: unbalanced transaction rejected; balanced accepted")
    void doubleEntry() throws Exception {
        postTx(token1, tx("unbalanced-1", "bad", alice, 100000, bob, 50000)).andExpect(status().isBadRequest());
        postTx(token1, tx("fund-alice-1", "Fund Alice", alice, 100000, bob, 100000)).andExpect(status().isCreated());
    }

    @Test @Order(4) @DisplayName("Invariant 2: rejected transaction left nothing behind (key reusable)")
    void rejectedPostingLeavesNoTrace() throws Exception {
        // "unbalanced-1" failed earlier; if a header had leaked, this would now be 409.
        postTx(token1, tx("unbalanced-1", "now balanced", alice, 500, bob, 500)).andExpect(status().isCreated());
    }

    @Test @Order(5) @DisplayName("Invariant 7: replaying an idempotency key does not create a second posting")
    void idempotency() throws Exception {
        // Current implementation rejects every replay with 409.
        postTx(token1, tx("fund-alice-1", "Fund Alice", alice, 100000, bob, 100000)).andExpect(status().isConflict());
        // Same key, different payload must also be rejected.
        postTx(token1, tx("fund-alice-1", "Different", alice, 7, bob, 7)).andExpect(status().isConflict());
    }

    @Test @Order(6) @DisplayName("Invariant 6: tenant 2 cannot touch tenant 1's accounts; same account numbers allowed")
    void tenantIsolation() throws Exception {
        register("janeme@livinghope.com.ng", "tenant-2", "Living Hope ltd");
        token2 = login("tenant-2", "janeme@livinghope.com.ng");
        bWallet = createAccount(token2, "1001", "B wallet", "ASSET", "USD"); // same number as tenant 1: OK
        String bEquity = createAccount(token2, "3001", "B equity", "EQUITY", "USD");

        postTx(token2, tx("illegal-cross-tx", "cross", alice, 5000, bWallet, 5000)).andExpect(status().isBadRequest());
        postTx(token2, tx("legit-t2", "own accounts", bWallet, 5000, bEquity, 5000)).andExpect(status().isCreated());
    }

    @Test @Order(7) @DisplayName("Login with wrong tenant or password -> 400")
    void badLogin() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tenantId":"tenant-2","email":"johndoe@mangital.com","password":"password"}"""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/onboarding/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tenantId":"tenant-1","email":"johndoe@mangital.com","password":"wrong"}"""))
                .andExpect(status().isBadRequest());
    }
}