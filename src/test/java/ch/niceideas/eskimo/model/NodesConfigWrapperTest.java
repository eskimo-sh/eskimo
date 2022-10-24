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

package ch.niceideas.eskimo.model;

import ch.niceideas.eskimo.services.*;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class NodesConfigWrapperTest {

    NodesConfigWrapper ncw = new NodesConfigWrapper("{\n" +
            "    \"elasticsearch3\": \"on\",\n" +
            "    \"elasticsearch1\": \"on\",\n" +
            "    \"elasticsearch2\": \"on\",\n" +
            "    \"node_id1\": \"192.168.56.21\",\n" +
            "    \"node_id3\": \"192.168.56.23\",\n" +
            "    \"node_id2\": \"192.168.56.22\",\n" +
            "    \"kafka3\": \"on\",\n" +
            "    \"logstash1\": \"on\",\n" +
            "    \"kafka2\": \"on\",\n" +
            "    \"logstash2\": \"on\",\n" +
            "    \"logstash3\": \"on\",\n" +
            "    \"kafka1\": \"on\",\n" +
            "    \"gluster1\": \"on\",\n" +
            "    \"gluster3\": \"on\",\n" +
            "    \"gluster2\": \"on\",\n" +
            "    \"k8s-slave3\": \"on\",\n" +
            "    \"zookeeper\": \"1\",\n" +
            "    \"k8s-slave2\": \"on\",\n" +
            "    \"k8s-slave1\": \"on\",\n" +
            "    \"ntp1\": \"on\",\n" +
            "    \"prometheus3\": \"on\",\n" +
            "    \"prometheus2\": \"on\",\n" +
            "    \"prometheus1\": \"on\",\n" +
            "    \"ntp3\": \"on\",\n" +
            "    \"ntp2\": \"on\",\n" +
            "    \"kube-master\": \"1\"\n" +
            "}");

    @Test
    public void testHasServiceConfigured() throws Exception {
        assertTrue(ncw.hasServiceConfigured("kube-master"));
        assertTrue(ncw.hasServiceConfigured("ntp"));
        assertFalse(ncw.hasServiceConfigured("blablabla"));
    }

    @Test
    public void testIsServiceOnNode() throws Exception {
        assertTrue(ncw.isServiceOnNode("kube-master", 1));
        assertFalse(ncw.isServiceOnNode("kube-master", 2));

        assertTrue(ncw.isServiceOnNode("zookeeper", 1));
        assertFalse(ncw.isServiceOnNode("zookeeper", 2));
    }

    @Test
    public void testGetNodeAddressKeys() throws Exception {
        assertEquals ("node_id1,node_id2,node_id3", String.join(",", ncw.getNodeAddressKeys()));
    }

    @Test
    public void testGetServiceKeys() throws Exception {
        assertEquals ("elasticsearch1,elasticsearch2,elasticsearch3,gluster1,gluster2,gluster3,k8s-slave1,k8s-slave2,k8s-slave3,kafka1,kafka2,kafka3,kube-master,logstash1,logstash2,logstash3,ntp1,ntp2,ntp3,prometheus1,prometheus2,prometheus3,zookeeper", String.join(",", ncw.getServiceKeys()));
    }

    @Test
    public void testGetNodeAddress() throws Exception {
        assertEquals ("192.168.56.21", ncw.getNodeAddress(1));
        assertEquals ("192.168.56.22", ncw.getNodeAddress(2));
        assertEquals ("192.168.56.23", ncw.getNodeAddress(3));
    }

    @Test
    public void testGetNodeName() throws Exception {
        assertEquals ("192-168-56-21", ncw.getNodeName(1));
        assertEquals ("192-168-56-22", ncw.getNodeName(2));
        assertEquals ("192-168-56-23", ncw.getNodeName(3));
    }

    @Test
    public void testGetAllNodeAddressesWithService() throws Exception {
        assertEquals ("192.168.56.21,192.168.56.22,192.168.56.23", String.join(",", ncw.getAllNodeAddressesWithService("ntp")));
        assertEquals ("192.168.56.21", String.join(",", ncw.getAllNodeAddressesWithService("kube-master")));
    }

    @Test
    public void testGetServicesForNode() throws Exception {
        assertEquals ("elasticsearch,gluster,k8s-slave,kafka,kube-master,logstash,ntp,prometheus,zookeeper", String.join(",", ncw.getServicesForNode("192.168.56.21")));
        assertEquals ("elasticsearch,gluster,k8s-slave,kafka,kube-master,logstash,ntp,prometheus,zookeeper", String.join(",", ncw.getServicesForNode(1)));
    }

    @Test
    public void testGetNodeNumber() throws Exception {
        assertEquals (1, ncw.getNodeNumber("192.168.56.21"));
        assertEquals (3, ncw.getNodeNumber("192.168.56.23"));
    }

    @Test
    public void testGetNodeNumbers() throws Exception {
        assertEquals ("1,2,3", ncw.getNodeNumbers("elasticsearch").stream().map(nbr -> "" + nbr).collect(Collectors.joining(",")));
        assertEquals ("1,2,3", ncw.getNodeNumbers("k8s-slave").stream().map(nbr -> "" + nbr).collect(Collectors.joining(",")));
        assertEquals ("1", ncw.getNodeNumbers("zookeeper").stream().map(nbr -> "" + nbr).collect(Collectors.joining(",")));
    }

    @Test
    public void testGetFirstNodeName() throws Exception {
        assertEquals ("192-168-56-21", ncw.getFirstNodeName("elasticsearch"));
        assertEquals ("192-168-56-21", ncw.getFirstNodeName("ntp"));
        assertEquals ("192-168-56-21", ncw.getFirstNodeName("zookeeper"));
    }
}
