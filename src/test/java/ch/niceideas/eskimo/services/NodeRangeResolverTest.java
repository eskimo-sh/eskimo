/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import junit.framework.TestCase;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.comparator.Comparators;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NodeRangeResolverTest extends TestCase {

    private NodeRangeResolver nrr = null;

    @Before
    public void setUp() throws Exception {
        nrr = new NodeRangeResolver();
    }

    @Test
    public void testRangeOverlapNode() throws Exception {

        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11");
                put("action_id2", "192.168.10.11-192.168.10.14");
                put("cerebro", "1");
                put("elasticsearch1", "on");
                put("elasticsearch2", "on");
                put("kafka1", "on");
                put("kafka2", "on");
                put("ntp1", "on");
                put("ntp2", "on");
                put("prometheus1", "on");
                put("prometheus2", "on");
                put("gluster1", "on");
                put("gluster2", "on");
                put("kibana", "1");
                put("gdash", "1");
                put("logstash1", "on");
                put("logstash2", "on");
                put("mesos-agent1", "on");
                put("mesos-agent2", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
                put("spark-executor2", "on");
                put("spark-history-server", "1");
                put("zeppelin", "1");
                put("zookeeper", "1");
            }});

            nrr.resolveRanges(nodesConfig);
        });

        assertEquals("Configuration is illegal. IP address 192.168.10.11 is referenced by multiple ranges / nodes", exception.getMessage());
    }

    @Test
    public void testEmptyRange() throws Exception {
        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11");
                put("action_id2", "192.168.10.15-192.168.10.14");
                put("cerebro", "1");
                put("elasticsearch1", "on");
                put("elasticsearch2", "on");
                put("kafka1", "on");
                put("kafka2", "on");
                put("ntp1", "on");
                put("ntp2", "on");
                put("prometheus1", "on");
                put("prometheus2", "on");
                put("gluster1", "on");
                put("gluster2", "on");
                put("kibana", "1");
                put("gdash", "1");
                put("logstash1", "on");
                put("logstash2", "on");
                put("mesos-agent1", "on");
                put("mesos-agent2", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
                put("spark-executor2", "on");
                put("spark-history-server", "1");
                put("zeppelin", "1");
                put("zookeeper", "1");
            }});

            nrr.resolveRanges(nodesConfig);
        });

        assertEquals("Range resolves to empty address set : 192.168.10.15-192.168.10.14", exception.getMessage());
    }

    @Test
    public void testRangeOverlapRange() throws Exception {
        NodesConfigurationException exception = assertThrows(NodesConfigurationException.class, () -> {
            NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
                put("action_id1", "192.168.10.11-192.168.10.13");
                put("action_id2", "192.168.10.13-192.168.10.14");
                put("cerebro", "1");
                put("elasticsearch1", "on");
                put("elasticsearch2", "on");
                put("kafka1", "on");
                put("kafka2", "on");
                put("ntp1", "on");
                put("ntp2", "on");
                put("prometheus1", "on");
                put("prometheus2", "on");
                put("gluster1", "on");
                put("gluster2", "on");
                put("kibana", "1");
                put("gdash", "1");
                put("logstash1", "on");
                put("logstash2", "on");
                put("mesos-agent1", "on");
                put("mesos-agent2", "on");
                put("mesos-master", "1");
                put("spark-executor1", "on");
                put("spark-executor2", "on");
                put("spark-history-server", "1");
                put("zeppelin", "1");
                put("zookeeper", "1");
            }});

            nrr.resolveRanges(nodesConfig);
        });

        assertEquals("Configuration is illegal. IP address 192.168.10.13 is referenced by multiple ranges / nodes", exception.getMessage());
    }

    @Test
    public void testGenerateRangeIps() throws Exception {

        List<String> range = nrr.generateRangeIps("192.168.0.1-192.168.0.15");

        assertNotNull(range);

        assertEquals(15, range.size());
        assertEquals("192.168.0.1", range.get(0));
        assertEquals("192.168.0.10", range.get(9));
        assertEquals("192.168.0.15", range.get(14));

        range = nrr.generateRangeIps("201.110.50.12-201.110.52.12");

        assertNotNull(range);

        assertEquals(513, range.size());
        assertEquals("202.110.50.12", range.get(0));
        assertEquals("202.110.50.21", range.get(9));
        assertEquals("202.110.50.31", range.get(19));
        assertEquals("202.110.50.131", range.get(119));
        assertEquals("202.110.51.87", range.get(331));
        assertEquals("202.110.52.12", range.get(512));
    }

    @Test
    public void testNominal() throws Exception {

        NodesConfigWrapper nodesConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("action_id1", "192.168.10.11");
            put("action_id2", "192.168.11.12-192.168.11.14");
            put("cerebro", "1");
            put("elasticsearch1", "on");
            put("elasticsearch2", "on");
            put("kafka1", "on");
            put("kafka2", "on");
            put("ntp1", "on");
            put("ntp2", "on");
            put("prometheus1", "on");
            put("prometheus2", "on");
            put("gluster1", "on");
            put("gluster2", "on");
            put("kibana", "1");
            put("gdash", "1");
            put("logstash1", "on");
            put("logstash2", "on");
            put("mesos-agent1", "on");
            put("mesos-agent2", "on");
            put("mesos-master", "1");
            put("spark-executor1", "on");
            put("spark-executor2", "on");
            put("spark-history-server", "1");
            put("zeppelin", "1");
            put("zookeeper", "1");
        }});

        NodesConfigWrapper resolvedConfig = nrr.resolveRanges(nodesConfig);

        assertNotNull(resolvedConfig);

        NodesConfigWrapper expectedResultConfig = new NodesConfigWrapper(new HashMap<String, Object>() {{
            put("action_id1", "192.168.10.11");
            put("action_id2", "192.168.11.12");
            put("action_id3", "192.168.11.13");
            put("action_id4", "192.168.11.14");
            put("cerebro", "1");
            put("elasticsearch1", "on");
            put("elasticsearch2", "on");
            put("elasticsearch3", "on");
            put("elasticsearch4", "on");
            put("gdash", "1");
            put("gluster1", "on");
            put("gluster2", "on");
            put("gluster3", "on");
            put("gluster4", "on");
            put("kafka1", "on");
            put("kafka2", "on");
            put("kafka3", "on");
            put("kafka4", "on");
            put("kibana", "1");
            put("logstash1", "on");
            put("logstash2", "on");
            put("logstash3", "on");
            put("logstash4", "on");
            put("mesos-agent1", "on");
            put("mesos-agent2", "on");
            put("mesos-agent3", "on");
            put("mesos-agent4", "on");
            put("mesos-master", "1");
            put("ntp1", "on");
            put("ntp2", "on");
            put("ntp3", "on");
            put("ntp4", "on");
            put("prometheus1", "on");
            put("prometheus2", "on");
            put("prometheus3", "on");
            put("prometheus4", "on");
            put("spark-executor1", "on");
            put("spark-executor2", "on");
            put("spark-executor3", "on");
            put("spark-executor4", "on");
            put("spark-history-server", "1");
            put("zeppelin", "1");
            put("zookeeper", "1");
        }});
        
        assertEquals(expectedResultConfig.getFormattedValue(), resolvedConfig.getFormattedValue());
    }

    @Test
    public void testMultipleRanges() throws Exception {

        InputStream servicesConfigStream = ResourceUtils.getResourceAsStream("NodeRangeResolverTest/multiple-ranges-nodes-config.json");
        NodesConfigWrapper newConfig = new NodesConfigWrapper(StreamUtils.getAsString(servicesConfigStream));

        NodesConfigWrapper resolvedConfig = nrr.resolveRanges(newConfig);

        assertNotNull(resolvedConfig);

        assertEquals("action_id1,action_id2,action_id3,action_id4,action_id5,action_id6,action_id7", String.join(",",
                resolvedConfig.getIpAddressKeys().stream()
                        .sorted(Comparators.comparable())
                        .collect(Collectors.toList())
                        .toArray(new String[0])));

        assertEquals("192.168.10.11,192.168.10.13,192.168.10.14,192.168.10.15,192.168.10.16,192.168.10.17,192.168.10.18", String.join(",",
                resolvedConfig.getAllNodeAddressesWithService("ntp").stream()
                        .sorted(Comparators.comparable())
                        .collect(Collectors.toList())
                        .toArray(new String[0])));

        assertEquals("192.168.10.13,192.168.10.14,192.168.10.15,192.168.10.16,192.168.10.17,192.168.10.18", String.join(",",
                resolvedConfig.getAllNodeAddressesWithService("kafka").stream()
                        .sorted(Comparators.comparable())
                        .collect(Collectors.toList())
                        .toArray(new String[0])));

        assertEquals("192.168.10.11", String.join(",",
                resolvedConfig.getAllNodeAddressesWithService("mesos-master").stream()
                        .sorted(Comparators.comparable())
                        .collect(Collectors.toList())
                        .toArray(new String[0])));
    }

}
