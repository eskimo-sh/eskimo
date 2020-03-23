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

import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.SerializablePair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.services.*;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MarathonOperationsCommand implements Serializable {

    private static final Logger logger = Logger.getLogger(MarathonOperationsCommand.class);

    private final MarathonServicesConfigWrapper rawMarathonServicesConfig;

    private ArrayList<String> installations = new ArrayList<>();
    private ArrayList<String> uninstallations = new ArrayList<>();

    // TODO marathon dependent services not supported for now
    //private ArrayList<String> restarts = new ArrayList<>();

    public static MarathonOperationsCommand create (
            ServicesDefinition servicesDefinition,
            ServicesInstallStatusWrapper servicesInstallStatus,
            MarathonServicesConfigWrapper rawMarathonServicesConfig) throws MarathonServicesConfigurationException {

        MarathonOperationsCommand retCommand = new MarathonOperationsCommand(rawMarathonServicesConfig);

        // 1. Find out about services that need to be installed
        for (String service : servicesDefinition.listMarathonServices()) {
            if (rawMarathonServicesConfig.getValueForPath(service + "_install").equals("on")
                    && !servicesInstallStatus.isServiceInstalled(service)) {

                retCommand.addInstallation(service);

            }
        }

        // 2. Find out about services that need to be uninstalled
        for (String installation : servicesInstallStatus.getRootKeys()) {

            String installedService = installation.substring(0, installation.indexOf(OperationsCommand.INSTALLED_ON_IP_FLAG));

            // search it in config
            if (!StringUtils.isBlank ((String)rawMarathonServicesConfig.getValueForPath(installedService + "_install"))
                    &&!rawMarathonServicesConfig.getValueForPath(installedService + "_install").equals("on") ) {
                retCommand.addUninstallation(installedService);
            }
        }

        // 3. If a services changed, dependent services need to be restarted
        // TODO Find ouy if this needs to be supported

        return retCommand;
    }


    MarathonOperationsCommand(MarathonServicesConfigWrapper rawMarathonServicesConfig) {
        this.rawMarathonServicesConfig = rawMarathonServicesConfig;
    }

    public MarathonServicesConfigWrapper getRawConfig() {
        return rawMarathonServicesConfig;
    }

    void addInstallation(String service) {
        installations.add(service);
    }

    void addUninstallation(String service) {
        uninstallations.add(service);
    }

    public List<String> getInstallations() {
        return installations;
    }

    public List<String> getUninstallations() {
        return uninstallations;
    }

    public JSONObject toJSON () {
        return new JSONObject(new HashMap<String, Object>() {{
            put("installations", new JSONArray(installations));
            put("uninstallations", new JSONArray(uninstallations));
        }});
    }
}
