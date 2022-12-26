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

package ch.niceideas.eskimo.utils;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.services.SetupService;
import ch.niceideas.eskimo.services.SetupServiceImpl;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class PackageVersionGenerator {

    private static final Logger logger = Logger.getLogger(PackageVersionGenerator.class);

    public static void main (String[] args) {

        if (args.length == 0) {
            logger.error("Expected working directory as first argument");
            System.exit(-1);
        }

        if (args[0] == null || args[0].length() == 0) {
            logger.error("Expected working directory as first argument");
            System.exit(-2);
        }

        new PackageVersionGenerator().generatePackageVersionFile(args[0]);
    }

    void generatePackageVersionFile(String workingDirString) {
        File workingDirectory = new File (workingDirString);
        if (!workingDirectory.exists()) {
            logger.error("Directory " + workingDirString + " doesn't exist.");
            System.exit(-3);
        }

        SetupService setupService = new SetupServiceImpl();

        Map<String, Pair<String, String>> packages = new HashMap<>();

        File[] files = workingDirectory.listFiles();
        if (files != null && files.length > 0) {
            for (File packageFile : files) {

                String fileName = packageFile.getName();

                if ((fileName.startsWith(SetupService.DOCKER_TEMPLATE_PREFIX)
                        || fileName.startsWith(SetupService.KUBE_PREFIX)
                ) && fileName.endsWith("tar.gz")) {

                    String packageName;
                    if (fileName.startsWith(SetupService.DOCKER_TEMPLATE_PREFIX)) {
                        packageName = fileName.substring(
                                SetupService.DOCKER_TEMPLATE_PREFIX.length(),
                                fileName.indexOf("_", SetupService.DOCKER_TEMPLATE_PREFIX.length() + 1));
                    } else {
                        packageName = fileName.substring(
                                SetupService.KUBE_PREFIX.length(),
                                fileName.indexOf("_", SetupService.KUBE_PREFIX.length() + 1));
                    }

                    Pair<String, String> version = setupService.parseVersion(fileName);

                    Pair<String, String> previousVersion = packages.computeIfAbsent(packageName, k -> version);
                    if (setupService.compareVersion(version, previousVersion) > 0) {
                        packages.put(packageName, version);
                    }

                }
            }
        }

        JsonWrapper resultJSON = new JsonWrapper("{}");
        for (Map.Entry<String, Pair<String, String>> packageEntry : packages.entrySet()) {
            String packageName = packageEntry.getKey();
            Pair<String, String> version = packageEntry.getValue();
            resultJSON.setValueForPath(packageName + ".software", version.getKey());
            resultJSON.setValueForPath(packageName + ".distribution", version.getValue());
        }

        //System.err.println (resultJSON.getFormattedValue());

        File resultFile = new File (workingDirectory, "eskimo_packages_versions.json");
        try {
            FileUtils.writeFile(resultFile, resultJSON.getFormattedValue());
        } catch (FileException e) {
            logger.error (e, e);
            throw new RuntimeException(e);
        }

    }
}
