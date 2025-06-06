package com.example.demo.api;

import com.example.demo.dto.UserDTO;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminAPI {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // CRUD: Create (dành cho admin, khác với register)
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody UserDTO userDTO) {
        // Kiểm tra email đã tồn tại
        if (userRepository.findByEmail(userDTO.getEmail()) != null) {
            return ResponseEntity.badRequest().body("Email already exists");
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword())); // Mã hóa password
        user.setFull_name(userDTO.getFull_name());
        user.setSlug(userDTO.getFull_name().toLowerCase().replace(" ", "-"));

        // Gán role nếu được chỉ định, nếu không gán role mặc định "customer"
        UUID roleId = userDTO.getRole_id();
        Role role;
        if (roleId != null) {
            role = roleRepository.findById(roleId)
                    .orElse(null);
            if (role == null) {
                return ResponseEntity.badRequest().body("Lỗi: Role với ID " + roleId + " không tồn tại");
            }
        } else {
            role = roleRepository.findByName("customer")
                    .orElseGet(() -> {
                        Role defaultRole = new Role();
                        defaultRole.setId(UUID.randomUUID());
                        defaultRole.setName("customer");
                        defaultRole.setDescription("Khách hàng mặc định");
                        return roleRepository.save(defaultRole);
                    });
        }
        user.setRole(role);

        // Lưu user vào database
        userRepository.save(user);
        return ResponseEntity.ok("User created successfully");
    }

    // CRUD: Read (lấy danh sách tất cả users, dành cho admin)
    @GetMapping("/get-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<UserDTO> userDTOs = users.stream().map(user -> {
            UserDTO userDTO = new UserDTO();
            userDTO.setId(user.getId());
            userDTO.setEmail(user.getEmail());
            userDTO.setFull_name(user.getFull_name());
            userDTO.setRole_id(user.getRole() != null ? user.getRole().getId() : null);
            return userDTO;
        }).toList();
        return ResponseEntity.ok(userDTOs);
    }

    // CRUD: Read (lấy thông tin chi tiết một user theo id, dành cho admin)
    @GetMapping("/get-by-id/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserById(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Lỗi: Người dùng với ID " + id + " không tồn tại");
        }

        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setEmail(user.getEmail());
        userDTO.setFull_name(user.getFull_name());
        userDTO.setRole_id(user.getRole() != null ? user.getRole().getId() : null);

        return ResponseEntity.ok(userDTO);
    }

    // CRUD: Update (cập nhật thông tin user, dành cho admin)
    @PutMapping("/update/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @RequestBody UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Lỗi: Người dùng với ID " + id + " không tồn tại");
        }

        // Kiểm tra email mới có bị trùng không (trừ email của chính user đang cập nhật)
        User existingUserWithEmail = userRepository.findByEmail(userDTO.getEmail());
        if (existingUserWithEmail != null && !existingUserWithEmail.getId().equals(id)) {
            return ResponseEntity.badRequest().body("Email already exists");
        }

        user.setEmail(userDTO.getEmail());
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword())); // Mã hóa password mới
        }
        user.setFull_name(userDTO.getFull_name());
        user.setSlug(userDTO.getFull_name().toLowerCase().replace(" ", "-"));

        // Cập nhật role nếu được chỉ định
        UUID roleId = userDTO.getRole_id();
        if (roleId != null) {
            Role role = roleRepository.findById(roleId)
                    .orElse(null);
            if (role == null) {
                return ResponseEntity.badRequest().body("Lỗi: Role với ID " + roleId + " không tồn tại");
            }
            user.setRole(role);
        }

        userRepository.save(user);
        return ResponseEntity.ok("User updated successfully");
    }

    // CRUD: Delete (xóa user, dành cho admin)
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Lỗi: Người dùng với ID " + id + " không tồn tại");
        }

        // Xóa refresh token liên quan
        refreshTokenService.deleteByUserId(id);
        // Xóa user
        userRepository.delete(user);
        return ResponseEntity.ok("User deleted successfully");
    }
}