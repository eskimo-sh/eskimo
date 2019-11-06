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


# reinitializing log
sudo rm -f /tmp/logstash_install_log

# build

echo " - Configuring host elasticsearch config part"
. ./setupESCommon.sh
if [[ $? != 0 ]]; then
    echo "Common configuration part failed !"
    exit -20
fi

echo " - Configuring host logstash config part"
. ./setupLogstashCommon.sh $SELF_IP_ADDRESS $GLUSTER_AVAILABLE
if [[ $? != 0 ]]; then
    echo "Logstash Common configuration part failed !"
    exit -21
fi


echo " - Building docker container for logstash"
build_container logstash logstash /tmp/logstash_install_log

# create and start container
echo " - Running docker container to configure logstash executor"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -d \
        -v /var/log/logstash:/var/log/logstash \
        -v /var/lib/logstash:/var/lib/logstash \
        --name logstash \
        -i \
        -t eskimo:logstash bash >> /tmp/logstash_install_log 2>&1
fail_if_error $? "/tmp/logstash_install_log" -2

# connect to container
#docker exec -it logstash bash

echo " - Logstash Remote Server Scripts"
for i in `find ./command_server`; do
    if [[ -f $SCRIPT_DIR/$i ]]; then
        filename=`basename $i`
        docker cp $SCRIPT_DIR/$i logstash:/usr/local/sbin/$filename >> /tmp/logstash_install_log 2>&1
        docker exec logstash chmod 755 /usr/local/sbin/$filename >> /tmp/logstash_install_log 2>&1
        fail_if_error $? /tmp/logstash_install_log -30
    fi
done

echo " - Configuring logstash container (common part)"
docker exec logstash bash /scripts/inContainerSetupESCommon.sh $elasticsearch_user_id | tee -a /tmp/logstash_install_log 2>&1
if [[ `tail -n 1 /tmp/logstash_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script (common part) ended up in error"
    cat /tmp/logstash_install_log
    exit -102
fi

echo " - Configuring logstash container"
docker exec logstash bash /scripts/inContainerSetupLogstash.sh | tee -a /tmp/logstash_install_log 2>&1
if [[ `tail -n 1 /tmp/logstash_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat /tmp/logstash_install_log
    exit -101
fi

#echo " - TODO"
#docker exec -it logstash TODO

echo " - Handling topology and setting injection"
handle_topology_settings logstash /tmp/logstash_install_log

echo " - Committing changes to local template and exiting container logstash"
commit_container logstash /tmp/logstash_install_log

echo " - Copying logstash command line programs docker wrappers to /usr/local/bin"
for i in `find ./logstash_wrappers -mindepth 1`; do
    sudo cp $i /usr/local/bin
    filename=`echo $i | cut -d '/' -f 3`
    sudo chmod 755 /usr/local/bin/$filename
done

echo " - Copy logstash remote client to /usr/local/bin"
sudo cp command_server/logstash-cli /usr/local/bin
sudo chmod 755 /usr/local/bin/logstash-cli

echo " - Installing and checking systemd service file"
install_and_check_service_file logstash /tmp/logstash_install_log
