package ch.niceideas.eskimo.test.services;

import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.MemoryModel;
import ch.niceideas.eskimo.services.NodesConfigurationService;
import ch.niceideas.eskimo.services.SSHCommandException;
import ch.niceideas.eskimo.services.SystemException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("test-nodes-conf")
public class NodesConfigurationServiceTestImpl implements NodesConfigurationService {

    private ServiceOperationsCommand appliedCommand = null;

    public ServiceOperationsCommand getAppliedCommand() {
        return appliedCommand;
    }

    public void reset () {
        this.appliedCommand = null;
    }

    @Override
    public void applyNodesConfig(ServiceOperationsCommand command) {
        this.appliedCommand = command;
    }

    @Override
    public void installTopologyAndSettings(NodesConfigWrapper nodesConfig, KubernetesServicesConfigWrapper kubeServicesConfig, ServicesInstallStatusWrapper servicesInstallStatus, MemoryModel memoryModel, String node) throws SystemException, SSHCommandException, IOException {
        // No-Op
    }

    @Override
    public void restartServiceForSystem(SimpleOperationCommand.SimpleOperationId operationId) {
        // No-Op
    }

    @Override
    public void installEskimoBaseSystem(MessageLogger ml, String node) {

    }

    @Override
    public String getNodeFlavour(SSHConnection connection) {
        return "debian";
    }

    @Override
    public void copyCommand (String source, String target, SSHConnection connection) {

    }

    @Override
    public void uninstallService(ServiceOperationsCommand.ServiceOperationId operationId) {

    }

    @Override
    public void installService(ServiceOperationsCommand.ServiceOperationId operationId) {

    }
}
