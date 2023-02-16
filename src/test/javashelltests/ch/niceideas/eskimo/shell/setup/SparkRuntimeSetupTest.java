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

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SparkRuntimeSetupTest extends AbstractSetupShellTest {

    protected static String jailPath = null;

    private static boolean initialized = false;

    @BeforeEach
    public void setUp() throws Exception {
        if (!initialized) {
            jailPath = setupJail(getServiceName());
            initialized = true;
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
        return "spark-runtime";
    }

    @Override
    protected String getTemplateName() {
        return "spark";
    }

    @Override
    protected String getImageName() {
        return "spark";
    }

    @Override
    protected void copyScripts(String jailPath) throws IOException {
        // setup.sh and common.sh are automatic
        copyFile(jailPath, "setupCommon.sh");
        copyFile(jailPath, "inContainerSetupSpark.sh");
        copyFile(jailPath, "Dockerfile.spark-runtime");
        copyFile(jailPath, "inContainerSetupSparkCommon.sh");
        copyFile(jailPath, "inContainerInjectTopology.sh");
    }

    @Override
    protected String[] getScriptsToExecute() {
        return new String[] {
                "setup.sh",
                "inContainerSetupSpark.sh",
                "inContainerInjectTopology.sh"
        };
    }

    @Override
    protected String[][] getCustomScriptsToExecute() {
        return new String[][] {
                new String[] {"inContainerSetupSparkCommon.sh", "3302"}
        };
    }

    @Test
    public void testSystemDockerManipulations() throws Exception {
        assertKubernetesServiceDockerCommands();
        assertKubernetesServiceDockerCommands(getJailPath() + "/spark_executor_setup/", "spark-runtime", true);
    }

    @Test
    public void testConfigurationFileUpdate() throws Exception {
        String configEchoed = FileUtils.readFile(new File (getJailPath() + "/.log_bash"));

        //System.err.println (configEchoed);

        // test a few essential ones
        assertTrue (configEchoed.contains("spark.master=k8s://https://192.168.10.11:6443\""));
        assertTrue (configEchoed.contains("spark.kubernetes.driver.master=https://192.168.10.11:6443\""));
        assertTrue (configEchoed.contains("spark.es.nodes=elasticsearch.eskimo.svc.cluster.eskimo\""));
        assertTrue (configEchoed.contains("spark.driver.host=192.168.10.13\""));
        assertTrue (configEchoed.contains("spark.driver.bindAddress=192.168.10.13\""));

        configEchoed = FileUtils.readFile(new File (getJailPath() + "/.log_sudo"));

        //System.err.println (configEchoed);

        // test a few essential ones
        assertTrue (configEchoed.contains("echo -e \"export SPARK_CONF_DIR=/usr/local/lib/spark/conf\"  >> /usr/local/lib/spark/conf/spark-env.sh"));
        assertTrue (configEchoed.contains("echo -e \"export SPARK_LOG_DIR=/usr/local/lib/spark/logs\"  >> /usr/local/lib/spark/conf/spark-env.sh"));
        assertTrue (configEchoed.contains("echo -e \"export KUBECONFIG=/home/spark/.kube/config\"  >> /usr/local/lib/spark/conf/spark-env.sh"));
        assertTrue (configEchoed.contains("echo -e \"spark.eventLog.dir=/var/lib/spark/eventlog\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"));
        assertTrue (configEchoed.contains("echo -e \"spark.dynamicAllocation.enabled=true\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"));
        assertTrue (configEchoed.contains("echo -e \"spark.kubernetes.container.image=kubernetes.registry:5000/spark-runtime:0\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"));
        assertTrue (configEchoed.contains("echo -e \"spark.kubernetes.file.upload.path=/var/lib/spark/data\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"));
        assertTrue (configEchoed.contains("echo -e \"spark.kubernetes.driver.podTemplateFile=/usr/local/lib/spark/conf/spark-pod-template.yaml\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"));
        assertTrue (configEchoed.contains("echo -e \"spark.kubernetes.executor.podTemplateFile=/usr/local/lib/spark/conf/spark-pod-template.yaml\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"));
        assertTrue (configEchoed.contains("echo -e \"spark.kubernetes.authenticate.driver.serviceAccountName=eskimo\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"));

    }
}
