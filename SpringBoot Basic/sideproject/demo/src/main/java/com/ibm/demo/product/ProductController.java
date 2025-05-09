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

import jakarta.validation.Valid;

@RestController // Restful Controller
@RequestMapping("/product") // 基礎路徑
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // Create Product
    @PostMapping("/create")
    public ResponseEntity<Integer> createProduct(@Valid @RequestBody CreateProductRequest createProductRequest) {
        Integer productId = productService.createProduct(createProductRequest);
        return ResponseEntity.ok(productId);
    }

    // Read Product List
    @GetMapping("/getList")
    public ResponseEntity<List<GetProductListResponse>> getProductList() {
        List<GetProductListResponse> productList = productService.getProductList();
        return ResponseEntity.ok(productList);
    }

    // Read Product Detail
    @GetMapping("/getDetail/{id}")
    public ResponseEntity<GetProductDetailResponse> getProductDetail(@PathVariable Integer id) {
        GetProductDetailResponse productDetail = productService.getProductDetail(id);
        return ResponseEntity.ok(productDetail);
    }

    @GetMapping("/getDetails")
    public ResponseEntity<Map<Integer, GetProductDetailResponse>> getProductDetails(
            @RequestParam("ids") Set<Integer> ids) {
        Map<Integer, GetProductDetailResponse> productDetailsMap = productService.getProductDetails(ids);
        return ResponseEntity.ok(productDetailsMap);
    }

    // Update Product
    @PutMapping("/update")
    public ResponseEntity<Void> updateProduct(UpdateProductRequest updateProductRequest) {
        productService.updateProduct(updateProductRequest);
        return ResponseEntity.ok().build();
    }

    // Delete Product
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
    @PutMapping("/batchUpdateStockQuantity")
    public void updateProductsStock(@RequestBody Map<Integer, Integer> stockUpdates) {
        productService.updateProductsStock(stockUpdates);
    }

}
