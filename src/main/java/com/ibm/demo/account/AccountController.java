package com.ibm.demo.account;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.ibm.demo.util.CreatedResponse;
import com.ibm.demo.util.PageResponse;

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
        // DELETE /account/{id} 不在這裡 —— 它的行為依賴 order 領域，
        // 故落在 orchestration.AccountLifecycleController（對外路徑與契約不變）。

        private final AccountService accountService;

        // Create Account
        @Operation(summary = "建立新帳戶", description = "建立新帳戶。")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "建立成功，回傳帳戶 ID 與 Location 標頭"),
                        @ApiResponse(responseCode = "400", description = "參數驗證失敗", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PostMapping
        public ResponseEntity<CreatedResponse> createAccount(
                        @Valid @RequestBody CreateAccountRequest createAccountRequest) {
                Integer accountId = accountService.createAccount(createAccountRequest);
                return CreatedResponse.at(accountId);
        }

        // Read Account List (Paginated)
        @Operation(summary = "獲取帳戶列表（分頁）", description = "獲取所有帳戶的分頁列表。受限於 SQLRestriction 規則，僅會回傳未停用（未軟刪除）的帳戶。")
        @ApiResponse(responseCode = "200", description = "成功取得帳戶分頁列表")
        @GetMapping
        public ResponseEntity<PageResponse<GetAccountListResponse>> getAccountList(
                        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
                PageResponse<GetAccountListResponse> accountPage = accountService.getAccountList(pageable);
                return ResponseEntity.ok(accountPage);
        }

        // Read Account Detail
        @Operation(summary = "獲取帳戶詳細資訊", description = "根據 ID 獲取帳戶詳細資訊。受限於 SQLRestriction 規則，若帳戶不存在或已停用，將拋出 NotFound。")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "成功取得帳戶詳細資訊"),
                        @ApiResponse(responseCode = "404", description = "帳戶不存在", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping("/{id}")
        public ResponseEntity<GetAccountDetailResponse> getAccountDetail(
                        @Parameter(description = "帳戶 ID", example = "1", required = true) @PathVariable Integer id) {
                GetAccountDetailResponse accountDetail = accountService.getAccountDetail(id);
                return ResponseEntity.ok(accountDetail);
        }

        // Update Account
        @Operation(summary = "更新帳戶", description = "更新帳戶名稱；要停用帳戶請改用 DELETE。受限於 SQLRestriction 規則，若帳戶 ID 不存在或已停用，將拋出 NotFound。")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "更新成功"),
                        @ApiResponse(responseCode = "400", description = "參數驗證失敗", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "帳戶不存在", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @PutMapping("/{id}")
        public ResponseEntity<Void> updateAccount(
                        @Parameter(description = "帳戶 ID", example = "1", required = true) @PathVariable Integer id,
                        @Valid @RequestBody UpdateAccountRequest updateAccountRequest) {
                accountService.updateAccount(id, updateAccountRequest);
                return ResponseEntity.noContent().build();
        }

        // Assert Account Order Eligibility (internal)
        @Operation(summary = "驗證帳戶下單資格", description = "內部使用：驗證帳戶是否具下單資格。受限於 SQLRestriction 規則，已停用（軟刪除）的帳戶查詢即不可見，故帳戶不存在或已停用時一律回傳 NotFound。")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "帳戶具下單資格"),
                        @ApiResponse(responseCode = "404", description = "帳戶不存在或不可下單", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        @GetMapping("/{id}/order-eligibility")
        public ResponseEntity<Void> assertCanPlaceOrder(
                        @Parameter(description = "帳戶 ID", example = "1", required = true) @PathVariable Integer id) {
                accountService.assertCanPlaceOrder(id);
                return ResponseEntity.noContent().build();
        }
}
