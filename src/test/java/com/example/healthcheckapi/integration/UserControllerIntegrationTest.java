package com.example.healthcheckapi.integration;

import com.example.healthcheckapi.entity.User;
import com.example.healthcheckapi.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class UserControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    public void setup() {
        userRepository.deleteAll();
    }

    // ========== POSITIVE TEST CASES ==========

    @Test
    public void testCreateUser_Success() throws Exception {
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("username", "test@example.com");
        userRequest.put("password", "password123");
        userRequest.put("first_name", "John");
        userRequest.put("last_name", "Doe");

        mockMvc.perform(post("/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", containsString("/v1/user/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").value("test@example.com"))
                .andExpect(jsonPath("$.first_name").value("John"))
                .andExpect(jsonPath("$.last_name").value("Doe"))
                .andExpect(jsonPath("$.account_created").exists())
                .andExpect(jsonPath("$.account_updated").exists())
                .andExpect(jsonPath("$.password").doesNotExist());

        // Verify data persistence
        assertTrue(userRepository.existsByUsername("test@example.com"));
    }

    @Test
    public void testCreateUser_WithDifferentValidInputs() throws Exception {
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("username", "jane.smith@company.org");
        userRequest.put("password", "SecurePass@2024");
        userRequest.put("first_name", "Jane");
        userRequest.put("last_name", "Smith-Johnson");

        mockMvc.perform(post("/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("jane.smith@company.org"))
                .andExpect(jsonPath("$.first_name").value("Jane"))
                .andExpect(jsonPath("$.last_name").value("Smith-Johnson"));
    }

    @Test
    public void testGetUser_Success() throws Exception {
        // Create user first
        User user = new User();
        user.setUsername("getuser@test.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFirstName("Get");
        user.setLastName("Test");
        User savedUser = userRepository.save(user);

        String auth = Base64.getEncoder().encodeToString("getuser@test.com:password123".getBytes());

        mockMvc.perform(get("/v1/user/" + savedUser.getId())
                        .header("Authorization", "Basic " + auth))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(savedUser.getId()))
                .andExpect(jsonPath("$.username").value("getuser@test.com"))
                .andExpect(jsonPath("$.first_name").value("Get"))
                .andExpect(jsonPath("$.last_name").value("Test"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    public void testUpdateUser_FullUpdate() throws Exception {
        // Create user first
        User user = new User();
        user.setUsername("update@test.com");
        user.setPassword(passwordEncoder.encode("oldpassword"));
        user.setFirstName("Old");
        user.setLastName("Name");
        User savedUser = userRepository.save(user);

        String auth = Base64.getEncoder().encodeToString("update@test.com:oldpassword".getBytes());

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("first_name", "New");
        updateRequest.put("last_name", "Updated");
        updateRequest.put("password", "newpassword123");

        mockMvc.perform(put("/v1/user/" + savedUser.getId())
                        .header("Authorization", "Basic " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNoContent());

        // Verify update
        User updatedUser = userRepository.findById(savedUser.getId()).orElse(null);
        assertNotNull(updatedUser);
        assertEquals("New", updatedUser.getFirstName());
        assertEquals("Updated", updatedUser.getLastName());
        assertTrue(passwordEncoder.matches("newpassword123", updatedUser.getPassword()));
    }

    @Test
    public void testUpdateUser_PartialUpdate() throws Exception {
        User user = new User();
        user.setUsername("partial@test.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFirstName("Original");
        user.setLastName("Name");
        User savedUser = userRepository.save(user);

        String auth = Base64.getEncoder().encodeToString("partial@test.com:password123".getBytes());

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("first_name", "Modified");

        mockMvc.perform(put("/v1/user/" + savedUser.getId())
                        .header("Authorization", "Basic " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNoContent());

        User updatedUser = userRepository.findById(savedUser.getId()).orElse(null);
        assertEquals("Modified", updatedUser.getFirstName());
        assertEquals("Name", updatedUser.getLastName()); // Unchanged
    }

    // ========== NEGATIVE TEST CASES ==========

    @Test
    public void testCreateUser_MissingRequiredFields() throws Exception {
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("username", "test@example.com");
        // Missing password, first_name, last_name

        mockMvc.perform(post("/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateUser_InvalidEmailFormat() throws Exception {
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("username", "notanemail");
        userRequest.put("password", "password123");
        userRequest.put("first_name", "John");
        userRequest.put("last_name", "Doe");

        mockMvc.perform(post("/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateUser_DuplicateEmail() throws Exception {
        // Create first user
        User existingUser = new User();
        existingUser.setUsername("duplicate@test.com");
        existingUser.setPassword(passwordEncoder.encode("password123"));
        existingUser.setFirstName("First");
        existingUser.setLastName("User");
        userRepository.save(existingUser);

        // Try to create duplicate
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("username", "duplicate@test.com");
        userRequest.put("password", "password456");
        userRequest.put("first_name", "Second");
        userRequest.put("last_name", "User");

        mockMvc.perform(post("/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateUser_ShortPassword() throws Exception {
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("username", "test@example.com");
        userRequest.put("password", "short");  // Less than 8 characters
        userRequest.put("first_name", "John");
        userRequest.put("last_name", "Doe");

        mockMvc.perform(post("/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testGetUser_WithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v1/user/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testGetUser_WithoutAuthentication_IncludesWwwAuthenticate() throws Exception {
        mockMvc.perform(get("/v1/user/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Basic")));
    }

    @Test
    public void testGetUser_WithInvalidCredentials() throws Exception {
        User user = new User();
        user.setUsername("auth@test.com");
        user.setPassword(passwordEncoder.encode("correctpassword"));
        user.setFirstName("Auth");
        user.setLastName("Test");
        User savedUser = userRepository.save(user);

        String auth = Base64.getEncoder().encodeToString("auth@test.com:wrongpassword".getBytes());

        mockMvc.perform(get("/v1/user/" + savedUser.getId())
                        .header("Authorization", "Basic " + auth))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testGetUser_AccessingOtherUserData() throws Exception {
        // Create two users
        User user1 = new User();
        user1.setUsername("user1@test.com");
        user1.setPassword(passwordEncoder.encode("password1"));
        user1.setFirstName("User");
        user1.setLastName("One");
        userRepository.save(user1);

        User user2 = new User();
        user2.setUsername("user2@test.com");
        user2.setPassword(passwordEncoder.encode("password2"));
        user2.setFirstName("User");
        user2.setLastName("Two");
        User savedUser2 = userRepository.save(user2);

        // Try to access user2's data with user1's credentials
        String auth = Base64.getEncoder().encodeToString("user1@test.com:password1".getBytes());

        mockMvc.perform(get("/v1/user/" + savedUser2.getId())
                        .header("Authorization", "Basic " + auth))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testGetUser_NonExistentId() throws Exception {
        User user = new User();
        user.setUsername("exists@test.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFirstName("Exists");
        user.setLastName("User");
        userRepository.save(user);

        String auth = Base64.getEncoder().encodeToString("exists@test.com:password123".getBytes());

        mockMvc.perform(get("/v1/user/99999")
                        .header("Authorization", "Basic " + auth))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testUpdateUser_WithoutAuthentication() throws Exception {
        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("first_name", "New");

        mockMvc.perform(put("/v1/user/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testUpdateUser_OtherUserData() throws Exception {
        // Create two users
        User user1 = new User();
        user1.setUsername("update1@test.com");
        user1.setPassword(passwordEncoder.encode("password1"));
        user1.setFirstName("User");
        user1.setLastName("One");
        userRepository.save(user1);

        User user2 = new User();
        user2.setUsername("update2@test.com");
        user2.setPassword(passwordEncoder.encode("password2"));
        user2.setFirstName("User");
        user2.setLastName("Two");
        User savedUser2 = userRepository.save(user2);

        String auth = Base64.getEncoder().encodeToString("update1@test.com:password1".getBytes());

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("first_name", "Hacked");

        mockMvc.perform(put("/v1/user/" + savedUser2.getId())
                        .header("Authorization", "Basic " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testUpdateUser_InvalidFields() throws Exception {
        User user = new User();
        user.setUsername("invalid@test.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFirstName("Valid");
        user.setLastName("User");
        User savedUser = userRepository.save(user);

        String auth = Base64.getEncoder().encodeToString("invalid@test.com:password123".getBytes());

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("username", "newemail@test.com"); // Username cannot be updated
        updateRequest.put("id", 999); // ID cannot be updated

        mockMvc.perform(put("/v1/user/" + savedUser.getId())
                        .header("Authorization", "Basic " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testUpdateUser_ShortPassword() throws Exception {
        User user = new User();
        user.setUsername("shortpass@test.com");
        user.setPassword(passwordEncoder.encode("oldpassword"));
        user.setFirstName("Short");
        user.setLastName("Pass");
        User savedUser = userRepository.save(user);

        String auth = Base64.getEncoder().encodeToString("shortpass@test.com:oldpassword".getBytes());

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("password", "short"); // Less than 8 characters

        mockMvc.perform(put("/v1/user/" + savedUser.getId())
                        .header("Authorization", "Basic " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    // ========== EDGE CASE TESTS ==========

    @Test
    public void testCreateUser_MaxLengthStrings() throws Exception {
        String longNameString = "a".repeat(255); // Max for names
        String maxPasswordString = "a".repeat(72); // BCrypt max is 72 bytes

        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("username", "maxlength@test.com");
        userRequest.put("password", maxPasswordString); // Use 72 chars for password
        userRequest.put("first_name", longNameString);
        userRequest.put("last_name", longNameString);

        mockMvc.perform(post("/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated());
    }

    @Test
    public void testCreateUser_SpecialCharactersInName() throws Exception {
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put("username", "special@test.com");
        userRequest.put("password", "password123");
        userRequest.put("first_name", "O'Connor-Smith");
        userRequest.put("last_name", "José María");

        mockMvc.perform(post("/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.first_name").value("O'Connor-Smith"))
                .andExpect(jsonPath("$.last_name").value("José María"));
    }

    @Test
    public void testUpdateUser_NoChanges() throws Exception {
        User user = new User();
        user.setUsername("nochange@test.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFirstName("Same");
        user.setLastName("Name");
        User savedUser = userRepository.save(user);

        String auth = Base64.getEncoder().encodeToString("nochange@test.com:password123".getBytes());

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("first_name", "Same");
        updateRequest.put("last_name", "Name");

        mockMvc.perform(put("/v1/user/" + savedUser.getId())
                        .header("Authorization", "Basic " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNoContent());
    }

    @Test
    public void testWrongHttpMethod_DeleteUser_405AndAllowHeader() throws Exception {
        // Create a real user so we can auth
        User u = new User();
        u.setUsername("method@test.com");
        u.setPassword(passwordEncoder.encode("pass12345"));
        u.setFirstName("Method");
        u.setLastName("Tester");
        u = userRepository.save(u);

        String auth = "Basic " + Base64.getEncoder().encodeToString("method@test.com:pass12345".getBytes());

        mockMvc.perform(delete("/v1/user/" + u.getId())
                        .header("Authorization", auth))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(header().string("Allow", containsString("PUT")));
    }

    @Test
    public void testUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/v1/user")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<user><username>test@test.com</username></user>"))
                .andExpect(status().isUnsupportedMediaType());
    }
    @Test
    public void testUnknownEndpoint_Authenticated_Returns403() throws Exception {
        // Create a user for authentication
        User user = new User();
        user.setUsername("test@test.com");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFirstName("Test");
        user.setLastName("User");
        userRepository.save(user);

        String auth = "Basic " + Base64.getEncoder().encodeToString("test@test.com:password123".getBytes());

        // With authentication, unknown endpoints return 403 due to .anyRequest().denyAll()
        mockMvc.perform(get("/does-not-exist")
                        .header("Authorization", auth))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/v2/user")
                        .header("Authorization", auth))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/invalid")
                        .header("Authorization", auth))
                .andExpect(status().isForbidden());
    }
}