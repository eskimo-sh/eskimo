package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;

import java.util.*;

public interface OperationsMonitoringService extends OperationsContext {

    OperationsMonitoringStatusWrapper getOperationsMonitoringStatus (Map<String, Integer> lastLinePerOp);

    boolean isProcessingPending();

    void operationsStarted(JSONOpCommand operation) throws ServiceDefinitionException, NodesConfigurationException, SystemException;

    void operationsFinished(boolean success);

    void interruptProcessing();

    boolean getLastOperationSuccess();

    void addGlobalInfo (String message);

    void addInfo(OperationId operation, String message);

    void addInfo(OperationId operation, String[] messages);

    List<String> getNewMessages (OperationId operation, int lastLine);

    Pair<Integer, String> fetchNewMessages (OperationId operation, int lastLine);

    void startOperation(OperationId operationId);

    void endOperationError(OperationId operationId);

    void endOperation(OperationId operationId);

    boolean isInterrupted ();

    NodesConfigWrapper getNodesConfig() throws NodesConfigurationException;
}
