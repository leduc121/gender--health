package com.example.demo.api;


import java.util.List;
import java.util.ArrayList;
import com.example.demo.dto.ErrorResponse;
import com.example.demo.dto.ValidationError;
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
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

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

    private static final Pattern  PHONE_PATTERN = Pattern.compile("^(?:\\+84|0)\\d{9,10}$");
    private static final Pattern FULL_NAME_PATTERN = Pattern.compile("^[A-Za-zÀ-ỹ0-9]+( [A-Za-zÀ-ỹ0-9]+)*$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");



    //kiem tra xem trong full name co chua icon dac biet khong

    private boolean containsEmoji(String text) {
        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.codePointAt(i);
            if ((codePoint >= 0x1F600 && codePoint <= 0x1F64F) || // Emoticons
                    (codePoint >= 0x1F300 && codePoint <= 0x1F5FF) || // Misc Symbols and Pictographs
                    (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) || // Transport and Map Symbols
                    (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF)) { // Flags
                return true;
            }
        }
        return false;
    }



    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserDTO userDTO) {
        List<ValidationError> errors = new ArrayList<>();

        //kiem tra email
        if (userDTO.getEmail() == null || userDTO.getEmail().isEmpty()) {
            errors.add(new ValidationError("email", "Email không được để trống"));
        } else {
            if (userDTO.getEmail().contains(" ")) {
                errors.add(new ValidationError("email", "Email không được chứa khoảng trắng"));
            } else if (!EMAIL_PATTERN.matcher(userDTO.getEmail()).matches()) {
                errors.add(new ValidationError("email", "Email không hợp lệ"));
            } else if (userRepository.findByEmail(userDTO.getEmail()) != null) {
                errors.add(new ValidationError("email", "Email đã tồn tại"));
            }
        }

        // kiem tra full name
        if (userDTO.getFull_name() == null || userDTO.getFull_name().isEmpty()) {
            errors.add(new ValidationError("full_name", "Tên không được để trống"));
        } else {
            String trimmedName = userDTO.getFull_name().trim();
            if (!trimmedName.equals(userDTO.getFull_name())) {
                errors.add(new ValidationError("full_name", "Tên không được chứa khoảng trắng ở đầu hoặc cuối"));
            } else if (!FULL_NAME_PATTERN.matcher(userDTO.getFull_name()).matches()) {
                errors.add(new ValidationError("full_name", "Tên chỉ được chứa chữ cái, số và khoảng trắng giữa các từ"));
            } else if (containsEmoji(userDTO.getFull_name())) {
                errors.add(new ValidationError("full_name", "Tên không được chứa icon hoặc emoji"));
            }
        }

        //kiem tra sdt
        if (userDTO.getPhone() == null || userDTO.getPhone().isEmpty()) {
            errors.add(new ValidationError("phone", "Số điện thoại không được để trống"));
        } else if (!PHONE_PATTERN.matcher(userDTO.getPhone()).matches()) {
            errors.add(new ValidationError("phone", "Số điện thoại không hợp lệ. Phải bắt đầu bằng +84 hoặc 0, theo sau là 9 hoặc 10 chữ số"));
        }

        //kiem tra pass
        if (userDTO.getPassword() == null || userDTO.getPassword().isEmpty()) {
            errors.add(new ValidationError("password", "Mật khẩu không được để trống"));
        }

        //response lỗi
        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(errors));
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setFull_name(userDTO.getFull_name());

        // slug
        String baseSlug = userDTO.getFull_name().toLowerCase().replace(" ", "-");
        Random random = new Random();
        int randomNumber = random.nextInt(1000);
        String formattedNumber = String.format("%03d", randomNumber);
        user.setSlug(baseSlug + "-" + formattedNumber);

        user.setPhone(userDTO.getPhone());

        // Gán role mặc định là "customer" nếu không chỉ định role_id
        UUID roleId = userDTO.getRole_id();
        Role role;
        if (roleId != null) {
            role = roleRepository.findById(roleId)
                    .orElse(null);
            if (role == null) {
                errors.add(new ValidationError("role_id", "Role với ID " + roleId + " không tồn tại"));
                return ResponseEntity.badRequest().body(new ErrorResponse(errors));
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

        //Lưu user vào db
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