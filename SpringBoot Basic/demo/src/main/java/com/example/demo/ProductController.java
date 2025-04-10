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

// 建立 (POST /api/products)
@PostMapping("/products")
public ResponseEntity<Product> createProduct(@RequestBody Product product) {
    Product createdProduct = productService.createProduct(product);
    return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
}

// 讀取所有 (GET /api/products)
@GetMapping("/products")
public ResponseEntity<List<Product>> getAllProducts() {
    List<Product> products = productService.getAllProducts();
    return new ResponseEntity<>(products, HttpStatus.OK);
}

// 根據ID讀取 (GET /api/products/{id})
@GetMapping("/products/{id}")
public ResponseEntity<Product> getProductById(@PathVariable Long id) {
    Optional<Product> productOptional = productService.getProductById(id);
    return productOptional
            .map(product -> new ResponseEntity<>(product, HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
}

// 更新 (PUT /api/products/{id})
@PutMapping("/products/{id}")
public ResponseEntity<Product> updateProduct(@PathVariable Long id, @RequestBody Product productDetails) {
    Optional<Product> updatedProductOptional = productService.updateProduct(id, productDetails);
    return updatedProductOptional
            .map(product -> new ResponseEntity<>(product, HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
}

// 刪除 (DELETE /api/products/{id})
@DeleteMapping("/products/{id}")
public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
    boolean deleted = productService.deleteProduct(id);
    if (deleted) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    } else {
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
}
}
