package com.ibm.demo.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * OpenAPI 文件的認證描述。
 *
 * 這裡宣告的是<b>文件層的 metadata</b>，不參與實際授權判斷 —— 真正擋請求的是
 * {@link SecurityConfig} 的 filter chain。兩者要分開理解：即使 filter chain 已經要求認證，
 * 若文件沒宣告 security scheme，Swagger UI 就不知道該怎麼帶憑證，也不會顯示 Authorize 按鈕，
 * 於是 "Try it out" 發出的請求一律不帶 {@code Authorization} header，必然 401。
 *
 * {@code security} 放在 {@code @OpenAPIDefinition} 上表示<b>全域</b>要求，對應
 * {@code anyRequest().authenticated()}；不需要逐個 controller 標註。放行清單
 * （/actuator/health、springdoc 自身端點）不是 controller 端點，不會出現在文件裡，無須例外處理。
 *
 * 帳密取自 {@code app.auth.api-*}（application.yml，可由 env 覆寫），預設 {@code api}。
 * 實際在 Authorize 對話框輸入的憑證由 {@code springdoc.swagger-ui.persist-authorization=true}
 * 存進瀏覽器 localStorage，重新整理／重開分頁不需重打；伺服器端不保存憑證。
 */
@Configuration
@OpenAPIDefinition(security = @SecurityRequirement(name = OpenApiConfig.BASIC_AUTH))
@SecurityScheme(name = OpenApiConfig.BASIC_AUTH, type = SecuritySchemeType.HTTP, scheme = "basic", description = "機器帳號 HTTP Basic：使用者名稱為 app.auth.api-username（預設 api）。")
public class OpenApiConfig {

    /** scheme 名稱；@SecurityScheme 與 @SecurityRequirement 必須一致才會連上。 */
    static final String BASIC_AUTH = "basicAuth";
}
