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


package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.services.NodesConfigurationService;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.ServicesSettingsService;
import ch.niceideas.eskimo.services.SetupException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SettingsOperationsCommand {

    private Map<String, Map<String, List<ChangedSettings>>> changedSettings = null;
    private List<String> restartedServices = null;
    private ServicesSettingsWrapper newSettings;

    public static SettingsOperationsCommand create (
            String settingsFormAsString, ServicesSettingsService servicesSettingsService)
            throws FileException, SetupException {

        Map<String, Map<String, List<ChangedSettings>>> changedSettings = new HashMap<>();
        List<String> restartedServices = new ArrayList<>();

        ServicesSettingsWrapper newSettings = servicesSettingsService.prepareSaveSettings(settingsFormAsString, changedSettings, restartedServices);

        SettingsOperationsCommand retCommand = new SettingsOperationsCommand();
        retCommand.setChangedSettings(changedSettings);
        retCommand.setRestartedServices(restartedServices);
        retCommand.setNewSettings(newSettings);

        return retCommand;
    }

    public ServicesSettingsWrapper getNewSettings() {
        return newSettings;
    }

    void setNewSettings(ServicesSettingsWrapper newSettings) {
        this.newSettings = newSettings;
    }

    public Map<String, Map<String, List<ChangedSettings>>> getChangedSettings() {
        return changedSettings;
    }

    public void setChangedSettings(Map<String, Map<String, List<ChangedSettings>>> changedSettings) {
        this.changedSettings = changedSettings;
    }

    public List<String> getRestartedServices() {
        return restartedServices;
    }

    void setRestartedServices(List<String> restartedServices) {
        this.restartedServices = restartedServices;
    }

    public JSONObject toJSON() {

        Map<String, Object> retAsMap = new HashMap<>();

        // 1. settings changes
        Map<String, Object> settingsMap = new HashMap<>();

        for (Map.Entry<String, Map<String, List<ChangedSettings>>> entry : changedSettings.entrySet()) {

            String service = entry.getKey();
            Map<String, List<ChangedSettings>> changedSettingsForService = entry.getValue();

            Map<String, Object> fileSettingsMap = new HashMap<>();

            for (Map.Entry<String, List<ChangedSettings>> fileEntry : changedSettingsForService.entrySet())  {

                String filename = fileEntry.getKey();
                JSONArray settingsArray = new JSONArray(fileEntry.getValue().stream()
                    .map(settings -> settings.toJSON())
                    .collect(Collectors.toList())
                );

                fileSettingsMap.put (filename, settingsArray);
            }

            settingsMap.put (service, fileSettingsMap);
        }

        retAsMap.put("settings", new JSONObject(settingsMap));

        // 2. restarts
        retAsMap.put("restarts", new JSONArray(restartedServices));

        return new JSONObject(retAsMap);
    }

    public static class ChangedSettings implements Serializable {

        private final String service;
        private final String configFile;
        private final String key;
        private final String value;
        private final String oldValue;

        public ChangedSettings(String service, String configFile, String key, String value, String oldValue) {
            this.service = service;
            this.configFile = configFile;
            this.key = key;
            this.value = value;
            this.oldValue = oldValue;
        }

        public String getService() {
            return service;
        }

        public String getConfigFile() {
            return configFile;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public String getOldValue() {
            return oldValue;
        }

        public JSONObject toJSON() {
            return new JSONObject(new HashMap<String, Object>(){{
                //put ("service", service);
                //put ("configFile", configFile);
                put ("key", key);
                put ("oldValue", StringUtils.isNotBlank(oldValue) ? oldValue : "");
                put ("value", StringUtils.isNotBlank(value) ? value : "");
            }});

        }
    }
}
