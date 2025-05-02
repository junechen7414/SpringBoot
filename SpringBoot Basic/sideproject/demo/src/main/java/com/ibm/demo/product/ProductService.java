package com.ibm.demo.product;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.ibm.demo.exception.InvalidRequestException;
import com.ibm.demo.exception.NotFound.ProductNotFoundException;
import com.ibm.demo.product.DTO.CreateProductRequest;
import com.ibm.demo.product.DTO.CreateProductResponse;
import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.product.DTO.GetProductListResponse;
import com.ibm.demo.product.DTO.UpdateProductRequest;
import com.ibm.demo.product.DTO.UpdateProductResponse;

import jakarta.transaction.Transactional;

@Service
public class ProductService {
    private final ProductRepository productRepository;    

    /**
     * 建構子，注入 ProductRepository。
     *
     * @param productRepository 商品資料庫存取介面
     */
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;        
    }

    /**
     * 建立新商品。
     *
     * @param product_DTO 包含新商品資訊的請求 DTO
     * @return 包含已建立商品資訊的回應 DTO
     */
    @Transactional
    public CreateProductResponse createProduct(CreateProductRequest product_DTO) {
        Product newProduct = new Product();
        newProduct.setName(product_DTO.getName());
        newProduct.setPrice(product_DTO.getPrice());
        newProduct.setStockQty(product_DTO.getStockQty());
        // 預設銷售狀態為 1001 (可銷售)
        newProduct.setSaleStatus(1001);
        Product savedProduct = productRepository.save(newProduct);
        return new CreateProductResponse(savedProduct.getId(),
                savedProduct.getName(), savedProduct.getPrice(), savedProduct.getSaleStatus(),
                savedProduct.getStockQty(), savedProduct.getCreateDate());
    }

    /**
     * 獲取所有商品的列表。
     *
     * @return 包含所有商品列表資訊的回應 DTO 列表
     */
    public List<GetProductListResponse> getProductList() {
        return productRepository.getProductList();
    }

    /**
     * 根據 ID 獲取單一商品的詳細資訊。
     *
     * @param id 商品 ID
     * @return 包含商品詳細資訊的回應 DTO
     * @throws NullPointerException 若找不到指定 ID 的商品
     */
    public GetProductDetailResponse getProductDetail(Integer id) {
        Product existingProduct = findProductById(id);
        return mapProductToDetailResponse(existingProduct);
    }

    /**
     * 根據一組 ID 獲取多個商品的詳細資訊。
     * 1. 驗證商品 ID 集合是否有效。
     * 2. 查詢所有指定的商品是否存在。
     * 3. 驗證所有查詢到的商品是否可銷售。
     * 4. 將商品實體轉換為詳細資訊 DTO。
     *
     * @param ids 商品 ID 集合
     * @return 以商品 ID 為鍵，商品詳細資訊 DTO 為值的 Map
     * @throws IllegalArgumentException 若商品 ID 集合為空或 null
     * @throws NullPointerException     若有商品 ID 找不到對應的商品
     * @throws IllegalArgumentException 若有商品不可銷售
     */
    public Map<Integer, GetProductDetailResponse> getProductDetails(Set<Integer> ids) {
        // 驗證傳入的商品ID不為null或空集合
        validateProductIds(ids);
        // 使用多個ID查詢多個商品實體，若有對應不上的ID會拋出例外
        List<Product> products = findProductsByIds(ids);
        // 驗證找出的所有商品狀態皆為可銷售
        validateProductsAreSellable(products);
        // 將商品映射到以商品ID為key的DTO Map
        return mapProductsToDetailResponses(products);
    }

    /**
     * 更新現有商品的資訊。
     *
     * @param updateProductRequestDto 包含要更新的商品資訊的請求 DTO
     * @return 包含已更新商品資訊的回應 DTO
     * @throws NullPointerException 若找不到指定 ID 的商品
     */
    @Transactional
    public UpdateProductResponse updateProduct(UpdateProductRequest updateProductRequestDto) {
        // 1. 取得商品實體並驗證帳戶是否存在否則拋出例外
        Integer productId = updateProductRequestDto.getId();
        Product existingProduct = findProductById(productId);        
        // 2. 更新商品屬性
        existingProduct.setName(updateProductRequestDto.getName());
        existingProduct.setPrice(updateProductRequestDto.getPrice());
        existingProduct.setSaleStatus(updateProductRequestDto.getSaleStatus());
        existingProduct.setStockQty(updateProductRequestDto.getStockQty());
        // 3. 儲存商品資料
        Product updatedProduct = productRepository.save(existingProduct);        
        return mapProductToUpdateResponse(updatedProduct);
    }

    /**
     * 刪除商品 (邏輯刪除，將銷售狀態設為 1002)。
     *
     * @param id 要刪除的商品 ID
     * @throws NullPointerException 若找不到指定 ID 的商品
     */
    @Transactional
    public void deleteProduct(Integer id) {
        Product existingProduct = findProductById(id);
        // 將銷售狀態設為 1002 (不可銷售)
        existingProduct.setSaleStatus(1002);
        productRepository.save(existingProduct);
    }

    /**
     * 批量更新商品庫存。
     * 
     * @param stockUpdates Map<商品ID, 新庫存數量>
     * @throws InvalidRequestException 若更新資料為空
     * @throws NullPointerException     若有商品不存在
     */
    @Transactional
    public void updateProductsStock(Map<Integer, Integer> stockUpdates) {
        if (stockUpdates == null || stockUpdates.isEmpty()) {
            throw new InvalidRequestException("Stock updates cannot be null or empty");
        }

        List<Product> products = findProductsByIds(stockUpdates.keySet());

        for (Product product : products) {
            Integer newStock = stockUpdates.get(product.getId());
            product.setStockQty(newStock);
        }

        productRepository.saveAll(products);
    }

    /**
     * 將單一 Product 實體映射到 GetProductDetailResponse DTO。
     *
     * @param product 商品實體
     * @return 商品詳細資訊 DTO
     */
    private GetProductDetailResponse mapProductToDetailResponse(Product product) {
        return new GetProductDetailResponse(
                product.getName(),
                product.getPrice(),
                product.getSaleStatus(),
                product.getStockQty());
    }

    /**
     * 將單一 Product 實體映射到 UpdateProductResponse DTO。
     *
     * @param product 商品實體
     * @return 商品更新回應 DTO
     */
    private UpdateProductResponse mapProductToUpdateResponse(Product product) {
        return new UpdateProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getSaleStatus(),
                product.getStockQty(),
                product.getCreateDate(),
                product.getModifiedDate());
    }

    /**
     * 使用 UpdateProductRequest DTO 的資料更新 Product 實體的屬性。
     *
     * @param product 要更新的商品實體
     * @param request 包含更新資料的請求 DTO
     */
    // private void updateProductEntity(Product product, UpdateProductRequest
    // request) {
    // product.setName(request.getName());
    // product.setPrice(request.getPrice());
    // product.setSaleStatus(request.getSaleStatus());
    // product.setStockQty(request.getStockQty());
    // // JPA 會自動處理 modifiedDate 的更新 (如果 @LastModifiedDate 有設定)
    // }

    /**
     * 驗證一組商品 ID 是否有效且可銷售。
     * 1. 檢查商品 ID 集合是否為空或 null。
     * 2. 查詢商品是否存在，若有缺失則拋出例外。
     * 3. 驗證每個商品的銷售狀態是否為可銷售。
     *
     * @param productIds 商品 ID 集合
     * @throws IllegalArgumentException 若商品 ID 集合為空或 null
     * @throws NullPointerException     若有商品 ID 找不到對應的商品
     * @throws IllegalArgumentException 若有商品不可銷售
     */
    public void validateProducts(Set<Integer> productIds) {
        validateProductIds(productIds);
        List<Product> products = findProductsByIds(productIds);
        validateProductsAreSellable(products);
    }

    /**
     * 驗證單一商品是否可銷售。
     *
     * @param product 商品實體
     * @throws InvalidRequestException 若商品不可銷售 (銷售狀態為 1002)
     */
    public void validateProductIsSellable(Product product) {
        if (product.getSaleStatus() == 1002) {
            throw new InvalidRequestException("商品id: " + product.getId() + " 不可銷售");
        }
    }

    /**
     * 驗證商品列表中的所有商品是否都可銷售。
     *
     * @param products 商品實體列表
     * @throws InvalidRequestException 若列表中有任何商品不可銷售
     */
    public void validateProductsAreSellable(List<Product> products) {
        for (Product product : products) {
            try {
                validateProductIsSellable(product);
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException(e.getMessage(), e);
            }
        }
    }

    /**
     * 根據一組 ID 查詢多個商品實體。
     *
     * @param productIds 商品 ID 集合
     * @return 包含查詢到的商品實體的列表
     * @throws InvalidRequestException 若有商品 ID 找不到對應的商品，並列出缺失的 ID
     */
    public List<Product> findProductsByIds(Set<Integer> productIds) {
        validateProductIds(productIds);
        List<Product> products = productRepository.findAllById(productIds);
        if (products.size() != productIds.size()) {
            // 使用傳統迴圈找出缺失的 ID
            Set<Integer> foundIdSet = new HashSet<>();
            for (Product product : products) {
                foundIdSet.add(product.getId());
            }
            Set<Integer> missingIds = new HashSet<>(productIds);
            missingIds.removeAll(foundIdSet);
            throw new ProductNotFoundException("Products not found with ids: " + missingIds);
        }
        return products;
    }

    /**
     * 驗證商品 ID 集合是否為空或 null。
     *
     * @param productIds 商品 ID 集合
     * @throws InvalidRequestException 若商品 ID 集合為空或 null
     */
    public void validateProductIds(Set<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            throw new InvalidRequestException("Product IDs cannot be null or empty");
        }
    }

    /**
     * 將 Product 實體列表映射到以商品 ID 為鍵的 GetProductDetailResponse DTO Map。
     *
     * @param products 商品實體列表
     * @return 以商品 ID 為鍵，商品詳細資訊 DTO 為值的 Map
     */
    public Map<Integer, GetProductDetailResponse> mapProductsToDetailResponses(List<Product> products) {
        // 使用傳統迴圈進行轉換
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        for (Product product : products) {
            GetProductDetailResponse detailResponse = mapProductToDetailResponse(product);
            productDetailsMap.put(product.getId(), detailResponse);
        }
        return productDetailsMap;
    }

    /**
     * 根據 ID 查找單一商品實體。若商品不存在拋出例外
     *
     * @param productId 商品 ID
     * @return 找到的商品實體
     * @throws NullPointerException 若找不到指定 ID 的商品
     */
    public Product findProductById(Integer productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));
    }
}
