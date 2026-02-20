package com.ibm.demo.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.demo.enums.ProductStatus;
import com.ibm.demo.exception.ResourceNotFoundException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductAlreadyExistException;
import com.ibm.demo.product.DTO.CreateProductRequest;
import com.ibm.demo.product.DTO.UpdateProductRequest;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    // 建議點：明確建立 SUT，讓依賴關係顯性且易於理解
    private ProductService productService;

    // 測試資料常數
    private final Integer ACTIVE_PRODUCT_ID = 1;
    private final BigDecimal DEFAULT_PRICE = new BigDecimal("10.00");
    private final Integer DEFAULT_STOCK = 100;
    private final Integer STATUS_SELLABLE = ProductStatus.AVAILABLE.getCode();
    private final String EXISTING_NAME = "Existing Product Name";
    private final String NEW_PRODUCT_NAME = "New Product Name";

    @BeforeEach
    void setUp() {
        // 手動建立物件，確保測試不受 Mockito 自動注入行為的靜默錯誤影響
        productService = new ProductService(productRepository);
    }

    @Nested
    @DisplayName("建立產品成功流程")
    class CreateProductSuccessTests {

        @Test
        @DisplayName("當輸入資料合法時，應成功儲存產品並回傳 ID")
        void createProduct_Success() {
            // Arrange
            CreateProductRequest request = CreateProductRequest.builder()
                    .name("New Product")
                    .price(new BigDecimal("100"))
                    .available(10)
                    .build();

            when(productRepository.existsByName("New Product")).thenReturn(false);

            // 模擬儲存後會產生 ID
            Product savedProduct = new Product();
            savedProduct.setId(100);
            when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

            // Act
            Integer resultId = productService.createProduct(request);

            // Assert
            assertThat(resultId).isEqualTo(100);

            // 驗證是否有執行 save 動作
            verify(productRepository, times(1)).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("建立產品業務邏輯")
    class CreateProductTests {

        @Test
        @DisplayName("建立產品時，若名稱已存在應拋出異常")
        void createProduct_WhenNameAlreadyExists_ShouldThrowException() {
            // Arrange
            CreateProductRequest request = CreateProductRequest.builder()
                    .name(EXISTING_NAME)
                    .price(DEFAULT_PRICE)
                    .available(DEFAULT_STOCK)
                    .build();

            when(productRepository.existsByName(EXISTING_NAME)).thenReturn(true);

            // Act & Assert (建議點：使用 AssertJ 提升可讀性)
            assertThatThrownBy(() -> productService.createProduct(request))
                    .isInstanceOf(ProductAlreadyExistException.class);

            // Verify
            verify(productRepository, never()).save(any(Product.class));
            verifyNoMoreInteractions(productRepository);
        }
    }

    @Nested
    @DisplayName("查詢產品業務邏輯")
    class GetProductTests {

        @Test
        @DisplayName("查詢不存在的產品應拋出 ResourceNotFoundException")
        void getProductDetail_WhenNotFound_ShouldThrowException() {
            Integer nonExistentId = 999;
            when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductDetail(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("更新產品業務邏輯")
    class UpdateProductTests {

        @Test
        @DisplayName("更新不存在的產品應拋出 ResourceNotFoundException")
        void updateProduct_WhenNotFound_ShouldThrowException() {
            UpdateProductRequest request = UpdateProductRequest.builder()
                    .id(999).name(NEW_PRODUCT_NAME).build();

            when(productRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProduct(request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("更新產品名稱時，若新名稱已被其他產品佔用應拋出異常")
        void updateProduct_WhenNewNameExists_ShouldThrowException() {
            // Arrange (建議點：在每個測試內建立獨立實例，確保隔離性)
            Product existingProduct = createTestProduct(ACTIVE_PRODUCT_ID, "Old Name", DEFAULT_PRICE, STATUS_SELLABLE,
                    DEFAULT_STOCK);
            UpdateProductRequest request = UpdateProductRequest.builder()
                    .id(ACTIVE_PRODUCT_ID)
                    .name(EXISTING_NAME)
                    .build();

            when(productRepository.findById(ACTIVE_PRODUCT_ID)).thenReturn(Optional.of(existingProduct));
            when(productRepository.existsByName(EXISTING_NAME)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> productService.updateProduct(request))
                    .isInstanceOf(ProductAlreadyExistException.class);

            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("刪除產品業務邏輯")
    class DeleteProductTests {

        @Test
        @DisplayName("刪除時若產品已是停效狀態，應拋出 ResourceNotFoundException")
        void deleteProduct_WhenAlreadyInactive_ShouldThrowException() {
            // Arrange
            Integer id = 99;

            // 關鍵擊破點：模擬 Repository 因為 @SQLRestriction
            // 而無法在資料庫中找到非啟用(Y)狀態的產品
            when(productRepository.findById(id)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> productService.deleteProduct(id))
                    .isInstanceOf(ResourceNotFoundException.class);

            // 驗證：既然第一步就找不到，就不應該呼叫到 repository.delete
            verify(productRepository, never()).delete(any());
        }

        @Test
        @DisplayName("刪除不存在的 ID 應拋出 ResourceNotFoundException")
        void deleteProduct_WhenIdNotFound_ShouldThrowException() {
            Integer nonExistentId = 888;
            when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteProduct(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // --- Helper Methods ---
    private Product createTestProduct(Integer id, String name, BigDecimal price, Integer saleStatus,
            Integer available) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(price);
        product.setSaleStatus(saleStatus);
        product.setAvailable(available);
        return product;
    }
}