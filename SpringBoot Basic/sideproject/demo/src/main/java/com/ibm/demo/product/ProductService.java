package com.ibm.demo.product;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ibm.demo.product.DTO.CreateProductRequestDTO;
import com.ibm.demo.product.DTO.CreateProductResponseDTO;
import com.ibm.demo.product.DTO.ProductDetailResponseDTO;
import com.ibm.demo.product.DTO.ProductListResponseDTO;
import com.ibm.demo.product.DTO.UpdateProductRequestDTO;
import com.ibm.demo.product.DTO.UpdateProductResponseDTO;

import jakarta.transaction.Transactional;

@Service
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;

    @Transactional
    public CreateProductResponseDTO createProduct(CreateProductRequestDTO product_DTO) {
        Product newProduct = new Product(product_DTO.getName(), product_DTO.getPrice(), product_DTO.getSaleStatus(), product_DTO.getStockQty());
        Product savedProduct = productRepository.save(newProduct);
        CreateProductResponseDTO createProductResponseDTO = new CreateProductResponseDTO(savedProduct.getId(),savedProduct.getName(), savedProduct.getPrice(),savedProduct.getSaleStatus(),savedProduct.getStockQty(),savedProduct.getCreateDate());
        return createProductResponseDTO;
        
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
    public UpdateProductResponseDTO updateProduct(UpdateProductRequestDTO updateProductRequestDto) {
        Product existingProduct = productRepository.findById(updateProductRequestDto.getId())
        .orElseThrow(() -> new NullPointerException("Product not found with id: " + updateProductRequestDto.getId()));
        existingProduct.setName(updateProductRequestDto.getName());
        existingProduct.setPrice(updateProductRequestDto.getPrice());
        existingProduct.setSaleStatus(updateProductRequestDto.getSaleStatus());
        existingProduct.setStockQty(updateProductRequestDto.getStockQty());        
        Product updatedProduct = productRepository.save(existingProduct);
        UpdateProductResponseDTO updatedProductResponseDto = new UpdateProductResponseDTO(updatedProduct.getId(),updatedProduct.getName(), updatedProduct.getPrice(),updatedProduct.getSaleStatus(),updatedProduct.getStockQty(),updatedProduct.getCreateDate(),updatedProduct.getModifiedDate());
        return updatedProductResponseDto;
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
