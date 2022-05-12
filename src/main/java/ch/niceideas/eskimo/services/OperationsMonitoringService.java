package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
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

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class OperationsMonitoringService implements OperationsContext {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    @Getter
    private ServicesInstallationSorter servicesInstallationSorter;

    private final ReentrantLock systemActionLock = new ReentrantLock();
    private final AtomicBoolean interruption = new AtomicBoolean(false);
    private final AtomicBoolean interruptionNotified = new AtomicBoolean(false);
    private boolean lastOperationSuccess;

    private List<? extends OperationId> operationList = null;
    private final MessagingManager globalMessages = new MessagingManager();
    private final Map<OperationId, MessagingManager> operationLogs = new ConcurrentHashMap<>();
    private final Map<OperationId, OperationStatus> operationStatus = new ConcurrentHashMap<>();
    private JSONOpCommand currentOperation = null;

    /* For tests */
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    void setNodeRangeResolver (NodeRangeResolver nodeRangeResolver) {
        this.nodeRangeResolver = nodeRangeResolver;
    }
    void setServicesInstallationSorter (ServicesInstallationSorter servicesInstallationSorter) {
        this.servicesInstallationSorter = servicesInstallationSorter;
    }

    public OperationsMonitoringStatusWrapper getOperationsMonitoringStatus (Map<String, Integer> lastLinePerOp) {

        if (operationList == null) {
            return new OperationsMonitoringStatusWrapper("{\"status\": \"none\"}");
        }

        return new OperationsMonitoringStatusWrapper(new JSONObject(new HashMap<String, Object>() {{

            put ("result", "OK");

            put("labels", new JSONArray(new ArrayList<>() {{
                for (OperationId opId : operationList) {
                    add(new JSONObject(new HashMap<>(){{
                        put ("operation", opId.toString());
                        put ("label", opId.getMessage());
                    }}));
                }
            }}));

            put ("globalMessages", new JSONObject(new HashMap<>() {{
                Pair<Integer, String> newLines = globalMessages.fetchElements(lastLinePerOp.computeIfAbsent("global", (op) -> 0));
                put("lastLine", newLines.getKey());
                put("lines", Base64.getEncoder().encodeToString(newLines.getValue().getBytes()));
            }}));

            put("messages", new JSONObject(new HashMap<>() {{
                    for (OperationId opId : operationLogs.keySet()) {
                        MessagingManager mgr = operationLogs.computeIfAbsent(opId, (op) -> {
                            throw new IllegalStateException();
                        });

                        Pair<Integer, String> newLines = mgr.fetchElements(lastLinePerOp.computeIfAbsent(opId.toString(), (op) -> 0));

                        put(opId.toString(), new JSONObject(new TreeMap<>() {{
                            put("lastLine", newLines.getKey());
                            put("lines", Base64.getEncoder().encodeToString(newLines.getValue().getBytes()));
                        }}));
                    }
                }}));

            put("status", new JSONObject(new HashMap<>() {{
                for (OperationId opId : operationLogs.keySet()) {
                    put(opId.toString(), operationStatus.computeIfAbsent(opId, (op) -> OperationStatus.INIT).toString());
                }
            }}));
        }}));
    }

    public boolean isProcessingPending() {
        return systemActionLock.isLocked();
    }

    void operationsStarted(JSONOpCommand operation) throws ServiceDefinitionException, NodesConfigurationException, SystemException {
        currentOperation = operation;
        systemActionLock.lock();

        globalMessages.clear();
        operationLogs.clear();
        operationStatus.clear();

        operationList = operation.getAllOperationsInOrder(this);
        operationList.forEach(
                operationId -> {
                    operationLogs.computeIfAbsent(operationId, opId -> new MessagingManager());
                    operationStatus.computeIfAbsent(operationId, opId -> OperationStatus.INIT);
                });

    }

    void operationsFinished(boolean success) {
        setLastOperationSuccess(success);
        systemActionLock.unlock();
        interruption.set(false);
        interruptionNotified.set(false);

        currentOperation = null;
    }

    public void interruptProcessing() {
        if (isProcessingPending()) {
            interruption.set(true);

            operationList.forEach(operationId -> {
                if (operationStatus.get(operationId) == null ||
                        operationStatus.get(operationId) == OperationStatus.INIT) {
                    operationStatus.put (operationId, OperationStatus.CANCELLED);
                }
            });
        }
    }

    boolean isInterrupted () {
        notifyInterruption();
        return interruption.get();
    }

    void notifyInterruption() {
        if (interruption.get() && !interruptionNotified.get()) {
            notificationService.addError("Processing has been interrupted");
            //messagingService.addLine("Processing has been interrupted");
            interruptionNotified.set(true);
        }
    }

    public boolean getLastOperationSuccess() {
        return lastOperationSuccess;
    }

    private void setLastOperationSuccess(boolean success) {
        lastOperationSuccess = success;
    }

    public void addGlobalInfo (String message) {
        globalMessages.addLines(message);
    }

    // Individual operation monitoring
    public void addInfo(OperationId operation, String message) {
        if (StringUtils.isNotBlank(message)) {
            if (!isProcessingPending()) {
                throw new IllegalStateException("Need to start an Operations group first.");
            }
            MessagingManager msgMgr = operationLogs.computeIfAbsent(operation, (op) -> {
                throw new IllegalStateException();
            });
            msgMgr.addLines(message);
        }
    }

    public void addInfo(OperationId operation, String[] messages) {
        if (messages != null && messages.length > 0) {
            if (!isProcessingPending()) {
                throw new IllegalStateException("Need to start an Operations group first.");
            }
            MessagingManager msgMgr = operationLogs.computeIfAbsent(operation, (op) -> {
                throw new IllegalStateException();
            });
            msgMgr.addLines(messages);
        }
    }

    public List<String> getNewMessages (OperationId operation, int lastLine) {
        MessagingManager msgMgr = operationLogs.get(operation);
        if (msgMgr == null) {
            return Collections.emptyList();
        }
        return msgMgr.getSubList(lastLine);
    }

    public Pair<Integer, String> fetchNewMessages (OperationId operation, int lastLine) {
        MessagingManager msgMgr = operationLogs.get(operation);
        if (msgMgr == null) {
            return new Pair<>(0, "");
        }
        return msgMgr.fetchElements(lastLine);
    }

    public void startOperation(OperationId operationId) {
        if (!isProcessingPending()) {
            throw new IllegalStateException("Need to start an Operations group first.");
        }
        operationStatus.put(operationId, OperationStatus.RUNNING);
    }

    public void endOperationError(OperationId operationId) {
        if (!isProcessingPending()) {
            throw new IllegalStateException("Need to start an Operations group first.");
        }
        operationStatus.put(operationId, OperationStatus.ERROR);
    }

    public void endOperation(OperationId operationId) {
        if (!isProcessingPending()) {
            throw new IllegalStateException("Need to start an Operations group first.");
        }
        if (!operationStatus.get(operationId).equals(OperationStatus.ERROR)) {
            operationStatus.put(operationId, OperationStatus.COMPLETE);
        }
    }

    @Override
    public NodesConfigWrapper getNodesConfig() throws NodesConfigurationException {
        try {
            NodesConfigWrapper rawNodesConfig = configurationService.loadNodesConfig();
            return nodeRangeResolver.resolveRanges(rawNodesConfig);
        } catch (SystemException | SetupException e) {
            throw new NodesConfigurationException(e);
        }
    }
}
