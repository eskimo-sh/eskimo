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

import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.SystemStatusWrapper;

import java.util.HashMap;

public class StandardSetupHelpers {

    public static NodesConfigWrapper getStandard2NodesSetup() {
        return new NodesConfigWrapper(new HashMap<>() {{
            put("node_id1", "192.168.10.11");
            put("kube-master", "1");
            put("gluster1", "on");
            put("etcd1", "on");
            put("kube-slave1", "on");
            put("ntp1", "on");

            put("node_id2", "192.168.10.13");
            put("zookeeper", "2");
            put("gluster2", "on");
            put("etcd2", "on");
            put("kube-slave2", "on");
            put("ntp2", "on");
        }});
    }

    public static ServicesInstallStatusWrapper getStandard2NodesInstallStatus() {
        return new ServicesInstallStatusWrapper(new HashMap<>() {{
            put("cerebro_installed_on_IP_KUBERNETES_NODE", "OK");
            put("elasticsearch_installed_on_IP_KUBERNETES_NODE", "OK");
            put("gluster_installed_on_IP_192-168-10-11", "OK");
            put("gluster_installed_on_IP_192-168-10-13", "OK");
            put("kafka-manager_installed_on_IP_KUBERNETES_NODE", "OK");
            put("logstash_installed_on_IP_KUBERNETES_NODE", "OK");
            put("kafka_installed_on_IP_KUBERNETES_NODE", "OK");
            put("kibana_installed_on_IP_KUBERNETES_NODE", "OK");
            put("kube-slave_installed_on_IP_192-168-10-11", "OK");
            put("kube-slave_installed_on_IP_192-168-10-13", "OK");
            put("kube-master_installed_on_IP_192-168-10-11", "OK");
            put("ntp_installed_on_IP_192-168-10-11", "OK");
            put("ntp_installed_on_IP_192-168-10-13", "OK");
            put("etcd_installed_on_IP_192-168-10-11", "OK");
            put("etcd_installed_on_IP_192-168-10-13", "OK");
            put("spark-runtime_installed_on_IP_KUBERNETES_NODE", "OK");
            put("spark-console_installed_on_IP_KUBERNETES_NODE", "OK");
            put("zeppelin_installed_on_IP_KUBERNETES_NODE", "OK");
            put("zookeeper_installed_on_IP_192-168-10-13", "OK");
        }});
    }

    public static SystemStatusWrapper getStandard2NodesSystemStatus() {
        return  new SystemStatusWrapper (new HashMap<>() {{
            put("node_address_192-168-10-11", "192.168.10.11");
            put("node_address_192-168-10-13", "192.168.10.13");
            put("node_alive_192-168-10-11", "OK");
            put("node_alive_192-168-10-13", "OK");
            put("node_nbr_192-168-10-11", "1");
            put("node_nbr_192-168-10-13", "2");
            put("service_cerebro_192-168-10-11", "OK");
            put("service_elasticsearch_192-168-10-11", "OK");
            put("service_elasticsearch_192-168-10-13", "OK");
            put("service_gluster_192-168-10-11", "OK");
            put("service_gluster_192-168-10-13", "OK");
            put("service_kafka_192-168-10-11", "OK");
            put("service_kafka_192-168-10-13", "OK");
            put("service_kafka-manager_192-168-10-11", "OK");
            put("service_kibana_192-168-10-11", "OK");
            put("service_logstash_192-168-10-11", "OK");
            put("service_logstash_192-168-10-13", "OK");
            put("service_kube-slave_192-168-10-11", "OK");
            put("service_kube-slave_192-168-10-13", "OK");
            put("service_kube-master_192-168-10-11", "OK");
            put("service_ntp_192-168-10-11", "OK");
            put("service_ntp_192-168-10-13", "OK");
            put("service_etcd_192-168-10-11", "OK");
            put("service_etcd_192-168-10-13", "OK");
            put("service_spark-runtime_192-168-10-11", "OK");
            put("service_spark-runtime_192-168-10-13", "OK");
            put("service_spark-console_192-168-10-13", "OK");
            put("service_zeppelin_192-168-10-13", "OK");
            put("service_zookeeper_192-168-10-13", "OK");
        }});
    }

    public static KubernetesServicesConfigWrapper getStandardKubernetesConfig() {
        return new KubernetesServicesConfigWrapper(new HashMap<>() {{
            put("cerebro_install", "on");
            put("cerebro_cpu", "1");
            put("cerebro_ram", "800M");
            put("kibana_install", "on");
            put("kibana_cpu", "1");
            put("kibana_ram", "800M");
            put("spark-runtime_install", "on");
            put("spark-runtime_cpu", "1");
            put("spark-runtime_ram", "800M");
            put("logstash_install", "on");
            put("logstash_cpu", "1");
            put("logstash_ram", "800M");
            put("spark-console_install", "on");
            put("spark-console_cpu", "1");
            put("spark-console_ram", "800M");
            put("kafka-manager_install", "on");
            put("kafka-manager_cpu", "1");
            put("kafka-manager_ram", "800M");
            put("kafka_install", "on");
            put("kafka_cpu", "1");
            put("kafka_ram", "800M");
            put("zeppelin_install", "on");
            put("zeppelin_cpu", "1");
            put("zeppelin_ram", "800M");
            put("elasticsearch_install", "on");
            put("elasticsearch_cpu", "1");
            put("elasticsearch_ram", "800M");
        }});
    }
}
