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


package ch.niceideas.eskimo.test.services;

import ch.niceideas.eskimo.model.SSHConnection;
import ch.niceideas.eskimo.services.SSHCommandService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("test-ssh")
public class SSHCommandServiceTestImpl implements SSHCommandService {

    private String returnResult = "";
    private StringBuilder executedCommands = new StringBuilder();
    private StringBuilder executedScpCommands = new StringBuilder();

    private ConnectionResultBuilder connectionResultBuilder = null;
    private NodeResultBuilder nodeResultBuilder = null;

    public interface ConnectionResultBuilder {
        String build (SSHConnection connection, String script);
    }

    public interface NodeResultBuilder {
        String build (String node, String script);
    }

    public String getExecutedCommands() {
        return executedCommands.toString();
    }

    public String getExecutedScpCommands() {
        return executedScpCommands.toString();
    }

    public void reset() {
        this.connectionResultBuilder = null;
        this.nodeResultBuilder = null;
        this.returnResult = "";
        this.executedCommands = new StringBuilder();
        this.executedScpCommands = new StringBuilder();
    }

    public void setResult(String returnResult) {
        this.returnResult = returnResult;
    }

    public void setConnectionResultBuilder(ConnectionResultBuilder connectionResultBuilder) {
        this.connectionResultBuilder = connectionResultBuilder;
    }

    public void setNodeResultBuilder(NodeResultBuilder nodeResultBuilder) {
        this.nodeResultBuilder = nodeResultBuilder;
    }

    @Override
    public String runSSHScript(SSHConnection connection, String script) {
        executedCommands.append(script);
        executedCommands.append("\n");
        if (connectionResultBuilder != null) {
            return connectionResultBuilder.build(connection, script);
        }
        return returnResult;
    }

    @Override
    public String runSSHScript(String node, String script) {
        executedCommands.append(script);
        executedCommands.append("\n");
        if (nodeResultBuilder != null) {
            return nodeResultBuilder.build(node, script);
        }
        return returnResult;
    }

    @Override
    public String runSSHCommand(SSHConnection connection, String[] command) {
        executedCommands.append(String.join(" ", command));
        executedCommands.append("\n");
        if (connectionResultBuilder != null) {
            return connectionResultBuilder.build(connection, String.join(",", command));
        }
        return returnResult;
    }

    @Override
    public String runSSHScriptPath(SSHConnection connection, String scriptName) {
        executedCommands.append(scriptName);
        executedCommands.append("\n");
        if (connectionResultBuilder != null) {
            return connectionResultBuilder.build(connection, scriptName);
        }
        return returnResult;
    }

    @Override
    public String runSSHScriptPath(String node, String scriptName) {
        executedCommands.append(scriptName);
        executedCommands.append("\n");
        if (nodeResultBuilder != null) {
            return nodeResultBuilder.build(node, scriptName);
        }
        return returnResult;
    }

    @Override
    public String runSSHScript(String node, String script, boolean throwsException) {
        executedCommands.append(script);
        executedCommands.append("\n");
        if (nodeResultBuilder != null) {
            return nodeResultBuilder.build(node, script);
        }
        return returnResult;
    }

    @Override
    public String runSSHScript(SSHConnection connection, String script, boolean throwsException) {
        executedCommands.append(script);
        executedCommands.append("\n");
        if (connectionResultBuilder != null) {
            return connectionResultBuilder.build(connection, script);
        }
        return returnResult;
    }

    @Override
    public String runSSHCommand(String node, String command) {
        executedCommands.append(command);
        executedCommands.append("\n");
        if (nodeResultBuilder != null) {
            return nodeResultBuilder.build(node, command);
        }
        return returnResult;
    }

    @Override
    public String runSSHCommand(SSHConnection connection, String command) {
        executedCommands.append(command);
        executedCommands.append("\n");
        if (connectionResultBuilder != null) {
            return connectionResultBuilder.build(connection, command);
        }
        return returnResult;
    }

    @Override
    public void copySCPFile(String node, String filePath) {
        this.executedScpCommands.append(node).append(":").append(filePath).append("\n");
    }

    @Override
    public void copySCPFile(SSHConnection connection, String filePath) {
        this.executedScpCommands.append(connection != null ? connection.getHostname() : "null").append(":").append(filePath).append("\n");
    }

}
