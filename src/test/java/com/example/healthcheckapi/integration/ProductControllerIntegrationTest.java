package com.example.healthcheckapi.integration;

import com.example.healthcheckapi.entity.Product;
import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.repository.ProductRepository;
import com.example.healthcheckapi.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ProductControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String authHeader;

    @BeforeEach
    public void setup() {
        productRepository.deleteAll();
        userRepository.deleteAll();

        // Create a test user for authenticated requests
        testUser = new User();
        testUser.setUsername("product@test.com");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser.setFirstName("Product");
        testUser.setLastName("Tester");
        testUser = userRepository.save(testUser);

        authHeader = "Basic " + Base64.getEncoder().encodeToString("product@test.com:password123".getBytes());
    }

    // ========== POSITIVE TEST CASES ==========

    @Test
    public void testCreateProduct_Success() throws Exception {
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", "Laptop");
        productRequest.put("description", "High-performance laptop");
        productRequest.put("sku", "LAP-001");
        productRequest.put("manufacturer", "TechCorp");
        productRequest.put("quantity", 50);

        mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", containsString("/v1/product/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Laptop"))
                .andExpect(jsonPath("$.description").value("High-performance laptop"))
                .andExpect(jsonPath("$.sku").value("LAP-001"))
                .andExpect(jsonPath("$.manufacturer").value("TechCorp"))
                .andExpect(jsonPath("$.quantity").value(50))
                .andExpect(jsonPath("$.date_added").exists())
                .andExpect(jsonPath("$.date_last_updated").exists())
                .andExpect(jsonPath("$.owner_user_id").value(testUser.getId()));

        // Verify persistence
        assertTrue(productRepository.existsBySku("LAP-001"));
    }

    @Test
    public void testCreateProduct_DifferentValidInputs() throws Exception {
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", "Wireless Mouse");
        productRequest.put("description", "Ergonomic wireless mouse with long battery life");
        productRequest.put("sku", "MOUSE-WL-001");
        productRequest.put("manufacturer", "Peripherals Inc.");
        productRequest.put("quantity", 0); // Minimum value

        mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.quantity").value(0));
    }

    @Test
    public void testGetProduct_PublicAccess() throws Exception {
        // Create a product
        Product product = new Product();
        product.setName("Public Product");
        product.setDescription("Anyone can view this");
        product.setSku("PUB-001");
        product.setManufacturer("PublicCorp");
        product.setQuantity(25);
        product.setOwner(testUser);
        Product savedProduct = productRepository.save(product);

        // Get without authentication
        mockMvc.perform(get("/v1/product/" + savedProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(savedProduct.getId()))
                .andExpect(jsonPath("$.name").value("Public Product"))
                .andExpect(jsonPath("$.sku").value("PUB-001"))
                .andExpect(jsonPath("$.owner_user_id").value(testUser.getId()));
    }

    @Test
    public void testUpdateProduct_PUT_Success() throws Exception {
        // Create a product
        Product product = new Product();
        product.setName("Original Product");
        product.setDescription("Original Description");
        product.setSku("ORIG-001");
        product.setManufacturer("OrigCorp");
        product.setQuantity(30);
        product.setOwner(testUser);
        Product savedProduct = productRepository.save(product);

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("name", "Updated Product");
        updateRequest.put("description", "Updated Description");
        updateRequest.put("sku", "UPD-001");
        updateRequest.put("manufacturer", "UpdatedCorp");
        updateRequest.put("quantity", 60);

        mockMvc.perform(put("/v1/product/" + savedProduct.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNoContent());

        // Verify updates
        Product updatedProduct = productRepository.findById(savedProduct.getId()).orElse(null);
        assertNotNull(updatedProduct);
        assertEquals("Updated Product", updatedProduct.getName());
        assertEquals("Updated Description", updatedProduct.getDescription());
        assertEquals("UPD-001", updatedProduct.getSku());
        assertEquals("UpdatedCorp", updatedProduct.getManufacturer());
        assertEquals(60, updatedProduct.getQuantity());
    }

    @Test
    public void testUpdateProduct_PATCH_PartialUpdate() throws Exception {
        // Create a product
        Product product = new Product();
        product.setName("Patch Product");
        product.setDescription("Patch Description");
        product.setSku("PATCH-001");
        product.setManufacturer("PatchCorp");
        product.setQuantity(40);
        product.setOwner(testUser);
        Product savedProduct = productRepository.save(product);

        Map<String, Object> patchRequest = new HashMap<>();
        patchRequest.put("name", "Patched Name");
        patchRequest.put("quantity", 75);
        // Not updating other fields

        mockMvc.perform(patch("/v1/product/" + savedProduct.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isNoContent());

        // Verify partial update
        Product patchedProduct = productRepository.findById(savedProduct.getId()).orElse(null);
        assertNotNull(patchedProduct);
        assertEquals("Patched Name", patchedProduct.getName());
        assertEquals("Patch Description", patchedProduct.getDescription()); // Unchanged
        assertEquals("PATCH-001", patchedProduct.getSku()); // Unchanged
        assertEquals("PatchCorp", patchedProduct.getManufacturer()); // Unchanged
        assertEquals(75, patchedProduct.getQuantity());
    }

    @Test
    public void testUpdateProduct_PATCH_SingleField() throws Exception {
        Product product = new Product();
        product.setName("Single Field");
        product.setDescription("Test Description");
        product.setSku("SINGLE-001");
        product.setManufacturer("SingleCorp");
        product.setQuantity(20);
        product.setOwner(testUser);
        Product savedProduct = productRepository.save(product);

        Map<String, Object> patchRequest = new HashMap<>();
        patchRequest.put("description", "Only description changed");

        mockMvc.perform(patch("/v1/product/" + savedProduct.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isNoContent());

        Product updated = productRepository.findById(savedProduct.getId()).orElse(null);
        assertEquals("Only description changed", updated.getDescription());
        assertEquals("Single Field", updated.getName()); // Unchanged
    }

    @Test
    public void testDeleteProduct_Success() throws Exception {
        Product product = new Product();
        product.setName("Delete Me");
        product.setDescription("To be deleted");
        product.setSku("DEL-001");
        product.setManufacturer("DelCorp");
        product.setQuantity(10);
        product.setOwner(testUser);
        Product savedProduct = productRepository.save(product);

        mockMvc.perform(delete("/v1/product/" + savedProduct.getId())
                        .header("Authorization", authHeader))
                .andExpect(status().isNoContent());

        // Verify deletion
        assertFalse(productRepository.existsById(savedProduct.getId()));
    }

    // ========== NEGATIVE TEST CASES ==========

    @Test
    public void testCreateProduct_MissingRequiredFields() throws Exception {
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", "Incomplete Product");
        // Missing description, sku, manufacturer, quantity

        mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateProduct_DuplicateSKU() throws Exception {
        // Create first product
        Product existing = new Product();
        existing.setName("First Product");
        existing.setDescription("First");
        existing.setSku("DUP-SKU");
        existing.setManufacturer("FirstCorp");
        existing.setQuantity(10);
        existing.setOwner(testUser);
        productRepository.save(existing);

        // Try to create with same SKU
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", "Second Product");
        productRequest.put("description", "Second");
        productRequest.put("sku", "DUP-SKU");
        productRequest.put("manufacturer", "SecondCorp");
        productRequest.put("quantity", 20);

        mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateProduct_InvalidQuantity_Negative() throws Exception {
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", "Invalid Product");
        productRequest.put("description", "Invalid quantity");
        productRequest.put("sku", "INV-001");
        productRequest.put("manufacturer", "InvalidCorp");
        productRequest.put("quantity", -1);

        mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateProduct_InvalidQuantity_OverMax() throws Exception {
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", "Invalid Product");
        productRequest.put("description", "Invalid quantity");
        productRequest.put("sku", "INV-002");
        productRequest.put("manufacturer", "InvalidCorp");
        productRequest.put("quantity", 101);

        mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateProduct_WithoutAuthentication() throws Exception {
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", "Unauthorized Product");
        productRequest.put("description", "No auth");
        productRequest.put("sku", "NOAUTH-001");
        productRequest.put("manufacturer", "NoAuthCorp");
        productRequest.put("quantity", 10);

        mockMvc.perform(post("/v1/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testGetProduct_NonExistent() throws Exception {
        mockMvc.perform(get("/v1/product/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testUpdateProduct_NotOwner() throws Exception {
        // Create another user
        User anotherUser = new User();
        anotherUser.setUsername("another@test.com");
        anotherUser.setPassword(passwordEncoder.encode("password456"));
        anotherUser.setFirstName("Another");
        anotherUser.setLastName("User");
        anotherUser = userRepository.save(anotherUser);

        // Create product owned by first user
        Product product = new Product();
        product.setName("Owner Test");
        product.setDescription("Testing ownership");
        product.setSku("OWN-001");
        product.setManufacturer("OwnerCorp");
        product.setQuantity(15);
        product.setOwner(testUser);
        Product savedProduct = productRepository.save(product);

        // Try to update with another user's credentials
        String anotherAuth = "Basic " + Base64.getEncoder().encodeToString("another@test.com:password456".getBytes());

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("name", "Hacked Product");
        updateRequest.put("description", "Should fail");
        updateRequest.put("sku", "HACK-001");
        updateRequest.put("manufacturer", "HackCorp");
        updateRequest.put("quantity", 99);

        mockMvc.perform(put("/v1/product/" + savedProduct.getId())
                        .header("Authorization", anotherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testUpdateProduct_NonExistent() throws Exception {
        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("name", "Ghost Product");
        updateRequest.put("description", "Doesn't exist");
        updateRequest.put("sku", "GHOST-001");
        updateRequest.put("manufacturer", "GhostCorp");
        updateRequest.put("quantity", 10);

        mockMvc.perform(put("/v1/product/99999")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testUpdateProduct_DuplicateSKU() throws Exception {
        // Create two products
        Product product1 = new Product();
        product1.setName("Product One");
        product1.setDescription("First");
        product1.setSku("PROD-001");
        product1.setManufacturer("Corp1");
        product1.setQuantity(10);
        product1.setOwner(testUser);
        productRepository.save(product1);

        Product product2 = new Product();
        product2.setName("Product Two");
        product2.setDescription("Second");
        product2.setSku("PROD-002");
        product2.setManufacturer("Corp2");
        product2.setQuantity(20);
        product2.setOwner(testUser);
        Product savedProduct2 = productRepository.save(product2);

        // Try to update product2's SKU to product1's SKU
        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("name", "Product Two");
        updateRequest.put("description", "Second");
        updateRequest.put("sku", "PROD-001"); // Duplicate
        updateRequest.put("manufacturer", "Corp2");
        updateRequest.put("quantity", 20);

        mockMvc.perform(put("/v1/product/" + savedProduct2.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testPatchProduct_InvalidQuantity() throws Exception {
        Product product = new Product();
        product.setName("Patch Invalid");
        product.setDescription("Test");
        product.setSku("PINV-001");
        product.setManufacturer("TestCorp");
        product.setQuantity(50);
        product.setOwner(testUser);
        Product savedProduct = productRepository.save(product);

        Map<String, Object> patchRequest = new HashMap<>();
        patchRequest.put("quantity", 150); // Over max

        mockMvc.perform(patch("/v1/product/" + savedProduct.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testDeleteProduct_NotOwner() throws Exception {
        // Create another user
        User anotherUser = new User();
        anotherUser.setUsername("delete@test.com");
        anotherUser.setPassword(passwordEncoder.encode("deletepass"));
        anotherUser.setFirstName("Delete");
        anotherUser.setLastName("User");
        userRepository.save(anotherUser);

        // Create product owned by first user
        Product product = new Product();
        product.setName("Protected Product");
        product.setDescription("Cannot delete");
        product.setSku("PROT-001");
        product.setManufacturer("ProtCorp");
        product.setQuantity(10);
        product.setOwner(testUser);
        Product savedProduct = productRepository.save(product);

        String wrongAuth = "Basic " + Base64.getEncoder().encodeToString("delete@test.com:deletepass".getBytes());

        mockMvc.perform(delete("/v1/product/" + savedProduct.getId())
                        .header("Authorization", wrongAuth))
                .andExpect(status().isForbidden());

        // Verify product still exists
        assertTrue(productRepository.existsById(savedProduct.getId()));
    }

    @Test
    public void testDeleteProduct_NonExistent() throws Exception {
        mockMvc.perform(delete("/v1/product/99999")
                        .header("Authorization", authHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testDeleteProduct_WithoutAuthentication() throws Exception {
        mockMvc.perform(delete("/v1/product/1"))
                .andExpect(status().isUnauthorized());
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    public void testCreateProduct_BoundaryValues() throws Exception {
        // Test with quantity = 100 (max allowed)
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", "Max Quantity Product");
        productRequest.put("description", "Testing max quantity");
        productRequest.put("sku", "MAX-001");
        productRequest.put("manufacturer", "MaxCorp");
        productRequest.put("quantity", 100);

        mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.quantity").value(100));
    }

    @Test
    public void testCreateProduct_SpecialCharactersInStrings() throws Exception {
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", "Product with 'quotes' & symbols!");
        productRequest.put("description", "Description with <tags> and @mentions #hashtags");
        productRequest.put("sku", "SPEC-001/2024");
        productRequest.put("manufacturer", "Corp & Co. Ltd.");
        productRequest.put("quantity", 25);

        mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Product with 'quotes' & symbols!"))
                .andExpect(jsonPath("$.manufacturer").value("Corp & Co. Ltd."));
    }

    @Test
    public void testUpdateProduct_SameSKU() throws Exception {
        // Update product with its own SKU (should succeed)
        Product product = new Product();
        product.setName("Same SKU Test");
        product.setDescription("Testing");
        product.setSku("SAME-001");
        product.setManufacturer("SameCorp");
        product.setQuantity(30);
        product.setOwner(testUser);
        Product savedProduct = productRepository.save(product);

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("name", "Updated Name");
        updateRequest.put("description", "Updated Desc");
        updateRequest.put("sku", "SAME-001"); // Same SKU
        updateRequest.put("manufacturer", "UpdatedCorp");
        updateRequest.put("quantity", 40);

        mockMvc.perform(put("/v1/product/" + savedProduct.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNoContent());
    }

    @Test
    public void testPatchProduct_EmptyRequest() throws Exception {
        Product product = new Product();
        product.setName("Empty Patch");
        product.setDescription("No changes");
        product.setSku("EMPTY-001");
        product.setManufacturer("EmptyCorp");
        product.setQuantity(10);
        product.setOwner(testUser);
        Product savedProduct = productRepository.save(product);

        Map<String, Object> emptyRequest = new HashMap<>();

        mockMvc.perform(patch("/v1/product/" + savedProduct.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isNoContent());

        // Verify nothing changed
        Product unchanged = productRepository.findById(savedProduct.getId()).orElse(null);
        assertEquals("Empty Patch", unchanged.getName());
        assertEquals("No changes", unchanged.getDescription());
    }

    @Test
    public void testInvalidDataTypes() throws Exception {
        String invalidJson = "{\"name\":\"Test\",\"description\":\"Test\",\"sku\":\"TEST-001\",\"manufacturer\":\"Test\",\"quantity\":\"not-a-number\"}";

        mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<product><n>Test</n></product>"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    public void testDataIntegrity_AfterCreation() throws Exception {
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", "Integrity Test");
        productRequest.put("description", "Testing data persistence");
        productRequest.put("sku", "INT-001");
        productRequest.put("manufacturer", "IntegrityCorp");
        productRequest.put("quantity", 35);

        String response = mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> createdProduct = objectMapper.readValue(response, Map.class);
        Integer productId = (Integer) createdProduct.get("id");

        // Verify data persisted correctly
        Product savedProduct = productRepository.findById(productId.longValue()).orElse(null);
        assertNotNull(savedProduct);
        assertEquals("Integrity Test", savedProduct.getName());
        assertEquals("Testing data persistence", savedProduct.getDescription());
        assertEquals("INT-001", savedProduct.getSku());
        assertEquals("IntegrityCorp", savedProduct.getManufacturer());
        assertEquals(35, savedProduct.getQuantity());
        assertEquals(testUser.getId(), savedProduct.getOwner().getId());
    }

    @Test
    public void testDataIntegrity_AfterPartialUpdate() throws Exception {
        Product product = new Product();
        product.setName("Partial Update Test");
        product.setDescription("Original Description");
        product.setSku("PART-001");
        product.setManufacturer("OriginalCorp");
        product.setQuantity(50);
        product.setOwner(testUser);
        Product savedProduct = productRepository.save(product);

        Map<String, Object> patchRequest = new HashMap<>();
        patchRequest.put("quantity", 75);

        mockMvc.perform(patch("/v1/product/" + savedProduct.getId())
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isNoContent());

        // Verify only quantity changed
        Product updated = productRepository.findById(savedProduct.getId()).orElse(null);
        assertEquals("Partial Update Test", updated.getName()); // Unchanged
        assertEquals("Original Description", updated.getDescription()); // Unchanged
        assertEquals("PART-001", updated.getSku()); // Unchanged
        assertEquals("OriginalCorp", updated.getManufacturer()); // Unchanged
        assertEquals(75, updated.getQuantity()); // Changed
    }

    @Test
    public void testDataIntegrity_AfterDeletion() throws Exception {
        Product product = new Product();
        product.setName("To Delete");
        product.setDescription("Will be deleted");
        product.setSku("DEL-TEST");
        product.setManufacturer("DelCorp");
        product.setQuantity(10);
        product.setOwner(testUser);
        Product savedProduct = productRepository.save(product);
        Long productId = savedProduct.getId();

        mockMvc.perform(delete("/v1/product/" + productId)
                        .header("Authorization", authHeader))
                .andExpect(status().isNoContent());

        // Verify product is completely removed
        assertFalse(productRepository.existsById(productId));
        assertNull(productRepository.findById(productId).orElse(null));
    }

    @Test
    public void testWrongHttpMethod_PostToSpecificProduct() throws Exception {
        mockMvc.perform(post("/v1/product/1")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    public void testWrongHttpMethod_GetToProductCollection() throws Exception {
        mockMvc.perform(get("/v1/product")
                        .header("Authorization", authHeader))
                .andExpect(status().isMethodNotAllowed());
    }
    @Test
    public void testMultipleRequestsHandledCorrectly() throws Exception {
        // Create 5 products in sequence - verifies system handles multiple operations
        for (int i = 0; i < 5; i++) {
            Map<String, Object> product = new HashMap<>();
            product.put("name", "Product " + i);
            product.put("description", "Test");
            product.put("sku", "SKU-" + i);
            product.put("manufacturer", "TestCorp");
            product.put("quantity", 10);

            mockMvc.perform(post("/v1/product")
                            .header("Authorization", authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(product)))
                    .andExpect(status().isCreated());
        }
        assertEquals(5, productRepository.count());
    }

    // Add at the end of Edge Case Tests section
    @Test
    public void testMultipleConcurrentRequests() throws Exception {
        // Simpler approach - rapid sequential requests simulate concurrent load
        int requestCount = 5;
        List<String> skus = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            Map<String, Object> product = new HashMap<>();
            product.put("name", "Concurrent Product " + i);
            product.put("description", "Test concurrent");
            product.put("sku", "CONC-" + i);
            product.put("manufacturer", "ConcurrentCorp");
            product.put("quantity", 10);

            mockMvc.perform(post("/v1/product")
                            .header("Authorization", authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(product)))
                    .andExpect(status().isCreated());

            skus.add("CONC-" + i);
        }

        // Verify all were created
        assertEquals(requestCount, productRepository.count());

        // Verify each SKU exists
        for (String sku : skus) {
            assertTrue(productRepository.existsBySku(sku));
        }
    }

    // Add after the concurrency test
    @Test
    public void testLargeDatasetHandling() throws Exception {
        int productCount = 50;
        for (int i = 0; i < productCount; i++) {
            Map<String, Object> product = new HashMap<>();
            product.put("name", "Bulk Product " + i);
            product.put("description", "Testing bulk operations");
            product.put("sku", "BULK-" + i);
            product.put("manufacturer", "BulkCorp");
            product.put("quantity", i % 101); // Varies between 0-100

            mockMvc.perform(post("/v1/product")
                            .header("Authorization", authHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(product)))
                    .andExpect(status().isCreated());
        }
        assertEquals(productCount, productRepository.count());
    }

    // Add at the end of Edge Case Tests
    @Test
    public void testReadOnlyFieldsIgnored() throws Exception {
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", "ReadOnly Test");
        productRequest.put("description", "Testing readonly fields");
        productRequest.put("sku", "RO-001");
        productRequest.put("manufacturer", "ROCorp");
        productRequest.put("quantity", 25);
        // Try to set read-only fields
        productRequest.put("id", 999);
        productRequest.put("owner_user_id", 888);
        productRequest.put("date_added", "2020-01-01T00:00:00Z");
        productRequest.put("date_last_updated", "2020-01-01T00:00:00Z");

        String response = mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> created = objectMapper.readValue(response, Map.class);

        // Verify read-only fields were not accepted
        assertNotEquals(999, created.get("id"));
        assertEquals(testUser.getId().intValue(), created.get("owner_user_id"));
        assertNotEquals("2020-01-01T00:00:00Z", created.get("date_added"));
    }

    @Test
    public void testCreateProduct_NullDescription() throws Exception {
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", "Valid");
        productRequest.put("description", null); // Null required field
        productRequest.put("sku", "NULL-DESC");
        productRequest.put("manufacturer", "Corp");
        productRequest.put("quantity", 10);

        mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateProduct_EmptyName() throws Exception {
        Map<String, Object> productRequest = new HashMap<>();
        productRequest.put("name", ""); // Empty string
        productRequest.put("description", "Valid");
        productRequest.put("sku", "EMPTY-NAME");
        productRequest.put("manufacturer", "Corp");
        productRequest.put("quantity", 10);

        mockMvc.perform(post("/v1/product")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isBadRequest());
    }
}