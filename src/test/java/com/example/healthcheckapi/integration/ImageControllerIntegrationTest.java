package com.example.healthcheckapi.integration;

import com.example.healthcheckapi.entity.Image;
import com.example.healthcheckapi.entity.Product;
import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.repository.ImageRepository;
import com.example.healthcheckapi.repository.ProductRepository;
import com.example.healthcheckapi.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {
        "file.upload-dir=./build/test-uploads"
})
public class ImageControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private User testUser;
    private User anotherUser;
    private Product testProduct;
    private String authHeader;
    private String anotherAuthHeader;

    @BeforeEach
    public void setup() throws Exception {
        // Clean up database
        imageRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        // Clean up upload directory
        cleanUploadDirectory();

        // Create test users
        testUser = new User();
        testUser.setUsername("imagetest@test.com");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser.setFirstName("Image");
        testUser.setLastName("Tester");
        testUser = userRepository.save(testUser);

        anotherUser = new User();
        anotherUser.setUsername("another@test.com");
        anotherUser.setPassword(passwordEncoder.encode("password456"));
        anotherUser.setFirstName("Another");
        anotherUser.setLastName("User");
        anotherUser = userRepository.save(anotherUser);

        // Create test product
        testProduct = new Product();
        testProduct.setName("Test Product");
        testProduct.setDescription("Product for image testing");
        testProduct.setSku("IMG-TEST-001");
        testProduct.setManufacturer("TestCorp");
        testProduct.setQuantity(10);
        testProduct.setOwner(testUser);
        testProduct = productRepository.save(testProduct);

        // Setup auth headers
        authHeader = "Basic " + Base64.getEncoder()
                .encodeToString("imagetest@test.com:password123".getBytes());
        anotherAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString("another@test.com:password456".getBytes());
    }

    private void cleanUploadDirectory() throws Exception {
        Path uploadPath = Paths.get(uploadDir);
        if (Files.exists(uploadPath)) {
            Files.walk(uploadPath)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            if (!path.equals(uploadPath)) {
                                Files.delete(path);
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    });
        }
    }

    // ========== POSITIVE TEST CASES ==========

    @Test
    public void testUploadImage_Success_JPG() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                "fake image content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location",
                        containsString("/v1/product/" + testProduct.getId() + "/image/")))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.image_id").exists())
                .andExpect(jsonPath("$.product_id").value(testProduct.getId()))
                .andExpect(jsonPath("$.file_name").value("test-image.jpg"))
                .andExpect(jsonPath("$.date_created").exists())
                .andExpect(jsonPath("$.s3_bucket_path").exists())
                .andExpect(jsonPath("$.s3_bucket_path",
                        startsWith("user_" + testUser.getId())));

        // Verify database persistence
        assertEquals(1, imageRepository.count());

        // Verify file exists on disk
        Image savedImage = imageRepository.findAll().get(0);
        Path filePath = Paths.get(uploadDir, savedImage.getS3BucketPath());
        assertTrue(Files.exists(filePath));
    }

    @Test
    public void testUploadImage_Success_PNG() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.png",
                "image/png",
                "fake png content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file_name").value("test-image.png"));
    }

    @Test
    public void testUploadImage_Success_JPEG() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.jpeg",
                "image/jpeg",
                "fake jpeg content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file_name").value("test-image.jpeg"));
    }

    @Test
    public void testUploadMultipleImages_Success() throws Exception {
        // Upload first image
        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "image1.jpg",
                "image/jpeg",
                "content 1".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file1)
                        .header("Authorization", authHeader))
                .andExpect(status().isCreated());

        // Upload second image
        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "image2.png",
                "image/png",
                "content 2".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file2)
                        .header("Authorization", authHeader))
                .andExpect(status().isCreated());

        // Upload third image
        MockMultipartFile file3 = new MockMultipartFile(
                "file",
                "image3.jpg",
                "image/jpeg",
                "content 3".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file3)
                        .header("Authorization", authHeader))
                .andExpect(status().isCreated());

        // Verify all three images are saved
        assertEquals(3, imageRepository.count());
    }

    @Test
    public void testUploadImage_SameFileName_DifferentUsers() throws Exception {
        // Create product for another user
        Product anotherProduct = new Product();
        anotherProduct.setName("Another Product");
        anotherProduct.setDescription("Different product");
        anotherProduct.setSku("IMG-TEST-002");
        anotherProduct.setManufacturer("AnotherCorp");
        anotherProduct.setQuantity(5);
        anotherProduct.setOwner(anotherUser);
        anotherProduct = productRepository.save(anotherProduct);

        // Upload same filename to both products
        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "same-name.jpg",
                "image/jpeg",
                "user1 content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file1)
                        .header("Authorization", authHeader))
                .andExpect(status().isCreated());

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "same-name.jpg",
                "image/jpeg",
                "user2 content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + anotherProduct.getId() + "/image")
                        .file(file2)
                        .header("Authorization", anotherAuthHeader))
                .andExpect(status().isCreated());

        // Both should succeed - files stored in different directories
        assertEquals(2, imageRepository.count());

        List<Image> images = imageRepository.findAll();
        // Verify they're in different user directories
        assertNotEquals(images.get(0).getS3BucketPath(), images.get(1).getS3BucketPath());
    }

    @Test
    public void testGetAllImages_PublicAccess() throws Exception {
        // Upload images first
        uploadTestImage("image1.jpg");
        uploadTestImage("image2.png");
        uploadTestImage("image3.jpg");

        // Get images without authentication (public endpoint)
        mockMvc.perform(get("/v1/product/" + testProduct.getId() + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].image_id").exists())
                .andExpect(jsonPath("$[0].product_id").value(testProduct.getId()))
                .andExpect(jsonPath("$[0].file_name").exists())
                .andExpect(jsonPath("$[0].date_created").exists())
                .andExpect(jsonPath("$[0].s3_bucket_path").exists());
    }

    @Test
    public void testGetAllImages_EmptyList() throws Exception {
        // No images uploaded
        mockMvc.perform(get("/v1/product/" + testProduct.getId() + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void testGetSpecificImage_PublicAccess() throws Exception {
        // Upload an image
        Image uploadedImage = uploadTestImage("specific-image.jpg");

        // Get specific image without authentication
        mockMvc.perform(get("/v1/product/" + testProduct.getId() +
                        "/image/" + uploadedImage.getImageId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.image_id").value(uploadedImage.getImageId()))
                .andExpect(jsonPath("$.product_id").value(testProduct.getId()))
                .andExpect(jsonPath("$.file_name").value("specific-image.jpg"))
                .andExpect(jsonPath("$.date_created").exists())
                .andExpect(jsonPath("$.s3_bucket_path").exists());
    }

    @Test
    public void testDeleteImage_Success() throws Exception {
        // Upload an image
        Image uploadedImage = uploadTestImage("delete-me.jpg");
        Long imageId = uploadedImage.getImageId();
        String filePath = uploadedImage.getS3BucketPath();

        // Verify image and file exist before deletion
        assertTrue(imageRepository.existsById(imageId));
        assertTrue(Files.exists(Paths.get(uploadDir, filePath)));

        // Delete the image
        mockMvc.perform(delete("/v1/product/" + testProduct.getId() +
                        "/image/" + imageId)
                        .header("Authorization", authHeader))
                .andExpect(status().isNoContent());

        // Verify image deleted from database
        assertFalse(imageRepository.existsById(imageId));

        // Verify file deleted from disk
        assertFalse(Files.exists(Paths.get(uploadDir, filePath)));
    }

    @Test
    public void testDeleteImage_MultipleImages_DeleteOne() throws Exception {
        // Upload multiple images
        Image image1 = uploadTestImage("keep1.jpg");
        Image image2 = uploadTestImage("delete.jpg");
        Image image3 = uploadTestImage("keep2.jpg");

        // Delete middle image
        mockMvc.perform(delete("/v1/product/" + testProduct.getId() +
                        "/image/" + image2.getImageId())
                        .header("Authorization", authHeader))
                .andExpect(status().isNoContent());

        // Verify only one deleted
        assertEquals(2, imageRepository.count());
        assertTrue(imageRepository.existsById(image1.getImageId()));
        assertFalse(imageRepository.existsById(image2.getImageId()));
        assertTrue(imageRepository.existsById(image3.getImageId()));
    }

    // ========== NEGATIVE TEST CASES ==========

    @Test
    public void testUploadImage_WithoutAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file))
                .andExpect(status().isUnauthorized());

        // Verify no image was saved
        assertEquals(0, imageRepository.count());
    }

    @Test
    public void testUploadImage_WithoutAuthentication_IncludesWwwAuthenticate() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")));
    }

    @Test
    public void testUploadImage_InvalidCredentials() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        String invalidAuth = "Basic " + Base64.getEncoder()
                .encodeToString("imagetest@test.com:wrongpassword".getBytes());

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", invalidAuth))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testUploadImage_NotOwner() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        // Try to upload to testUser's product using anotherUser's credentials
        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", anotherAuthHeader))
                .andExpect(status().isForbidden());

        // Verify no image was saved
        assertEquals(0, imageRepository.count());
    }

    @Test
    public void testUploadImage_NonExistentProduct() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/99999/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testUploadImage_InvalidFileType_GIF() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.gif",
                "image/gif",
                "gif content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isBadRequest());

        assertEquals(0, imageRepository.count());
    }

    @Test
    public void testUploadImage_InvalidFileType_PDF() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testUploadImage_InvalidFileType_TXT() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "file.txt",
                "text/plain",
                "text content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testUploadImage_EmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testUploadImage_NoFileProvided() throws Exception {
        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .header("Authorization", authHeader))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testUploadImage_NoFileExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "noextension",
                "image/jpeg",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testGetAllImages_NonExistentProduct() throws Exception {
        mockMvc.perform(get("/v1/product/99999/image"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testGetSpecificImage_NonExistentProduct() throws Exception {
        mockMvc.perform(get("/v1/product/99999/image/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testGetSpecificImage_NonExistentImage() throws Exception {
        mockMvc.perform(get("/v1/product/" + testProduct.getId() + "/image/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testGetSpecificImage_ImageFromDifferentProduct() throws Exception {
        // Create another product
        Product anotherProduct = new Product();
        anotherProduct.setName("Another Product");
        anotherProduct.setDescription("Different");
        anotherProduct.setSku("IMG-TEST-003");
        anotherProduct.setManufacturer("Corp");
        anotherProduct.setQuantity(5);
        anotherProduct.setOwner(testUser);
        anotherProduct = productRepository.save(anotherProduct);

        // Upload image to first product
        Image image = uploadTestImage("test.jpg");

        // Try to get it using second product's ID
        mockMvc.perform(get("/v1/product/" + anotherProduct.getId() +
                        "/image/" + image.getImageId()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testDeleteImage_WithoutAuthentication() throws Exception {
        Image image = uploadTestImage("test.jpg");

        mockMvc.perform(delete("/v1/product/" + testProduct.getId() +
                        "/image/" + image.getImageId()))
                .andExpect(status().isUnauthorized());

        // Verify image still exists
        assertTrue(imageRepository.existsById(image.getImageId()));
    }

    @Test
    public void testDeleteImage_NotOwner() throws Exception {
        Image image = uploadTestImage("test.jpg");

        // Try to delete with another user's credentials
        mockMvc.perform(delete("/v1/product/" + testProduct.getId() +
                        "/image/" + image.getImageId())
                        .header("Authorization", anotherAuthHeader))
                .andExpect(status().isForbidden());

        // Verify image still exists
        assertTrue(imageRepository.existsById(image.getImageId()));
    }

    @Test
    public void testDeleteImage_NonExistentProduct() throws Exception {
        mockMvc.perform(delete("/v1/product/99999/image/1")
                        .header("Authorization", authHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testDeleteImage_NonExistentImage() throws Exception {
        mockMvc.perform(delete("/v1/product/" + testProduct.getId() + "/image/99999")
                        .header("Authorization", authHeader))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testDeleteImage_ImageFromDifferentProduct() throws Exception {
        // Create another product for same user
        Product anotherProduct = new Product();
        anotherProduct.setName("Another Product");
        anotherProduct.setDescription("Different");
        anotherProduct.setSku("IMG-TEST-004");
        anotherProduct.setManufacturer("Corp");
        anotherProduct.setQuantity(5);
        anotherProduct.setOwner(testUser);
        anotherProduct = productRepository.save(anotherProduct);

        // Upload image to first product
        Image image = uploadTestImage("test.jpg");

        // Try to delete using second product's ID
        mockMvc.perform(delete("/v1/product/" + anotherProduct.getId() +
                        "/image/" + image.getImageId())
                        .header("Authorization", authHeader))
                .andExpect(status().isNotFound());

        // Verify image still exists
        assertTrue(imageRepository.existsById(image.getImageId()));
    }

    @Test
    public void testDeleteImage_AlreadyDeleted() throws Exception {
        Image image = uploadTestImage("test.jpg");
        Long imageId = image.getImageId();

        // Delete once
        mockMvc.perform(delete("/v1/product/" + testProduct.getId() +
                        "/image/" + imageId)
                        .header("Authorization", authHeader))
                .andExpect(status().isNoContent());

        // Try to delete again
        mockMvc.perform(delete("/v1/product/" + testProduct.getId() +
                        "/image/" + imageId)
                        .header("Authorization", authHeader))
                .andExpect(status().isNotFound());
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    public void testUploadImage_CaseInsensitiveExtension_JPG() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.JPG",
                "image/jpeg",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isCreated());
    }

    @Test
    public void testUploadImage_CaseInsensitiveExtension_Png() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.Png",
                "image/png",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isCreated());
    }

    @Test
    public void testUploadImage_SpecialCharactersInFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test image with spaces & symbols!.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file_name")
                        .value("test image with spaces & symbols!.jpg"));
    }

    @Test
    public void testUploadImage_VeryLongFilename() throws Exception {
        String longName = "a".repeat(200) + ".jpg";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                longName,
                "image/jpeg",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/v1/product/" + testProduct.getId() + "/image")
                        .file(file)
                        .header("Authorization", authHeader))
                .andExpect(status().isCreated());
    }

    @Test
    public void testUploadImage_UniqueStoragePaths() throws Exception {
        // Upload two images with same filename
        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "duplicate.jpg",
                "image/jpeg",
                "content 1".getBytes()
        );

        String response1 = mockMvc.perform(
                        multipart("/v1/product/" + testProduct.getId() + "/image")
                                .file(file1)
                                .header("Authorization", authHeader))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "duplicate.jpg",
                "image/jpeg",
                "content 2".getBytes()
        );

        String response2 = mockMvc.perform(
                        multipart("/v1/product/" + testProduct.getId() + "/image")
                                .file(file2)
                                .header("Authorization", authHeader))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Parse responses
        var image1 = objectMapper.readValue(response1, java.util.Map.class);
        var image2 = objectMapper.readValue(response2, java.util.Map.class);

        // Verify different storage paths (UUID makes them unique)
        assertNotEquals(image1.get("s3_bucket_path"), image2.get("s3_bucket_path"));
    }

    @Test
    public void testDataIntegrity_AfterImageUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "integrity-test.jpg",
                "image/jpeg",
                "test content".getBytes()
        );

        String response = mockMvc.perform(
                        multipart("/v1/product/" + testProduct.getId() + "/image")
                                .file(file)
                                .header("Authorization", authHeader))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var responseMap = objectMapper.readValue(response, java.util.Map.class);
        Integer imageId = (Integer) responseMap.get("image_id");

        // Verify database record
        Image savedImage = imageRepository.findById(imageId.longValue()).orElse(null);
        assertNotNull(savedImage);
        assertEquals("integrity-test.jpg", savedImage.getFileName());
        assertEquals(testProduct.getId(), savedImage.getProduct().getId());
        assertNotNull(savedImage.getDateCreated());
        assertNotNull(savedImage.getS3BucketPath());
        assertTrue(savedImage.getS3BucketPath()
                .startsWith("user_" + testUser.getId() + "/product_" + testProduct.getId()));

        // Verify file on disk
        Path filePath = Paths.get(uploadDir, savedImage.getS3BucketPath());
        assertTrue(Files.exists(filePath));
        assertEquals("test content", new String(Files.readAllBytes(filePath)));
    }

    @Test
    public void testDataIntegrity_AfterImageDeletion() throws Exception {
        Image image = uploadTestImage("deletion-test.jpg");
        String storagePath = image.getS3BucketPath();
        Long imageId = image.getImageId();

        // Verify exists before deletion
        Path filePath = Paths.get(uploadDir, storagePath);
        assertTrue(Files.exists(filePath));
        assertTrue(imageRepository.existsById(imageId));

        // Delete
        mockMvc.perform(delete("/v1/product/" + testProduct.getId() +
                        "/image/" + imageId)
                        .header("Authorization", authHeader))
                .andExpect(status().isNoContent());

        // Verify complete removal
        assertFalse(imageRepository.existsById(imageId));
        assertFalse(Files.exists(filePath));
        assertNull(imageRepository.findById(imageId).orElse(null));
    }

    @Test
    public void testCascadeDelete_ProductDeletion() throws Exception {
        // Upload images
        uploadTestImage("cascade1.jpg");
        uploadTestImage("cascade2.jpg");
        uploadTestImage("cascade3.jpg");

        assertEquals(3, imageRepository.count());

        // Delete product (should cascade delete images)
        productRepository.delete(testProduct);

        // Verify images deleted from database
        assertEquals(0, imageRepository.count());
    }

    @Test
    public void testMultipleImagesHandling() throws Exception {
        int imageCount = 10;
        for (int i = 0; i < imageCount; i++) {
            uploadTestImage("bulk-image-" + i + ".jpg");
        }

        assertEquals(imageCount, imageRepository.count());

        // Verify all can be retrieved
        mockMvc.perform(get("/v1/product/" + testProduct.getId() + "/image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(imageCount)));
    }

    // ========== HELPER METHODS ==========

    private Image uploadTestImage(String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                "image/jpeg",
                ("content of " + filename).getBytes()
        );

        String response = mockMvc.perform(
                        multipart("/v1/product/" + testProduct.getId() + "/image")
                                .file(file)
                                .header("Authorization", authHeader))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        var responseMap = objectMapper.readValue(response, java.util.Map.class);
        Integer imageId = (Integer) responseMap.get("image_id");

        return imageRepository.findById(imageId.longValue()).orElse(null);
    }
}