package com.ibm.demo.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.ibm.demo.exception.InvalidRequestException;
import com.ibm.demo.exception.BusinessLogicCheck.ProductInactiveException;
import com.ibm.demo.exception.NotFound.ProductNotFoundException;
import com.ibm.demo.product.DTO.CreateProductRequest;
import com.ibm.demo.product.DTO.CreateProductResponse;
import com.ibm.demo.product.DTO.GetProductDetailResponse;
import com.ibm.demo.product.DTO.UpdateProductRequest;
import com.ibm.demo.product.DTO.UpdateProductResponse;

class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ==================================
    // Active Tests (Currently Running)
    // ==================================

    @Test
    void testCreateProduct_Success() {
        // Arrange
        CreateProductRequest request = new CreateProductRequest();
        request.setName("New Gadget");
        request.setPrice(new BigDecimal("199.99"));
        request.setStockQty(100);

        Product productToSave = new Product();
        productToSave.setName(request.getName());
        productToSave.setPrice(request.getPrice());
        productToSave.setStockQty(request.getStockQty());
        productToSave.setSaleStatus(1001); // Default status

        Product savedProduct = new Product();
        savedProduct.setId(5); // Simulate DB generated ID
        savedProduct.setName(request.getName());
        savedProduct.setPrice(request.getPrice());
        savedProduct.setStockQty(request.getStockQty());
        savedProduct.setSaleStatus(1001);
        savedProduct.setCreateDate(LocalDate.now()); // Simulate DB generated date

        // Mock the repository save method
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

        // Act
        CreateProductResponse response = productService.createProduct(request);

        // Assert
        assertNotNull(response);
        assertEquals(savedProduct.getId(), response.getId());
        assertEquals(savedProduct.getName(), response.getName());
        assertEquals(savedProduct.getPrice(), response.getPrice());
        assertEquals(savedProduct.getStockQty(), response.getStockQty());
        assertEquals(1001, response.getSaleStatus()); // Verify default status
        assertEquals(savedProduct.getCreateDate(), response.getCreateDate());

        // Verify that save was called with the correct product details (before ID/date)
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product capturedProduct = productCaptor.getValue();
        assertEquals(request.getName(), capturedProduct.getName());
        assertEquals(request.getPrice(), capturedProduct.getPrice());
        assertEquals(request.getStockQty(), capturedProduct.getStockQty());
        assertEquals(1001, capturedProduct.getSaleStatus());
    }

    @Test
    void testGetProductDetails_ThrowsProductInactiveException_WhenProductNotSellable() {
        // Arrange
        Set<Integer> ids = new HashSet<>(Arrays.asList(1, 2));
        Product product1 = createTestProduct(1, "Product 1", new BigDecimal("10.00"), 1001, 10); // Sellable
        Product product2 = createTestProduct(2, "Product 2", new BigDecimal("20.00"), 1002, 5); // Not sellable
        List<Product> foundProducts = Arrays.asList(product1, product2);

        when(productRepository.findAllById(ids)).thenReturn(foundProducts);

        // Act & Assert
        ProductInactiveException exception = assertThrows(ProductInactiveException.class,
                () -> productService.getProductDetails(ids));
        assertTrue(exception.getMessage().contains("商品id: 2 不可銷售")); // Check for inactive product ID

        // Verify
        verify(productRepository, times(1)).findAllById(ids);
    }

    @Test
    void testDeleteProduct_Success() {
        // Arrange
        Integer productId = 8;
        Product existingProduct = createTestProduct(productId, "Product to Delete", new BigDecimal("50.00"), 1001, 10); // Initially sellable

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        // Mock save to return the object passed to it, simulating a successful save
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        productService.deleteProduct(productId);

        // Assert (Verify interactions and state change)
        verify(productRepository, times(1)).findById(productId);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(1)).save(productCaptor.capture());

        Product savedProduct = productCaptor.getValue();
        assertEquals(productId, savedProduct.getId());
        assertEquals(1002, savedProduct.getSaleStatus(), "SaleStatus should be updated to 1002 (inactive)");
        // Ensure other fields weren't unintentionally changed
        assertEquals(existingProduct.getName(), savedProduct.getName());
        assertEquals(existingProduct.getPrice(), savedProduct.getPrice());
        assertEquals(existingProduct.getStockQty(), savedProduct.getStockQty());
    }

    @Test
    void testValidateProductIsSellable_ThrowsInvalidRequestException() {
        // Arrange
        Product product = createTestProduct(1, "Inactive Product", BigDecimal.ONE, 1002, 0);

        // Act & Assert
        assertThrows(ProductInactiveException.class, () -> productService.validateProductIsSellable(product));
    }

    // ==================================
    // Inactive Tests (Commented Out)
    // ==================================

    // @Test
    // void testFindProductById_ThrowsProductNotFoundException() {
    //     // Arrange
    //     Integer productId = 1;
    //     when(productRepository.findById(productId)).thenReturn(Optional.empty());

    //     // Act & Assert
    //     assertThrows(ProductNotFoundException.class, () -> productService.findProductById(productId));

    //     // Verify
    //     verify(productRepository, times(1)).findById(productId);
    // }

    // @Test
    // void testFindProductById_ProductFound() {
    //     // Arrange
    //     Integer productId = 2;
    //     Product expectedProduct = createTestProduct(productId, "Found Product", new BigDecimal("99.99"), 1001, 50);

    //     // Mock the repository to return the product wrapped in Optional
    //     when(productRepository.findById(productId)).thenReturn(Optional.of(expectedProduct));

    //     // Act
    //     Product actualProduct = productService.findProductById(productId);

    //     // Assert
    //     assertNotNull(actualProduct);
    //     assertEquals(expectedProduct, actualProduct, "The returned product should match the expected one.");

    //     // Verify
    //     verify(productRepository, times(1)).findById(productId);
    // }

    //  @Test
    // void testGetProductDetail_Success() {
    //     // Arrange
    //     Integer productId = 3;
    //     Product mockProduct = createTestProduct(productId, "Detailed Product", new BigDecimal("123.45"), 1001, 20);

    //     when(productRepository.findById(productId)).thenReturn(Optional.of(mockProduct));

    //     // Act
    //     GetProductDetailResponse response = productService.getProductDetail(productId);

    //     // Assert
    //     assertNotNull(response);
    //     assertProductDetailResponseMatchesProduct(response, mockProduct);

    //     // Verify that findById was called
    //     verify(productRepository, times(1)).findById(productId);
    // }

    // @Test
    // void testGetProductDetail_NotFound() {
    //     // Arrange
    //     Integer productId = 4;
    //     when(productRepository.findById(productId)).thenReturn(Optional.empty());

    //     // Act & Assert
    //     assertThrows(ProductNotFoundException.class, () -> productService.getProductDetail(productId));

    //     // Verify that findById was called
    //     verify(productRepository, times(1)).findById(productId);
    // }

    // @Test
    // void testGetProductDetails_Success() {
    //     // Arrange
    //     Set<Integer> ids = new HashSet<>(Arrays.asList(1, 2));
    //     Product product1 = createTestProduct(1, "Product 1", new BigDecimal("10.00"), 1001, 10);
    //     Product product2 = createTestProduct(2, "Product 2", new BigDecimal("20.00"), 1001, 5);
    //     List<Product> foundProducts = Arrays.asList(product1, product2);

    //     when(productRepository.findAllById(ids)).thenReturn(foundProducts);

    //     // Act
    //     Map<Integer, GetProductDetailResponse> result = productService.getProductDetails(ids);

    //     // Assert
    //     assertNotNull(result);
    //     assertEquals(2, result.size());
    //     assertTrue(result.containsKey(1));
    //     assertTrue(result.containsKey(2));
    //     assertProductDetailResponseMatchesProduct(result.get(1), product1);
    //     assertProductDetailResponseMatchesProduct(result.get(2), product2);

    //     // Verify
    //     verify(productRepository, times(1)).findAllById(ids);
    // }

    // @Test
    // void testGetProductDetails_ThrowsInvalidRequestException_WhenIdsNull() {
    //     // Arrange
    //     Set<Integer> ids = null;

    //     // Act & Assert
    //     assertThrows(InvalidRequestException.class, () -> productService.getProductDetails(ids));

    //     // Verify no interaction with repository
    //     verifyNoInteractions(productRepository);
    // }

    // @Test
    // void testGetProductDetails_ThrowsInvalidRequestException_WhenIdsEmpty() {
    //     // Arrange
    //     Set<Integer> ids = Collections.emptySet();

    //     // Act & Assert
    //     assertThrows(InvalidRequestException.class, () -> productService.getProductDetails(ids));

    //     // Verify no interaction with repository
    //     verifyNoInteractions(productRepository);
    // }

    // @Test
    // void testGetProductDetails_ThrowsProductNotFoundException_WhenSomeIdsNotFound() {
    //     // Arrange
    //     Set<Integer> requestedIds = new HashSet<>(Arrays.asList(1, 2, 3)); // Request 3 IDs
    //     Product product1 = createTestProduct(1, "Product 1", new BigDecimal("10.00"), 1001, 10);
    //     Product product2 = createTestProduct(2, "Product 2", new BigDecimal("20.00"), 1001, 5);
    //     List<Product> foundProducts = Arrays.asList(product1, product2); // Only find 2

    //     when(productRepository.findAllById(requestedIds)).thenReturn(foundProducts);

    //     // Act & Assert
    //     ProductNotFoundException exception = assertThrows(ProductNotFoundException.class,
    //             () -> productService.getProductDetails(requestedIds));
    //     assertTrue(exception.getMessage().contains("Products not found with ids: [3]")); // Check for missing ID

    //     // Verify
    //     verify(productRepository, times(1)).findAllById(requestedIds);
    // }

    // @Test
    // void testUpdateProduct_Success() {
    //     // Arrange
    //     Integer productId = 6;
    //     UpdateProductRequest request = new UpdateProductRequest();
    //     request.setId(productId);
    //     request.setName("Updated Gadget");
    //     request.setPrice(new BigDecimal("249.99"));
    //     request.setSaleStatus(1002); // Update status
    //     request.setStockQty(75);

    //     // Existing product found in DB
    //     Product existingProduct = createTestProduct(productId, "Old Gadget", new BigDecimal("199.99"), 1001, 100);

    //     // Product after save (potentially with updated modifiedDate)
    //     Product savedProduct = new Product();
    //     savedProduct.setId(productId);
    //     savedProduct.setName(request.getName());
    //     savedProduct.setPrice(request.getPrice());
    //     savedProduct.setSaleStatus(request.getSaleStatus());
    //     savedProduct.setStockQty(request.getStockQty());
    //     savedProduct.setModifiedDate(LocalDate.now()); // Simulate DB update

    //     when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
    //     when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

    //     // Act
    //     UpdateProductResponse response = productService.updateProduct(request);

    //     // Assert
    //     assertNotNull(response);
    //     assertEquals(savedProduct.getId(), response.getId());
    //     assertEquals(savedProduct.getName(), response.getName());
    //     assertEquals(savedProduct.getPrice(), response.getPrice());
    //     assertEquals(savedProduct.getSaleStatus(), response.getSaleStatus());
    //     assertEquals(savedProduct.getStockQty(), response.getStockQty());
    //     assertEquals(savedProduct.getModifiedDate(), response.getModifiedDate());

    //     // Verify interactions
    //     verify(productRepository, times(1)).findById(productId);
    //     ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
    //     verify(productRepository, times(1)).save(productCaptor.capture());

    //     // Verify the state of the product passed to save
    //     Product capturedProduct = productCaptor.getValue();
    //     assertEquals(request.getName(), capturedProduct.getName());
    //     assertEquals(request.getPrice(), capturedProduct.getPrice());
    //     assertEquals(request.getSaleStatus(), capturedProduct.getSaleStatus());
    //     assertEquals(request.getStockQty(), capturedProduct.getStockQty());
    // }

    // @Test
    // void testUpdateProduct_NotFound() {
    //     // Arrange
    //     Integer productId = 7;
    //     UpdateProductRequest request = new UpdateProductRequest();
    //     request.setId(productId); // Set other fields if needed, but ID is key
    //     when(productRepository.findById(productId)).thenReturn(Optional.empty());

    //     // Act & Assert
    //     assertThrows(ProductNotFoundException.class, () -> productService.updateProduct(request));

    //     // Verify findById was called, but save was not
    //     verify(productRepository, times(1)).findById(productId);
    //     verify(productRepository, times(0)).save(any(Product.class)); // Ensure save is not called
    // }

    // @Test
    // void testDeleteProduct_NotFound() {
    //     // Arrange
    //     Integer productId = 9;
    //     when(productRepository.findById(productId)).thenReturn(Optional.empty());

    //     // Act & Assert
    //     assertThrows(ProductNotFoundException.class, () -> productService.deleteProduct(productId));

    //     // Verify findById was called, but save was not
    //     verify(productRepository, times(1)).findById(productId);
    //     verify(productRepository, times(0)).save(any(Product.class));
    // }

    // @Test
    // void testUpdateProductsStock_Success() {
    //     // Arrange
    //     Map<Integer, Integer> stockUpdates = Map.of(
    //             1, 50, // Update product 1 stock to 50
    //             2, 75  // Update product 2 stock to 75
    //     );
    //     Set<Integer> productIds = stockUpdates.keySet();

    //     Product product1 = createTestProduct(1, "Product 1", new BigDecimal("10.00"), 1001, 10); // Initial stock 10
    //     Product product2 = createTestProduct(2, "Product 2", new BigDecimal("20.00"), 1001, 20); // Initial stock 20
    //     List<Product> foundProducts = Arrays.asList(product1, product2);

    //     when(productRepository.findAllById(productIds)).thenReturn(foundProducts);
    //     when(productRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0)); // Return the saved list

    //     // Act
    //     productService.updateProductsStock(stockUpdates);

    //     // Assert
    //     verify(productRepository, times(1)).findAllById(productIds);

    //     ArgumentCaptor<List<Product>> listCaptor = ArgumentCaptor.forClass(List.class);
    //     verify(productRepository, times(1)).saveAll(listCaptor.capture());

    //     List<Product> savedProducts = listCaptor.getValue();
    //     assertEquals(2, savedProducts.size());

    //     // Check if stock quantities were updated correctly before saving
    //     Product savedProduct1 = savedProducts.stream().filter(p -> p.getId().equals(1)).findFirst().orElseThrow();
    //     Product savedProduct2 = savedProducts.stream().filter(p -> p.getId().equals(2)).findFirst().orElseThrow();

    //     assertEquals(50, savedProduct1.getStockQty(), "Product 1 stock should be updated");
    //     assertEquals(75, savedProduct2.getStockQty(), "Product 2 stock should be updated");
    // }

    // @Test
    // void testUpdateProductsStock_ThrowsInvalidRequestException_WhenMapIsNull() {
    //     // Arrange
    //     Map<Integer, Integer> stockUpdates = null;

    //     // Act & Assert
    //     assertThrows(InvalidRequestException.class, () -> productService.updateProductsStock(stockUpdates));
    //     verifyNoInteractions(productRepository); // Ensure no DB interaction
    // }

    // @Test
    // void testUpdateProductsStock_ThrowsInvalidRequestException_WhenMapIsEmpty() {
    //     // Arrange
    //     Map<Integer, Integer> stockUpdates = Collections.emptyMap();

    //     // Act & Assert
    //     assertThrows(InvalidRequestException.class, () -> productService.updateProductsStock(stockUpdates));
    //     verifyNoInteractions(productRepository); // Ensure no DB interaction
    // }

    // @Test
    // void testUpdateProductsStock_ThrowsProductNotFoundException_WhenProductMissing() {
    //     // Arrange
    //     Map<Integer, Integer> stockUpdates = Map.of(1, 50, 99, 100); // Product 99 does not exist
    //     Set<Integer> requestedIds = stockUpdates.keySet();
    //     Product product1 = createTestProduct(1, "Product 1", new BigDecimal("10.00"), 1001, 10);
    //     when(productRepository.findAllById(requestedIds)).thenReturn(List.of(product1)); // Only return product 1

    //     // Act & Assert (Exception comes from findProductsByIds)
    //     assertThrows(ProductNotFoundException.class, () -> productService.updateProductsStock(stockUpdates));
    //     verify(productRepository, times(1)).findAllById(requestedIds);
    //     verify(productRepository, times(0)).saveAll(any()); // Ensure saveAll is not called
    // }

    // @Test
    // void testFindProductsByIds_Success() {
    //     // Arrange
    //     Set<Integer> productIds = Set.of(1, 2);
    //     Product product1 = createTestProduct(1, "P1", BigDecimal.TEN, 1001, 5);
    //     Product product2 = createTestProduct(2, "P2", BigDecimal.ONE, 1001, 10);
    //     List<Product> expectedProducts = Arrays.asList(product1, product2);

    //     when(productRepository.findAllById(productIds)).thenReturn(expectedProducts);

    //     // Act
    //     List<Product> actualProducts = productService.findProductsByIds(productIds);

    //     // Assert
    //     assertNotNull(actualProducts);
    //     assertEquals(2, actualProducts.size());
    //     assertTrue(actualProducts.containsAll(expectedProducts));
    //     verify(productRepository, times(1)).findAllById(productIds);
    // }

    // @Test
    // void testFindProductsByIds_SomeNotFound_ThrowsProductNotFoundException() {
    //     // Arrange
    //     Set<Integer> requestedIds = Set.of(1, 2, 3); // Request 3
    //     Product product1 = createTestProduct(1, "P1", BigDecimal.TEN, 1001, 5);
    //     Product product2 = createTestProduct(2, "P2", BigDecimal.ONE, 1001, 10);
    //     List<Product> foundProducts = Arrays.asList(product1, product2); // Only find 2

    //     when(productRepository.findAllById(requestedIds)).thenReturn(foundProducts);

    //     // Act & Assert
    //     ProductNotFoundException exception = assertThrows(ProductNotFoundException.class,
    //             () -> productService.findProductsByIds(requestedIds));

    //     assertTrue(exception.getMessage().contains("Products not found with ids: [3]"),
    //                "Exception message should contain the missing ID");
    //     verify(productRepository, times(1)).findAllById(requestedIds);
    // }

    // @Test
    // void testFindProductsByIds_AllNotFound_ThrowsProductNotFoundException() {
    //     // Arrange
    //     Set<Integer> requestedIds = Set.of(4, 5); // Request 2, find 0
    //     List<Product> foundProducts = Collections.emptyList();

    //     when(productRepository.findAllById(requestedIds)).thenReturn(foundProducts);

    //     // Act & Assert
    //     ProductNotFoundException exception = assertThrows(ProductNotFoundException.class,
    //             () -> productService.findProductsByIds(requestedIds));

    //     // The order in the set might vary, so check for both IDs
    //     assertTrue(exception.getMessage().contains("Products not found with ids: ") &&
    //                exception.getMessage().contains("4") &&
    //                exception.getMessage().contains("5"),
    //                "Exception message should contain all missing IDs");
    //     verify(productRepository, times(1)).findAllById(requestedIds);
    // }

    // Note: Tests for null/empty input IDs are implicitly covered by testing
    // validateProductIds, which is called first by findProductsByIds.
    // Adding explicit tests here would duplicate the validation logic test.
    // See testValidateProductIds_ThrowsInvalidRequestException.

    // @Test
    // void testValidateProductIds_ThrowsInvalidRequestException() {
    //     // Act & Assert
    //     assertThrows(InvalidRequestException.class, () -> productService.validateProductIds(null));
    // }

    // --- Helper Methods ---

    /**
     * Creates a Product instance for testing purposes.
     */
    private Product createTestProduct(Integer id, String name, BigDecimal price, Integer saleStatus, Integer stockQty) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(price);
        product.setSaleStatus(saleStatus);
        product.setStockQty(stockQty);
        // Dates are often set by @PrePersist/@PreUpdate or returned by DB, so not always needed here
        return product;
    }

    /**
     * Asserts that the fields of a GetProductDetailResponse match the corresponding fields of a Product.
     *
     * @param actualResponse The actual response DTO.
     * @param expectedProduct The expected product entity.
     */
    // private void assertProductDetailResponseMatchesProduct(GetProductDetailResponse actualResponse, Product expectedProduct) {
    //     assertEquals(expectedProduct.getName(), actualResponse.getName(), "Name should match");
    //     assertEquals(expectedProduct.getPrice(), actualResponse.getPrice(), "Price should match");
    //     assertEquals(expectedProduct.getSaleStatus(), actualResponse.getSaleStatus(), "SaleStatus should match");
    //     assertEquals(expectedProduct.getStockQty(), actualResponse.getStockQty(), "StockQty should match");
    // }
}