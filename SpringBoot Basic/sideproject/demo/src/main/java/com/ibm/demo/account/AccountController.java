package com.ibm.demo.account;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.demo.account.DTO.CreateAccountRequest;
import com.ibm.demo.account.DTO.CreateAccountResponse;
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.account.DTO.GetAccountListResponse;
import com.ibm.demo.account.DTO.UpdateAccountRequest;
import com.ibm.demo.account.DTO.UpdateAccountResponse;

import jakarta.validation.Valid;

@RestController // Restful Controller
@RequestMapping("/api/accounts") // 基礎路徑
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // Create Account
    @PostMapping
    public ResponseEntity<CreateAccountResponse> createAccount(@RequestBody @Valid CreateAccountRequest createAccountRequest) {
        CreateAccountResponse createAccountResponse = accountService.createAccount(createAccountRequest);
        return ResponseEntity.ok(createAccountResponse);
    }

    // Read Account List
    @GetMapping("/getList")
    public ResponseEntity<List<GetAccountListResponse>> getAccountList() {
        List<GetAccountListResponse> accountList = accountService.getAccountList();
        return ResponseEntity.ok(accountList);
    }

    // Read Account Detail
    @GetMapping("/getDetail/{id}")
    public ResponseEntity<GetAccountDetailResponse> getAccountDetail(@PathVariable Integer id) {
        GetAccountDetailResponse accountDetail = accountService.getAccountDetail(id);
        return ResponseEntity.ok(accountDetail);
    }

    // Update Account
    @PutMapping
    public ResponseEntity<UpdateAccountResponse> updateAccount(UpdateAccountRequest updateAccountRequest) {
        UpdateAccountResponse updateAccountResponse = accountService.updateAccount(updateAccountRequest);
        return ResponseEntity.ok(updateAccountResponse);
    }

    // Delete Account
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Integer id) {
        accountService.deleteAccount(id);
        return ResponseEntity.ok().build();
    }

}
