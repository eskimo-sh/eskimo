package ch.niceideas.eskimo.test.infrastructure;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

public class SecurityContextHelper {

    public static void loginAdmin() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ADMIN"));
        TestingAuthenticationToken auth = new TestingAuthenticationToken("test", "test", authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
