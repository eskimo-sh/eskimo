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
                "    \"elasticsearch\",\n" +
                "    \"ntp\",\n" +
                "    \"prometheus\",\n" +
                "    \"zookeeper\",\n" +
                "    \"gluster\",\n" +
                "    \"kafka\",\n" +
                "    \"mesos-master\",\n" +
                "    \"logstash\",\n" +
                "    \"flink\",\n" +
                "    \"marathon\",\n" +
                "    \"spark\",\n" +
                "    \"cerebro\",\n" +
                "    \"grafana\",\n" +
                "    \"kibana\",\n" +
                "    \"kafka-manager\",\n" +
                "    \"zeppelin\"\n" +
                "  ],\n" +
                "  \"buildMesos\": [\n" +
                "    \"mesos-debian\",\n" +
                "    \"mesos-redhat\"\n" +
                "  ],\n" +
                "  \"downloadMesos\": [],\n" +
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

        assertEquals ("Build_base-eskimo," +
                "Build_elasticsearch," +
                "Build_ntp," +
                "Build_prometheus," +
                "Build_zookeeper," +
                "Build_gluster," +
                "Build_kafka," +
                "Build_mesos-master," +
                "Build_logstash," +
                "Build_flink," +
                "Build_marathon," +
                "Build_spark," +
                "Build_cerebro," +
                "Build_grafana," +
                "Build_kibana," +
                "Build_kafka-manager," +
                "Build_zeppelin," +
                "Build_mesos-debian," +
                "Build_mesos-redhat",
                opInOrder.stream().map(SetupCommand.SetupOperationId::toString).collect(Collectors.joining(",")));
    }

}
