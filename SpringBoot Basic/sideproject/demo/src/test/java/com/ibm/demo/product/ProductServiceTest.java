package com.ibm.demo.product;

import com.ibm.demo.exception.InvalidRequestException;
import com.ibm.demo.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testFindProductById_ThrowsResourceNotFoundException() {
        // Arrange
        Integer productId = 1;
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> productService.findProductById(productId));
    }

    @Test
    void testValidateProductIsSellable_ThrowsInvalidRequestException() {
        // Arrange
        Product product = new Product();
        product.setId(1);
        product.setSaleStatus(1002);

        // Act & Assert
        assertThrows(InvalidRequestException.class, () -> productService.validateProductIsSellable(product));
    }

    @Test
    void testValidateProductIds_ThrowsInvalidRequestException() {
        // Act & Assert
        assertThrows(InvalidRequestException.class, () -> productService.validateProductIds(null));
    }
}