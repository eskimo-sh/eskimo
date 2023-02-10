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

package ch.niceideas.eskimo.model;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.service.ServiceDefinition;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ServicesInstallStatusWrapper extends JsonWrapper implements Serializable {

    public static final String INSTALLED_ON_IP_FLAG = "_installed_on_IP_";

    private static final Logger logger = Logger.getLogger(ServicesInstallStatusWrapper.class);

    public ServicesInstallStatusWrapper(File statusFile) throws FileException {
        super(FileUtils.readFile(statusFile));
    }

    public static ServicesInstallStatusWrapper empty() {
        return new ServicesInstallStatusWrapper("{}");
    }

    public ServicesInstallStatusWrapper(Map<String, Object> map) {
        super(new JSONObject(map));
    }

    public ServicesInstallStatusWrapper(String jsonString) {
        super(jsonString);
    }

    public boolean isServiceOK(Service service, Node node) {
        try {
            return ("OK".equals(getValueForPath(service + INSTALLED_ON_IP_FLAG + node.getName())));
        } catch (JSONException e) {
            logger.error(e, e);
            return false;
        }
    }

    public boolean isServiceInstalledAnywhere(Service service) {
        try {
            for (Node node : getNodesAndKubeFlags()) {
                if ("OK".equals(getValueForPath(service + INSTALLED_ON_IP_FLAG + node.getName()))
                        || "restart".equals(getValueForPath(service + INSTALLED_ON_IP_FLAG + node.getName()))) {
                    return true;
                }
            }
            return false;
        } catch (JSONException e) {
            logger.error(e, e);
            return false;
        }
    }

    public boolean isServiceInstalled(Service service, Node node) {
        try {
            return ("OK".equals(getValueForPath(service + INSTALLED_ON_IP_FLAG + node.getName()))
                 || "restart".equals(getValueForPath(service + INSTALLED_ON_IP_FLAG + node.getName())));
        } catch (JSONException e) {
            logger.error(e, e);
            return false;
        }
    }

    public void copyFrom(String flag, ServicesInstallStatusWrapper other) {
        this.setValueForPath(flag, other.getValueForPath(flag));
    }

    public List<String> getInstalledServicesFlags() {
        return keySet().stream()
                .filter (installFlag -> !installFlag.startsWith("node_check_IP"))
                .collect(Collectors.toList());
    }

    public Service getService(String serviceInstallStatusFlag) {
        return Service.from(serviceInstallStatusFlag.substring(0, serviceInstallStatusFlag.indexOf("_installed")));
    }

    public List<String> getAllInstallStatusesExceptServiceOnNode(Service service, Node node) {
        return keySet().stream()
                .filter(is -> is.startsWith("node_check_IP")
                        || !Service.from(is.substring(0, is.indexOf(INSTALLED_ON_IP_FLAG))).equals(service)
                        || !is.contains(node.getName()))
                .collect(Collectors.toList());
    }

    public Set<Node> getNodesAndKubeFlags() {
        return getRootKeys().stream()
                .filter(key -> key.contains(INSTALLED_ON_IP_FLAG))
                .map(key -> key.substring(key.indexOf(INSTALLED_ON_IP_FLAG) + INSTALLED_ON_IP_FLAG.length()))
                .map(Node::fromName)
                .collect(Collectors.toSet());
    }

    public static Pair<Service, Node> parseInstallStatusFlag (String serviceInstallStatusFlag) {
        if (StringUtils.isBlank(serviceInstallStatusFlag)) {
            return null;
        }
        return new Pair<> (
                Service.from(serviceInstallStatusFlag.substring (0, serviceInstallStatusFlag.indexOf(INSTALLED_ON_IP_FLAG))),
                Node.fromName(serviceInstallStatusFlag.substring(serviceInstallStatusFlag.indexOf(INSTALLED_ON_IP_FLAG) + INSTALLED_ON_IP_FLAG.length()))
        );
    }

    public List<Pair<Service, Node>> getAllServiceNodeInstallationPairs() {
        return getRootKeys().stream()
                .filter(key -> key.contains(INSTALLED_ON_IP_FLAG))
                .map(ServicesInstallStatusWrapper::parseInstallStatusFlag)
                .sorted(new PairComparator<>())
                .collect(Collectors.toList());
    }

    public void removeInstallationFlag (Service service, Node node) {
        removeRootKey(service + INSTALLED_ON_IP_FLAG + node.getName());
    }

    public void setInstallationFlagOK (Service service, Node node) {
        setValueForPath(service + INSTALLED_ON_IP_FLAG + node.getName(), "OK");
    }

    public void setInstallationFlagRestart (Service service, Node node) {
        setValueForPath(service + INSTALLED_ON_IP_FLAG + node.getName(), "restart");
    }

    private void setInstallationFlag (Service service, Node node, String flag) {
        setValueForPath(service + INSTALLED_ON_IP_FLAG + node.getName(), flag);
    }

    public Set<Node> getNodes() {
        return getRootKeys().stream()
                .filter(key -> key.contains(INSTALLED_ON_IP_FLAG))
                .map(key -> key.substring(key.indexOf(INSTALLED_ON_IP_FLAG) + INSTALLED_ON_IP_FLAG.length()))
                .filter(key -> !key.equals(Node.KUBERNETES_NODE.getName()))
                .map(Node::fromName)
                .collect(Collectors.toSet());
    }

    public Node getFirstNode(ServiceDefinition serviceDef) {
        return getFirstNode(serviceDef.toService());
    }

    public Node getFirstNode(Service service) {
        List<Node> allNodes = getRootKeys().stream()
                .filter(key -> key.contains(INSTALLED_ON_IP_FLAG))
                .filter(key -> key.startsWith(service + "_"))
                .map(key -> key.substring(key.indexOf(INSTALLED_ON_IP_FLAG) + INSTALLED_ON_IP_FLAG.length()))
                .map(Node::fromName)
                .collect(Collectors.toList());
        if (allNodes.isEmpty()) {
            return null;
        }
        return allNodes.get(0);
    }
}