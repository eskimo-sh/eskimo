package ch.niceideas.eskimo.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.stream.Collectors;

public class SecurityHelper {

    // to be used statically only
    private SecurityHelper() {
    }

    public static List<Role> getuserRoles() throws AuthorizationException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new AuthorizationException("No logged in user");
        }

        return auth.getAuthorities().stream()
                .map(a -> Role.valueOf(a.getAuthority()))
                .collect(Collectors.toList());
    }
}
