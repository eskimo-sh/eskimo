package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.services.ServicesInstallationSorter;

public interface OperationsContext {

    ServicesInstallationSorter getServicesInstallationSorter();

    NodesConfigWrapper getNodesConfig();
}
