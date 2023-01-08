package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.EskimoApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.fail;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-conf"})
public class UserEditControllerTest {

    @Autowired
    private UserEditController userEditController;


    @BeforeEach
    public void testSetup() {
        // TODO
    }

    @Test
    public void testEditUser() {
        fail ("To Be Implemented");
    }

}
