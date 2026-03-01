package com.ibm.demo.product;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.exception.BusinessLogicCheck.ProductAlreadyExistException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductStockNotEnoughException;
import com.ibm.demo.exception.BusinessLogicCheck.ResourceNotFoundException;
import com.ibm.demo.product.DTO.CreateProductRequest;
import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.product.DTO.GetProductListResponse;
import com.ibm.demo.product.DTO.UpdateProductRequest;
import com.ibm.demo.util.OrderItemRequest;
import com.ibm.demo.util.ServiceValidator;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    /**
     * 建構子，注入 Repository，已用lombok註解RequiredArgsConstructor定義。
     *
     * @param productRepository 商品資料庫存取介面
     */
    // public ProductService(ProductRepository productRepository) {
    //     this.productRepository = productRepository;
    // }

    /**
     * 建立新商品。
     *
     * @param product_DTO 包含新商品資訊的請求 DTO
     * @return 包含已建立商品資訊的回應 DTO
     */
    @Transactional
    public Integer createProduct(CreateProductRequest product_DTO) {
        ServiceValidator.validateNotNull(product_DTO, "Create product request");
        // 1. 驗證商品名稱是否已存在
        String requestProductName = product_DTO.name();
        checkProductExistsByNameOrThrow(requestProductName);
        // 2. 建構商品實體
        Product newProduct = Product.builder()
                .name(requestProductName)
                .price(product_DTO.price())
                .available(product_DTO.available())
                .saleStatus(ProductStatus.AVAILABLE.getCode())
                .build();
        // 3. 儲存商品資料
        Product savedProduct = productRepository.save(newProduct);
        return savedProduct.getId();
    }

    /**
     * 獲取所有商品的列表。
     *
     * @return 包含所有商品列表資訊的回應 DTO 列表
     */
    public List<GetProductListResponse> getProductList() {
        return productRepository.findAllProducts();
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
        // 注：@SQLRestriction 已保證只查詢未刪除且可銷售的商品
        List<Product> products = findProductsByIds(ids);
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
        ServiceValidator.validateNotNull(updateProductRequestDto, "Update product request");
        // 1. 取得商品實體並驗證帳戶是否存在否則拋出例外
        Integer productId = updateProductRequestDto.id();
        Product existingProduct = findProductByIdOrThrow(productId);
        // 2. 若商品名稱有變更，驗證新名稱是否已存在
        String requestProductName = updateProductRequestDto.name();
        if (!existingProduct.getName().equals(requestProductName)) {
            checkProductExistsByNameOrThrow(requestProductName);
        }
        // 3. 更新商品屬性
        existingProduct.setName(updateProductRequestDto.name());
        existingProduct.setPrice(updateProductRequestDto.price());
        existingProduct.setSaleStatus(updateProductRequestDto.saleStatus());
        existingProduct.setAvailable(updateProductRequestDto.available());
        // 4. 儲存商品資料
        productRepository.save(existingProduct);
    }

    /**
     * 刪除商品 (邏輯刪除，將銷售狀態設為不可銷售)。
     *
     * @param id 要刪除的商品 ID
     */
    @Transactional
    public void deleteProduct(Integer id) {
        Product existingProduct = findProductByIdOrThrow(id);
        // 使用 delete() 觸發 @SQLDelete，執行軟刪除邏輯
        productRepository.delete(existingProduct);
    }

    @Transactional
    public void processOrderItems(Set<OrderItemRequest> originalItems, Set<OrderItemRequest> updatedItems) {
        // 0. 驗證輸入的訂單商品明細集合是否為空，且updatedItems中productId要存在，否則拋出ResourceNotFound
        ServiceValidator.validateNotNull(originalItems, "Original order items");
        ServiceValidator.validateNotNull(updatedItems, "Updated order items");

        Set<Integer> updatedProductIds = updatedItems.stream()
                .map(OrderItemRequest::productId)
                .collect(Collectors.toSet());

        if (!updatedProductIds.isEmpty()) {
            List<Product> foundProducts = productRepository.findAllById(updatedProductIds);
            if (foundProducts.size() != updatedProductIds.size()) {
                Set<Integer> foundIds = foundProducts.stream().map(Product::getId).collect(Collectors.toSet());
                String missingIds = updatedProductIds.stream().filter(id -> !foundIds.contains(id))
                        .map(String::valueOf).collect(Collectors.joining(", "));
                throw new ResourceNotFoundException("Products not found with IDs: " + missingIds);
            }
        }

        // 1. 將新舊項目轉成 Map，方便快速比對 (Key: ProductId, Value: Quantity)
        Map<Integer, Integer> oldMap = originalItems.stream()
                .collect(Collectors.toMap(OrderItemRequest::productId, OrderItemRequest::quantity));
        Map<Integer, Integer> newMap = updatedItems.stream()
                .collect(Collectors.toMap(OrderItemRequest::productId, OrderItemRequest::quantity));

        // 2. 獲取所有涉及到的 Product ID (聯集)
        Set<Integer> allProductIds = new HashSet<>();
        allProductIds.addAll(oldMap.keySet());
        allProductIds.addAll(newMap.keySet());

        // 3. 統一計算差值並處理
        for (Integer productId : allProductIds) {
            int oldQty = oldMap.getOrDefault(productId, 0);
            int newQty = newMap.getOrDefault(productId, 0);

            int diff = newQty - oldQty;

            if (diff > 0) {
                // 需要更多庫存
                int rowsAffected = productRepository.reserveProduct(productId, diff);
                if (rowsAffected == 0) {
                    throw new ProductStockNotEnoughException("商品 ID " + productId + " 庫存不足，無法預留");
                }
            } else if (diff == 0) {
                // 數量沒變不處理
                continue;
            } else if (diff < 0) {
                // 釋放多餘庫存
                int rowsAffected = productRepository.releaseProduct(productId, Math.abs(diff));
                if (rowsAffected == 0) {
                    throw new ProductStockNotEnoughException("商品 ID " + productId + " 預留的庫存不足，無法釋放");
                }
            }
        }
    }

    /**
     * 將單一 Product 實體映射到 GetProductDetailResponse DTO。
     *
     * @param product 商品實體
     * @return 商品詳細資訊 DTO
     */
    private GetProductDetailResponse mapProductToDetailResponse(Product product) {
        return GetProductDetailResponse.builder()
                .name(product.getName())
                .price(product.getPrice())
                .saleStatus(product.getSaleStatus())
                .available(product.getAvailable())
                .build();
    }

    /**
     * 根據一組 ID 查詢多個商品實體。
     *
     * @param productIds 商品 ID 集合
     * @return 包含查詢到的商品實體的列表
     */
    private List<Product> findProductsByIds(Set<Integer> productIds) {
        ServiceValidator.validateNotNull(productIds, "Product IDs");
        List<Product> products = productRepository.findAllById(productIds);
        return products;
    }

    /**
     * 將 Product 實體列表映射到以商品 ID 為鍵的 GetProductDetailResponse DTO Map。
     *
     * @param products 商品實體列表
     * @return 以商品 ID 為鍵，商品詳細資訊 DTO 為值的 Map
     */
    private Map<Integer, GetProductDetailResponse> mapProductsToDetailResponses(List<Product> products) {
        return products.stream()
                .collect(Collectors.toMap(Product::getId, this::mapProductToDetailResponse));
    }

    // 根據商品名稱檢查商品是否已存在
    private void checkProductExistsByNameOrThrow(String productName) {
        ServiceValidator.validateNotNull(productName, "Product name");
        if (productRepository.existsByName(productName)) {
            throw new ProductAlreadyExistException(productName + " already exists");
        }
    }

    /**
     * 根據 ID 查找單一商品實體，若商品不存在拋出例外。主要共用丟同樣例外的部分不然code都看起來很長且自動排版還分兩行，#%^#(˙^*。
     *
     * @param productId 商品 ID
     * @return 找到的商品實體
     */
    private Product findProductByIdOrThrow(Integer productId) {
        ServiceValidator.validateNotNull(productId, "Product ID");
        Product result = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        return result;
    }
}
