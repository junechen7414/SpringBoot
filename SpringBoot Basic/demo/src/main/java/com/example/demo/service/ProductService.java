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

    // Removed original demo methods (performJpaOperations, testConnection)
}
