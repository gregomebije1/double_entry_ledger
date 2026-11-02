package com.gregomebije.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Models {
     private Models() {}

    //public enum ComplianceTier { TIER_1, TIER_2 }
     public enum LedgerLineType { DEBIT, CREDIT }
     public enum AccountType { 
          ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE;
          
           /** ASSET/EXPENSE are debit-normal; everything else is credit-normal. */
          public boolean isDebitNormal() {
               return this == ASSET || this == EXPENSE;
          }
     }

    public record PostTransactionRequest(String narration, List<LedgerLine> lines) {}
    public record CreateAccountRequest(String account_id, String currency) {}
    
    public record TenantRegistrationDto (  String tenantId,  String tenantName,  String adminEmail,  String adminPassword,  String firstName,  String lastName ) {}
    public record LoginRequestDto (  String tenantId,  String email,  String password ) {}
    public record AuthResponseDto (  String accessToken ) {}
    public record CreateAccountDto (  String accountNumber,  String name,  String type,  String currency ) {}
    public record CreateTransactionDto ( String description,  String idempotencyKey,  List<LedgerLine> lines ) {}
    //public record TransactionLineDto (  UUID accountId,  Long amount ) {}
    public record LedgerLine(UUID accountId, LedgerLineType ledgerLineType, long debit, long credit) {}


    }