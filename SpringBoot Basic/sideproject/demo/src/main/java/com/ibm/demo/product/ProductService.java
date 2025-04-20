package com.ibm.demo.product;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ibm.demo.product.DTO.CreateProductRequest;
import com.ibm.demo.product.DTO.CreateProductResponse;
import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.product.DTO.GetProductListResponse;
import com.ibm.demo.product.DTO.UpdateProductRequest;
import com.ibm.demo.product.DTO.UpdateProductResponse;

import jakarta.transaction.Transactional;

@Service
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;

    @Transactional
    public CreateProductResponse createProduct(CreateProductRequest product_DTO) {
        Product newProduct = new Product(product_DTO.getName(), product_DTO.getPrice(), product_DTO.getSaleStatus(), product_DTO.getStockQty());
        Product savedProduct = productRepository.save(newProduct);
        CreateProductResponse createProductResponseDTO = new CreateProductResponse(savedProduct.getId(),savedProduct.getName(), savedProduct.getPrice(),savedProduct.getSaleStatus(),savedProduct.getStockQty(),savedProduct.getCreateDate());
        return createProductResponseDTO;
        
    }
    
    public List<GetProductListResponse> getProductList() {
        return productRepository.getProductList();
    }

    public GetProductDetailResponse getProductDetail(int id) {
        Product existingProduct = productRepository.findById(id)
            .orElseThrow(() -> new NullPointerException("Product not found with id: " + id));        
        GetProductDetailResponse product_dto = new GetProductDetailResponse(existingProduct.getName(), existingProduct.getPrice(),existingProduct.getSaleStatus(),existingProduct.getStockQty(),existingProduct.getCreateDate(),existingProduct.getModifiedDate());
        return product_dto;        
    }

    @Transactional
    public UpdateProductResponse updateProduct(UpdateProductRequest updateProductRequestDto) {
        Product existingProduct = productRepository.findById(updateProductRequestDto.getId())
        .orElseThrow(() -> new NullPointerException("Product not found with id: " + updateProductRequestDto.getId()));
        existingProduct.setName(updateProductRequestDto.getName());
        existingProduct.setPrice(updateProductRequestDto.getPrice());
        existingProduct.setSaleStatus(updateProductRequestDto.getSaleStatus());
        existingProduct.setStockQty(updateProductRequestDto.getStockQty());        
        Product updatedProduct = productRepository.save(existingProduct);
        UpdateProductResponse updatedProductResponseDto = new UpdateProductResponse(updatedProduct.getId(),updatedProduct.getName(), updatedProduct.getPrice(),updatedProduct.getSaleStatus(),updatedProduct.getStockQty(),updatedProduct.getCreateDate(),updatedProduct.getModifiedDate());
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
