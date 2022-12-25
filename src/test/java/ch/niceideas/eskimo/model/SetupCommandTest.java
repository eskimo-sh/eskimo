package ch.niceideas.eskimo.model;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.EskimoApplication;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.test.infrastructure.SecurityContextHelper;
import ch.niceideas.eskimo.test.services.SetupServiceTestImpl;
import ch.niceideas.eskimo.utils.OSDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ContextConfiguration(classes = EskimoApplication.class)
@SpringBootTest(classes = EskimoApplication.class)
@TestPropertySource("classpath:application-test.properties")
@ActiveProfiles({"no-web-stack", "test-setup"})
public class SetupCommandTest {

    @Autowired
    private SetupServiceTestImpl setupServiceTest;

    @Autowired
    private ServicesDefinition servicesDefinition;

    private String setupConfig = null;

    @BeforeEach
    public void setUp() throws Exception {
        setupConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/setupConfig.json"), StandardCharsets.UTF_8);

        SecurityContextHelper.loginAdmin();
    }

    @Test
    public void toJSON () throws Exception {

        SetupCommand setupCommand = createCommand();

        assertEquals ("{\n" +
                "  \"buildPackage\": [\n" +
                "    \"base-eskimo\",\n" +
                "    \"ntp\",\n" +
                "    \"prometheus\",\n" +
                "    \"zookeeper\",\n" +
                "    \"gluster\",\n" +
                "    \"kube-master\",\n" +
                "    \"kubernetes-dashboard\",\n" +
                "    \"elasticsearch\",\n" +
                "    \"cerebro\",\n" +
                "    \"grafana\",\n" +
                "    \"spark\",\n" +
                "    \"kafka\",\n" +
                "    \"kafka-manager\",\n" +
                "    \"kibana\",\n" +
                "    \"logstash\",\n" +
                "    \"flink\",\n" +
                "    \"zeppelin\"\n" +
                "  ],\n" +
                "  \"buildKube\": [\"kube\"],\n" +
                "  \"downloadKube\": [],\n" +
                "  \"none\": false,\n" +
                "  \"downloadPackages\": [],\n" +
                "  \"packageUpdates\": [],\n" +
                "  \"packageDownloadUrl\": \"dummy\"\n" +
                "}", setupCommand.toJSON().toString(2));
    }

    private SetupCommand createCommand() throws IOException, FileException, SetupException {
        assumeTrue(OSDetector.isUnix());

        File packageDevPathTest = File.createTempFile("test_setup_service_package_dev", "folder");
        assertTrue (packageDevPathTest.delete());
        assertTrue (packageDevPathTest.mkdirs());

        setupServiceTest.setPackageDistributionPath(packageDevPathTest.getAbsolutePath());

        FileUtils.writeFile(new File (packageDevPathTest.getAbsolutePath() + "/build.sh"),
                "#!/bin/bash\n" +
                        "echo $@\n");

        return SetupCommand.create(new JsonWrapper(setupConfig), setupServiceTest, servicesDefinition);
    }

    @Test
    public void testGetAllOperationsInOrder() throws Exception {

        SetupCommand setupCommand = createCommand();

        List<SetupCommand.SetupOperationId> opInOrder = setupCommand.getAllOperationsInOrder(null);

        assertEquals ("Build_base-eskimo,Build_ntp,Build_prometheus,Build_zookeeper,Build_gluster,Build_kube-master,Build_kubernetes-dashboard,Build_elasticsearch,Build_cerebro,Build_grafana,Build_spark,Build_kafka,Build_kafka-manager,Build_kibana,Build_logstash,Build_flink,Build_zeppelin,Build_kube",
                opInOrder.stream().map(SetupCommand.SetupOperationId::toString).collect(Collectors.joining(",")));
    }

}
