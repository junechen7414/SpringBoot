package com.ibm.demo.product;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import com.ibm.demo.exception.ApiErrorResponse;
import com.ibm.demo.product.DTO.CreateProductRequest;
import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.product.DTO.GetProductListResponse;
import com.ibm.demo.product.DTO.UpdateProductRequest;
import com.ibm.demo.product.DTO.internal.AdjustStockRequest;
import com.ibm.demo.product.DTO.internal.OrderItemRequest;
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
@RequestMapping("/product")
@RequiredArgsConstructor
@Tag(name = "Product", description = "商品管理 API")
public class ProductController {
    private final ProductService productService;

    // Create Product
    @Operation(summary = "新增商品", description = "建立新商品。若已存在同名商品則拋出 ProductAlreadyExistException。成功則新增商品資料，預設銷售狀態為 1001 (AVAILABLE)。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "建立成功，回傳商品 ID"),
            @ApiResponse(responseCode = "400", description = "參數驗證失敗或商品名稱已存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<Integer> createProduct(@Valid @RequestBody CreateProductRequest createProductRequest) {
        Integer productId = productService.createProduct(createProductRequest);
        return ResponseEntity.ok(productId);
    }

    // Read Product List (Paginated)
    @Operation(summary = "獲取商品列表（分頁）", description = "獲取所有商品的分頁列表。受限於 SQLRestriction 規則，僅會回傳未被軟刪除且銷售狀態為 1001 (AVAILABLE) 的商品。")
    @ApiResponse(responseCode = "200", description = "成功取得商品分頁列表")
    @GetMapping
    public ResponseEntity<PageResponse<GetProductListResponse>> getProductList(
            @Parameter(description = "分頁參數（page=頁碼從0開始, size=每頁筆數, sort=排序欄位,方向）", example = "page=0&size=20&sort=id,asc") @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<GetProductListResponse> productPage = productService.getProductList(pageable);
        return ResponseEntity.ok(productPage);
    }

    // Batch Read Product Detail
    @Operation(summary = "批量獲取商品詳細資訊", description = "根據多個 ID 獲取商品詳細資訊。受限於 SQLRestriction 規則，若商品不存在、已軟刪除或銷售狀態非 1001 (AVAILABLE)，該 ID 將被忽略。")
    @ApiResponse(responseCode = "200", description = "成功取得商品詳細資訊列表")
    @GetMapping("/batch")
    public ResponseEntity<List<GetProductDetailResponse>> getProductBatch(
            @Parameter(description = "商品 ID 集合（逗號分隔）", example = "1,2,3", required = true) @RequestParam("ids") Set<Integer> ids) {
        return ResponseEntity.ok(productService.getProductDetails(ids).values().stream().toList());
    }

    // Read Product Detail
    @Operation(summary = "獲取單一商品詳細資訊", description = "根據 ID 獲取商品詳細資訊。受限於 SQLRestriction 規則，若商品不存在、已軟刪除或銷售狀態非 1001 (AVAILABLE)，將回傳 NotFound。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功取得商品詳細資訊"),
            @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<GetProductDetailResponse> getProductDetail(
            @Parameter(description = "商品 ID", example = "1", required = true) @PathVariable Integer id) {
        GetProductDetailResponse productDetail = productService.getProductDetail(id);
        return ResponseEntity.ok(productDetail);
    }

    // Update Product
    @Operation(summary = "更新商品", description = "更新現有商品資訊。受限於 SQLRestriction 規則，若商品 ID 不存在、已軟刪除或銷售狀態非 1001 (AVAILABLE)，將拋出 NotFound。若嘗試更改為已存在的商品名稱，則拋出 ProductAlreadyExistException。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "400", description = "參數驗證失敗或商品名稱已存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateProduct(
            @Parameter(description = "商品 ID", example = "1", required = true) @PathVariable Integer id,
            @Valid @RequestBody UpdateProductRequest updateProductRequest) {
        productService.updateProduct(id, updateProductRequest);
        return ResponseEntity.ok().build();
    }

    // Delete Product
    @Operation(summary = "刪除商品", description = "執行商品軟刪除。受限於 SQLRestriction 規則，若商品 ID 不存在、已軟刪除或銷售狀態非 1001 (AVAILABLE)，將拋出 NotFound。")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "刪除成功"),
            @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "商品 ID", example = "1", required = true) @PathVariable Integer id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "預留商品庫存", description = "內部使用：建立訂單時預留(reserve)商品庫存。")
    @ApiResponse(responseCode = "200", description = "預留成功")
    @PostMapping("/reserve")
    public void reserveStock(@RequestBody Set<OrderItemRequest> items) {
        productService.reserveStock(items);
    }

    @Operation(summary = "釋放商品庫存", description = "內部使用：刪除訂單時釋放(release)商品庫存。")
    @ApiResponse(responseCode = "200", description = "釋放成功")
    @PostMapping("/release")
    public void releaseStock(@RequestBody Set<OrderItemRequest> items) {
        productService.releaseStock(items);
    }

    @Operation(summary = "調整商品庫存", description = "內部使用：更新訂單時依新舊項目差值調整(adjust)商品庫存的預留量。")
    @ApiResponse(responseCode = "200", description = "調整成功")
    @PostMapping("/adjustStock")
    public void adjustStock(@RequestBody AdjustStockRequest request) {
        productService.adjustStock(request);
    }

}
