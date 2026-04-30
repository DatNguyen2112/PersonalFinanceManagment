package BankingSystem.Controller;

import BankingSystem.DTO.BankingDTO;
import BankingSystem.JWTConfig.JWTService;
import BankingSystem.JWTConfig.UserDetailsImpl;
import BankingSystem.Services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

// ==========================================
// Auth Controller
// ==========================================
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class UserController {
    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<BankingDTO.AuthResponse> register(@Valid @RequestBody BankingDTO.RegisterRequest request) {
        var result = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and get JWT")
    public ResponseEntity<BankingDTO.AuthResponse> login(@Valid @RequestBody BankingDTO.LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", security = @SecurityRequirement(name = "Bearer"))
    public ResponseEntity<?> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {

        // Không có token hoặc token không hợp lệ
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Missing or invalid Authorization token");
        }

        // Sai kiểu principal (không phải BankingUserDetails)
        if (!(userDetails instanceof UserDetailsImpl.BankingUserDetails principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid authentication principal");
        }

        return ResponseEntity.ok(authService.mapToUserDTO(principal.getUser()));
    }
}
