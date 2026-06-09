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
import com.ibm.demo.account.DTO.GetAccountDetailResponse;
import com.ibm.demo.account.DTO.GetAccountListResponse;
import com.ibm.demo.account.DTO.UpdateAccountRequest;
import com.ibm.demo.exception.ApiErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
@Tag(name = "Account", description = "帳戶管理 API")
public class AccountController {
    private final AccountService accountService;

    // Create Account
    @Operation(summary = "建立新帳戶", description = "建立新帳戶。成功則新增帳戶資料，預設狀態為啟用 'Y'。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "建立成功，回傳帳戶 ID"),
            @ApiResponse(responseCode = "400", description = "參數驗證失敗",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<Integer> createAccount(@Valid @RequestBody CreateAccountRequest createAccountRequest) {
        Integer accountId = accountService.createAccount(createAccountRequest);
        return ResponseEntity.ok(accountId);
    }

    // Read Account List
    @Operation(summary = "獲取帳戶列表", description = "獲取所有帳戶的列表。受限於 SQLRestriction 規則，僅會回傳未軟刪除且狀態為啟用 'Y' 的帳戶。")
    @ApiResponse(responseCode = "200", description = "成功取得帳戶列表")
    @GetMapping
    public ResponseEntity<List<GetAccountListResponse>> getAccountList() {
        List<GetAccountListResponse> accountList = accountService.getAccountList();
        return ResponseEntity.ok(accountList);
    }

    // Read Account Detail
    @Operation(summary = "獲取帳戶詳細資訊", description = "根據 ID 獲取帳戶詳細資訊。受限於 SQLRestriction 規則，若帳戶不存在、已軟刪除或狀態非啟用 'Y'，將拋出 NotFound。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功取得帳戶詳細資訊"),
            @ApiResponse(responseCode = "404", description = "帳戶不存在",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<GetAccountDetailResponse> getAccountDetail(
            @Parameter(description = "帳戶 ID", example = "1", required = true)
            @PathVariable Integer id) {
        GetAccountDetailResponse accountDetail = accountService.getAccountDetail(id);
        return ResponseEntity.ok(accountDetail);
    }

    // Update Account
    @Operation(summary = "更新帳戶", description = "更新現有帳戶資訊。受限於 SQLRestriction 規則，若帳戶 ID 不存在、已軟刪除或狀態非啟用 'Y'，將拋出 NotFound。若欲將狀態從啟用 'Y' 變更為停用 'N'，會先檢查該帳戶是否仍有關聯訂單，若有則拋出 AccountStillHasOrderCanNotBeDeleteException。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "400", description = "參數驗證失敗或帳戶仍有關聯訂單",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "帳戶不存在",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateAccount(
            @Parameter(description = "帳戶 ID", example = "1", required = true)
            @PathVariable Integer id,
            @Valid @RequestBody UpdateAccountRequest updateAccountRequest) {
        accountService.updateAccount(id, updateAccountRequest);
        return ResponseEntity.ok().build();
    }

    // Delete Account
    @Operation(summary = "刪除帳戶", description = "執行帳戶軟刪除。受限於 SQLRestriction 規則，若帳戶 ID 不存在、已軟刪除或狀態非啟用 'Y'，將拋出 NotFound。若該帳戶仍有關聯訂單，則拋出 AccountStillHasOrderCanNotBeDeleteException。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "刪除成功"),
            @ApiResponse(responseCode = "400", description = "帳戶仍有關聯訂單，無法刪除",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "帳戶不存在",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @Parameter(description = "帳戶 ID", example = "1", required = true)
            @PathVariable Integer id) {
        accountService.deleteAccount(id);
        return ResponseEntity.ok().build();
    }

}
