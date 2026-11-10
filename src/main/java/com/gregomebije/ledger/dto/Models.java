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
          
           /** ASSET/EXPENSE are debit; everything else is credit. */
          public boolean isDebitNormal() {
               return this == ASSET || this == EXPENSE;
          }
     }

  
    public record LoginRequest (  String tenantId,  String email,  String password ) {}
    public record LoginResponse (  String accessToken ) {}

    public record CreateUserRequest (String email,  String password,  String firstName,  String lastName ) {}
    public record CreateTenantRequest (  String email, String tenantId,  String tenantName ) {}
    
    public record CreateAccountRequest (  String accountNumber,  String name,  String type,  String currency ) {}
    public record CreateTransactionRequest ( String description,  String idempotencyKey,  List<LedgerLine> lines ) {}
    public record LedgerLine(UUID accountId, LedgerLineType ledgerLineType, long debit, long credit) {}


    }