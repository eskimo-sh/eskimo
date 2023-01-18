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

package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.model.service.UIConfig;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.utils.ReturnStatusHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.Arrays;
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
        return ReturnStatusHelper.createOKStatus(map ->
                map.put("services", new JSONArray(servicesDefinition.listServicesInOrder())));
    }

    @GetMapping("/list-ui-services")
    @ResponseBody
    public String listUIServices() {
        return ReturnStatusHelper.createOKStatus(map ->
                map.put("uiServices", new JSONArray(servicesDefinition.listUIServices())));
    }

    @GetMapping("/get-ui-services-config")
    @ResponseBody
    public String getUIServicesConfig() {
        return ReturnStatusHelper.createOKStatus(map -> {
            Map<String, Object> uiServicesConfig = new HashMap<>();
            Map<String, UIConfig> uiConfigs = servicesDefinition.getUIServicesConfig();

            uiConfigs.keySet()
                    .forEach(name -> uiServicesConfig.put (name, uiConfigs.get(name).toJSON()));

            map.put("uiServicesConfig", new JSONObject(uiServicesConfig));
        });
    }

    @GetMapping("/get-ui-services-status-config")
    @ResponseBody
    public String getUIServicesStatusConfig() {
        return ReturnStatusHelper.createOKStatus(map -> {
            Map<String, Object> uiServicesStatusConfig = new HashMap<>();

            Arrays.stream(servicesDefinition.listAllServices())
                    .map(name -> servicesDefinition.getService(name))
                    .forEach(service -> uiServicesStatusConfig.put (service.getName(), service.toUiStatusConfigJSON()));

            map.put("uiServicesStatusConfig", new JSONObject(uiServicesStatusConfig));
        });
    }

    @GetMapping("/get-services-dependencies")
    @ResponseBody
    public String getServicesDependencies() {
        return ReturnStatusHelper.createOKStatus(map -> {
            Map<String, Object> servicesDependencies = new HashMap<>();

            Arrays.stream(servicesDefinition.listAllServices())
                    .map(name -> servicesDefinition.getService(name))
                    .forEach(service -> servicesDependencies.put (service.getName(), service.toDependenciesJSON()));

            map.put("servicesDependencies", new JSONObject(servicesDependencies));
        });
    }

    @GetMapping("/get-services-config")
    @ResponseBody
    public String getServicesConfigurations() {
        return ReturnStatusHelper.createOKStatus(map -> {
            Map<String, Object> servicesConfigurations = new HashMap<>();

            Arrays.stream(servicesDefinition.listAllServices())
                    .map(name -> servicesDefinition.getService(name))
                    .filter(service -> !service.isKubernetes())
                    .forEach(service -> servicesConfigurations.put (service.getName(), service.toConfigJSON()));

            map.put("servicesConfigurations", new JSONObject(servicesConfigurations));
        });
    }

    @GetMapping("/list-config-services")
    @ResponseBody
    public String listConfigServices() {
        return ReturnStatusHelper.createOKStatus(map -> {

            Map<String, Object> servicesConfigurations = new HashMap<>();

            Arrays.stream(servicesDefinition.listAllServices())
                    .map(name -> servicesDefinition.getService(name))
                    .forEach(service -> servicesConfigurations.put (service.getName(), service.toConfigJSON()));

            map.put("uniqueServices", new JSONArray(servicesDefinition.listUniqueServices()));
            map.put("multipleServices", new JSONArray(servicesDefinition.listMultipleServicesNonKubernetes()));
            map.put("mandatoryServices", new JSONArray(servicesDefinition.listMandatoryServices()));
            map.put("servicesConfigurations", new JSONObject(servicesConfigurations));
        });
    }

    @GetMapping("/get-kubernetes-services")
    @ResponseBody
    public String getKubernetesServices() {
        return ReturnStatusHelper.createOKStatus(map -> {

            Map<String, Object> kubeServicesConfig = new HashMap<>();

            Arrays.stream(servicesDefinition.listAllServices())
                    .map(name -> servicesDefinition.getService(name))
                    .filter(Service::isKubernetes)
                    .forEach(service -> kubeServicesConfig.put (service.getName(), service.toConfigJSON()));

            map.put("kubernetesServices", new JSONArray(servicesDefinition.listKubernetesServices()));
            map.put("kubernetesServicesConfigurations", new JSONObject(kubeServicesConfig));
        });
    }

}