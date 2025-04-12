package com.example.demo;

import com.example.demo.entity.Product;
import com.example.demo.service.ProductService; // Import ProductService
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus; // Import HttpStatus
import org.springframework.http.ResponseEntity; // Import ResponseEntity
import org.springframework.web.bind.annotation.*; // Import necessary annotations

import java.util.List; // Import List
import java.util.Optional; // Import Optional

@RestController // 標示為 REST 控制器
@RequestMapping("/api") // Add base path for API endpoints
public class ProductController {


    // Inject TestService for CRUD operations
    @Autowired
    private ProductService productService;


// 產品 CRUD 端點

// 建立 (POST /api/createProducts)
@PostMapping("/createProducts")
public ResponseEntity<Product> createProduct(@RequestBody Product product) {
    Product createdProduct = productService.createProduct(product);
    return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
}

// 讀取所有 (GET /api/getProducts)
@GetMapping("/getProducts")
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
@PutMapping("/updateProduct/{id}")
public ResponseEntity<Product> updateProduct(@PathVariable Long id, @RequestBody Product productDetails) {
    Optional<Product> updatedProductOptional = productService.updateProduct(id, productDetails);
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

}
