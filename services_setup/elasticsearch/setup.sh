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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

# CHange current folder to script dir (important !)
cd $SCRIPT_DIR

# Loading topology
loadTopology

# Defining topology variables
if [[ $SELF_NODE_NUMBER == "" ]]; then
    echo " - No Self Node Number found in topology"
    exit -1
fi

if [[ $SELF_IP_ADDRESS == "" ]]; then
    echo " - No Self IP address found in topology for node $SELF_NODE_NUMBER"
    exit -2
fi

# ElasticSearch's own ES master is actually the next master to use in the chain
export MASTER_IP_ADDRESS=`eval echo "\$"$(echo MASTER_ELASTICSEARCH_$SELF_IP_ADDRESS | tr -d .)`
if [[ $MASTER_IP_ADDRESS == "" ]]; then
    echo " - No master passed in argument. Zen discovery will not be configured"
fi

# reinitializing log
sudo rm -f /tmp/es_install_log

echo " - Building container elasticsearch"
build_container elasticsearch elasticsearch /tmp/es_install_log

echo " - Configuring host elasticsearch common part"
. ./setupESCommon.sh
if [[ $? != 0 ]]; then
    echo "Common configuration part failed !"
    exit -20
fi

echo " - Creating shared directory"
sudo mkdir -p /usr/local/etc/elasticsearch
sudo chown -R elasticsearch /usr/local/etc/elasticsearch

# create and start container
echo " - Running docker container"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -v /var/lib/elasticsearch:/var/lib/elasticsearch \
        -v /var/log/elasticsearch:/var/log/elasticsearch \
        -v /var/run/elasticsearch:/var/run/elasticsearch \
        -v /usr/local/etc/elasticsearch:/usr/local/etc/elasticsearch \
        --mount type=bind,source=/etc/eskimo_topology.sh,target=/etc/eskimo_topology.sh \
        -e NODE_NAME=$HOSTNAME \
        -d --name elasticsearch \
        -i \
        -t eskimo:elasticsearch bash >> /tmp/es_install_log 2>&1
fail_if_error $? "/tmp/es_install_log" -2

# connect to container
#docker exec -it elasticsearch bash

echo " - Configuring elasticsearch container (common part)"
docker exec elasticsearch bash /scripts/inContainerSetupESCommon.sh $elasticsearch_user_id | tee -a /tmp/es_install_log 2>&1
if [[ `tail -n 1 /tmp/es_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script (common part) ended up in error"
    cat /tmp/es_install_log
    exit -102
fi

echo " - Configuring Elasticsearch container"
docker exec elasticsearch bash /scripts/inContainerSetupElasticSearch.sh | tee -a /tmp/es_install_log 2>&1
if [[ `tail -n 1 /tmp/es_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat /tmp/es_install_log
    exit -100
fi

echo " - Handling topology and setting injection"
handle_topology_settings elasticsearch /tmp/cerebro_install_log

#echo " - TODO"
#docker exec -it elasticsearch TODO

echo " - Committing changes to local template and exiting container elasticsearch"
commit_container elasticsearch /tmp/es_install_log

echo " - Installing and checking systemd service file"
install_and_check_service_file elasticsearch /tmp/es_install_log





