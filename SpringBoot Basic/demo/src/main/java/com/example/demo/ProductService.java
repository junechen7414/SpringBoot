package com.example.demo;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Product_DTO getProductById(Long id) {
        Product existingProduct = productRepository.findById(id)
            .orElseThrow(() -> new NullPointerException("Product not found with id: " + id));        
        Product_DTO product_dto = new Product_DTO(existingProduct.getName(), existingProduct.getPrice());
        return product_dto;        
    }

    @Transactional
    public Product_DTO updateProduct(Product productDetails) {
        Long productId = productDetails.getId();
        // Find product or throw exception if not found
        Product existingProduct = productRepository.findById(productId)
            .orElseThrow(() -> new NullPointerException("Product not found with id: " + productId));        
        existingProduct.setName(productDetails.getName());
        existingProduct.setPrice(productDetails.getPrice());
        Product updatedProduct = productRepository.save(existingProduct);
        Product_DTO product_dto = new Product_DTO(updatedProduct.getName(), updatedProduct.getPrice());
        return product_dto;            
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
