package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.security.MutableUser;
import ch.niceideas.eskimo.test.services.UserDetailsManagerTestImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-conf", "test-security"})
public class UserEditControllerTest {

    @Autowired
    private UserEditController userEditController;

    @Autowired
    private UserDetailsManagerTestImpl userDetailsManagerTest;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    public void testSetup() {
        userDetailsManagerTest.createUser(new User("testUser", passwordEncoder.encode("testPassword"), Collections.emptyList()));
    }

    @Test
    public void testEditUser() {
        JsonWrapper userData = new JsonWrapper("{}}");
        userData.setValueForPath("edit-user-user-id", "testUser");
        userData.setValueForPath("edit-user-current-password", "testPassword");
        userData.setValueForPath("edit-user-new-password", "testPasswordNew");
        userData.setValueForPath("edit-user-repeat-password", "testPasswordNew");

        assertEquals ("{\n" +
                "  \"message\": \"password successfully changed.\",\n" +
                "  \"status\": \"OK\"\n" +
                "}", userEditController.editUser(userData.getFormattedValue()));
    }

    @Test
    public void testEditUser_WrongPassword() {
        JsonWrapper userData = new JsonWrapper("{}}");
        userData.setValueForPath("edit-user-user-id", "testUser");
        userData.setValueForPath("edit-user-current-password", "wrong");
        userData.setValueForPath("edit-user-new-password", "testPasswordNew");
        userData.setValueForPath("edit-user-repeat-password", "testPasswordNew");

        assertEquals ("{\n" +
                "  \"error\": \"Authentication failed. Perhaps wrong current password ?\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", userEditController.editUser(userData.getFormattedValue()));
    }

    @Test
    public void testEditUser_NonMatchingPassword() {
        JsonWrapper userData = new JsonWrapper("{}}");
        userData.setValueForPath("edit-user-user-id", "testUser");
        userData.setValueForPath("edit-user-current-password", "testPassword");
        userData.setValueForPath("edit-user-new-password", "testPasswordNew");
        userData.setValueForPath("edit-user-repeat-password", "nonMatching");

        assertEquals ("{\n" +
                "  \"error\": \"Both password don't match\",\n" +
                "  \"status\": \"KO\"\n" +
                "}", userEditController.editUser(userData.getFormattedValue()));
    }
}
