package com.example.demo.service;

import com.example.demo.entity.Product; // Updated import path for Product
import com.example.demo.repository.ProductRepository; // Updated import path for ProductRepository
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService { // Renamed class

    @Autowired
    private ProductRepository productRepository; // Updated repository type and name

    // --- CRUD Operations for API ---

    @Transactional
    public Product createProduct(Product product) { // Renamed method and parameter type
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() { // Renamed method and return type
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) { // Renamed method and return type
        return productRepository.findById(id);
    }

    @Transactional
    public Optional<Product> updateProduct(Long id, Product productDetails) { // Renamed method and parameter type
        return productRepository.findById(id).map(existingProduct -> {
            existingProduct.setName(productDetails.getName());
            existingProduct.setPrice(productDetails.getPrice()); // Updated to use price
            return productRepository.save(existingProduct);
        });
    }

    @Transactional
    public boolean deleteProduct(Long id) { // Renamed method
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return true;
        }
        return false;
    }

    // --- Custom Query Methods ---

    // 使用 JPA Query Method 根據名稱查詢 (區分大小寫)
    public List<Product> findProductsByName(String name) {
        return productRepository.findByName(name);
    }

    // 使用 JPQL 根據名稱查詢 (區分大小寫)
    public List<Product> findProductsByNameJpql(String name) {
        return productRepository.findByNameWithJpql(name);
    }

    // 使用 Native SQL 根據名稱查詢 (區分大小寫)
    public List<Product> findProductsByNameNative(String name) {
        return productRepository.findByNameWithNativeSql(name);
    }

    // 使用 Stream API 和 Lambda 運算式篩選價格高於指定金額的商品
    public List<Product> getProductsAbovePrice(Integer price) {
        List<Product> allProducts = productRepository.findAll();
        return allProducts.stream()
                .filter(product -> product.getPrice() > price)
                .collect(java.util.stream.Collectors.toList());
    }
}
