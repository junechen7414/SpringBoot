package com.ibm.demo.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
    private final String NEW_PRODUCT_NAME = "New Product Name";

    @BeforeEach
    void setUp() {
        // 手動建立物件，確保測試不受 Mockito 自動注入行為的靜默錯誤影響
        productService = new ProductService(productRepository);
    }

    @Nested
    @DisplayName("建立產品成功流程")
    class CreateProductSuccessTests {

        @ParameterizedTest
        @CsvSource({
            "New Product, 100, 10",
            "Another Product, 50, 100"
        })
        @DisplayName("當輸入資料合法時，應成功儲存產品並回傳 ID")
        void createProduct_Success(String name, BigDecimal price, int available) {
            // Arrange
            CreateProductRequest request = CreateProductRequest.builder()
                    .name(name)
                    .price(price)
                    .available(available)
                    .build();

            when(productRepository.existsByName(name)).thenReturn(false);

            // 模擬儲存後會產生 ID
            Product savedProduct = new Product();
            savedProduct.setId(100);
            when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

            // Act
            Integer resultId = productService.createProduct(request);

            // Assert
            assertThat(resultId).isEqualTo(100);

            // 使用 ArgumentCaptor 驗證儲存的產品物件內容
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(captor.capture());
            assertThat(captor.getValue())
                    .hasFieldOrPropertyWithValue("name", name)
                    .hasFieldOrPropertyWithValue("price", price)
                    .hasFieldOrPropertyWithValue("available", available)
                    .hasFieldOrPropertyWithValue("saleStatus", ProductStatus.AVAILABLE.getCode());
        }
    }

    @Nested
    @DisplayName("建立產品業務邏輯")
    class CreateProductTests {

        @ParameterizedTest
        @ValueSource(strings = {"Existing Product Name", "Duplicate Name"})
        @DisplayName("建立產品時，若名稱已存在應拋出異常")
        void createProduct_WhenNameAlreadyExists_ShouldThrowException(String existingName) {
            // Arrange
            CreateProductRequest request = CreateProductRequest.builder()
                    .name(existingName)
                    .price(DEFAULT_PRICE)
                    .available(DEFAULT_STOCK)
                    .build();

            when(productRepository.existsByName(existingName)).thenReturn(true);

            // Act & Assert (建議點：驗證異常類型和訊息內容)
            assertThatThrownBy(() -> productService.createProduct(request))
                    .isInstanceOf(ProductAlreadyExistException.class)
                    .hasMessageContaining(existingName)
                    .hasMessageContaining("already exists");

            // Verify
            verify(productRepository, never()).save(any(Product.class));
            verifyNoMoreInteractions(productRepository);
        }
    }

    @Nested
    @DisplayName("查詢產品成功流程")
    class GetProductSuccessTests {

        @ParameterizedTest
        @CsvSource({
            "1, Laptop, 10.00, 100",
            "2, Mouse, 5.00, 50"
        })
        @DisplayName("查詢存在的產品應成功回傳詳細資訊")
        void getProductDetail_WhenExists_Success(Integer id, String name, BigDecimal price, int available) {
            // Arrange
            Product existingProduct = createTestProduct(id, name, price,
                    STATUS_SELLABLE, available);
            when(productRepository.findById(id)).thenReturn(Optional.of(existingProduct));

            // Act
            var response = productService.getProductDetail(id);

            // Assert
            assertThat(response)
                    .hasFieldOrPropertyWithValue("name", name)
                    .hasFieldOrPropertyWithValue("price", price)
                    .hasFieldOrPropertyWithValue("saleStatus", STATUS_SELLABLE)
                    .hasFieldOrPropertyWithValue("available", available);

            verify(productRepository).findById(id);
        }
    }

    @Nested
    @DisplayName("查詢產品業務邏輯")
    class GetProductTests {

        @ParameterizedTest
        @ValueSource(ints = {999, 888})
        @DisplayName("查詢不存在的產品應拋出 ResourceNotFoundException")
        void getProductDetail_WhenNotFound_ShouldThrowException(Integer nonExistentId) {
            when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductDetail(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found")
                    .hasMessageContaining(String.valueOf(nonExistentId));
        }
    }

    @Nested
    @DisplayName("更新產品成功流程")
    class UpdateProductSuccessTests {

        @ParameterizedTest
        @CsvSource({
            "1, Old Name, 50.00, New Product Name, 99.99, 200",
            "2, Product X, 10.00, Product Y, 20.00, 10"
        })
        @DisplayName("更新產品所有欄位應成功儲存")
        void updateProduct_AllFields_Success(Integer id, String oldName, BigDecimal oldPrice, String newName, BigDecimal newPrice, int newAvailable) {
            // Arrange
            Product existingProduct = createTestProduct(id, oldName, oldPrice,
                    STATUS_SELLABLE, 50);
            UpdateProductRequest request = UpdateProductRequest.builder()
                    .id(id)
                    .name(newName)
                    .price(newPrice)
                    .saleStatus(ProductStatus.AVAILABLE.getCode())
                    .available(newAvailable)
                    .build();

            when(productRepository.findById(id)).thenReturn(Optional.of(existingProduct));
            when(productRepository.existsByName(newName)).thenReturn(false);

            // Act
            productService.updateProduct(request);

            // Assert：使用 ArgumentCaptor 驗證更新後的產品內容
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(captor.capture());
            assertThat(captor.getValue())
                    .hasFieldOrPropertyWithValue("id", id)
                    .hasFieldOrPropertyWithValue("name", newName)
                    .hasFieldOrPropertyWithValue("price", newPrice)
                    .hasFieldOrPropertyWithValue("available", newAvailable);
        }

        @Test
        @DisplayName("更新產品名稱保持不變時，不應驗證名稱重複")
        void updateProduct_SameNameNoCheck_Success() {
            // Arrange
            Product existingProduct = createTestProduct(ACTIVE_PRODUCT_ID, "Same Name", DEFAULT_PRICE,
                    STATUS_SELLABLE, DEFAULT_STOCK);
            UpdateProductRequest request = UpdateProductRequest.builder()
                    .id(ACTIVE_PRODUCT_ID)
                    .name("Same Name")
                    .price(new BigDecimal("25.00"))
                    .saleStatus(ProductStatus.AVAILABLE.getCode())
                    .available(150)
                    .build();

            when(productRepository.findById(ACTIVE_PRODUCT_ID)).thenReturn(Optional.of(existingProduct));
            Product savedProduct = new Product();
            when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

            // Act
            productService.updateProduct(request);

            // Assert：驗證只呼叫一次 findById，不應呼叫 existsByName
            verify(productRepository).findById(ACTIVE_PRODUCT_ID);
            verify(productRepository, never()).existsByName(any());
            verify(productRepository).save(any(Product.class));
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
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found")
                    .hasMessageContaining("999");

            verify(productRepository, never()).save(any());
        }

        @ParameterizedTest
        @CsvSource({
            "1, Old Name, Existing Name",
            "2, Product A, Duplicate Name"
        })
        @DisplayName("更新產品名稱時，若新名稱已被其他產品佔用應拋出異常")
        void updateProduct_WhenNewNameExists_ShouldThrowException(Integer id, String oldName, String newName) {
            // Arrange (建議點：在每個測試內建立獨立實例，確保隔離性)
            Product existingProduct = createTestProduct(id, oldName, DEFAULT_PRICE, STATUS_SELLABLE,
                    DEFAULT_STOCK);
            UpdateProductRequest request = UpdateProductRequest.builder()
                    .id(id)
                    .name(newName)
                    .build();

            when(productRepository.findById(id)).thenReturn(Optional.of(existingProduct));
            when(productRepository.existsByName(newName)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> productService.updateProduct(request))
                    .isInstanceOf(ProductAlreadyExistException.class)
                    .hasMessageContaining(newName)
                    .hasMessageContaining("already exists");

            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("刪除產品成功流程")
    class DeleteProductSuccessTests {

        @Test
        @DisplayName("刪除存在的活躍產品應成功執行軟刪除")
        void deleteProduct_WhenExists_Success() {
            // Arrange
            Product activeProduct = createTestProduct(ACTIVE_PRODUCT_ID, "Active Product", DEFAULT_PRICE,
                    STATUS_SELLABLE, DEFAULT_STOCK);
            when(productRepository.findById(ACTIVE_PRODUCT_ID)).thenReturn(Optional.of(activeProduct));

            // Act
            productService.deleteProduct(ACTIVE_PRODUCT_ID);

            // Assert
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).delete(captor.capture());
            assertThat(captor.getValue())
                    .hasFieldOrPropertyWithValue("id", ACTIVE_PRODUCT_ID)
                    .hasFieldOrPropertyWithValue("name", "Active Product");
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
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found")
                    .hasMessageContaining("99");

            // 驗證：既然第一步就找不到，就不應該呼叫到 repository.delete
            verify(productRepository, never()).delete(any());
        }

        @ParameterizedTest
        @ValueSource(ints = {888, 777})
        @DisplayName("刪除不存在的 ID 應拋出 ResourceNotFoundException")
        void deleteProduct_WhenIdNotFound_ShouldThrowException(Integer nonExistentId) {
            when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteProduct(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found")
                    .hasMessageContaining(String.valueOf(nonExistentId));
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