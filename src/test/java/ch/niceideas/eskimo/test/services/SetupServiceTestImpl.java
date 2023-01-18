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

package ch.niceideas.eskimo.test.services;

import ch.niceideas.common.exceptions.CommonRTException;
import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.SetupCommand;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.services.SetupService;
import ch.niceideas.eskimo.services.SetupServiceImpl;
import lombok.Setter;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.util.Set;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile({"test-setup"})
public class SetupServiceTestImpl extends SetupServiceImpl implements SetupService {

    private static final Logger logger = Logger.getLogger(SetupServiceTestImpl.class);

    @Autowired
    private ServicesDefinition servicesDefinition;

    private File configStoragePath = null;

    private boolean setupError = false;
    private boolean setupCompleted = false;

    public void setSetupError() {
        this.setupError = true;
        this.setupCompleted = false;
    }

    public void setSetupCompleted() {
        this.setupError = false;
        this.setupCompleted = true;
    }

    public void setPackageDistributionPath(String packageDistributionPath) {
        this.packageDistributionPath = packageDistributionPath;
    }

    public void reset() {
        setupError = false;
        setupCompleted = false;
        tearDown();
        init();
    }

    @PreDestroy
    public void tearDown() {
        if (configStoragePath.exists()) {
            try {
                FileUtils.delete(configStoragePath);
            } catch (FileUtils.FileDeleteFailedException e) {
                throw new CommonRTException(e);
            }
        }
    }

    @PostConstruct
    public void init() {
        try {
            configStoragePath = File.createTempFile("eskimo_test", "_storage");
            if (!configStoragePath.delete()) {
                throw new CommonRTException("Could not delete temp file before folder creation " + configStoragePath.getAbsolutePath());
            }

            if (!configStoragePath.mkdir()) {
                throw new CommonRTException("Could not create folder " + configStoragePath.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new CommonRTException(e);
        }
    }

    @Override
    public String getPackagesToBuild() {
        return super.getPackagesToBuild();
    }

    @Override
    public String getConfigStoragePath() {
        return configStoragePath.getAbsolutePath();
    }

    @Override
    public String getPackagesDownloadUrlRoot() {
        return "dummy";
    }

    @Override
    public void ensureSetupCompleted() throws SetupException {
        if (setupError) {
            throw new SetupException("Test Error");
        }
    }

    @Override
    public SetupCommand saveAndPrepareSetup(String configAsString) throws SetupException {
        JsonWrapper setupConfigJSON = new JsonWrapper(configAsString);
        return SetupCommand.create(setupConfigJSON, this, servicesDefinition);
    }

    @Override
    public Pair<String, String> parseVersion(String name) {
        return null;
    }

    @Override
    public String findLastPackageFile(String prefix, String packageName) {
        if (packageName.equals("kube")) {
            return "eskimo_kube_1.23.5_1.tar.gz";
        }
        return prefix+"_"+packageName+"_dummy_1.dummy";
    }

    @Override
    public void prepareSetup(JsonWrapper setupConfig, Set<String> downloadPackages, Set<String> buildPackage, Set<String> downloadKube, Set<String> buildKube, Set<String> packageUpdate) throws SetupException {

        File packagesDistribFolder = null;
        try {
            packagesDistribFolder = new File(packageDistributionPath).getCanonicalFile();
        } catch (IOException e) {
            logger.error (e, e);
            throw new SetupException (e.getMessage(), e);
        }
        if (!packagesDistribFolder.exists()) {
            throw new UnsupportedOperationException("test packages distrib path folder should be created beforehand");
        }

        // 1. Find out about missing packages
        String servicesOrigin = (String) setupConfig.getValueForPath("setup-services-origin");
        if (StringUtils.isEmpty(servicesOrigin) || servicesOrigin.equals(BUILD_FLAG)) { // for services default is build

            findMissingPackages(packagesDistribFolder, buildPackage);

        } else {

            logger.warn("test service doesn't support download");

        }

        // 2. Find out about missing Kube distrib
        String kubeOrigin = (String) setupConfig.getValueForPath("setup-kube-origin");
        if (StringUtils.isEmpty(kubeOrigin) || kubeOrigin.equals(DOWNLOAD_FLAG)) { // for Kube default is download

            logger.warn("test service doesn't support download");

        } else {
            findMissingKube(packagesDistribFolder, buildKube);
        }


    }

    @Override
    public void applySetup(SetupCommand setupCommand) {
        // No-Op
    }

    @Override
    public int compareVersion(Pair<String, String> first, Pair<String, String> second) {
        return 0;
    }

    @Override
    public int compareSoftwareVersion(String firstVersion, String secondVersion) {
        return 0;
    }

    @Override
    public String getStoragePathConfDir() {
        return configStoragePath.getAbsolutePath();
    }
}
