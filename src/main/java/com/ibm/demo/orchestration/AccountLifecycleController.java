package com.ibm.demo.orchestration;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

/**
 * 帳戶的<b>跨領域端點</b>：更新與刪除。路徑仍是 {@code /account/{id}}，對呼叫端而言與
 * {@code AccountController} 上的其他端點沒有分別 —— 對外契約完全不變。
 *
 * <p><b>為什麼這兩個端點不與其他 account 端點同一個 controller？</b>
 * 它們的行為依賴 order 領域（見 {@link AccountLifecycleService}）。若留在
 * {@code com.ibm.demo.account} 內，該 package 就得反向依賴 order，循環依賴又會回來。
 *
 * <p>這個切分也預告了服務拆分後的歸屬：真的拆成 account / product / order 三個部署單元時，
 * 這兩個端點無法住在 account 服務裡（否則 account 服務又要回頭呼叫 order 服務），它們屬於
 * 協調層或 gateway/BFF 那一側。
 *
 * <p>{@code @Tag} 與 {@code AccountController} 同名，OpenAPI 文件上仍歸在 Account 群組。
 */
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
@Tag(name = "Account", description = "帳戶管理 API")
public class AccountLifecycleController {

    private final AccountLifecycleService accountLifecycleService;

    // Update Account
    @Operation(summary = "更新帳戶", description = "更新現有帳戶資訊。受限於 SQLRestriction 規則，若帳戶 ID 不存在、已軟刪除或狀態非啟用 'Y'，將拋出 NotFound。若欲將狀態從啟用 'Y' 變更為停用 'N'，會先檢查該帳戶是否仍有關聯訂單，若有則拋出 BusinessException（ACCOUNT_STILL_HAS_ORDER_CAN_NOT_BE_DELETED）。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "更新成功"),
            @ApiResponse(responseCode = "400", description = "參數驗證失敗或帳戶仍有關聯訂單", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "帳戶不存在", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateAccount(
            @Parameter(description = "帳戶 ID", example = "1", required = true) @PathVariable Integer id,
            @Valid @RequestBody UpdateAccountRequest updateAccountRequest) {
        accountLifecycleService.updateAccount(id, updateAccountRequest);
        return ResponseEntity.noContent().build();
    }

    // Delete Account
    @Operation(summary = "刪除帳戶", description = "執行帳戶軟刪除。受限於 SQLRestriction 規則，若帳戶 ID 不存在、已軟刪除或狀態非啟用 'Y'，將拋出 NotFound。若該帳戶仍有關聯訂單，則拋出 BusinessException（ACCOUNT_STILL_HAS_ORDER_CAN_NOT_BE_DELETED）。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "刪除成功"),
            @ApiResponse(responseCode = "400", description = "帳戶仍有關聯訂單，無法刪除", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "帳戶不存在", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @Parameter(description = "帳戶 ID", example = "1", required = true) @PathVariable Integer id) {
        accountLifecycleService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }
}
