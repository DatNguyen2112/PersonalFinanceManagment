package BankingSystem.JWTConfig;

import BankingSystem.Entity.User;
import BankingSystem.Repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collection;
import java.util.List;

@Service
@Primary
public class UserDetailsImpl implements UserDetailsService {
    private final UserRepository userRepository;

    public UserDetailsImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new BankingUserDetails(user);
    }

    public static class BankingUserDetails implements UserDetails {
        private final User user;

        public BankingUserDetails(User user) {
            this.user = user;
        }

        public User getUser() {
            return user;
        }

        public Long getUserId() {
            return user.getId();
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        }

        @Override public String getPassword() { return user.getPassword(); }
        @Override public String getUsername() { return user.getUsername(); }
        @Override public boolean isAccountNonExpired() { return true; }
        @Override public boolean isAccountNonLocked() { return user.getAccountNonLocked(); }
        @Override public boolean isCredentialsNonExpired() { return true; }
        @Override public boolean isEnabled() { return user.getEnabled(); }
    }
}
