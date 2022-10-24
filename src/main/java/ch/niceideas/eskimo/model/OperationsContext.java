package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.services.satellite.ServicesInstallationSorter;

public interface OperationsContext {

    ServicesInstallationSorter getServicesInstallationSorter();

    NodesConfigWrapper getNodesConfig() throws NodesConfigurationException;
}
