package com.ibm.demo.product;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public CreateProductResponse createProduct(CreateProductRequest product_DTO) {
        Product newProduct = new Product();
        newProduct.setName(product_DTO.getName());
        newProduct.setPrice(product_DTO.getPrice());
        newProduct.setStockQty(product_DTO.getStockQty());
        newProduct.setSaleStatus(1001);
        Product savedProduct = productRepository.save(newProduct);
        CreateProductResponse createProductResponseDTO = new CreateProductResponse(savedProduct.getId(),
                savedProduct.getName(), savedProduct.getPrice(), savedProduct.getSaleStatus(),
                savedProduct.getStockQty(), savedProduct.getCreateDate());
        return createProductResponseDTO;

    }

    public List<GetProductListResponse> getProductList() {
        return productRepository.getProductList();
    }

    public GetProductDetailResponse getProductDetail(Integer id) {
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new NullPointerException("Product not found with id: " + id));
        GetProductDetailResponse product_dto = mapProductToDetailResponse(existingProduct);
        return product_dto;
    }

    public Map<Integer,GetProductDetailResponse> getProductDetails(Set<Integer> ids){
        if(ids == null || ids.isEmpty()){
            return new HashMap<>();
        }
        
        List<Product> products = productRepository.findAllById(ids);

        if(products.size()!=ids.size()){
            Set<Integer> foundIdSet = new HashSet<>();
            for(Product product : products){
                foundIdSet.add(product.getId());
            }
            Set<Integer> missingIds = new HashSet<>(ids);
            missingIds.removeAll(foundIdSet);
            throw new NullPointerException("Products not found with ids: " + missingIds);
        }

        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();

        for(Product product : products){
            // Convert the product entity to its DTO representation
            GetProductDetailResponse detailResponse = mapProductToDetailResponse(product);
            // Add the product ID and its DTO to the map
            productDetailsMap.put(product.getId(), detailResponse);
        }        

        return productDetailsMap;
    }

    @Transactional
    public UpdateProductResponse updateProduct(UpdateProductRequest updateProductRequestDto) {
        Product existingProduct = productRepository.findById(updateProductRequestDto.getId())
                .orElseThrow(() -> new NullPointerException(
                        "Product not found with id: " + updateProductRequestDto.getId()));
        existingProduct.setName(updateProductRequestDto.getName());
        existingProduct.setPrice(updateProductRequestDto.getPrice());
        existingProduct.setSaleStatus(updateProductRequestDto.getSaleStatus());
        existingProduct.setStockQty(updateProductRequestDto.getStockQty());
        Product updatedProduct = productRepository.save(existingProduct);
        UpdateProductResponse updatedProductResponseDto = new UpdateProductResponse(updatedProduct.getId(),
                updatedProduct.getName(), updatedProduct.getPrice(), updatedProduct.getSaleStatus(),
                updatedProduct.getStockQty(), updatedProduct.getCreateDate(), updatedProduct.getModifiedDate());
        return updatedProductResponseDto;
    }

    @Transactional
    public void deleteProduct(Integer id) {
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new NullPointerException("Product not found with id: " + id));
        existingProduct.setSaleStatus(1002);
        productRepository.save(existingProduct);
    }

    // Helper method to map Product entity to GetProductDetailResponse DTO
    private GetProductDetailResponse mapProductToDetailResponse(Product product) {
        return new GetProductDetailResponse(
                product.getName(),
                product.getPrice(),
                product.getSaleStatus(),
                product.getStockQty(),
                product.getCreateDate(),
                product.getModifiedDate());
    }
}
