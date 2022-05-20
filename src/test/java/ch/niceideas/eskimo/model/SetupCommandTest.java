package ch.niceideas.eskimo.model;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.services.AbstractServicesDefinitionTest;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.services.SetupService;
import ch.niceideas.eskimo.services.SystemServiceTest;
import ch.niceideas.eskimo.utils.OSDetector;
import com.google.javascript.jscomp.jarjar.com.google.common.util.concurrent.AbstractService;
import org.junit.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assume.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SetupCommandTest extends AbstractServicesDefinitionTest {

    private String setupConfig = null;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        setupConfig =  StreamUtils.getAsString(ResourceUtils.getResourceAsStream("SetupServiceTest/setupConfig.json"));
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
                "    \"flink\",\n" +
                "    \"kafka\",\n" +
                "    \"kafka-manager\",\n" +
                "    \"kibana\",\n" +
                "    \"logstash\",\n" +
                "    \"zeppelin\"\n" +
                "  ],\n" +
                "  \"buildKube\": [\"kube\"],\n" +
                "  \"downloadKube\": [],\n" +
                "  \"none\": false,\n" +
                "  \"downloadPackages\": [],\n" +
                "  \"packageUpdates\": [],\n" +
                "  \"packageDownloadUrl\": \"https://niceideas.ch/eskimo/\"\n" +
                "}", setupCommand.toJSON().toString(2));
    }

    private SetupCommand createCommand() throws IOException, FileException, SetupException {
        assumeTrue(OSDetector.isUnix());

        File packageDevPathTest = File.createTempFile("test_setup_service_package_dev", "folder");
        packageDevPathTest.delete();
        packageDevPathTest.mkdirs();

        setupService.setPackagesDevPathForTests(packageDevPathTest.getAbsolutePath());
        setupService.setPackageDistributionPath(packageDevPathTest.getAbsolutePath());

        FileUtils.writeFile(new File (packageDevPathTest.getAbsolutePath() + "/build.sh"),
                "#!/bin/bash\n" +
                        "echo $@\n");

        return SetupCommand.create(new JsonWrapper(setupConfig), setupService, def);
    }

    @Test
    public void testGetAllOperationsInOrder() throws Exception {

        SetupCommand setupCommand = createCommand();

        List<SetupCommand.SetupOperationId> opInOrder = setupCommand.getAllOperationsInOrder(null);

        assertEquals ("" +
                        "Build_base-eskimo," +
                        "Build_ntp," +
                        "Build_prometheus," +
                        "Build_zookeeper," +
                        "Build_gluster," +
                        "Build_kube-master," +
                        "Build_kubernetes-dashboard," +
                        "Build_elasticsearch," +
                        "Build_cerebro," +
                        "Build_grafana," +
                        "Build_spark," +
                        "Build_flink," +
                        "Build_kafka," +
                        "Build_kafka-manager," +
                        "Build_kibana," +
                        "Build_logstash," +
                        "Build_zeppelin," +
                        "Build_kube",
                opInOrder.stream().map(SetupCommand.SetupOperationId::toString).collect(Collectors.joining(",")));
    }

}
