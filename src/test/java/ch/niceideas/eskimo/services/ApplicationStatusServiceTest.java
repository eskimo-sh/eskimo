package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ApplicationStatusServiceTest extends AbstractSystemTest {

    @Test
    public void testUpdateAndGetStatus() {

        applicationStatusService.updateStatus();

        JsonWrapper appStatus = applicationStatusService.getStatus();

        assertNotNull (appStatus);

        assertEquals("30s", appStatus.getValueForPathAsString("monitoringDashboardRefreshPeriod"));
        assertEquals("DEV-SNAPSHOT", appStatus.getValueForPathAsString("buildVersion"));
        assertEquals("LATEST DEV", appStatus.getValueForPathAsString("buildTimestamp"));
        assertEquals("(Setup incomplete)", appStatus.getValueForPathAsString("sshUsername"));
        assertEquals("true", appStatus.getValueForPathAsString("enableMarathon"));

        assertEquals("[{\"title\":\"Access all monitoring dashboards in Grafana\"," +
                        "\"service\":\"grafana\"}," +
                        "{\"title\":\"Monitor Mesos Processes\"," +
                        "\"service\":\"mesos-master\"}," +
                        "{\"title\":\"Manage Marathon Services\"," +
                        "\"service\":\"marathon\"}," +
                        "{\"title\":\"Manage your kafka topics\"," +
                        "\"service\":\"kafka-manager\"}," +
                        "{\"title\":\"Monitor your Spark jobs\"," +
                        "\"service\":\"spark-history-server\"}," +
                        "{\"title\":\"Manage and Monitor your Flink jobs\"," +
                        "\"service\":\"flink-app-master\"}," +
                        "{\"title\":\"Manage your data in Elasticsearch\"," +
                        "\"service\":\"cerebro\"}," +
                        "{\"title\":\"Visualize your data in Elasticsearch\"," +
                        "\"service\":\"kibana\"}," +
                        "{\"title\":\"Use Zeppelin for your Data Science projects\"," +
                        "\"service\":\"zeppelin\"}]",
                appStatus.getValueForPathAsString("links"));

        assertEquals("true", appStatus.getValueForPathAsString("isSnapshot"));
    }

}
