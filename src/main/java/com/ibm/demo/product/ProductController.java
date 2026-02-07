package com.ibm.demo.product;

import java.util.List;
import java.util.Map;
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
    @PostMapping("/create")
    public ResponseEntity<Integer> createProduct(@Valid @RequestBody CreateProductRequest createProductRequest) {
        Integer productId = productService.createProduct(createProductRequest);
        return ResponseEntity.ok(productId);
    }

    // Read Product List
    @GetMapping("/getList")
    public ResponseEntity<List<GetProductListResponse>> getProductList(@RequestParam(required = false) Integer status) {
        List<GetProductListResponse> productList = productService.getProductList(status);
        return ResponseEntity.ok(productList);
    }

    // Read Product Detail
    @GetMapping("/getDetail/{id}")
    public ResponseEntity<GetProductDetailResponse> getProductDetail(@PathVariable Integer id) {
        GetProductDetailResponse productDetail = productService.getProductDetail(id);
        return ResponseEntity.ok(productDetail);
    }

    @Operation(summary = "多個ID取得多筆資料", description = "盡力找沒找到不拋出例外回傳空list，找到則回傳")
    @GetMapping("/getDetails")
    public ResponseEntity<Map<Integer, GetProductDetailResponse>> getProductDetails(
            @RequestParam("ids") Set<Integer> ids) {
        Map<Integer, GetProductDetailResponse> productDetailsMap = productService.getProductDetails(ids);
        return ResponseEntity.ok(productDetailsMap);
    }

    // Update Product
    @Operation(summary = "更新商品", description = "該ID商品若沒找到拋出NotFound例外，再檢查是否要更改成已經存在的商品名稱拋出特定例外，沒有則更新成功")
    @PutMapping("/update")
    public ResponseEntity<Void> updateProduct(UpdateProductRequest updateProductRequest) {
        productService.updateProduct(updateProductRequest);
        return ResponseEntity.ok().build();
    }

    // Delete Product
    @Operation(summary = "刪除商品", description = "找不到商品或商品已經軟刪除過拋出NotFound，沒有則軟刪除")
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 批量更新商品庫存
     *
     * @param stockUpdates Map<商品ID, 新庫存數量>
     */
    @Operation(summary = "批量更新商品庫存", description = "傳入key是id value是要更新的庫存量之 Map，檢核有value = null 和傳入空列表時拋出例外")
    @PutMapping("/batchUpdateStockQuantity")
    public void updateProductsStock(@RequestBody Map<Integer, Integer> stockUpdates) {
        productService.updateProductsStock(stockUpdates);
    }

}
