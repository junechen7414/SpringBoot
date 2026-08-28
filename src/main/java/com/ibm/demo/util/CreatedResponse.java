package com.ibm.demo.util;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 資源建立成功的統一回應：{@code 201 Created} + {@code Location} 標頭 + 帶 {@code id} 的 body。
 *
 * <p><b>為什麼不回裸的 {@code Integer}</b>：裸純量在線路上是 {@code 5} 這樣一行沒有名字的數字，
 * 呼叫端必須靠「我知道這個端點回的是 ID」這種文件外的默契來解讀，而且無法在不破壞相容性的前提下
 * 補第二個欄位（例如日後要一併回傳 order number）。包一層 {@code {"id": 5}} 之後，欄位語意寫在
 * 線路上，未來加欄位是相容變更。
 *
 * <p><b>為什麼要 {@code Location}</b>：201 的語意是「資源已建立」，而 {@code Location} 是 HTTP 內建、
 * 與 body 無關的「它在哪裡」表達方式。呼叫端拼 URL 的責任因此從客戶端移回伺服端 —— 路徑改版時
 * 不必同步修改每個客戶端。
 *
 * <p><b>使用前提</b>：{@link #at(Integer)} 假設建立端點的 URI 就是集合 URI（{@code POST /account}
 * → {@code /account/{id}}）。若日後出現 {@code POST /account/{id}/xxx} 這種子資源建立端點，
 * 請自行組 {@code Location}，不要硬套此方法。
 */
@Schema(name = "CreatedResponse", description = "資源建立成功回應")
public record CreatedResponse(

        @Schema(description = "新建立資源的識別碼", example = "1") Integer id) {

    /**
     * 組出 {@code 201 Created} 回應，{@code Location} 指向「目前請求 URI + /{id}」。
     *
     * <p>用 {@code fromCurrentRequestUri()} 而非 {@code fromCurrentRequest()}：後者會把 query string
     * 一起帶進 {@code Location}，而新資源的位置與建立請求帶了什麼查詢參數無關。
     *
     * @param id 新建立資源的識別碼
     * @return 201 回應（含 {@code Location} 與 {@code {"id": ...}} body）
     */
    public static ResponseEntity<CreatedResponse> at(Integer id) {
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(new CreatedResponse(id));
    }
}
