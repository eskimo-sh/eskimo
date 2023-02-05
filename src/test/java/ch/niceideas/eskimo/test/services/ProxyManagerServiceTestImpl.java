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

import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.services.ConnectionManagerException;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("test-proxy")
public class ProxyManagerServiceTestImpl implements ProxyManagerService {

    private int tomcatServerLocalPort;

    private final Map<Node, List<ProxyTunnelConfig>> forwarderConfigForHosts = new HashMap<>();

    public void setForwarderConfigForHosts (Node host, List<ProxyTunnelConfig> forwardersConfig) {
        forwarderConfigForHosts.put (host, forwardersConfig);
    }

    public void setTomcatLocalPort(int tomcatServerLocalPort) {
        this.tomcatServerLocalPort = tomcatServerLocalPort;
    }

    public void reset() {
        this.forwarderConfigForHosts.clear();
        this.tomcatServerLocalPort = 0;
    }

    @Override
    public HttpHost getServerHost(String serviceId) {
        return null;
    }

    @Override
    public String getServerURI(Service service, String pathInfo) {
        return null;
    }

    @Override
    public Node extractHostFromPathInfo(String pathInfo) {
        return null;
    }

    @Override
    public List<ProxyTunnelConfig> getTunnelConfigForHost(Node host) {
        return Optional.ofNullable(forwarderConfigForHosts.get(host)).orElse(Collections.emptyList());
    }

    @Override
    public void updateServerForService(Service service, Node runtimeNode) {

    }

    @Override
    public void removeServerForService(Service service, Node runtimeNode) {

    }

    @Override
    public Collection<String> getAllTunnelConfigKeys() {
        return null;
    }

    @Override
    public ProxyTunnelConfig getTunnelConfig(String serviceName) {
        if (serviceName.equals("cerebro")) {
            return new ProxyTunnelConfig(Service.from("cerebro"), tomcatServerLocalPort, Node.fromName("dummy"), -1);
        }
        if (serviceName.equals("grafana")) {
            return new ProxyTunnelConfig(Service.from("grafana"), tomcatServerLocalPort, Node.fromName("dummy"), -1);
        }
        return new ProxyTunnelConfig(Service.from (serviceName), 12345, Node.fromAddress("192.168.10.11"), 8080);
    }

}
