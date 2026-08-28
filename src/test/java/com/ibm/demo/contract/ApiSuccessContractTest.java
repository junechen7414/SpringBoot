package com.ibm.demo.contract;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ibm.demo.account.AccountController;
import com.ibm.demo.account.AccountService;
import com.ibm.demo.order.OrderController;
import com.ibm.demo.order.OrderService;
import com.ibm.demo.product.ProductController;
import com.ibm.demo.product.ProductService;

/**
 * 成功回應的 <b>wire format 契約測試</b>。
 *
 * <p>與 {@link ApiErrorContractTest} 的分工：錯誤側的格式與 domain 無關，因此用探針 controller 隔離；
 * 成功側則相反 —— 「建立回 201、無內容回 204」是<b>每個端點各自的行為</b>，拿探針測只會驗到探針自己。
 * 所以這裡刻意載入<b>真正的三個 domain controller</b>（service 以 mock 頂替），斷言的是呼叫端打
 * {@code POST /account} 時真正收到的 status line、{@code Location} 標頭與 body。
 *
 * <p>釘住的三條規則（見 {@code docs/api-response-contract-decision.md} §7 Phase 5）：
 * <ol>
 *   <li>建立資源 → {@code 201 Created} + {@code Location} + {@code {"id": n}}；<b>不是</b>裸純量。</li>
 *   <li>成功但無內容可回（更新／刪除／內部庫存變動／資格驗證）→ {@code 204 No Content} 且 body 為空。</li>
 *   <li>有內容才回 {@code 200} + body，且 body 是具名物件而非裸 {@code true} / 裸數字。</li>
 * </ol>
 *
 * <p>這些事實在型別層是看不見的：把 {@code CreatedResponse.at(id)} 改回 {@code ResponseEntity.ok(id)}
 * 仍然編譯得過、單元測試全綠，只有這裡會紅。
 */
@WebMvcTest
@DisplayName("成功回應 wire format 契約")
class ApiSuccessContractTest {

    /** mock service 一律回這個 ID，好讓 {@code Location} 的預期值可以逐字比對。 */
    private static final int NEW_ID = 7;

    /**
     * 契約測試的 context 由 {@link ContractTestApplication} 決定（只有探針），這裡把真正的 controller
     * 明確 {@code @Import} 進來 —— 不開 component scan，因此不會連帶拉進 JPA、{@code RestClientConfig}
     * 等與 wire format 無關的東西。
     */
    @TestConfiguration
    @Import({ AccountController.class, ProductController.class, OrderController.class })
    static class RealControllers {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private OrderService orderService;

    // =====================================================================
    // 規則 1：建立資源 → 201 + Location + {"id": n}
    // =====================================================================

    static Stream<Arguments> createRequests() {
        return Stream.of(
                arguments("/account", "{\"name\":\"契約帳戶\"}"),
                arguments("/product", "{\"name\":\"契約商品\",\"price\":10.5,\"available\":3}"),
                arguments("/order", "{\"accountId\":1,\"items\":[{\"productId\":1,\"quantity\":2}]}"));
    }

    @ParameterizedTest(name = "POST {0}")
    @MethodSource("createRequests")
    @WithMockUser
    @DisplayName("建立資源一律回 201 + Location，body 只有 id 一個欄位")
    void createReturnsCreatedWithLocationAndIdBody(String collectionUri, String requestBody) throws Exception {
        stubCreateReturningNewId();

        String location = mockMvc.perform(post(collectionUri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + collectionUri + "/" + NEW_ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(NEW_ID))
                // 只有 id：既沒有 success/data 信封，也沒有偷偷多回一個欄位
                .andExpect(jsonPath("$.*", hasSize(1)))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        // Location 必須真的指向一個可 GET 的資源 URI，而不是一個組得出來但沒人接的字串。
        // mock service 回 null，controller 因此回 200 空 body —— 這裡要的只是「路由存在」。
        mockMvc.perform(get(URI.create(location))).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Location 不得帶入建立請求的 query string")
    void locationExcludesQueryString() throws Exception {
        stubCreateReturningNewId();

        // 用 fromCurrentRequestUri() 而非 fromCurrentRequest() 的唯一可觀測差別就在這裡：
        // 新資源的位置與「建立它時順手帶了什麼查詢參數」無關。
        mockMvc.perform(post("/account")
                .param("trace", "abc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"契約帳戶\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/account/" + NEW_ID));
    }

    // =====================================================================
    // 規則 2：成功但無內容 → 204，且 body 真的是空的
    // =====================================================================

    static Stream<Arguments> noContentRequests() {
        String accountBody = "{\"name\":\"契約帳戶\",\"status\":\"Y\"}";
        String productBody = "{\"name\":\"契約商品\",\"price\":10.5,\"saleStatus\":1001,\"available\":3}";
        String orderBody = "{\"orderStatus\":1001,\"items\":[{\"productId\":1,\"quantity\":2}]}";
        String items = "{\"items\":[{\"productId\":1,\"quantity\":2}]}";

        return Stream.of(
                arguments("PUT /account/{id}", json(put("/account/1"), accountBody)),
                arguments("DELETE /account/{id}", delete("/account/1")),
                arguments("GET /account/{id}/order-eligibility", get("/account/1/order-eligibility")),
                arguments("PUT /product/{id}", json(put("/product/1"), productBody)),
                arguments("DELETE /product/{id}", delete("/product/1")),
                arguments("POST /product/reserve", json(post("/product/reserve"), items)),
                arguments("POST /product/release", json(post("/product/release"), items)),
                arguments("POST /product/adjust-stock", json(post("/product/adjust-stock"),
                        "{\"from\":[],\"to\":[{\"productId\":1,\"quantity\":2}]}")),
                arguments("PUT /order/{orderId}", json(put("/order/1"), orderBody)),
                arguments("DELETE /order/{orderId}", delete("/order/1")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noContentRequests")
    @WithMockUser
    @DisplayName("成功但無內容可回的端點一律 204，且不得帶 body")
    void mutationWithoutContentReturnsNoContent(String label, MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isNoContent())
                // 204 帶 body 是自相矛盾的回應；順手也擋掉「回一個 {} 佔位」這種寫法
                .andExpect(content().string(""))
                .andExpect(header().doesNotExist("Content-Type"));
    }

    // =====================================================================
    // 規則 3：有內容才回 200，且 body 是具名物件
    // =====================================================================

    @Test
    @WithMockUser
    @DisplayName("訂單存在性查詢回 200 + 具名欄位，而非裸的 true/false")
    void existenceQueryReturnsNamedField() throws Exception {
        Mockito.when(orderService.isActiveAccountInOrder(1)).thenReturn(true);

        mockMvc.perform(get("/order/account/1/existence"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // 逐字比對：欄位名是契約的一部分，改名等於破壞呼叫端
                .andExpect(content().json("{\"hasActiveOrder\":true}", JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.*", hasSize(1)));
    }

    // --- Helpers ---

    private void stubCreateReturningNewId() {
        Mockito.when(accountService.createAccount(ArgumentMatchers.any())).thenReturn(NEW_ID);
        Mockito.when(productService.createProduct(ArgumentMatchers.any())).thenReturn(NEW_ID);
        Mockito.when(orderService.createOrder(ArgumentMatchers.any())).thenReturn(NEW_ID);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
