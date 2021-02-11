package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.services.NodesConfigurationException;
import ch.niceideas.eskimo.services.ServicesInstallationSorter;

public interface OperationsContext {

    ServicesInstallationSorter getServicesInstallationSorter();

    NodesConfigWrapper getNodesConfig() throws NodesConfigurationException;
}
