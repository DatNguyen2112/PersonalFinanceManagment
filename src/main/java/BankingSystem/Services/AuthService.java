package BankingSystem.Services;

import BankingSystem.DTO.BankingDTO;
import BankingSystem.Entity.User;
import BankingSystem.Exception.*;
import BankingSystem.JWTConfig.JWTService;
import BankingSystem.JWTConfig.UserDetailsImpl;
import BankingSystem.Repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.DuplicateResourceException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.security.auth.login.AccountLockedException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final AuthenticationManager authenticationManager;

    public BankingDTO.AuthResponse register(BankingDTO.RegisterRequest request) {
        try {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new EmailAlreadyExistsException((request.getEmail()));
            }
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new UsernameAlreadyExistsException(request.getUsername());
            }

            User user = User.builder()
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .plainTextPassword(request.getPassword())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .phoneNumber(request.getPhoneNumber())
                    .role(User.UserRole.CUSTOMER)
                    .build();

            user = userRepository.save(user);
            log.info("New user registered: {}", user.getUsername());

            UserDetailsImpl.BankingUserDetails userDetails = new UserDetailsImpl.BankingUserDetails(user);
            String accessToken = jwtService.generateToken(userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            return buildAuthResponse(user, accessToken, refreshToken);

        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("auth_register_failed username={} error={}",
                    request.getUsername(), ex.getMessage(), ex);
            throw new BankingException("REGISTER_ERROR", "Đăng ký thất bại", ex);
        }
    }

    public BankingDTO.AuthResponse login(BankingDTO.LoginRequest request) {
        try {
            // Dùng Spring Security AuthenticationManager để xác thực
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword()));

            var userDetails = (UserDetailsImpl.BankingUserDetails)
                    authentication.getPrincipal();

            var user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(InvalidCredentialsException::new);

            // Update last login
            user.setLastLogin(LocalDateTime.now());
            user.setFailedLoginAttempts(0);
            userRepository.save(user);

            String accessToken = jwtService.generateToken(userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            log.info("User logged in: {}", user.getUsername());
            return buildAuthResponse(user, accessToken, refreshToken);

        } catch (BadCredentialsException ex) {
            log.warn("auth_login_bad_credentials username={}", request.getUsername());
            throw new InvalidCredentialsException();
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("auth_login_failed username={} error={}",
                    request.getUsername(), ex.getMessage(), ex);
            throw new BankingException("LOGIN_ERROR", "Đăng nhập thất bại", ex);
        }
    }

    private BankingDTO.AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return BankingDTO.AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(mapToUserDTO(user))
                .build();
    }

    public BankingDTO.UserDTO mapToUserDTO(User user) {
        return BankingDTO.UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();
    }
}
