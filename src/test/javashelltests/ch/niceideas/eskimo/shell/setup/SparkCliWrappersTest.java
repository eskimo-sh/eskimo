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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SparkCliWrappersTest extends AbstractSetupShellTest {

    private static final Logger logger = Logger.getLogger(SparkCliWrappersTest.class);

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
        return "spark-cli";
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
        copyFile(jailPath, "spark_wrappers/beeline");
        copyFile(jailPath, "spark_wrappers/pyspark");
        copyFile(jailPath, "spark_wrappers/run-example");
        copyFile(jailPath, "spark_wrappers/spark-class");
        copyFile(jailPath, "spark_wrappers/spark-shell");
        copyFile(jailPath, "spark_wrappers/spark-sql");
        copyFile(jailPath, "spark_wrappers/spark-submit");
        copyFile(jailPath, "spark_wrappers/sparkR");
    }

    @Test
    public void testSparkR() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-sparkR.sh", "sparkR --properties-file /tmp/test.properties -driver-class-path /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar --py-files /usr/local/lib/python-eskimo/test.py");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-sparkR.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user spark " +
                "--privileged " +
                "-v /var/log/spark:/var/log/spark:shared " +
                "-v /tmp:/tmp:slave " +
                "-v /var/lib/spark:/var/lib/spark:slave " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "-v /usr/local/lib/python-eskimo:/usr/local/lib/python-eskimo:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/home/spark/.kube/config,target=/home/spark/.kube/config " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-v /etc/k8s:/etc/k8s:ro " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/spark:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/sparkR --properties-file /tmp/test.properties -driver-class-path /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar --py-files /usr/local/lib/python-eskimo/test.py\n", dockerLogs);
    }

    @Test
    public void testSparkSubmit() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-spark-submit.sh", "spark-submit --properties-file /tmp/test.properties -driver-class-path /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar --py-files /usr/local/lib/python-eskimo/test.py /usr/lib/spark/example.jar");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-spark-submit.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user spark " +
                "--privileged " +
                "-v /var/log/spark:/var/log/spark:shared " +
                "-v /tmp:/tmp:slave " +
                "-v /var/lib/spark:/var/lib/spark:slave " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "-v /usr/local/lib/python-eskimo:/usr/local/lib/python-eskimo:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/home/spark/.kube/config,target=/home/spark/.kube/config " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-v /etc/k8s:/etc/k8s:ro " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/spark:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/spark-submit --properties-file /tmp/test.properties -driver-class-path /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar --py-files /usr/local/lib/python-eskimo/test.py /usr/lib/spark/example.jar\n", dockerLogs);
    }

    @Test
    public void testSparkSql() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-spark-sql.sh", "spark-sql --properties-file /tmp/test.properties -driver-class-path /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar --py-files /usr/local/lib/python-eskimo/test.py");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-spark-sql.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user spark " +
                "--privileged " +
                "-v /var/log/spark:/var/log/spark:shared " +
                "-v /tmp:/tmp:slave " +
                "-v /var/lib/spark:/var/lib/spark:slave " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "-v /usr/local/lib/python-eskimo:/usr/local/lib/python-eskimo:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/home/spark/.kube/config,target=/home/spark/.kube/config " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-v /etc/k8s:/etc/k8s:ro " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/spark:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/spark-sql --properties-file /tmp/test.properties -driver-class-path /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar --py-files /usr/local/lib/python-eskimo/test.py\n", dockerLogs);
    }

    @Test
    public void testSparkShell() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-spark-shell.sh", "spark-shell --properties-file /tmp/test.properties -driver-class-path /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar --py-files /usr/local/lib/python-eskimo/test.py");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-spark-shell.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user spark " +
                "--privileged " +
                "-v /var/log/spark:/var/log/spark:shared " +
                "-v /tmp:/tmp:slave " +
                "-v /var/lib/spark:/var/lib/spark:slave " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "-v /usr/local/lib/python-eskimo:/usr/local/lib/python-eskimo:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/home/spark/.kube/config,target=/home/spark/.kube/config " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-v /etc/k8s:/etc/k8s:ro " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/spark:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/spark-shell --properties-file /tmp/test.properties -driver-class-path /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar --py-files /usr/local/lib/python-eskimo/test.py\n", dockerLogs);
    }

    @Test
    public void testSparkClass() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-spark-class.sh", "spark-class --jar /tmp/test.jar -classpath /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-spark-class.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user spark " +
                "--privileged " +
                "-v /var/log/spark:/var/log/spark:shared " +
                "-v /var/lib/spark:/var/lib/spark:slave " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/home/spark/.kube/config,target=/home/spark/.kube/config " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-v /etc/k8s:/etc/k8s:ro " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/spark:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/spark-class --jar /tmp/test.jar -classpath /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar\n", dockerLogs);
    }

    @Test
    public void testRunExample() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-run-example.sh", "run-example --properties-file /tmp/test.properties -driver-class-path /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar --py-files /usr/local/lib/python-eskimo/test.py");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-run-example.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user spark " +
                "--privileged " +
                "-v /var/log/spark:/var/log/spark:shared " +
                "-v /tmp:/tmp:slave " +
                "-v /var/lib/spark:/var/lib/spark:slave " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "-v /usr/local/lib/python-eskimo:/usr/local/lib/python-eskimo:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/home/spark/.kube/config,target=/home/spark/.kube/config " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-v /etc/k8s:/etc/k8s:ro " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/spark:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/run-example --properties-file /tmp/test.properties -driver-class-path /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar --py-files /usr/local/lib/python-eskimo/test.py\n", dockerLogs);
    }

    @Test
    public void testPyspark() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-pyspark.sh", "pyspark --properties-file /tmp/test.properties -driver-class-path /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar --py-files /usr/local/lib/python-eskimo/test.py");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-pyspark.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user spark " +
                "--privileged " +
                "-v /var/log/spark:/var/log/spark:shared " +
                "-v /tmp:/tmp:slave " +
                "-v /var/lib/spark:/var/lib/spark:slave " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "-v /usr/local/lib/python-eskimo:/usr/local/lib/python-eskimo:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/home/spark/.kube/config,target=/home/spark/.kube/config " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-v /etc/k8s:/etc/k8s:ro " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/spark:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/pyspark --properties-file /tmp/test.properties -driver-class-path /var/lib/eskimo/test.jar:/var/lib/spark/spark.jar --py-files /usr/local/lib/python-eskimo/test.py\n", dockerLogs);
    }

    @Test
    public void testBeeline() throws Exception {
        FlinkCliWrappersTest.createScript(jailPath, "test-beeline.sh", "beeline -f /tmp/test.json -w /var/lib/eskimo/test.json");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/test-beeline.sh"}, true);
        logger.debug (result);

        String dockerLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(getJailPath() + "/.log_docker"), StandardCharsets.UTF_8);
        String kubeNSFile = FlinkCliWrappersTest.extractKubeNSFile(dockerLogs);
        assertEquals ("image ls -a\n" +
                "run " +
                "-i " +
                "--rm " +
                "--network host " +
                "--user spark " +
                "--privileged " +
                "-v /var/log/spark:/var/log/spark:shared " +
                "-v /var/lib/eskimo:/var/lib/eskimo:slave " +
                "-v /tmp:/tmp:slave " +
                "--mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh " +
                "--mount type=bind,source=/etc/eskimo_services-settings.json,target=/etc/eskimo_services-settings.json " +
                "--mount type=bind,source=/home/spark/.kube/config,target=/home/spark/.kube/config " +
                "--mount type=bind,source=/tmp/" + kubeNSFile + ",target=/tmp/" + kubeNSFile + " " +
                "-v /etc/k8s:/etc/k8s:ro " +
                "-e NODE_NAME=testhost " +
                "-e ADDITONAL_HOSTS_FILE=/tmp/" + kubeNSFile + " " +
                "kubernetes.registry:5000/spark:0 " +
                    "/usr/local/bin/kube_do /usr/local/bin/beeline -f /tmp/test.json -w /var/lib/eskimo/test.json\n", dockerLogs);
    }

}
