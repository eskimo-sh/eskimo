package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.eskimo.EskimoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-conf"})
public class ApplicationStatusServiceTest {

    @Autowired
    private ApplicationStatusService applicationStatusService;

    @Test
    public void testUpdateAndGetStatus() {

        UsernamePasswordAuthenticationToken principal =
                new UsernamePasswordAuthenticationToken ("user1", "test", new ArrayList<SimpleGrantedAuthority>(){{
                    add(new SimpleGrantedAuthority("admin"));
                    add(new SimpleGrantedAuthority("user"));
                }});

        SecurityContextHolder.getContext().setAuthentication(principal);

        applicationStatusService.updateStatus();

        JsonWrapper appStatus = applicationStatusService.getStatus();

        assertNotNull (appStatus);

        assertEquals("30s", appStatus.getValueForPathAsString("monitoringDashboardRefreshPeriod"));
        assertEquals("@project.version@", appStatus.getValueForPathAsString("buildVersion"));
        assertEquals("@maven.build.timestamp@", appStatus.getValueForPathAsString("buildTimestamp"));
        assertEquals("(Setup incomplete)", appStatus.getValueForPathAsString("sshUsername"));
        assertEquals("true", appStatus.getValueForPathAsString("enableKubernetes"));

        assertEquals("true", appStatus.getValueForPathAsString("enableKubernetes"));

        assertEquals("user1", appStatus.getValueForPathAsString("username"));
        assertEquals("admin,user", appStatus.getValueForPathAsString("roles"));
    }

}
