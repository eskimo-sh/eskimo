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

    void installEskimoBaseSystem(MessageLogger ml, String node) throws SSHCommandException;

    String getNodeFlavour(SSHConnection connection) throws SSHCommandException, SystemException;

    void copyCommand (String source, String target, SSHConnection connection) throws SSHCommandException;

    void uninstallService(ServiceOperationsCommand.ServiceOperationId operationId) throws SystemException;

    void installService(ServiceOperationsCommand.ServiceOperationId operationId) throws SystemException;
}
