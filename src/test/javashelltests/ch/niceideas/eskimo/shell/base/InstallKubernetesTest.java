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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class InstallKubernetesTest {

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
                new File("./services_setup/base-eskimo/install-kubernetes.sh"),
                new File (jailPath + "/install-kubernetes.sh"));

        ProcessHelper.exec(new String[]{"bash", "-c", "chmod 777 " + jailPath + "/install-kubernetes.sh"}, true);

        // I need the real bash
        assertTrue (new File (jailPath + "/bash").delete());

        createEskimoKubeDummyPackage();
    }

    private void createEskimoKubeDummyPackage() throws Exception {
        File k8s = new File (jailPath, "k8s");
        assertTrue (k8s.mkdirs());

        File cfssl = new File (k8s, "cfssl");
        assertTrue (cfssl.mkdirs());
        File cfsslBin = new File (cfssl, "bin");
        assertTrue (cfsslBin.mkdirs());
        FileUtils.writeFile(new File (cfsslBin, "cfssl-1.6.3_linux_amd64"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (cfsslBin, "cfssl"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (cfsslBin, "cfssljson-1.6.3_linux_amd64"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (cfsslBin, "cfssljson"), "#!/bin/bash\necho OK");

        File cniPluginsV = new File (k8s, "cni-plugins-v1.1.1");
        assertTrue (cniPluginsV.mkdirs());
        ProcessHelper.exec(new String[]{"bash", "-c", "cd " + k8s.getAbsolutePath() + " && /bin/ln -s cni-plugins-v1.1.1 cni-plugins"}, true);
        FileUtils.writeFile(new File (cniPluginsV, "bridge"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (cniPluginsV, "host-device"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (cniPluginsV, "host-local"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (cniPluginsV, "loopback"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (cniPluginsV, "static"), "#!/bin/bash\necho OK");

        File criDockerdV = new File (k8s, "cri-dockerd-0.3.0");
        assertTrue (criDockerdV.mkdirs());
        ProcessHelper.exec(new String[]{"bash", "-c", "cd " + k8s.getAbsolutePath() + " && /bin/ln -s cri-dockerd-0.3.0 cri-dockerd"}, true);
        FileUtils.writeFile(new File (cniPluginsV, "cri-dockerd"), "#!/bin/bash\necho OK");

        File etcdV = new File (k8s, "etcd-v3.5.6");
        assertTrue (etcdV.mkdirs());
        ProcessHelper.exec(new String[]{"bash", "-c", "cd " + k8s.getAbsolutePath() + " && /bin/ln -s etcd-v3.5.6 etcd"}, true);
        File etcdBin = new File (etcdV, "bin");
        assertTrue (etcdBin.mkdirs());
        FileUtils.writeFile(new File (etcdBin, "etcd"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (etcdBin, "etcdctl"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (etcdBin, "etcdutl"), "#!/bin/bash\necho OK");

        File images = new File (k8s, "images");
        assertTrue (images.mkdirs());
        FileUtils.writeFile(new File (images, "coredns_coredns:1.10.0.tar.gz"), "DUMMY");
        FileUtils.writeFile(new File (images, "k8s.gcr.io_pause:3.6.tar.gz"), "DUMMY");

        File kubernetesV = new File (k8s, "kubernetes-v1.25.3");
        assertTrue (kubernetesV.mkdirs());
        ProcessHelper.exec(new String[]{"bash", "-c", "cd " + k8s.getAbsolutePath() + " && /bin/ln -s kubernetes-v1.25.3 kubernetes"}, true);
        File kubernetesClient = new File (kubernetesV, "client");
        assertTrue (kubernetesClient.mkdirs());
        File kubernetesClientBin = new File (kubernetesClient, "client");
        assertTrue (kubernetesClientBin.mkdirs());
        FileUtils.writeFile(new File (kubernetesClientBin, "kubectl"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (kubernetesClientBin, "kubectl-convert"), "#!/bin/bash\necho OK");
        File kubernetesServer = new File (kubernetesV, "server");
        assertTrue (kubernetesServer.mkdirs());
        File kubernetesServerBin = new File (kubernetesServer, "client");
        assertTrue (kubernetesServerBin.mkdirs());
        FileUtils.writeFile(new File (kubernetesServerBin, "kubeadm"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (kubernetesServerBin, "kube-aggregator"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (kubernetesServerBin, "kube-apiserver"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (kubernetesServerBin, "kube-controller-manager"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (kubernetesServerBin, "kube-ctl"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (kubernetesServerBin, "kubelet"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (kubernetesServerBin, "kube-proxy"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (kubernetesServerBin, "kube-scheduler"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (kubernetesServerBin, "kube-log-runner"), "#!/bin/bash\necho OK");
        FileUtils.writeFile(new File (kubernetesServerBin, "mounter"), "#!/bin/bash\necho OK");

        File kubeRouterV = new File (k8s, "kube-router-V1.5.3");
        assertTrue (kubeRouterV.mkdirs());
        ProcessHelper.exec(new String[]{"bash", "-c", "cd " + k8s.getAbsolutePath() + " && /bin/ln -s kube-router-V1.5.3 kube-router"}, true);
        File kubeRouterBin = new File (kubeRouterV, "bin");
        assertTrue (kubeRouterBin.mkdirs());
        FileUtils.writeFile(new File (kubeRouterBin, "kube-router"), "#!/bin/bash\necho OK");

        FileUtils.createTarFile(jailPath + "/k8s", new File (jailPath + "/eskimo_kube_1.25.5.tar.gz"));
        Thread.sleep(100);
        FileUtils.delete(k8s);
    }

    private void createTestScript(String scriptName) throws FileException {

        String script = "#!/bin/bash\n" + "\n" +
                AbstractSetupShellTest.COMMON_SCRIPT_HACKS +
                "# Call command\n" +
                "$SCRIPT_DIR/install-kubernetes.sh";
        FileUtils.writeFile(new File (jailPath + "/" + scriptName), script);
    }


    @Test
    public void testRunScript() throws Exception {

        createTestScript("calling_script.sh");

        String result = ProcessHelper.exec(new String[]{"bash", jailPath + "/calling_script.sh"}, true);

        assertExpectedScriptOutput(result);

        assertExpectedSudoCommands();
    }

    private void assertExpectedSudoCommands() throws Exception {
        String sudoLogs = StreamUtils.getAsString(ResourceUtils.getResourceAsStream(jailPath + "/.log_sudo"), StandardCharsets.UTF_8);
        if (StringUtils.isNotBlank(sudoLogs)) {

            int indexOfLnK8s = sudoLogs.indexOf("ln -s /usr/local/lib/k8s-1.25.5 /usr/local/lib/k8s");
            assertTrue(indexOfLnK8s > -1);

            int indexOfLnK8sEtc = sudoLogs.indexOf("ln -s /usr/local/etc/k8s /usr/local/lib/k8s/etc", indexOfLnK8s);
            assertTrue(indexOfLnK8sEtc > -1);

            int indexOfLnEtcdEtc = sudoLogs.indexOf("ln -s /usr/local/etc/etcd /usr/local/lib/k8s/etcd_etc", indexOfLnK8sEtc);
            assertTrue(indexOfLnEtcdEtc > -1);

            int indexOfLnCfssl = sudoLogs.indexOf("ln -s /usr/local/lib/k8s/cfssl/bin/cfssl /usr/local/bin/cfssl", indexOfLnEtcdEtc);
            assertTrue(indexOfLnCfssl > -1);

            int indexOfLnCfsslJson = sudoLogs.indexOf("ln -s /usr/local/lib/k8s/cfssl/bin/cfssljson /usr/local/bin/cfssljson", indexOfLnCfssl);
            assertTrue(indexOfLnCfsslJson > -1);

            int indexOfLnCni = sudoLogs.indexOf("ln -s /usr/local/lib/k8s/cni-plugins /opt/cni/bin", indexOfLnCfsslJson);
            assertTrue(indexOfLnCni > -1);

        } else {
            fail ("Expected Sudo Logs");
        }
    }

    private void assertExpectedScriptOutput(String result) {

        int indexOfTitle = result.indexOf("INSTALLING KUBERNETES");
        assertTrue(indexOfTitle > -1);

        int indexOfCsDir = result.indexOf("Changing to temp directory", indexOfTitle + 1);
        assertTrue(indexOfCsDir > -1);

        int indexOfKubeVersion = result.indexOf("Eskimo Kube package version is 1.25.5", indexOfCsDir + 1);
        assertTrue(indexOfKubeVersion > -1);

        int indexOfExtract = result.indexOf("Extracting kube_1.25.5", indexOfKubeVersion + 1);
        assertTrue(indexOfExtract > -1);

        int indexOfInstall = result.indexOf("Installing Kubernetes", indexOfExtract + 1);
        assertTrue(indexOfInstall > -1);

        int indexOfSymlinkK8s = result.indexOf("Symlinking /usr/local/lib/k8s-1.25.5 to /usr/local/lib/k8s", indexOfInstall + 1);
        assertTrue(indexOfSymlinkK8s > -1);

        int indexOfSymlinkK8sConfig = result.indexOf("Simlinking k8s config to /usr/local/etc/k8s", indexOfSymlinkK8s + 1);
        assertTrue(indexOfSymlinkK8sConfig > -1);

        int indexOfSymlinkEtcdConfig = result.indexOf("Simlinking etcd config to /usr/local/etc/etcd", indexOfSymlinkK8sConfig + 1);
        assertTrue(indexOfSymlinkEtcdConfig > -1);

        int indexOfSymlinkK8sBinaries = result.indexOf("Simlinking K8s binaries to /usr/local/bin", indexOfSymlinkK8sConfig + 1);
        assertTrue(indexOfSymlinkK8sBinaries > -1);

        int indexOfCfssl = result.indexOf("cfssl", indexOfSymlinkK8sConfig + 1);
        assertTrue(indexOfCfssl > -1);

        int indexOfEtcd = result.indexOf("etcd", indexOfCfssl + 1);
        assertTrue(indexOfEtcd > -1);

        int indexOfKubeClient = result.indexOf("Kubernetes client", indexOfEtcd + 1);
        assertTrue(indexOfKubeClient > -1);

        int indexOfKubeRouter = result.indexOf("Kube-router", indexOfKubeClient + 1);
        assertTrue(indexOfKubeRouter > -1);

        int indexOfKubeServer = result.indexOf("Kubernetes server", indexOfKubeRouter + 1);
        assertTrue(indexOfKubeServer > -1);

        int indexOfCriDockerd = result.indexOf("Installing cri-dockerd", indexOfKubeServer + 1);
        assertTrue(indexOfCriDockerd > -1);

        int indexOfCniPlugins = result.indexOf("Installing cni plugins", indexOfCriDockerd + 1);
        assertTrue(indexOfCniPlugins > -1);

        int indexOfCleaning = result.indexOf("Cleaning build folder", indexOfCniPlugins + 1);
        assertTrue(indexOfCleaning > -1);

        // these are expected in error indeed in the test
        int indexOfEtcdBinError = result.indexOf("ls: cannot access '/usr/local/lib/k8s/etcd/bin/': No such file or directory", indexOfCleaning + 1);
        assertTrue(indexOfEtcdBinError > -1);

        int indexOfKubeCliError = result.indexOf("ls: cannot access '/usr/local/lib/k8s/kubernetes/client/bin/': No such file or directory", indexOfEtcdBinError + 1);
        assertTrue(indexOfKubeCliError > -1);

        int indexOfKubeRouterError = result.indexOf("ls: cannot access '/usr/local/lib/k8s/kube-router/bin/': No such file or directory", indexOfKubeCliError + 1);
        assertTrue(indexOfKubeRouterError > -1);

        int indexOfKubeServerError = result.indexOf("ls: cannot access '/usr/local/lib/k8s/kubernetes/server/bin/': No such file or directory", indexOfKubeRouterError + 1);
        assertTrue(indexOfKubeServerError > -1);

        int indexOfDriDockerError = result.indexOf("ls: cannot access '/usr/local/lib/k8s/cri-dockerd/': No such file or directory\n", indexOfKubeServerError + 1);
        assertTrue(indexOfDriDockerError > -1);

    }

}
