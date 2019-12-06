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

package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.model.UIConfig;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.utils.ErrorStatusHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;


@Controller
public class ServicesController {

    @Resource
    private ServicesDefinition servicesDefinition;

    /** for tests only ! */
    public void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }

    @GetMapping("/list-services")
    @ResponseBody
    public String listServices() {
        try {
            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("services", new JSONArray(servicesDefinition.listServicesInOrder()));
            }}).toString(2);
        } catch (JSONException e) {
            return ErrorStatusHelper.createErrorStatus(e);
        }
    }

    @GetMapping("/list-ui-services")
    @ResponseBody
    public String listUIServices() {
        try {
            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("uiServices", new JSONArray(servicesDefinition.listUIServices()));
            }}).toString(2);
        } catch (JSONException e) {
            return ErrorStatusHelper.createErrorStatus(e);
        }
    }

    @GetMapping("/get-ui-services-config")
    @ResponseBody
    public String getUIServicesConfig() {
        try {
            Map<String, Object> uiServicesConfig = new HashMap<>();
            Map<String, UIConfig> uiConfigs = servicesDefinition.getUIServicesConfig();

            uiConfigs.keySet()
                    .forEach(name -> uiServicesConfig.put (name, uiConfigs.get(name).toJSON()));

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("uiServicesConfig", new JSONObject(uiServicesConfig));
            }}).toString(2);
        } catch (JSONException e) {
            return ErrorStatusHelper.createErrorStatus(e);
        }
    }

    @GetMapping("/get-ui-services-status-config")
    @ResponseBody
    public String getUIServicesStatusConfig() {

        Map<String, Object> uiServicesStatusConfig = new HashMap<>();
        servicesDefinition.getAllServices().stream()
                .map(name -> servicesDefinition.getService(name))
                .forEach(service -> uiServicesStatusConfig.put (service.getName(), service.toUiStatusConfigJSON()));

        try {
            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("uiServicesStatusConfig", new JSONObject(uiServicesStatusConfig));
            }}).toString(2);
        } catch (JSONException e) {
            return ErrorStatusHelper.createErrorStatus(e);
        }
    }

    @GetMapping("/get-services-dependencies")
    @ResponseBody
    public String getServicesDependencies() {

        Map<String, Object> servicesDependencies = new HashMap<>();

        try {
            servicesDefinition.getAllServices().stream()
                    .map(name -> servicesDefinition.getService(name))
                    .forEach(service -> servicesDependencies.put (service.getName(), service.toDependenciesJSON()));

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("servicesDependencies", new JSONObject(servicesDependencies));
            }}).toString(2);
        } catch (JSONException e) {
            return ErrorStatusHelper.createErrorStatus(e);
        }
    }

    @GetMapping("/get-services-config")
    @ResponseBody
    public String getServicesConfigurations() {

        Map<String, Object> servicesConfigurations = new HashMap<>();

        try {
            servicesDefinition.getAllServices().stream()
                    .map(name -> servicesDefinition.getService(name))
                    .forEach(service -> servicesConfigurations.put (service.getName(), service.toConfigJSON()));

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("servicesConfigurations", new JSONObject(servicesConfigurations));
            }}).toString(2);
        } catch (JSONException e) {
            return ErrorStatusHelper.createErrorStatus(e);
        }
    }

    @GetMapping("/list-config-services")
    @ResponseBody
    public String listConfigServices() {
        try {

            Map<String, Object> servicesConfigurations = new HashMap<>();

            servicesDefinition.getAllServices().stream()
                    .map(name -> servicesDefinition.getService(name))
                    .forEach(service -> servicesConfigurations.put (service.getName(), service.toConfigJSON()));

            return new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("uniqueServices", new JSONArray(servicesDefinition.listUniqueServices()));
                put("multipleServices", new JSONArray(servicesDefinition.listMultipleServices()));
                put("mandatoryServices", new JSONArray(servicesDefinition.listMandatoryServices()));
                put("servicesConfigurations", new JSONObject(servicesConfigurations));
            }}).toString(2);
        } catch (JSONException e) {
            return ErrorStatusHelper.createErrorStatus(e);
        }
    }

}