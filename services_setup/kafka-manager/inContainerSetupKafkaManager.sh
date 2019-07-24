#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


MASTER_ZK_IP_ADDRESS=$1
if [[ $MASTER_ZK_IP_ADDRESS == "" ]]; then
    echo " - No master passed in argument"
    exit -5
fi


echo "-- SETTING UP KAFKA MANAGER-----------------------------------------------------"

echo " - Getting kafka user ID"
set +e
kafka_user_id=`id -u kafka 2> /tmp/cerebro_install_log`
set -e
echo " - Found user with ID $kafka_user_id"
if [[ $kafka_user_id == "" ]]; then
    echo "Docker Kafka USER ID not found"
    exit -2
fi


echo " - Simlinking logs to /var/log/kafka-manager/"
sudo rm -Rf /usr/local/lib/kafka-manager/logs
sudo ln -s /var/log/kafka-manager /usr/local/lib/kafka-manager/logs

echo " - Adapting Configuration file"

sudo sed -i s/"kafka-manager.zkhosts=\"kafka-manager-zookeeper:2181\""/"kafka-manager.zkhosts=\"$MASTER_ZK_IP_ADDRESS:2181\""/g /usr/local/lib/kafka-manager/conf/application.conf


echo " - Starting kafka manager to inject eskimo cluster payload"
/usr/local/lib/kafka-manager/bin/kafka-manager \
        -Dapplication.home=/usr/local/lib/kafka-manager/ \
        -Dpidfile.path=/tmp/kafka-manager-temp.pid \
        -Dconfig.file=/usr/local/lib/kafka-manager/conf/application.conf \
        -Dhttp.port=22080 >> /tmp/kafka_manager_install_log 2>&1 &
KAFKA_MANAGER_PID=$!
fail_if_error $? "/tmp/kafka_manager_install_log" -5

sleep 10
if ps -p $KAFKA_MANAGER_PID > /dev/null; then
    echo " - Kafka Manager successfully started"
else
    echo "Could not successfully start kafka manager in container"
    cat /tmp/kafka_manager_install_log
    echo "Could not successfully start kafka manager in container"
    exit -10
fi

if [[ `curl -XGET http://localhost:22080/clusters/Eskimo 2>/dev/null | grep "Unknown cluster"` != "" ]]; then
    echo " - Creating Eskimo Cluster in Kafka Manager"
    payload="name=Eskimo&zkHosts=$MASTER_ZK_IP_ADDRESS%3A2181&kafkaVersion=2.2.0&jmxEnabled=true&jmxUser=&jmxPass=&pollConsumers=true"
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

    curl -XPOST http://localhost:22080/clusters -d $payload >> /tmp/kafka_manager_install_log 2>&1
    fail_if_error $? "/tmp/kafka_manager_install_log" -4
fi

echo " - Stopping kafka manager"
kill $KAFKA_MANAGER_PID  >> /tmp/kafka_manager_install_log 2>&1
fail_if_error $? "/tmp/kafka_manager_install_log" -5

echo " - Changing zookeeper host back to marker for startup topology injection"
sudo sed -i s/"kafka-manager.zkhosts=\"$MASTER_ZK_IP_ADDRESS:2181\""/"kafka-manager.zkhosts=\"MASTER_ZK_IP_ADDRESS:2181\""/g /usr/local/lib/kafka-manager/conf/application.conf


echo " - Changing owner of config directory to kafka"
sudo chown -R kafka. /usr/local/lib/kafka-manager/conf

# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container config SUCCESS"
