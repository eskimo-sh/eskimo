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

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.OperationsCommand;
import ch.niceideas.eskimo.model.ServicesConfigWrapper;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ServicesConfigService {

    private static final Logger logger = Logger.getLogger(ServicesConfigService.class);
    public static final String SERVICES_CONFIG_JSON_FILE = "/services-config.json";

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private SetupService setupService;

    @Autowired
    private SystemService systemService;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    /* For tests */
    void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setSetupService(SetupService setupService) {
        this.setupService = setupService;
    }
    void setSystemService(SystemService systemService) {
        this.systemService = systemService;
    }
    void setNodeRangeResolver(NodeRangeResolver nodeRangeResolver) {
        this.nodeRangeResolver = nodeRangeResolver;
    }

    private ReentrantLock servicesConfigFileLock = new ReentrantLock();


    public void saveServicesConfig(ServicesConfigWrapper status) throws FileException, SetupException {
        servicesConfigFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            FileUtils.writeFile(new File(configStoragePath + SERVICES_CONFIG_JSON_FILE), status.getFormattedValue());
        } finally {
            servicesConfigFileLock.unlock();
        }
    }

    public ServicesConfigWrapper loadServicesConfig() throws FileException, SetupException {
        servicesConfigFileLock.lock();
        try {
            String configStoragePath = setupService.getConfigStoragePath();
            File statusFile = new File(configStoragePath + SERVICES_CONFIG_JSON_FILE);
            if (!statusFile.exists()) {
                return ServicesConfigWrapper.initEmpty(servicesDefinition);
            }

            return new ServicesConfigWrapper(statusFile);
        } finally {
            servicesConfigFileLock.unlock();
        }
    }

    public ServicesConfigWrapper loadServicesConfigNoLock() throws FileException, SetupException {
        String configStoragePath = setupService.getConfigStoragePath();
        File statusFile = new File(configStoragePath + SERVICES_CONFIG_JSON_FILE);
        if (!statusFile.exists()) {
            return ServicesConfigWrapper.initEmpty(servicesDefinition);
        }

        return new ServicesConfigWrapper(statusFile);
    }

    public void saveAndApplyServicesConfig(String configFormAsString)  throws FileException, SetupException, SystemException  {

        servicesConfigFileLock.lock();
        try {


            // 1. load saved config (or initialized one)
            ServicesConfigWrapper servicesConfig = loadServicesConfig();

            //System.out.println (servicesConfig.getFormattedValue());
            String[] dirtyServices = fillInEditedConfigs(new JSONObject(configFormAsString), servicesConfig.getSubJSONArray("configs"));

            //System.out.println (servicesConfig.getFormattedValue());

            saveServicesConfig (servicesConfig);


            if (dirtyServices != null && dirtyServices.length > 0) {
                NodesConfigWrapper nodesConfig = systemService.loadNodesConfig();

                OperationsCommand restartCommand = OperationsCommand.createForRestartsOnly(
                        servicesDefinition,
                        nodeRangeResolver,
                        dirtyServices,
                        nodesConfig);

                systemService.applyNodesConfig(restartCommand);
            }

        } catch (SystemException | NodesConfigurationException | ServiceDefinitionException e) {
            logger.error (e, e);
            throw new SystemException(e);

        } finally {
            servicesConfigFileLock.unlock();
        }
    }

    String[] fillInEditedConfigs(JSONObject configForm, JSONArray configArrayForService) {

        Set<String> dirtyServices = new HashSet<>();

        // apply changes, proceed service by service
        for (String service : servicesDefinition.getAllServices()) {

            // get all properties for service
            for (String configKey : configForm.keySet()) {

                if (configKey.startsWith(service)) {

                    String value = configForm.getString(configKey);

                    String propertyKey = configKey.substring(service.length() + 1).replace("-", ".");

                    // now iterate through saved (existing) configs and update values
                    main:
                    for (int i = 0; i < configArrayForService.length(); i++) {
                        JSONObject object = configArrayForService.getJSONObject(i);
                        String serviceName = object.getString("name");
                        if (serviceName.equals(service)) {

                            // iterate through all editableConfiguration
                            JSONArray editableConfigurations = object.getJSONArray("configs");
                            for (int j = 0; j < editableConfigurations.length(); j++) {
                                JSONObject editableConfiguration = editableConfigurations.getJSONObject(j);

                                JSONArray properties = editableConfiguration.getJSONArray("properties");
                                for (int k = 0; k < properties.length(); k++) {

                                    JSONObject property = properties.getJSONObject(k);

                                    String propertyName = property.getString("name");
                                    if (propertyName.equals(propertyKey)) {

                                        String defaultValue = property.getString("defaultValue");
                                        String previousValue = property.has("value") ? property.getString("value") : null;

                                        // Handle service dirtyness
                                        if (   (StringUtils.isBlank(previousValue) && StringUtils.isNotBlank(value))
                                            || (StringUtils.isNotBlank(previousValue) && StringUtils.isBlank(value))
                                            || (StringUtils.isNotBlank(previousValue) && StringUtils.isNotBlank(value) && !previousValue.equals(value)
                                                )) {
                                            dirtyServices.add(serviceName);
                                        }

                                        // Handle value saving
                                        if (StringUtils.isBlank(value) || value.equals(defaultValue)) {
                                            property.remove("value");
                                        } else {
                                            property.put("value", value);
                                        }
                                        break main;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return dirtyServices.toArray(new String[0]);
    }
}
