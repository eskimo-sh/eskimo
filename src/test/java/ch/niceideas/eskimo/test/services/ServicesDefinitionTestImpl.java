package ch.niceideas.eskimo.test.services;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.Topology;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.model.service.UIConfig;
import ch.niceideas.eskimo.services.*;
import org.json.JSONException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
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
        return getAllServicesString();
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
        return getDependentServices(service);
    }
}
