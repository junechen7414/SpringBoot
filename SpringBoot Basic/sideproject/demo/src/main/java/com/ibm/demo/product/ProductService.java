package com.ibm.demo.product;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ibm.demo.exception.InvalidRequestException;
import com.ibm.demo.exception.ResourceNotFoundException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductAlreadyExistException;
import com.ibm.demo.product.DTO.CreateProductRequest;
import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.product.DTO.GetProductListResponse;
import com.ibm.demo.product.DTO.UpdateProductRequest;

import jakarta.transaction.Transactional;
import lombok.NonNull;

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
    public Integer createProduct(CreateProductRequest product_DTO) {
        // 1. 驗證商品名稱是否已存在
        String requestProductName = product_DTO.getName();        
        checkProductExistsByNameOrThrow(requestProductName);
        // 2. 建構商品實體
        Product newProduct = new Product();
        newProduct.setName(requestProductName);
        newProduct.setPrice(product_DTO.getPrice());
        newProduct.setStockQty(product_DTO.getStockQty());
        // 預設銷售狀態為 1001 (可銷售)
        newProduct.setSaleStatus(1001);
        // 3. 儲存商品資料
        Product savedProduct = productRepository.save(newProduct);
        return savedProduct.getId();
    }

    /**
     * 獲取所有商品的列表。
     *
     * @return 包含所有商品列表資訊的回應 DTO 列表
     */
    public List<GetProductListResponse> getProductList(Integer status) {
        if (status == null) {
            return productRepository.findAllProducts();
        } else {
            return productRepository.findBySaleStatus(status);
        }
    }

    /**
     * 根據 ID 獲取單一商品的詳細資訊。
     *
     * @param id 商品 ID
     * @return 包含商品詳細資訊的回應 DTO
     */
    public GetProductDetailResponse getProductDetail(Integer id) {
        Product existingProduct = findProductByIdOrThrow(id);
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
     */
    public Map<Integer, GetProductDetailResponse> getProductDetails(Set<Integer> ids) {
        // 使用多個ID查詢多個商品實體，若有對應不上的ID會忽略 continue，若傳入null會拋出例外
        List<Product> products = findProductsByIds(ids);
        // 驗證找出的所有商品狀態皆為可銷售
        // validateProductsAreSellable(products);
        // 將商品映射到以商品ID為key的DTO Map
        return mapProductsToDetailResponses(products);
    }

    /**
     * 更新現有商品的資訊。
     *
     * @param updateProductRequestDto 包含要更新的商品資訊的請求 DTO
     * @return 包含已更新商品資訊的回應 DTO
     */
    @Transactional
    public void updateProduct(UpdateProductRequest updateProductRequestDto) {
        // 1. 取得商品實體並驗證帳戶是否存在否則拋出例外
        Integer productId = updateProductRequestDto.getId();
        Product existingProduct = findProductByIdOrThrow(productId);
        // 2. 驗證商品名稱是否已存在
        String requestProductName = updateProductRequestDto.getName();
        checkProductExistsByNameOrThrow(requestProductName);
        // 3. 更新商品屬性
        existingProduct.setName(updateProductRequestDto.getName());
        existingProduct.setPrice(updateProductRequestDto.getPrice());
        existingProduct.setSaleStatus(updateProductRequestDto.getSaleStatus());
        existingProduct.setStockQty(updateProductRequestDto.getStockQty());
        // 4. 儲存商品資料
        productRepository.save(existingProduct);
    }

    /**
     * 刪除商品 (邏輯刪除，將銷售狀態設為 1002)。
     *
     * @param id 要刪除的商品 ID
     */
    @Transactional
    public void deleteProduct(Integer id) {
        Product existingProduct = findProductByIdOrThrow(id);
        // 將銷售狀態設為 1002 (不可銷售)
        if(existingProduct.getSaleStatus() == 1002) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        existingProduct.setSaleStatus(1002);
        productRepository.save(existingProduct);
    }

    /**
     * 批量更新商品庫存。
     * 
     * @param stockUpdates Map<商品ID, 新庫存數量>
     */
    @Transactional
    public void updateProductsStock(Map<Integer, Integer> stockUpdates) {
        if (stockUpdates == null || stockUpdates.isEmpty()) {
            throw new InvalidRequestException("Stock updates cannot be null or empty");
        }

        // 驗證 stockUpdates 中的值不能為 null，且不能為負數 (如果這是業務需求)
        for (Map.Entry<Integer, Integer> entry : stockUpdates.entrySet()) {
            Integer productId = entry.getKey();
            Integer newStock = entry.getValue();

            if (productId == null) {
                // 理論上，JSON key 反序列化為 Integer key 時，key 不會是 null
                // 但為求完整性，可以加上此判斷
                throw new InvalidRequestException("Product ID in stock updates cannot be null.");
            }
            if (newStock == null) {
                throw new InvalidRequestException("Stock quantity for product ID " + productId + " cannot be null.");
            }
            if (newStock < 0) {
                // 假設庫存不能為負
                throw new InvalidRequestException("Stock quantity for product ID " + productId + " cannot be negative.");
            }
        }

        Set<Integer> productIdsToUpdate = stockUpdates.keySet();
        List<Product> products = findProductsByIds(productIdsToUpdate);

        // 確保請求中的所有 ID 都找到了對應的商品，如果需要嚴格檢查
        if (products.size() != productIdsToUpdate.size()) {
            Set<Integer> foundProductIds = products.stream().map(Product::getId).collect(Collectors.toSet());
            productIdsToUpdate.removeAll(foundProductIds); // 找出未找到的 ID
            throw new ResourceNotFoundException("Some products not found for IDs: " + productIdsToUpdate);
        }
        
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
     * 驗證單一商品是否可銷售。
     *
     * @param product 商品實體
     */
    // public void validateProductIsSellable(Product product) {
    //     if (product.getSaleStatus() == 1002) {
    //         throw new ProductInactiveException("商品id: " + product.getId() + " 不可銷售");
    //     }
    // }

    /**
     * 驗證商品列表中的所有商品是否都可銷售。
     *
     * @param products 商品實體列表
     */
    // public void validateProductsAreSellable(List<Product> products) {
    //     for (Product product : products) {
    //         validateProductIsSellable(product); // 直接呼叫，讓 ProductInactiveException 自然拋出
    //     }
    // }

    /**
     * 根據一組 ID 查詢多個商品實體。
     *
     * @param productIds 商品 ID 集合
     * @return 包含查詢到的商品實體的列表
     */
    public List<Product> findProductsByIds(Set<Integer> productIds) {
        if (productIds == null) {
            throw new InvalidRequestException("Product IDs cannot be null or empty");
        }
        List<Product> products = productRepository.findAllById(productIds);
        return products;
    }

    /**
     * 將 Product 實體列表映射到以商品 ID 為鍵的 GetProductDetailResponse DTO Map。
     *
     * @param products 商品實體列表
     * @return 以商品 ID 為鍵，商品詳細資訊 DTO 為值的 Map
     */
    public Map<Integer, GetProductDetailResponse> mapProductsToDetailResponses(List<Product> products) {
        Map<Integer, GetProductDetailResponse> productDetailsMap = new HashMap<>();
        for (Product product : products) {
            GetProductDetailResponse detailResponse = mapProductToDetailResponse(product);
            productDetailsMap.put(product.getId(), detailResponse);
        }
        return productDetailsMap;
    }

    // 根據商品名稱檢查商品是否已存在
    public void checkProductExistsByNameOrThrow(String productName) {
        if(productRepository.existsByName(productName)){
            throw new ProductAlreadyExistException(productName + " already exists");
        }
    }

    /**
     * 根據 ID 查找單一商品實體，若商品不存在拋出例外。主要共用丟同樣例外的部分不然code都看起來很長且自動排版還分兩行，#%^#(˙^*。
     *
     * @param productId 商品 ID
     * @return 找到的商品實體
     */
    public Product findProductByIdOrThrow(@NonNull Integer productId) {
        Product result = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        return result;
    }
}
