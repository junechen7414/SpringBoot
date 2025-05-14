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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.demo.account.DTO.CreateAccountRequest;
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.account.DTO.GetAccountListResponse;
import com.ibm.demo.account.DTO.UpdateAccountRequest;

import jakarta.validation.Valid;

@RestController // Restful Controller
@RequestMapping("/account") // 基礎路徑
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // Create Account
    @PostMapping("/create")
    public ResponseEntity<Integer> createAccount(@Valid @RequestBody CreateAccountRequest createAccountRequest) {
        Integer accountId = accountService.createAccount(createAccountRequest);
        return ResponseEntity.ok(accountId);
    }

    // Read Account List
    @GetMapping("/getList")
    public ResponseEntity<List<GetAccountListResponse>> getAccountList(@RequestParam(required = false) String status) {
        List<GetAccountListResponse> accountList = accountService.getAccountList(status);
        return ResponseEntity.ok(accountList);
    }

    // Read Account Detail
    @GetMapping("/getDetail/{id}")
    public ResponseEntity<GetAccountDetailResponse> getAccountDetail(@PathVariable Integer id) {
        GetAccountDetailResponse accountDetail = accountService.getAccountDetail(id);
        return ResponseEntity.ok(accountDetail);
    }

    // Update Account
    @PutMapping("/update")
    public ResponseEntity<Void> updateAccount(@Valid UpdateAccountRequest updateAccountRequest) {
        accountService.updateAccount(updateAccountRequest);
        return ResponseEntity.ok().build();
    }

    // Delete Account
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Integer id) {
        accountService.deleteAccount(id);
        return ResponseEntity.ok().build();
    }

    // Validate Account
    // @PostMapping("/validate/{id}")
    // public ResponseEntity<Void> validateAccount(@PathVariable Integer id) {
    //     accountService.validateAccount(id);
    //     return ResponseEntity.ok().build();
    // }

}
