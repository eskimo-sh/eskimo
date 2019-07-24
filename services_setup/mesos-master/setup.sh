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

# reinitializing log
sudo rm -f /tmp/mesos_install_log

# Find out mesos version
export AMESOS_VERSION=`find /usr/local/lib/ -mindepth 1 -maxdepth 1 ! -type l | grep "mesos-*.*" | cut -d '-' -f 2`

echo " - Building container mesos-master"
build_container mesos-master mesos-master /tmp/mesos_install_log

echo " - Creating shared directory"
if [[ ! -d /var/lib/mesos ]]; then
    sudo mkdir -p /var/lib/mesos
    sudo chmod -R 777 /var/lib/mesos
fi
if [[ ! -d /var/run/mesos ]]; then
    sudo mkdir -p /var/run/mesos
fi
if [[ ! -d /var/log/mesos ]]; then
    sudo mkdir -p /var/log/mesos
    sudo chmod -R 777 /var/log/mesos
fi

if [[ ! -f /usr/local/lib/mesos/sbin/mesos-init-wrapper.sh ]]; then
    echo " - Copying mesos-init-wrapper script"
    sudo cp $SCRIPT_DIR/mesos-init-wrapper.sh /usr/local/lib/mesos/sbin/
    sudo chmod 754 /usr/local/lib/mesos/sbin/mesos-init-wrapper.sh
    sudo ln -s /usr/local/lib/mesos/sbin/mesos-init-wrapper.sh /usr/local/sbin/mesos-init-wrapper.sh
fi

# create and start container
echo " - Running docker container"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -v /var/lib/mesos:/var/lib/mesos \
        -v /var/log/mesos:/var/log/mesos \
        -v /var/run/mesos:/var/run/mesos \
        -v /usr/local/bin:/usr/local/bin \
        -v /usr/local/sbin:/usr/local/sbin \
        -v /usr/local/lib/mesos/:/usr/local/lib/mesos/ \
        -v /usr/local/lib/mesos-$AMESOS_VERSION/:/usr/local/lib/mesos-$AMESOS_VERSION/ \
        -d \
        --name mesos-master \
        -i \
        -t eskimo:mesos-master bash >> /tmp/mesos_install_log 2>&1
fail_if_error $? "/tmp/mesos_install_log" -2

# connect to container
#docker exec -it mesos bash

echo " - Configuring mesos container"
docker exec mesos-master bash /scripts/inContainerSetupMesosMaster.sh $SELF_IP_ADDRESS \
        | tee -a /tmp/mesos_install_log 2>&1
if [[ `tail -n 1 /tmp/mesos_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat /tmp/mesos_install_log
    exit -100
fi

#echo " - TODO"
#docker exec -it mesos TODO

echo " - Handling topology injection"
handle_topology mesos-master /tmp/mesos_install_log

echo " - Committing changes to local template and exiting container mesos"
commit_container mesos-master /tmp/mesos_install_log

echo " - Installing and checking systemd service file"
install_and_check_service_file mesos-master /tmp/mesos_install_log





