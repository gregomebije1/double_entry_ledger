package com.gregomebije.ledger.api;

import com.gregomebije.ledger.core.LedgerService;
import com.gregomebije.ledger.core.UnbalancedLedgerException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LedgerController.class)
class LedgerControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean LedgerService ledgerService;

    @Test
    void createAccountShouldReturnCreated() throws Exception {
        mockMvc.perform(post("/api/v1/ledgers/accounts")
                .header("Authorization", "Bearer tenant-a-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"account_id":"account-a","currency":"USD"}
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    void balancedTransactionShouldReturnCreated() throws Exception {
        mockMvc.perform(post("/api/v1/ledgers/transactions")
                .header("Authorization", "Bearer tenant-a-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "narration":"P2P",
                          "lines":[
                            {"accountId":"a","type":"DEBIT","amount":2500},
                            {"accountId":"b","type":"CREDIT","amount":2500}
                          ]
                        }
                        """))
                .andExpect(status().isCreated());

        verify(ledgerService).postTransaction(
                anyString(), any());
    }

    @Test
    void missingAuthorizationShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/ledgers/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"account_id":"account-a","currency":"USD"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unbalancedTransactionShouldBeRepresentedAsServerFailureUntilExceptionHandlerIsAdded()
            throws Exception {
        doThrow(new UnbalancedLedgerException("unbalanced"))
                .when(ledgerService)
                .postTransaction(anyString(), any());

        mockMvc.perform(post("/api/v1/ledgers/transactions")
                .header("Authorization", "Bearer tenant-a-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "narration":"bad",
                          "lines":[
                            {"accountId":"a","type":"DEBIT","amount":2500},
                            {"accountId":"b","type":"CREDIT","amount":2499}
                          ]
                        }
                        """))
                .andExpect(status().is5xxServerError());
    }
}
