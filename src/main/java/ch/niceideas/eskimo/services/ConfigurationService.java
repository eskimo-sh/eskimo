/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.MarathonServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.locks.ReentrantLock;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ConfigurationService {

    private static final Logger logger = Logger.getLogger(ConfigurationService.class);

    public static final String NODES_STATUS_JSON_PATH = "/nodes-status.json";

    private ReentrantLock statusFileLock = new ReentrantLock();
    private ReentrantLock nodesConfigFileLock = new ReentrantLock();
    private ReentrantLock marathonServicesFileLock = new ReentrantLock();

    @Autowired
    private SetupService setupService;

    /* For tests */
    public void setSetupService(SetupService setupService) {
        this.setupService = setupService;
    }

    void updateAndSaveServicesInstallationStatus(SystemService.StatusUpdater statusUpdater) throws FileException, SetupException {
        statusFileLock.lock();
        try {
            ServicesInstallStatusWrapper status = loadServicesInstallationStatus();
            statusUpdater.updateStatus(status);
            String configStoragePath = setupService.getConfigStoragePath();
            FileUtils.writeFile(new File(configStoragePath + NODES_STATUS_JSON_PATH), status.getFormattedValue());
        } finally {
            statusFileLock.unlock();
        }
    }

    public void saveServicesInstallationStatus(ServicesInstallStatusWrapper status) throws FileException, SetupException {
        statusFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            FileUtils.writeFile(new File(configStoragePath + NODES_STATUS_JSON_PATH), status.getFormattedValue());
        } finally {
            statusFileLock.unlock();
        }
    }

    public ServicesInstallStatusWrapper loadServicesInstallationStatus() throws FileException, SetupException {
        statusFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            File statusFile = new File(configStoragePath + NODES_STATUS_JSON_PATH);
            if (!statusFile.exists()) {
                return ServicesInstallStatusWrapper.empty();
            }

            return new ServicesInstallStatusWrapper(statusFile);
        } finally {
            statusFileLock.unlock();
        }
    }

    public void saveNodesConfig(NodesConfigWrapper nodesConfig) throws FileException, SetupException {
        nodesConfigFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            FileUtils.writeFile(new File(configStoragePath + "/nodes-config.json"), nodesConfig.getFormattedValue());
        } finally {
            nodesConfigFileLock.unlock();
        }
    }

    public NodesConfigWrapper loadNodesConfig() throws SystemException, SetupException {
        nodesConfigFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            File nodesConfigFile = new File(configStoragePath + "/nodes-config.json");
            if (!nodesConfigFile.exists()) {
                return null;
            }

            return new NodesConfigWrapper(FileUtils.readFile(nodesConfigFile));
        } catch (JSONException | FileException e) {
            logger.error (e, e);
            throw new SystemException(e);
        } finally {
            nodesConfigFileLock.unlock();
        }
    }

    public JsonWrapper saveSetupConfig(String configAsString) throws SetupException, FileException {
        JsonWrapper setupConfigJSON = new JsonWrapper(configAsString);

        // First thing first : save storage path
        String configStoragePath = (String) setupConfigJSON.getValueForPath("setup_storage");
        if (StringUtils.isBlank(configStoragePath)) {
            throw new SetupException ("config Storage path cannot be empty.");
        }

        File entryFile = new File(setupService.getStoragePathConfDir() + "/storagePath.conf");
        FileUtils.writeFile(entryFile, configStoragePath);

        File storagePath = new File(configStoragePath);
        if (!storagePath.exists()) {

            storagePath.mkdirs();

            if (!storagePath.exists()) {
                throw new SetupException("Path \"" + configStoragePath + "\" doesn't exist and couldn't be created.");
            }
        }
        if (!storagePath.canWrite()) {
            String username = System.getProperty("user.name");
            throw new SetupException("User " + username + " cannot write in path " + storagePath + " doesn't exist.");
        }
        return setupConfigJSON;
    }

    public String loadSetupConfig() throws FileException, SetupException {
        File configFile = new File(setupService.getConfigStoragePath() + "/config.json");
        if (!configFile.exists()) {
            throw new SetupException ("Application is not initialized properly. Missing file 'config.conf' system configuration");
        }

        return FileUtils.readFile(configFile);
    }

    public MarathonServicesConfigWrapper loadMarathonServicesConfig() throws SystemException, SetupException {
        marathonServicesFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            File marathonServicesConfigFile = new File(configStoragePath + "/marathon-services-config.json");
            if (!marathonServicesConfigFile.exists()) {
                return null;
            }

            return new MarathonServicesConfigWrapper(FileUtils.readFile(marathonServicesConfigFile));
        } catch (JSONException | FileException e) {
            logger.error (e, e);
            throw new SystemException(e);
        } finally {
            marathonServicesFileLock.unlock();
        }
    }

    public void saveMarathonServicesConfig(MarathonServicesConfigWrapper marathonServicesConfig) throws FileException, SetupException {
        marathonServicesFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            FileUtils.writeFile(new File(configStoragePath + "/marathon-services-config.json"), marathonServicesConfig.getFormattedValue());
        } finally {
            marathonServicesFileLock.unlock();
        }
    }
}
