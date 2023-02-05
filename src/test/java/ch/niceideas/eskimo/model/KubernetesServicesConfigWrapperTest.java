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

import ch.niceideas.eskimo.types.Service;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class KubernetesServicesConfigWrapperTest {

    KubernetesServicesConfigWrapper kscw = new KubernetesServicesConfigWrapper("{\n" +
            "    \"spark-runtime_ram\": \"1.2G\",\n" +
            "    \"zeppelin_ram\": \"3G\",\n" +
            "    \"kibana_ram\": \"1024M\",\n" +
            "    \"kafka-manager_ram\": \"1G\",\n" +
            "    \"kafka_ram\": \"1G\",\n" +
            "    \"kafka-manager_install\": \"on\",\n" +
            "    \"elasticsearch_cpu\": \"0.4\",\n" +
            "    \"kafka_cpu\": \"0.3\",\n" +
            "    \"flink-runtime_ram\": \"1.2G\",\n" +
            "    \"kafka-manager_cpu\": \"0.1\",\n" +
            "    \"elasticsearch_install\": \"on\",\n" +
            "    \"kafka_install\": \"on\",\n" +
            "    \"kibana_cpu\": \"0.3\",\n" +
            "    \"zeppelin_install\": \"on\",\n" +
            "    \"spark-runtime_cpu\": \"0.4\",\n" +
            "    \"flink-runtime_cpu\": \"0.4\",\n" +
            "    \"logstash_cpu\": \"0.4\",\n" +
            "    \"flink-runtime_install\": \"on\",\n" +
            "    \"logstash_ram\": \"1G\",\n" +
            "    \"spark-runtime_install\": \"on\",\n" +
            "    \"cerebro_ram\": \"800M\",\n" +
            "    \"kubernetes-dashboard_cpu\": \"0.1\",\n" +
            "    \"spark-console_install\": \"on\",\n" +
            "    \"spark-console_ram\": \"1G\",\n" +
            "    \"elasticsearch_ram\": \"1024M\",\n" +
            "    \"grafana_ram\": \"800M\",\n" +
            "    \"kubernetes-dashboard_ram\": \"1G\",\n" +
            "    \"cerebro_install\": \"on\",\n" +
            "    \"spark-console_cpu\": \"0.1\",\n" +
            "    \"zeppelin_cpu\": \"0.4\",\n" +
            "    \"kibana_install\": \"on\",\n" +
            "    \"logstash_install\": \"on\",\n" +
            "    \"grafana_install\": \"on\",\n" +
            "    \"kubernetes-dashboard_install\": \"on\",\n" +
            "    \"cerebro_cpu\": \"0.2\",\n" +
            "    \"grafana_cpu\": \"0.2\"\n" +
            "}");

    @Test
    public void testGetEnabledServices()  {
        assertEquals ("cerebro,elasticsearch,flink-runtime,grafana,kafka-manager,kafka,kibana,kubernetes-dashboard,logstash,spark-console,spark-runtime,zeppelin",
                kscw.getEnabledServices().stream().map(Service::getName).collect(Collectors.joining(",")));
    }

    @Test
    public void testGetCpuSetting() {
        assertEquals ("0.4", kscw.getCpuSetting(Service.from ("elasticsearch")));
        assertEquals ("0.2", kscw.getCpuSetting(Service.from ("cerebro")));
    }

    @Test
    public void testGetRamSetting() {
        assertEquals ("1024M", kscw.getRamSetting(Service.from ("elasticsearch")));
        assertEquals ("800M", kscw.getRamSetting(Service.from ("cerebro")));
    }

    @Test
    public void testIsServiceInstallRequired() {
        assertTrue (kscw.isServiceInstallRequired(Service.from ("elasticsearch")));
        assertFalse (kscw.isServiceInstallRequired(Service.from ("dummy")));
    }

    @Test
    public void testHasEnabledServices() {
        assertTrue (kscw.hasEnabledServices());

        KubernetesServicesConfigWrapper other = new KubernetesServicesConfigWrapper(kscw.getFormattedValue());
        kscw.getRootKeys().stream()
                .filter(key -> key.contains(KubernetesServicesConfigWrapper.INSTALL_FLAG))
                .forEach(key -> other.setValueForPath(key, "off"));

        assertFalse (other.hasEnabledServices());
    }

    @Test
    public void testIsDifferentConfig() {
        assertFalse (kscw.isDifferentConfig(new KubernetesServicesConfigWrapper(kscw.getFormattedValue()), Service.from ("zeppelin")));
        assertFalse (kscw.isDifferentConfig(new KubernetesServicesConfigWrapper(kscw.getFormattedValue()), Service.from ("elasticsearch")));
        assertFalse (kscw.isDifferentConfig(new KubernetesServicesConfigWrapper(kscw.getFormattedValue()), Service.from ("cerebro")));
        assertFalse (kscw.isDifferentConfig(new KubernetesServicesConfigWrapper(kscw.getFormattedValue()), Service.from ("kibana")));

        KubernetesServicesConfigWrapper other = new KubernetesServicesConfigWrapper(kscw.getFormattedValue());
        other.setValueForPath("zeppelin_ram", "2048m");

        assertTrue (kscw.isDifferentConfig(other, Service.from ("zeppelin")));
        assertFalse (kscw.isDifferentConfig(other, Service.from ("elasticsearch")));
        assertFalse (kscw.isDifferentConfig(other, Service.from ("kibana")));
        assertFalse (kscw.isDifferentConfig(other, Service.from ("cerebro")));
    }

}
