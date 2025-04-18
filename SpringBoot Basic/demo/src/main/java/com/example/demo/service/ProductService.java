package com.example.demo.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Product_DTO;
import com.example.demo.entity.Product; // Updated import path for Product
import com.example.demo.repository.ProductRepository; // Updated import path for ProductRepository

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository; // Updated repository type and name

    // --- CRUD Operations for API ---

    @Transactional
    public Product createProduct(Product_DTO product_dto) {
        Product newProduct = new Product(product_dto.getName(), product_dto.getPrice());
        return productRepository.save(newProduct);        
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional
    public Optional<Product> updateProduct(Product productDetails) {
        Optional<Product> existingProductOptional = productRepository.findById(productDetails.getId());
        if (!existingProductOptional.isEmpty()) {
            Product existingProduct = existingProductOptional.get();
            existingProduct.setName(productDetails.getName());
            existingProduct.setPrice(productDetails.getPrice());
            return Optional.of(productRepository.save(existingProduct));
        }
        else{
            return Optional.empty();
        }        
    }

    @Transactional
    public boolean deleteProduct(Long id) {
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
