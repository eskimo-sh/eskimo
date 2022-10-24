package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.ServicesInstallationSorter;
import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

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
