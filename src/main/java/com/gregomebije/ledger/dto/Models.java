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
    
    public record CreateAccountRequest (  String accountCode,  String name,  String type,  String currency ) {}

    /*
    {
        "memo":"Fund Alice",
        "idempotencyKey":"fund-alice-1",
        "lines":[
            {"debit": 30300, "credit": 0, "account_code": "W300", "sub_ref": null},
            {"debit": 0, "credit": 30000, "account_code": "W100", "sub_ref": "cd369254-ffd9-47ff-bbd8-c9ef5a8e4b09"}, 
            {"debit": 0, "credit": 300, "account_code": "W200", "sub_ref": null}]
    }
    */
    public record CreateLedgerRequest (
        String memo,  
        String idempotencyKey,  
        List<LedgerLine> lines ) {}

    // A line may carry a sub_ref: the customer wallet Id or merchant id
    // That gives per-customer balances inside one account (a sub-ledger) 
    // without creating an account per customer.
    public record LedgerLine(
        String accountCode, 
        String subRef, 
        long debit, 
        long credit) {}
}