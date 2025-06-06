package com.example.demo.api;

import com.example.demo.dto.UserDTO;
import com.example.demo.entity.RefreshToken;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.RefreshTokenService;
import com.example.demo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserAPI {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserDTO userDTO) {
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

        // Gán role mặc định là "customer" nếu không chỉ định role_id
        UUID roleId = userDTO.getRole_id();
        Role role;
        if (roleId != null) {
            role = roleRepository.findById(roleId)
                    .orElse(null);
            if (role == null) {
                return ResponseEntity.badRequest().body("Lỗi: Role với ID " + roleId + " không tồn tại");
            }
        } else {
            // Tìm role "customer" mặc định
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
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserDTO loginRequest) {
        String email = loginRequest.getEmail();
        String password = loginRequest.getPassword();

        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body("Lỗi: Email không được để trống");
        }
        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest().body("Lỗi: Mật khẩu không được để trống");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Lỗi: Email hoặc mật khẩu không đúng");
        }

        User user = userRepository.findByEmail(email);
        String accessToken = jwtUtil.generateAccessToken(email);
        String refreshToken = jwtUtil.generateRefreshToken(email);

        // Lưu refresh token vào cơ sở dữ liệu
        refreshTokenService.deleteByUserId(user.getId());
        RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(user.getId());
        refreshToken = refreshTokenEntity.getToken();

        Map<String, String> response = new HashMap<>();
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("email", email);
        response.put("message", "Đăng nhập thành công. Chào mừng " + user.getFull_name());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || refreshToken.isEmpty()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Lỗi: Refresh token không được để trống");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        return refreshTokenService.findByToken(refreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUserId)
                .map(userId -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
                    String newAccessToken = jwtUtil.generateAccessToken(user.getEmail());

                    Map<String, String> response = new HashMap<>();
                    response.put("accessToken", newAccessToken);
                    response.put("refreshToken", refreshToken);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "Lỗi: Refresh token không hợp lệ");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
                });
    }

    @GetMapping("/get-by-email/{email}")
    public ResponseEntity<?> getUserByEmail(@PathVariable String email) {
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body("Lỗi: Email không được để trống");
        }

        User user = userRepository.findByEmail(email);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Lỗi: Người dùng với email " + email + " không tồn tại");
        }

        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setEmail(user.getEmail());
        // Không trả về mật khẩu để bảo mật
        userDTO.setFull_name(user.getFull_name());
        userDTO.setRole_id(user.getRole() != null ? user.getRole().getId() : null);

        return ResponseEntity.ok(userDTO);
    }
}