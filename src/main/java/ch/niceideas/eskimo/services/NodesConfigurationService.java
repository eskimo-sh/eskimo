package ch.niceideas.eskimo.services;

import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.MemoryModel;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;

import java.io.IOException;


public interface NodesConfigurationService {

    void applyNodesConfig(ServiceOperationsCommand command) throws NodesConfigurationException;

    void installTopologyAndSettings(
            NodesConfigWrapper nodesConfig,
            KubernetesServicesConfigWrapper kubeServicesConfig,
            ServicesInstallStatusWrapper servicesInstallStatus,
            MemoryModel memoryModel,
            String node)
            throws SystemException, SSHCommandException, IOException;

    void restartServiceForSystem(SimpleOperationCommand.SimpleOperationId operationId) throws SystemException;
}
