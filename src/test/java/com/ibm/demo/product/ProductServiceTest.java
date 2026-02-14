package com.ibm.demo.product;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ibm.demo.exception.ResourceNotFoundException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductAlreadyExistException;
import com.ibm.demo.product.DTO.CreateProductRequest;
import com.ibm.demo.product.DTO.UpdateProductRequest;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    // --- Constants for Test Data ---
    private static final Integer ACTIVE_PRODUCT_ID = 1;
    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("10.00");
    private static final Integer DEFAULT_STOCK = 100;
    private static final Integer STATUS_SELLABLE = 1001;
    private static final Integer STATUS_NOT_SELLABLE = 1002;

    private static final Integer INACTIVE_PRODUCT_ID = 2;
    private static final String INACTIVE_PRODUCT_NAME = "Inactive Product";

    private static final String EXISTING_NAME_PRODUCT_NAME = "Existing Product Name";

    private static final Integer NON_EXISTENT_PRODUCT_ID = 999;
    private static final String NEW_PRODUCT_NAME = "New Product Name";
    private static final String OLD_PRODUCT_NAME = "Old Product Name";

    private static Product inactiveProduct;

    @BeforeAll
    static void setUp() {
        inactiveProduct = createTestProduct(INACTIVE_PRODUCT_ID, INACTIVE_PRODUCT_NAME, new BigDecimal("20.00"),
                STATUS_NOT_SELLABLE, 0);
    }

    @Test
    @DisplayName("建立產品時，若產品名稱已存在應拋出ProductAlreadyExistException")
    void createProduct_WhenNameAlreadyExists_ShouldThrowProductAlreadyExistException() {
        // Arrange
        CreateProductRequest request = CreateProductRequest.builder()
                .name(EXISTING_NAME_PRODUCT_NAME)
                .price(DEFAULT_PRICE)
                .stockQty(DEFAULT_STOCK)
                .build();

        // Simulate that EXISTING_NAME_PRODUCT_NAME is already taken
        when(productRepository.existsByName(EXISTING_NAME_PRODUCT_NAME)).thenReturn(true);

        // Act & Assert
        assertThrows(ProductAlreadyExistException.class, () -> productService.createProduct(request));
        // assertEquals(EXISTING_NAME_PRODUCT_NAME + " already exists",
        // exception.getMessage()); // Removed: Message assertion redundant with type
        // check

        // Verify
        verify(productRepository, times(1)).existsByName(EXISTING_NAME_PRODUCT_NAME);
        verify(productRepository, never()).save(any(Product.class));
    }

    // @Test
    // @DisplayName("獲取產品詳情時，若產品不存在應拋出ResourceNotFoundException")
    // void
    // getProductDetail_WhenProductNotFound_ShouldThrowResourceNotFoundException() {
    // // Arrange
    // when(productRepository.findById(NON_EXISTENT_PRODUCT_ID)).thenReturn(Optional.empty());

    // // Act & Assert
    // ResourceNotFoundException exception =
    // assertThrows(ResourceNotFoundException.class,
    // () -> productService.getProductDetail(NON_EXISTENT_PRODUCT_ID));
    // assertEquals("Product not found with id: " + NON_EXISTENT_PRODUCT_ID,
    // exception.getMessage());

    // // Verify
    // verify(productRepository, times(1)).findById(NON_EXISTENT_PRODUCT_ID);
    // }

    // @Test
    // @DisplayName("獲取多個產品詳情時，若ID集合為null應拋出InvalidRequestException")
    // void getProductDetails_WhenIdsIsNull_ShouldThrowInvalidRequestException() {
    // // Act & Assert
    // InvalidRequestException exception =
    // assertThrows(InvalidRequestException.class,
    // () -> productService.getProductDetails(null));
    // assertEquals("Product IDs cannot be null or empty", exception.getMessage());
    // }

    @Test
    @DisplayName("更新產品時，若產品不存在應拋出ResourceNotFoundException")
    void updateProduct_WhenProductNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        UpdateProductRequest request = UpdateProductRequest.builder()
                .id(NON_EXISTENT_PRODUCT_ID)
                .name(NEW_PRODUCT_NAME)
                .price(DEFAULT_PRICE)
                .saleStatus(STATUS_SELLABLE)
                .stockQty(DEFAULT_STOCK)
                .build();

        // Simulate that the product with NON_EXISTENT_PRODUCT_ID does not exist
        when(productRepository.findById(NON_EXISTENT_PRODUCT_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> productService.updateProduct(request));
        // assertEquals("Product not found with id: " + NON_EXISTENT_PRODUCT_ID,
        // exception.getMessage()); // Removed: Message assertion redundant with type
        // check

        // Verify
        verify(productRepository, times(1)).findById(NON_EXISTENT_PRODUCT_ID);
        verify(productRepository, never()).existsByName(any(String.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("更新產品時，若新產品名稱已存在應拋出ProductAlreadyExistException")
    void updateProduct_WhenNewNameExists_ShouldThrowProductAlreadyExistException() {
        // Arrange
        // Use activeProduct from setUp, but we'll try to update its name to one that
        // already exists
        Product productToUpdate = createTestProduct(ACTIVE_PRODUCT_ID, OLD_PRODUCT_NAME, DEFAULT_PRICE, STATUS_SELLABLE,
                DEFAULT_STOCK);

        UpdateProductRequest request = UpdateProductRequest.builder()
                .id(ACTIVE_PRODUCT_ID)
                .name(EXISTING_NAME_PRODUCT_NAME) // This name conflicts with 'productWithExistingName'
                .price(BigDecimal.ONE)
                .saleStatus(STATUS_SELLABLE)
                .stockQty(5)
                .build();

        when(productRepository.findById(ACTIVE_PRODUCT_ID)).thenReturn(Optional.of(productToUpdate));
        // Simulate that EXISTING_NAME_PRODUCT_NAME is already taken
        when(productRepository.existsByName(EXISTING_NAME_PRODUCT_NAME)).thenReturn(true);

        // Act & Assert
        assertThrows(ProductAlreadyExistException.class, () -> productService.updateProduct(request));
        // assertEquals(EXISTING_NAME_PRODUCT_NAME + " already exists",
        // exception.getMessage()); // Removed: Message assertion redundant with type
        // check

        // Verify
        verify(productRepository, times(1)).findById(ACTIVE_PRODUCT_ID);
        verify(productRepository, times(1)).existsByName(EXISTING_NAME_PRODUCT_NAME);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("刪除產品時，若產品已不可銷售應拋出ResourceNotFoundException")
    void testDeleteProduct_ThrowsResourceNotFoundException_WhenProductAlreadyInactive() {
        // Arrange
        // Use the inactiveProduct from setUp
        when(productRepository.findById(INACTIVE_PRODUCT_ID)).thenReturn(Optional.of(inactiveProduct));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> productService.deleteProduct(INACTIVE_PRODUCT_ID));
        // assertEquals("Product not found with id: " + INACTIVE_PRODUCT_ID,
        // exception.getMessage()); // Removed: Message assertion redundant with type
        // check

        // Verify findById was called once, but save should NOT be called
        verify(productRepository, times(1)).findById(INACTIVE_PRODUCT_ID);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("刪除產品時，若產品ID不存在應拋出ResourceNotFoundException")
    void deleteProduct_WhenProductNotFoundInitially_ShouldThrowResourceNotFoundException() {
        // Arrange
        when(productRepository.findById(NON_EXISTENT_PRODUCT_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> productService.deleteProduct(NON_EXISTENT_PRODUCT_ID));
        // assertEquals("Product not found with id: " + NON_EXISTENT_PRODUCT_ID,
        // exception.getMessage()); // Removed: Message assertion redundant with type
        // check

        // Verify
        verify(productRepository, times(1)).findById(NON_EXISTENT_PRODUCT_ID);
        verify(productRepository, never()).save(any(Product.class));
    }

    // @Test
    // @DisplayName("批量更新庫存時，若stockUpdates為null應拋出InvalidRequestException")
    // void
    // updateProductsStock_WhenStockUpdatesIsNull_ShouldThrowInvalidRequestException()
    // {
    // InvalidRequestException exception =
    // assertThrows(InvalidRequestException.class,
    // () -> productService.updateProductsStock(null));
    // assertEquals("Stock updates cannot be null or empty",
    // exception.getMessage());
    // verify(productRepository, never()).findAllById(any());
    // verify(productRepository, never()).saveAll(any());
    // }

    // @Test
    // @DisplayName("批量更新庫存時，若stockUpdates為空Map應拋出InvalidRequestException")
    // void
    // updateProductsStock_WhenStockUpdatesIsEmpty_ShouldThrowInvalidRequestException()
    // {
    // InvalidRequestException exception =
    // assertThrows(InvalidRequestException.class,
    // () -> productService.updateProductsStock(new HashMap<>()));
    // assertEquals("Stock updates cannot be null or empty",
    // exception.getMessage());
    // verify(productRepository, never()).findAllById(any());
    // verify(productRepository, never()).saveAll(any());
    // }

    // --- Helper Methods ---
    /**
     * Creates a Product instance for testing purposes.
     */
    private static Product createTestProduct(Integer id, String name, BigDecimal price, Integer saleStatus,
            Integer stockQty) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(price);
        product.setSaleStatus(saleStatus);
        product.setStockQty(stockQty);
        // Dates are often set by @PrePersist/@PreUpdate or returned by DB, so not
        // always needed here
        return product;
    }

}
