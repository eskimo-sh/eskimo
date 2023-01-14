/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */


package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.services.satellite.ServicesInstallationSorter;
import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Profile("!test-operations")
public class OperationsMonitoringServiceImpl implements OperationsContext, OperationsMonitoringService {

    public static final String NO_OPERATION_GROUP_ERROR_MESSAGE = "Need to start an Operations group first.";
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

    @Override
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
                Pair<Integer, String> newLines = globalMessages.fetchElements(lastLinePerOp.computeIfAbsent("global", op -> 0));
                put("lastLine", newLines.getKey());
                put("lines", Base64.getEncoder().encodeToString(newLines.getValue().getBytes()));
            }}));

            put("messages", new JSONObject(new HashMap<>() {{
                    for (OperationId opId : operationLogs.keySet()) {
                        MessagingManager mgr = operationLogs.computeIfAbsent(opId, op -> {
                            throw new IllegalStateException();
                        });

                        Pair<Integer, String> newLines = mgr.fetchElements(lastLinePerOp.computeIfAbsent(opId.toString(), op -> 0));

                        put(opId.toString(), new JSONObject(new TreeMap<>() {{
                            put("lastLine", newLines.getKey());
                            put("lines", Base64.getEncoder().encodeToString(newLines.getValue().getBytes()));
                        }}));
                    }
                }}));

            put("status", new JSONObject(new HashMap<>() {{
                for (OperationId opId : operationLogs.keySet()) {
                    put(opId.toString(), operationStatus.computeIfAbsent(opId, op -> OperationStatus.INIT).toString());
                }
            }}));
        }}));
    }

    @Override
    public boolean isProcessingPending() {
        return systemActionLock.isLocked();
    }

    @Override
    public void startCommand(JSONOpCommand operation) throws ServiceDefinitionException, NodesConfigurationException, SystemException {
        if (currentOperation != null) {
            throw new IllegalStateException("Can't start an operation while another operation is in progress");
        }
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

    @Override
    public void endCommand(boolean success) {
        setLastOperationSuccess(success);
        systemActionLock.unlock();
        interruption.set(false);
        interruptionNotified.set(false);

        currentOperation = null;
    }

    @Override
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

    @Override
    public boolean isInterrupted () {
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

    @Override
    public boolean getLastOperationSuccess() {
        return lastOperationSuccess;
    }

    private void setLastOperationSuccess(boolean success) {
        lastOperationSuccess = success;
    }

    @Override
    public void addGlobalInfo (String message) {
        globalMessages.addLines(message);
    }

    // Individual operation monitoring
    @Override
    public void addInfo(OperationId operation, String message) {
        if (StringUtils.isNotBlank(message)) {
            if (!isProcessingPending()) {
                throw new IllegalStateException(NO_OPERATION_GROUP_ERROR_MESSAGE);
            }
            MessagingManager msgMgr = operationLogs.computeIfAbsent(operation, op -> {
                throw new IllegalStateException("No operation " + operation + " is know.");
            });
            msgMgr.addLines(message);
        }
    }

    @Override
    public void addInfo(OperationId operation, String[] messages) {
        if (messages != null && messages.length > 0) {
            if (!isProcessingPending()) {
                throw new IllegalStateException(NO_OPERATION_GROUP_ERROR_MESSAGE);
            }
            MessagingManager msgMgr = operationLogs.computeIfAbsent(operation, op -> {
                throw new IllegalStateException("No operation " + operation + " is know.");
            });
            msgMgr.addLines(messages);
        }
    }

    @Override
    public List<String> getNewMessages (OperationId operation, int lastLine) {
        MessagingManager msgMgr = operationLogs.get(operation);
        if (msgMgr == null) {
            return Collections.emptyList();
        }
        return msgMgr.getSubList(lastLine);
    }

    @Override
    public Pair<Integer, String> fetchNewMessages (OperationId operation, int lastLine) {
        MessagingManager msgMgr = operationLogs.get(operation);
        if (msgMgr == null) {
            return new Pair<>(0, "");
        }
        return msgMgr.fetchElements(lastLine);
    }

    @Override
    public void startOperation(OperationId operationId) {
        if (!isProcessingPending()) {
            throw new IllegalStateException(NO_OPERATION_GROUP_ERROR_MESSAGE);
        }
        operationStatus.put(operationId, OperationStatus.RUNNING);
    }

    @Override
    public void endOperationError(OperationId operationId) {
        if (!isProcessingPending()) {
            throw new IllegalStateException(NO_OPERATION_GROUP_ERROR_MESSAGE);
        }
        operationStatus.put(operationId, OperationStatus.ERROR);
    }

    @Override
    public void endOperation(OperationId operationId) {
        if (!isProcessingPending()) {
            throw new IllegalStateException(NO_OPERATION_GROUP_ERROR_MESSAGE);
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
