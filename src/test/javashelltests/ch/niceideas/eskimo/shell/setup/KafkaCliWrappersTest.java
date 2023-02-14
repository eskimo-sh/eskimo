/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */


package ch.niceideas.eskimo.shell.setup;

import ch.niceideas.common.utils.*;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class KafkaCliWrappersTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(KafkaCliWrappersTest.class);

    protected static String jailPath = null;

    private static boolean initialized = false;

    @BeforeEach
    public void setUp() throws Exception {
        if (!initialized) {
            jailPath = setupJail(getServiceName());

            // I need some real commands
            assertTrue (new File (jailPath + "/bash").delete());
            assertTrue (new File (jailPath + "/sed").delete());
            initialized = true;
        }
        File dockerLogs = new File (getJailPath() + "/.log_docker");
        if (dockerLogs.exists()) {
            assertTrue (dockerLogs.delete());
        }
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
        if (initialized && StringUtils.isNotBlank(jailPath)) {
            FileUtils.delete(new File(jailPath));
        }
    }

    @Override
    protected String getJailPath() {
        return jailPath;
    }

    @Override
    protected String getServiceName() {
        return "kafka-cli";
    }

    @Override
    protected String getImageName() {
        return null;
    }

    @Override
    protected String getTemplateName() {
        return null;
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh and common.sh are automatic
        copyFile(jailPath, "../base-eskimo/eskimo-utils.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-acls.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-broker-api-versions.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-configs.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-console-consumer.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-console-producer.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-consumer-groups.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-consumer-perf-test.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-delegation-tokens.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-delete-records.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-dump-log.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-log-dirs.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-mirror-maker.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-preferred-replica-election.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-producer-perf-test.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-reassign-partitions.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-replica-verification.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-run-class.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-streams-application-reset.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-topics.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-trogdor.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-verifiable-consumer.sh");
        copyFile(jailPath, "kafka_wrappers/kafka-verifiable-producer.sh");
    }

    @Test
    public void testKafkaVerifiableProducer() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-verifiable-producer.sh", "kafka-verifiable-producer.sh --producer.config /tmp/producer.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-verifiable-producer.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/kafka-verifiable-producer.sh --producer.config /tmp/producer.json\n", dockerLogs);
    }

    @Test
    public void testKafkaVerifiableConsumer() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-verifiable-consumer.sh", "kafka-verifiable-consumer.sh --consumer.config /tmp/consumer.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-verifiable-consumer.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/kafka-verifiable-consumer.sh --consumer.config /tmp/consumer.json\n", dockerLogs);
    }

    @Test
    public void testKafkaTrogdor() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-trogdor.sh", "kafka-trogdor.sh");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-trogdor.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/kafka-trogdor.sh\n", dockerLogs);
    }

    @Test
    public void testKafkaTopics() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-topics.sh", "kafka-topics.sh --command-config /tmp/test.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-topics.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/kafka-topics.sh --command-config /tmp/test.json\n", dockerLogs);
    }

    @Test
    public void testKafkaStreamsApplicationReset() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-streams-application-reset.sh", "kafka-streams-application-reset.sh --config-file /tmp/config.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-streams-application-reset.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/sbin/kafka-streams-application-reset.sh --config-file /tmp/config.json\n", dockerLogs);
    }

    @Test
    public void testKafkaRunClass() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-run-class.sh", "kafka-run-class.sh -jar /home/eskimo/test.jar -classpath /tmp/test.jar:/var/lib/spark/spark.jar");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-run-class.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /home/eskimo:/home/eskimo:slave " +
                "-v /var/lib/spark:/var/lib/spark:slave " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/kafka-run-class.sh -jar /home/eskimo/test.jar -classpath /tmp/test.jar:/var/lib/spark/spark.jar\n", dockerLogs);
    }

    @Test
    public void testKafkaReplicaVerification() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-replica-verification.sh", "kafka-replica-verification.sh");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-replica-verification.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/sbin/kafka-replica-verification.sh\n", dockerLogs);
    }

    @Test
    public void testKafkaReassignPartitions() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-reassign-partitions.sh", "kafka-reassign-partitions.sh --topics-to-move-json-file /tmp/topics.json --reassignment-json-file /var/lib/eskimo/default.assign.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-reassign-partitions.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/sbin/kafka-reassign-partitions.sh --topics-to-move-json-file /tmp/topics.json --reassignment-json-file /var/lib/eskimo/default.assign.json\n", dockerLogs);
    }

    @Test
    public void testKafkaProducerPerfTes() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-producer-perf-test.sh", "kafka-producer-perf-test.sh --producer.config /tmp/producer.json --payload-file /var/lib/test/payload.bin ");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-producer-perf-test.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /var/lib/test:/var/lib/test:slave " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/kafka-producer-perf-test.sh --producer.config /tmp/producer.json --payload-file /var/lib/test/payload.bin\n", dockerLogs);
    }

    @Test
    public void testKafkaPreferredReplicaElection() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-preferred-replica-election.sh", "kafka-preferred-replica-election.sh --path-to-json-file /var/lib/eskimo/eskimo.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-preferred-replica-election.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/sbin/kafka-preferred-replica-election.sh --path-to-json-file /var/lib/eskimo/eskimo.json\n", dockerLogs);
    }

    @Test
    public void testKafkaMirrorMaker() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-mirror-maker.sh", "kafka-mirror-maker.sh --consumer.config /tmp/consum.json --producer.config /var/lib/eskimo/produc,json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-mirror-maker.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/sbin/kafka-mirror-maker.sh --consumer.config /tmp/consum.json --producer.config /var/lib/eskimo/produc,json\n", dockerLogs);
    }

    @Test
    public void testKafkaLogDirs() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-dump-log.sh", "kafka-dump-log.sh");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-dump-log.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do kafka-dump-log.sh\n", dockerLogs);
    }

    @Test
    public void testKafkaDumpLogs() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-dump-log.sh", "kafka-dump-log.sh --files /tmp/test");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-dump-log.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do kafka-dump-log.sh --files /tmp/test\n", dockerLogs);
    }

    @Test
    public void testKafkaDeleteRecords() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-delete-records.sh", "kafka-delete-records.sh --command-config /tmp/config.json --offset-json-file /var/lib/kafka/offset.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-delete-records.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:slave " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do kafka-delete-records.sh --command-config /tmp/config.json --offset-json-file /var/lib/kafka/offset.json\n", dockerLogs);
    }

    @Test
    public void testKafkaDelegationTokens() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-delegation-tokens.sh", "kafka-delegation-tokens.sh --command-config /tmp/config.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-delegation-tokens.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/kafka-delegation-tokens.sh --command-config /tmp/config.json\n", dockerLogs);
    }

    @Test
    public void testKafkaConsumerPerfTest() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-consumer-perf-test.sh", "kafka-consumer-perf-test.sh --consumer.config /tmp/config.json ");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-consumer-perf-test.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/kafka-consumer-perf-test.sh --consumer.config /tmp/config.json\n", dockerLogs);
    }

    @Test
    public void testKafkaConsumerGroup() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-consumer-groups.sh", "kafka-consumer-groups.sh --command-config /tmp/config.json --from-file /var/lib/eskimo/kafka/test.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-consumer-groups.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /var/lib/eskimo/kafka:/var/lib/eskimo/kafka:slave " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/kafka-consumer-groups.sh --command-config /tmp/config.json --from-file /var/lib/eskimo/kafka/test.json\n", dockerLogs);
    }

    @Test
    public void testKafkaConsoleProducer() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-console-producer.sh", "kafka-console-producer.sh --producer.config /var/lib/eskimo/config,json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-console-producer.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/kafka-console-producer.sh --producer.config /var/lib/eskimo/config,json\n", dockerLogs);
    }

    @Test
    public void testKafkaConsoleConsumer() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-console-consumer.sh", "kafka-console-consumer.sh --consumer.config /var/lib/eskimo/config,json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-console-consumer.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/kafka-console-consumer.sh --consumer.config /var/lib/eskimo/config,json\n", dockerLogs);
    }

    @Test
    public void testKafkaConfigs() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-configs.sh", "kafka-configs.sh --command-config /tmp/command.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-configs.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/sbin/kafka-configs.sh --command-config /tmp/command.json\n", dockerLogs);
    }

    @Test
    public void testKafkaBrokerApiVersion() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-broker-api-versions.sh", "kafka-broker-api-versions.sh --command-config /tmp/command.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-broker-api-versions.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/kafka-broker-api-versions.sh --command-config /tmp/command.json\n", dockerLogs);
    }

    @Test
    public void testKafkaAcl() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-kafka-acls.sh", "kafka-acls.sh --command-config /tmp/command.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-kafka-acls.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user kafka " +
                "-v /var/log/kafka:/var/log/kafka:shared " +
                "-v /var/lib/kafka:/var/lib/kafka:shared " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/kafka:0 " +
                    "/usr/local/bin/kube_do /usr/local/sbin/kafka-acls.sh --command-config /tmp/command.json\n", dockerLogs);
    }

}
