/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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


package ch.niceideas.eskimo.test.testwrappers;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.model.MessageLogger;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.services.SetupService;
import ch.niceideas.eskimo.services.SetupServiceImpl;
import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile({"setup-under-test"})
public class SetupServiceUnderTest extends SetupServiceImpl implements SetupService {

    private static final Logger logger = Logger.getLogger(SetupServiceUnderTest.class);

    @Getter
    final List<String> builtPackageList = new ArrayList<>();

    @Getter
    final List<String> downloadPackageList = new ArrayList<>();

    @Setter
    private String packagesVersionFile = null;

    private boolean dontBuild = false;

    private boolean tweaLastVersionForTests = false;

    public void setServicesDefinition (ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }

    public void setConfigStoragePathInternal(String configStoragePathInternal) {
        this.configStoragePathInternal = configStoragePathInternal;
    }

    public void setPackagesDevPathForTests (String packagesDevPathForTest) {
        this.packagesDevPath = packagesDevPathForTest;
    }

    public void setPackageDistributionPath(String packageDistributionPath) {
        this.packageDistributionPath = packageDistributionPath;
    }

    public void reset() {
        builtPackageList.clear();
        downloadPackageList.clear();
        dontBuild = false;
        tweaLastVersionForTests = false;
        setBuildVersion("1.0-SNAPSHOT");
    }

    public void dontBuild() {
        dontBuild = true;
    }

    public void setStoragePathConfDir (String storagePathConfDir) {
        this.storagePathConfDir = storagePathConfDir;
    }

    @Override
    protected JsonWrapper loadRemotePackagesVersionFile() {
        return new JsonWrapper(packagesVersionFile);
    }

    @Override
    protected void dowloadFile(MessageLogger ml, File destinationFile, URL downloadUrl, String message) throws IOException {
        destinationFile.createNewFile();
        try {
            FileUtils.writeFile(destinationFile, "TEST DOWNLOADED CONTENT");
        } catch (FileException e) {
            logger.debug (e, e);
            throw new IOException(e);
        }
    }

    public void setBuildVersion (String buildVersion) {
        this.buildVersion = buildVersion;
    }

    @Override
    public void buildPackage(String image) throws SetupException {
        builtPackageList.add (image);
        if (!dontBuild) {
            super.buildPackage(image);
        }
    }

    @Override
    public void downloadPackage(String packageName, String fileName) throws SetupException {
        downloadPackageList.add (fileName);
        if (!dontBuild) {
            super.downloadPackage(packageName, fileName);
        }
    }

    @Override
    protected Pair<File, Pair<String, String>> findLastVersion(String prefix, String packageName, File packagesDistribFolder) {
        if (tweaLastVersionForTests) {
            return new Pair<>(new File("package_" + packageName + ".tgz"), new Pair<>("1.0", "0"));
        } else {
            return super.findLastVersion(prefix, packageName, packagesDistribFolder);
        }
    }

    @Override
    public void findMissingPackages(File packagesDistribFolder, Set<String> missingServices) {
        if (tweaLastVersionForTests) {
            // No-Op
        } else {
            super.findMissingPackages(packagesDistribFolder, missingServices);
        }
    }

    @Override
    public void findMissingKube(File packagesDistribFolder, Set<String> missingServices) {
        if (tweaLastVersionForTests) {
            // No-Op
        } else {
            super.findMissingKube(packagesDistribFolder, missingServices);
        }
    }

    public void tweakLastVersionForTests() {
        tweaLastVersionForTests = true;
    }
}
