/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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


package ch.niceideas.eskimo.test.services;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.services.OperationsMonitoringService;
import ch.niceideas.eskimo.services.ServiceDefinitionException;
import ch.niceideas.eskimo.services.SystemException;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.services.satellite.ServicesInstallationSorter;
import org.json.JSONException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("test-operations")
public class OperationsMonitoringServiceTestImpl implements OperationsMonitoringService {

    private boolean interruptProcessingError = false;

    private JSONOpCommand currentOperation = null;
    private boolean lastOperationSuccess = false;
    private boolean lastOperationSuccessError = false;

    private StringBuilder messagesCollector = new StringBuilder();

    private MessagingManager messagingManager = new MessagingManager();

    public void reset() {
        this.interruptProcessingError = false;
        this.currentOperation = null;
        this.lastOperationSuccess = false;
        this.lastOperationSuccessError = false;
        this.messagesCollector = new StringBuilder();
        this.messagingManager = new MessagingManager();
    }

    public String getAllMessages() {
        return messagesCollector.toString();
    }

    public void setLastOperationSuccessError() {
        this.lastOperationSuccessError = true;
    }

    public void setInterruptProcessingError() {
        this.interruptProcessingError = true;
    }

    @Override
    public ServicesInstallationSorter getServicesInstallationSorter() {
        return null;
    }

    @Override
    public OperationsMonitoringStatusWrapper getOperationsMonitoringStatus(Map<String, Integer> lastLinePerOp) {
        return null;
    }

    @Override
    public boolean isProcessingPending() {
        return currentOperation != null;
    }

    @Override
    public void startCommand(JSONOpCommand operation) throws ServiceDefinitionException, NodesConfigurationException, SystemException {
        if (currentOperation != null) {
            throw new IllegalStateException("Can't start an operation while another operation is in progress");
        }
        this.currentOperation = operation;
    }

    @Override
    public void endCommand(boolean success) {
        this.lastOperationSuccess = success;
        this.currentOperation = null;
    }

    @Override
    public void interruptProcessing() {
        if (interruptProcessingError) {
            throw new JSONException("Test Error");
        }
    }

    @Override
    public boolean getLastOperationSuccess() {
        if (lastOperationSuccessError) {
            throw new IllegalStateException("Test Error");
        }
        return lastOperationSuccess;
    }

    @Override
    public void addGlobalInfo(String message) {
        messagesCollector.append(message).append("\n");
    }

    @Override
    public void addInfo(OperationId<?> operation, String message) {
        messagesCollector.append(operation).append(" : ").append(message).append("\n");
        messagingManager.addLine(message);
    }

    @Override
    public void addInfo(OperationId<?> operation, String[] messages) {
        Arrays.stream(messages).forEach(
                message -> messagesCollector.append(operation).append(" : ").append(message).append("\n")
        );
        messagingManager.addLines(messages);
    }

    @Override
    public List<String> getNewMessages(OperationId<?> operation, int lastLine) {
        return messagingManager.getSubList(lastLine);
    }

    @Override
    public Pair<Integer, String> fetchNewMessages(OperationId<?> operation, int lastLine) {
        return messagingManager.fetchElements(lastLine);
    }

    @Override
    public void startOperation(OperationId<?> operationId) {

    }

    @Override
    public void endOperationError(OperationId<?> operationId) {

    }

    @Override
    public void endOperation(OperationId<?> operationId) {

    }

    @Override
    public boolean isInterrupted() {
        return false;
    }

    @Override
    public NodesConfigWrapper getNodesConfig() {
        return null;
    }
}
