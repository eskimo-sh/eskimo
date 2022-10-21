package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ApplicationStatusServiceTest extends AbstractSystemTest {

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
        assertEquals("DEV-SNAPSHOT", appStatus.getValueForPathAsString("buildVersion"));
        assertEquals("LATEST DEV", appStatus.getValueForPathAsString("buildTimestamp"));
        assertEquals("(Setup incomplete)", appStatus.getValueForPathAsString("sshUsername"));
        assertEquals("true", appStatus.getValueForPathAsString("enableKubernetes"));

        assertEquals("true", appStatus.getValueForPathAsString("enableKubernetes"));

        assertEquals("user1", appStatus.getValueForPathAsString("username"));
        assertEquals("admin,user", appStatus.getValueForPathAsString("roles"));
    }

}
