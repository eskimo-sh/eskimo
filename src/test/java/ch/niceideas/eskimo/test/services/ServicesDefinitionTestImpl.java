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


package ch.niceideas.eskimo.test.services;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.Topology;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.model.service.UIConfig;
import ch.niceideas.eskimo.services.ServiceDefinitionException;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.ServicesDefinitionImpl;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import org.json.JSONException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("test-services")
public class ServicesDefinitionTestImpl extends ServicesDefinitionImpl implements ServicesDefinition {

    private boolean error = false;

    protected String getServicesDefinitionFile() {
        return "classpath:services-test.json";
    }

    public void reset () {
        error = false;
    }

    public void setError() {
        error = true;
    }

    protected String getKubeMasterServuce() {
        return "cluster-master";
    }

    @Override
    public void executeInEnvironmentLock(EnvironmentOperation operation) throws FileException, ServiceDefinitionException, SetupException {
        if (error) {
            throw new ServiceDefinitionException("Test error");
        }
        super.executeInEnvironmentLock(operation);
    }

    @Override
    public String getAllServicesString() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.getAllServicesString();
    }

    @Override
    public Topology getTopology(NodesConfigWrapper nodesConfig, KubernetesServicesConfigWrapper kubeServicesConfig, String currentNode) throws ServiceDefinitionException, NodesConfigurationException {
        if (error) {
            throw new ServiceDefinitionException("Test error");
        }
        return super.getTopology(nodesConfig, kubeServicesConfig, currentNode);
    }

    @Override
    public Service getService(String serviceName) {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.getService(serviceName);
    }

    @Override
    public String[] listAllServices() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.listAllServices();
    }

    @Override
    public String[] listAllNodesServices() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.listAllNodesServices();
    }

    @Override
    public String[] listMultipleServicesNonKubernetes() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.listMultipleServicesNonKubernetes();
    }

    @Override
    public String[] listMultipleServices() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.listMultipleServices();
    }

    @Override
    public String[] listMandatoryServices() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.listMandatoryServices();
    }

    @Override
    public String[] listUniqueServices() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.listUniqueServices();
    }

    @Override
    public String[] listKubernetesServices() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.listKubernetesServices();
    }

    @Override
    public String[] listProxiedServices() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.listProxiedServices();
    }

    @Override
    public String[] listUIServices() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.listUIServices();
    }

    @Override
    public Map<String, UIConfig> getUIServicesConfig() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.getUIServicesConfig();
    }

    @Override
    public String[] listServicesInOrder() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.listServicesInOrder();
    }

    @Override
    public String[] listServicesOrderedByDependencies() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.listServicesOrderedByDependencies();
    }

    @Override
    public String[] listKubernetesServicesOrderedByDependencies() {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.listKubernetesServicesOrderedByDependencies();
    }

    @Override
    public int compareServices(Service one, Service other) {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.compareServices(one, other);
    }

    @Override
    public int compareServices(String servOne, String servOther) {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.compareServices(servOne, servOther);
    }

    @Override
    public Collection<String> getDependentServices(String service) {
        if (error) {
            throw new JSONException("Test error");
        }
        return super.getDependentServices(service);
    }
}
