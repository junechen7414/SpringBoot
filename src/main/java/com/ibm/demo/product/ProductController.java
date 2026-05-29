package com.ibm.demo.product;

import java.util.List;
import java.util.Set;

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

import com.ibm.demo.product.DTO.CreateProductRequest;
import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.product.DTO.GetProductListResponse;
import com.ibm.demo.product.DTO.UpdateProductRequest;
import com.ibm.demo.util.ProcessOrderItemsRequest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    // Create Product
    @Operation(summary = "新增商品", description = "建立新商品。若已存在同名商品則拋出 ProductAlreadyExistException。成功則新增商品資料，預設銷售狀態為 1001 (AVAILABLE)。")
    @PostMapping
    public ResponseEntity<Integer> createProduct(@Valid @RequestBody CreateProductRequest createProductRequest) {
        Integer productId = productService.createProduct(createProductRequest);
        return ResponseEntity.ok(productId);
    }

    // Read Product List
    @Operation(summary = "獲取商品列表", description = "獲取所有商品的列表。受限於 SQLRestriction 規則，僅會回傳未被軟刪除且銷售狀態為 1001 (AVAILABLE) 的商品。")
    @GetMapping
    public ResponseEntity<List<GetProductListResponse>> getProductList() {
        List<GetProductListResponse> productList = productService.getProductList();
        return ResponseEntity.ok(productList);
    }

    // Batch Read Product Detail
    @Operation(summary = "批量獲取商品詳細資訊", description = "根據多個 ID 獲取商品詳細資訊。受限於 SQLRestriction 規則，若商品不存在、已軟刪除或銷售狀態非 1001 (AVAILABLE)，該 ID 將被忽略。")
    @GetMapping("/batch")
    public ResponseEntity<List<GetProductDetailResponse>> getProductBatch(@RequestParam("ids") Set<Integer> ids) {
        return ResponseEntity.ok(productService.getProductDetails(ids).values().stream().toList());
    }

    // Read Product Detail
    @Operation(summary = "獲取單一商品詳細資訊", description = "根據 ID 獲取商品詳細資訊。受限於 SQLRestriction 規則，若商品不存在、已軟刪除或銷售狀態非 1001 (AVAILABLE)，將回傳 NotFound。")
    @GetMapping("/{id}")
    public ResponseEntity<GetProductDetailResponse> getProductDetail(@PathVariable Integer id) {
        GetProductDetailResponse productDetail = productService.getProductDetail(id);
        return ResponseEntity.ok(productDetail);
    }

    // Update Product
    @Operation(summary = "更新商品", description = "更新現有商品資訊。受限於 SQLRestriction 規則，若商品 ID 不存在、已軟刪除或銷售狀態非 1001 (AVAILABLE)，將拋出 NotFound。若嘗試更改為已存在的商品名稱，則拋出 ProductAlreadyExistException。")
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateProduct(@PathVariable Integer id,
            @Valid @RequestBody UpdateProductRequest updateProductRequest) {
        productService.updateProduct(id, updateProductRequest);
        return ResponseEntity.ok().build();
    }

    // Delete Product
    @Operation(summary = "刪除商品", description = "執行商品軟刪除。受限於 SQLRestriction 規則，若商品 ID 不存在、已軟刪除或銷售狀態非 1001 (AVAILABLE)，將拋出 NotFound。")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "處理訂單中的商品", description = "處理訂單中的商品資訊")
    @PostMapping("/processOrderItems")
    public void processOrderItems(@RequestBody ProcessOrderItemsRequest request) {
        productService.processOrderItems(request);
    }

}