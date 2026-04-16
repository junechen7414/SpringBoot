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

@RestController // Restful Controller
@RequestMapping("/product") // 基礎路徑
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // Create Product
    @Operation(summary = "新增商品", description = "如果有同名商品拋出特定例外，沒有則新增成功")
    @PostMapping
    public ResponseEntity<Integer> createProduct(@Valid @RequestBody CreateProductRequest createProductRequest) {
        Integer productId = productService.createProduct(createProductRequest);
        return ResponseEntity.ok(productId);
    }

    // Read Product List
    @GetMapping
    public ResponseEntity<List<GetProductListResponse>> getProductList() {
        List<GetProductListResponse> productList = productService.getProductList();
        return ResponseEntity.ok(productList);
    }

    // Batch Read Product Detail
    @GetMapping("/batch")
    public ResponseEntity<List<GetProductDetailResponse>> getProductBatch(@RequestParam("ids") Set<Integer> ids) {
        return ResponseEntity.ok(productService.getProductDetails(ids).values().stream().toList());
    }

    // Read Product Detail
    @GetMapping("/{id}")
    public ResponseEntity<GetProductDetailResponse> getProductDetail(@PathVariable Integer id) {
        GetProductDetailResponse productDetail = productService.getProductDetail(id);
        return ResponseEntity.ok(productDetail);
    }

    // Update Product
    @Operation(summary = "更新商品", description = "該ID商品若沒找到拋出NotFound例外，再檢查是否要更改成已經存在的商品名稱拋出特定例外，沒有則更新成功")
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateProduct(@PathVariable Integer id,
            @Valid @RequestBody UpdateProductRequest updateProductRequest) {
        productService.updateProduct(id, updateProductRequest);
        return ResponseEntity.ok().build();
    }

    // Delete Product
    @Operation(summary = "刪除商品", description = "找不到商品或商品已經軟刪除過拋出NotFound，沒有則軟刪除")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "處理訂單中的商品", description = "處理訂單中的商品資訊")
    @PostMapping("/processOrderItems")
    public void processOrderItems(@RequestBody ProcessOrderItemsRequest request) {
        productService.processOrderItems(request.originalItems(), request.updatedItems());
    }

}