package com.ibm.demo.product;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ibm.demo.product.DTO.CreateProductRequestDTO;
import com.ibm.demo.product.DTO.ProductDetailResponseDTO;
import com.ibm.demo.product.DTO.ProductListResponseDTO;
import com.ibm.demo.product.DTO.UpdateProductDTO;

import jakarta.transaction.Transactional;

@Service
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;

    @Transactional
    public Product createProduct(CreateProductRequestDTO product_DTO) {
        Product newProduct = new Product(product_DTO.getName(), product_DTO.getPrice(), product_DTO.getSaleStatus(), product_DTO.getStockQty());
        return productRepository.save(newProduct);
    }
    
    public List<ProductListResponseDTO> getProductList() {
        return productRepository.getProductList();
    }

    public ProductDetailResponseDTO getProductDetail(int id) {
        Product existingProduct = productRepository.findById(id)
            .orElseThrow(() -> new NullPointerException("Product not found with id: " + id));        
        ProductDetailResponseDTO product_dto = new ProductDetailResponseDTO(existingProduct.getName(), existingProduct.getPrice(),existingProduct.getSaleStatus(),existingProduct.getStockQty(),existingProduct.getCreateDate(),existingProduct.getModifiedDate());
        return product_dto;        
    }

    @Transactional
    public UpdateProductDTO updateProduct(UpdateProductDTO updateProduct_Dto) {
        Product existingProduct = productRepository.findById(updateProduct_Dto.getId())
        .orElseThrow(() -> new NullPointerException("Product not found with id: " + updateProduct_Dto.getId()));
        existingProduct.setName(updateProduct_Dto.getName());
        existingProduct.setPrice(updateProduct_Dto.getPrice());
        existingProduct.setSaleStatus(updateProduct_Dto.getSaleStatus());
        existingProduct.setStockQty(updateProduct_Dto.getStockQty());        
        Product updatedProduct = productRepository.save(existingProduct);
        UpdateProductDTO updatedProduct_dto = new UpdateProductDTO(updatedProduct.getId(),updatedProduct.getName(), updatedProduct.getPrice(),updatedProduct.getSaleStatus(),updatedProduct.getStockQty());
        return updatedProduct_dto;
    }

    @Transactional
    public void deleteProduct(int id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);            
        } else {
            throw new NullPointerException("Product not found with id: " + id);
        } 
    }
}
