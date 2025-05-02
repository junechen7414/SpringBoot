# Spring Boot 統一錯誤處理實施計畫

本計畫旨在為現有的 Spring Boot 應用程式建立一個統一且標準化的 API 錯誤處理機制。

## 步驟

1.  **建立統一的 API 錯誤回應 DTO (`ApiErrorResponse.java`)**
    *   **位置:** 在 `com.ibm.demo.exception` 套件下建立。
    *   **目的:** 定義一個標準的 JSON 結構來回傳所有 API 錯誤。
    *   **欄位:**
        *   `timestamp` (LocalDateTime): 錯誤發生時間。
        *   `status` (int): HTTP 狀態碼 (例如 400, 500)。
        *   `error` (String): HTTP 狀態描述 (例如 "Bad Request", "Internal Server Error")。
        *   `message` (String): 詳細錯誤訊息。
        *   `path` (String): 請求路徑。
    *   **取代:** 目前在 `GlobalExceptionHandler` 中手動建立 `Map<String, Object>` 的方式。

2.  **定義業務邏輯基底例外 (`BusinessLogicException.java`)**
    *   **位置:** 在 `com.ibm.demo.exception` 套件下建立。
    *   **繼承:** `RuntimeException`。
    *   **目的:** 作為所有特定業務規則驗證失敗（非資源找不到或輸入驗證錯誤）的父類別。
    *   **建構子:** 應接受 `String message`。

3.  **修改全域例外處理器 (`GlobalExceptionHandler.java`)**
    *   **路徑:** `c:\Users\WUJYUNCHEN\Desktop\ToBeCursorUsed\Training\SpringBoot Basic\sideproject\demo\src\main\java\com\ibm\demo\GlobalExceptionHandler.java`
    *   **註解:** 考慮使用 `@RestControllerAdvice` (可選，`@ControllerAdvice` 搭配 `ResponseEntity` 亦可)。
    *   **更新現有處理器:**
        *   `handleResourceNotFoundException`: 回傳 `ResponseEntity<ApiErrorResponse>`，狀態碼 `404 NOT_FOUND`。
        *   `handleValidationException`: 回傳 `ResponseEntity<ApiErrorResponse>`，狀態碼 `400 BAD_REQUEST`。
    *   **新增業務邏輯例外處理器:**
        *   使用 `@ExceptionHandler(BusinessLogicException.class)`。
        *   接收 `BusinessLogicException ex`, `WebRequest request`。
        *   建立 `ApiErrorResponse`，狀態碼 `400 BAD_REQUEST`，訊息來自 `ex.getMessage()`。
        *   回傳 `ResponseEntity<ApiErrorResponse>`。
    *   **修改通用例外處理器 (`handleGenericRuntimeException`):**
        *   捕捉所有未處理的 `Exception` 或 `RuntimeException`。
        *   **重要:** 使用 `logger.error("...", ex);` 記錄完整錯誤堆疊。
        *   建立 `ApiErrorResponse`，狀態碼 `500 INTERNAL_SERVER_ERROR`，使用通用錯誤訊息 (例如："發生未預期的內部錯誤")。
        *   回傳 `ResponseEntity<ApiErrorResponse>`。

4.  **在 Service 層應用新的例外**
    *   **檢視:** `AccountService`, `ProductService`, `OrderService` 等。
    *   **應用:**
        *   當發生業務規則驗證失敗時，拋出 `BusinessLogicException` 或其子類別，並提供清晰訊息。
        *   資源找不到的情況，繼續使用或拋出 `ResourceNotFoundException`。
        *   輸入參數驗證錯誤 (`@Valid` 失敗) 將由現有的 `ValidationException` 處理器處理 (回傳 400)。

## 預期成果

完成以上步驟後，應用程式將：
*   對所有 API 錯誤回傳一致的 JSON 格式 (`ApiErrorResponse`)。
*   區分業務邏輯錯誤 (HTTP 400) 和未預期的系統錯誤 (HTTP 500)。
*   提供更清晰、更易於前端或客戶端處理的錯誤訊息。
*   在後端日誌中保留詳細的錯誤堆疊信息以供除錯。