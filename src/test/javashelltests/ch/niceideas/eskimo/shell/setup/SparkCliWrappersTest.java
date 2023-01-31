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
        return "flink-cli";
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
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaVerifiableConsumer() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaTrogdor() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaTopics() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaStreamsApplicationReset() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaRunClass() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaReplicaVerification() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaReassignPartitions() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaProducerPerfTes() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaPreferredReplicaElection() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaMirrorMaker() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaLogDirs() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaDumpLogs() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaDeleteRecords() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaDelegationTokens() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaConsumerGroup() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaConsoleProducer() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaConsoleConsumer() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaConfigs() throws Exception {
        fail ("To Be Implemented");
    }

    @Test
    public void testKafkaBrokerApiVersion() throws Exception {
        fail ("To Be Implemented");
    }


    @Test
    public void testKafkaAcl() throws Exception {
        fail ("To Be Implemented");
    }

}
