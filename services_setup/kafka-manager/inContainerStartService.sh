#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
# Author : eskimo.sh / https://www.eskimo.sh
#
# Eskimo is available under a dual licensing model : commercial and GNU AGPL.
# If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
# terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
# Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
# any later version.
# Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
# commercial license.
#
# Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Affero Public License for more details.
#
# You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
# see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
# Boston, MA, 02110-1301 USA.
#
# You can be released from the requirements of the license by purchasing a commercial license. Buying such a
# commercial license is mandatory as soon as :
# - you develop activities involving Eskimo without disclosing the source code of your own product, software,
#   platform, use cases or scripts.
# - you deploy eskimo as part of a commercial product, platform or software.
# For more information, please contact eskimo.sh at https://www.eskimo.sh
#
# The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
# Software.
#

set -e

echo " - Injecting topology"
. /usr/local/sbin/inContainerInjectTopology.sh

# Defining topology variables
export ZOOKEEPER_IP_ADDRESS=$MASTER_ZOOKEEPER_1
if [[ $ZOOKEEPER_IP_ADDRESS == "" ]]; then
    echo " - No zookeeper master found in topology"
    exit 3
fi

echo " - Inject settings"
/usr/local/sbin/settingsInjector.sh kafka-manager

echo " - Deleting previous PID"
rm -Rf /var/run/kafka-manager/kafka-manager.pid

echo " - Creating required directories (as kafka)"
sudo /bin/mkdir -p /var/run/kafka/kafka-manager
sudo /bin/chown kafka /var/run/kafka/kafka-manager
sudo /bin/mkdir -p /var/log/kafka/kafka-manager
sudo /bin/chown kafka /var/log/kafka/kafka-manager


echo " - Starting service"
/usr/local/lib/kafka-manager/bin/kafka-manager \
    -Dapplication.home=/usr/local/lib/kafka-manager/ \
    -Dpidfile.path=/var/run/kafka/kafka-manager/kafka-manager.pid \
    -Dconfig.file=/usr/local/lib/kafka-manager/conf/application.conf \
    -Dhttp.port=22080 &
kafka_manager_pid=$!


echo " - Checking eskimo cluster configuration"
set +e
for i in $(seq 1 10); do

    if [[ -z "$NO_SLEEP" ]]; then sleep 5; fi

    echo "   + Attempt $i"
    clusterSearch=$(curl -XGET http://localhost:22080/clusters/Eskimo 2>&1)
    if [[ $? == 0 ]]; then
        break
    fi
    if [[ "$i" == "10" ]]; then
        echo "Could not fetch Eskimo cluster page in 50 seconds"
        echo $clusterSearch
    fi
done
set -e


if [[ $(echo $clusterSearch | grep "Unknown cluster") != "" ]]; then
    echo " - Creating Eskimo Cluster in Kafka Manager"
    payload="name=Eskimo&zkHosts=$ZOOKEEPER_IP_ADDRESS%3A2181&kafkaVersion=2.2.0&jmxEnabled=true&jmxUser=&jmxPass=&pollConsumers=true"
    payload+='&filterConsumers=true&tuning.brokerViewUpdatePeriodSeconds=15&tuning.clusterManagerThreadPoolSize=2'
    payload+='&tuning.clusterManagerThreadPoolQueueSize=100&tuning.kafkaCommandThreadPoolSize=2'
    payload+='&tuning.kafkaCommandThreadPoolQueueSize=100&tuning.logkafkaCommandThreadPoolSize=2'
    payload+='&tuning.logkafkaCommandThreadPoolQueueSize=100&tuning.logkafkaUpdatePeriodSeconds=30'
    payload+='&tuning.partitionOffsetCacheTimeoutSecs=5&tuning.brokerViewThreadPoolSize=2'
    payload+='&tuning.brokerViewThreadPoolQueueSize=1000&tuning.offsetCacheThreadPoolSize=2'
    payload+='&tuning.offsetCacheThreadPoolQueueSize=1000&tuning.kafkaAdminClientThreadPoolSize=2'
    payload+='&tuning.kafkaAdminClientThreadPoolQueueSize=1000&tuning.kafkaManagedOffsetMetadataCheckMillis=30000'
    payload+='&tuning.kafkaManagedOffsetGroupCacheSize=1000000&tuning.kafkaManagedOffsetGroupExpireDays=7'
    payload+='&securityProtocol=PLAINTEXT&saslMechanism=DEFAULT&jaasConfig='

    curl -XPOST http://localhost:22080/clusters -d $payload >> /tmp/eskimo-cluster-configuration.log 2>&1
    if [[ $? != 0 ]]; then
        echo "failed !!"
        cat /tmp/eskimo-cluster-configuration.log
    fi

else
    echo " - (Skipping Eskimo Cluster configuration in Kafka Manager since already exist)"
fi


echo " - Waiting on kafka manager process"
wait $kafka_manager_pid
