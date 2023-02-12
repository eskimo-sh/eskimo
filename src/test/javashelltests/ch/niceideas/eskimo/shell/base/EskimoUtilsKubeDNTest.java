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


package ch.niceideas.eskimo.shell.base;

import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.shell.setup.AbstractSetupShellTest;
import ch.niceideas.eskimo.utils.OSDetector;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EskimoUtilsKubeDNTest {

    private static final Logger logger = Logger.getLogger(EskimoUtilsKubeDNTest.class);

    protected String jailPath = null;

    /** Run Test on Linux only */
    @BeforeEach
    public void beforeMethod() {
        Assumptions.assumeTrue(OSDetector.isPosix());
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (StringUtils.isNotBlank(jailPath)) {
            FileUtils.delete(new File(jailPath));
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        jailPath = AbstractSetupShellTest.createJail();

        FileUtils.copy(
                new File("./services_setup/base-eskimo/eskimo-utils.sh"),
                new File (jailPath + "/eskimo-utils.sh"));

        ProcessHelper.exec(new String[]{"bash", "-c", "chmod 777 " + jailPath + "/eskimo-utils.sh"}, true);

        // I need some real commands
        assertTrue (new File (jailPath + "/bash").delete());
        assertTrue (new File (jailPath + "/sed").delete());

        // kubectl mck up command
        String resourceString = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("EskimoUtilsKubeDNTest/kubectl"), StandardCharsets.UTF_8);
        File targetPath = AbstractSetupShellTest.copyResource("kubectl", jailPath, resourceString);
        ProcessHelper.exec("chmod 755 " + targetPath, true);
    }

    private void createTestScript(String scriptName) throws FileException {

        String script = "#!/bin/bash\n" + "\n" +
                "SCRIPT_DIR=\"$( cd \"$( dirname \"${BASH_SOURCE[0]}\" )\" && pwd )\"\n" +
                "\n" +
                "# Change current folder to script dir (important !)\n" +
                "cd $SCRIPT_DIR\n" +
                "\n" +
                "# Avoid sleeps everywhere\n" +
                "export NO_SLEEP=true\n" +
                "\n" +
                "# Set test mode\n" +
                "export TEST_MODE=true\n" +
                "\n" +
                "# Using local commands\n" +
                "export PATH=$SCRIPT_DIR:$PATH\n" +
                "\n" +
                "# Source eskimo-utils.sh\n" +
                ". $SCRIPT_DIR/eskimo-utils.sh\n"+
                "\n" +
                "get_kube_services_IPs";

        FileUtils.writeFile(new File (jailPath + "/" + scriptName), script);
    }

    @Test
    public void testHostFileGeneration() throws Exception {

        createTestScript("calling_script.sh");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);
        logger.debug (result);

        assertEquals ("cerebro.default.svc.cluster.eskimo 172.30.0.6\n" +
                "cerebro.default.svc.cluster.local 172.30.0.6\n" +
                "cerebro.default.svc.kubernetes 172.30.0.6\n" +
                "elasticsearch-0.elasticsearch.default.svc.cluster.eskimo 172.30.0.5\n" +
                "elasticsearch-0.elasticsearch.default.svc.cluster.local 172.30.0.5\n" +
                "elasticsearch-0.elasticsearch.default.svc.kubernetes 172.30.0.5\n" +
                "elasticsearch-2.elasticsearch.default.svc.cluster.eskimo 172.30.2.2\n" +
                "elasticsearch-2.elasticsearch.default.svc.cluster.local 172.30.2.2\n" +
                "elasticsearch-2.elasticsearch.default.svc.kubernetes 172.30.2.2\n" +
                "elasticsearch-1.elasticsearch.default.svc.cluster.eskimo 172.30.3.2\n" +
                "elasticsearch-1.elasticsearch.default.svc.cluster.local 172.30.3.2\n" +
                "elasticsearch-1.elasticsearch.default.svc.kubernetes 172.30.3.2\n" +
                "elasticsearch-3.elasticsearch.default.svc.cluster.eskimo 172.30.4.2\n" +
                "elasticsearch-3.elasticsearch.default.svc.cluster.local 172.30.4.2\n" +
                "elasticsearch-3.elasticsearch.default.svc.kubernetes 172.30.4.2\n" +
                "elasticsearch.default.svc.cluster.eskimo 172.30.4.2\n" +
                "elasticsearch.default.svc.cluster.local 172.30.4.2\n" +
                "elasticsearch.default.svc.kubernetes 172.30.4.2\n" +
                "flink-runtime.default.svc.cluster.eskimo 172.30.3.6\n" +
                "flink-runtime.default.svc.cluster.local 172.30.3.6\n" +
                "flink-runtime.default.svc.kubernetes 172.30.3.6\n" +
                "flink-runtime-rest.default.svc.cluster.eskimo 172.30.3.6\n" +
                "flink-runtime-rest.default.svc.cluster.local 172.30.3.6\n" +
                "flink-runtime-rest.default.svc.kubernetes 172.30.3.6\n" +
                "grafana.default.svc.cluster.eskimo 172.30.3.3\n" +
                "grafana.default.svc.cluster.local 172.30.3.3\n" +
                "grafana.default.svc.kubernetes 172.30.3.3\n" +
                "kafka-0.kafka.default.svc.cluster.eskimo 172.30.0.8\n" +
                "kafka-0.kafka.default.svc.cluster.local 172.30.0.8\n" +
                "kafka-0.kafka.default.svc.kubernetes 172.30.0.8\n" +
                "kafka-2.kafka.default.svc.cluster.eskimo 172.30.2.4\n" +
                "kafka-2.kafka.default.svc.cluster.local 172.30.2.4\n" +
                "kafka-2.kafka.default.svc.kubernetes 172.30.2.4\n" +
                "kafka-1.kafka.default.svc.cluster.eskimo 172.30.3.5\n" +
                "kafka-1.kafka.default.svc.cluster.local 172.30.3.5\n" +
                "kafka-1.kafka.default.svc.kubernetes 172.30.3.5\n" +
                "kafka-3.kafka.default.svc.cluster.eskimo 172.30.4.4\n" +
                "kafka-3.kafka.default.svc.cluster.local 172.30.4.4\n" +
                "kafka-3.kafka.default.svc.kubernetes 172.30.4.4\n" +
                "kafka.default.svc.cluster.eskimo 172.30.4.4\n" +
                "kafka.default.svc.cluster.local 172.30.4.4\n" +
                "kafka.default.svc.kubernetes 172.30.4.4\n" +
                "kafka-manager.default.svc.cluster.eskimo 172.30.0.9\n" +
                "kafka-manager.default.svc.cluster.local 172.30.0.9\n" +
                "kafka-manager.default.svc.kubernetes 172.30.0.9\n" +
                "kibana.default.svc.cluster.eskimo 172.30.0.10\n" +
                "kibana.default.svc.cluster.local 172.30.0.10\n" +
                "kibana.default.svc.kubernetes 172.30.0.10\n" +
                "logstash-0.logstash.default.svc.cluster.eskimo 172.30.0.7\n" +
                "logstash-0.logstash.default.svc.cluster.local 172.30.0.7\n" +
                "logstash-0.logstash.default.svc.kubernetes 172.30.0.7\n" +
                "logstash-3.logstash.default.svc.cluster.eskimo 172.30.2.3\n" +
                "logstash-3.logstash.default.svc.cluster.local 172.30.2.3\n" +
                "logstash-3.logstash.default.svc.kubernetes 172.30.2.3\n" +
                "logstash-1.logstash.default.svc.cluster.eskimo 172.30.3.4\n" +
                "logstash-1.logstash.default.svc.cluster.local 172.30.3.4\n" +
                "logstash-1.logstash.default.svc.kubernetes 172.30.3.4\n" +
                "logstash-2.logstash.default.svc.cluster.eskimo 172.30.4.3\n" +
                "logstash-2.logstash.default.svc.cluster.local 172.30.4.3\n" +
                "logstash-2.logstash.default.svc.kubernetes 172.30.4.3\n" +
                "logstash.default.svc.cluster.eskimo 172.30.4.3\n" +
                "logstash.default.svc.cluster.local 172.30.4.3\n" +
                "logstash.default.svc.kubernetes 172.30.4.3\n" +
                "kube-dns.kube-system.svc.cluster.eskimo 172.30.0.3\n" +
                "kube-dns.kube-system.svc.cluster.local 172.30.0.3\n" +
                "kube-dns.kube-system.svc.kubernetes 172.30.0.3\n" +
                "dashboard-metrics-scraper.kubernetes-dashboard.svc.cluster.eskimo 192.168.56.24\n" +
                "dashboard-metrics-scraper.kubernetes-dashboard.svc.cluster.local 192.168.56.24\n" +
                "dashboard-metrics-scraper.kubernetes-dashboard.svc.kubernetes 192.168.56.24\n" +
                "kubernetes-dashboard.kubernetes-dashboard.svc.cluster.eskimo 192.168.56.22\n" +
                "kubernetes-dashboard.kubernetes-dashboard.svc.cluster.local 192.168.56.22\n" +
                "kubernetes-dashboard.kubernetes-dashboard.svc.kubernetes 192.168.56.22\n", result);
    }

}
