package com.example.demo;

import java.util.List; // Import List
import java.util.Optional; // Import Optional

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus; // Import HttpStatus
import org.springframework.http.ResponseEntity; // Import ResponseEntity
// Import necessary annotations
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.entity.Product;
import com.example.demo.service.ProductService; // Import ProductService

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;


@RestController // 標示為 REST 控制器
@RequestMapping("/api") // Add base path for API endpoints
public class ProductController {


    // Inject TestService for CRUD operations
    @Autowired
    private ProductService productService;


// 產品 CRUD 端點

// 建立 (POST /api/createProducts)
@PostMapping("/createProducts")
public ResponseEntity<Product> createProduct(@RequestBody Product_DTO product_dto) {
    Product createdProduct = productService.createProduct(product_dto);
    return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
}

// 讀取所有 (GET /api/getAllProducts)
@GetMapping("/getAllProducts")
public ResponseEntity<List<Product>> getAllProducts() {
    List<Product> products = productService.getAllProducts();
    return new ResponseEntity<>(products, HttpStatus.OK);
}

// 根據ID讀取 (GET /api/getProduct/{id})
@GetMapping("/getProduct/{id}")
public ResponseEntity<Product> getProductById(@PathVariable Long id) {
    Optional<Product> productOptional = productService.getProductById(id);
    return productOptional
            .map(product -> new ResponseEntity<>(product, HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
}

// 更新 (PUT /api/updateProduct/{id})
@PutMapping("/updateProduct")
public ResponseEntity<Product> updateProduct(@RequestBody Product productDetails) {
    Optional<Product> updatedProductOptional = productService.updateProduct(productDetails);
    return updatedProductOptional
            .map(product -> new ResponseEntity<>(product, HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
}

// 刪除 (DELETE /api/deleteProduct/{id})
@DeleteMapping("/deleteProduct/{id}")
public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
    boolean deleted = productService.deleteProduct(id);
    if (deleted) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    } else {
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
}

// --- Custom Query Endpoint ---

// 根據名稱查詢，可選擇不同的查詢實現方式
// GET /api/products/search/by-name?name=...&methodType=[jpa|jpql|native]
@GetMapping("/products/search/by-name")
public ResponseEntity<List<Product>> getProductsByName(
        @RequestParam String name,
        @RequestParam(required = false, defaultValue = "jpa") String methodType) {

    List<Product> products;

    switch (methodType.toLowerCase()) {
        case "jpql":
            products = productService.findProductsByNameJpql(name);
            break;
        case "native":
            products = productService.findProductsByNameNative(name);
            break;
        case "jpa":
        default: // 預設或無效時使用 JPA Query Method
            products = productService.findProductsByName(name);
            break;
    }

    if (products.isEmpty()) {
        // 即使找不到也回傳 OK 和空列表，符合 RESTful 風格
        // 如果希望找不到時回傳 404，可以取消註解下一行並註解掉 return new ResponseEntity<>(products, HttpStatus.OK);
        // return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        return new ResponseEntity<>(products, HttpStatus.OK);
    }
    return new ResponseEntity<>(products, HttpStatus.OK);
}

// 取得價格高於指定金額的商品
// GET /api/products/above-price?price=...
@Operation(summary = "查詢價格高於指定金額的商品")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "成功回傳商品列表"),
    @ApiResponse(responseCode = "400", description = "參數錯誤")
})
@Parameter(name = "price", description = "金額", required = true)
@GetMapping("/products/above-price")
public ResponseEntity<List<Product>> getProductsAbovePrice(@RequestParam Integer price) {
    List<Product> products = productService.getProductsAbovePrice(price);
    return new ResponseEntity<>(products, HttpStatus.OK);
}

}
