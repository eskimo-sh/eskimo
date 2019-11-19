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


# find out if gluster is available
if [[ `cat /etc/eskimo_topology.sh  | grep MASTER_GLUSTER` != "" ]]; then
    export GLUSTER_AVAILABLE=1
else
    export GLUSTER_AVAILABLE=0
fi

# reinitializing log
sudo rm -f /tmp/zeppelin_install_log

echo " - Configuring host spark config part"
. ./setupSparkCommon.sh $SELF_IP_ADDRESS $GLUSTER_AVAILABLE
if [[ $? != 0 ]]; then
    echo "Spark Common configuration part failed !"
    exit -20
fi

echo " - Configuring host flink config part"
. ./setupFlinkCommon.sh $SELF_IP_ADDRESS $GLUSTER_AVAILABLE
if [[ $? != 0 ]]; then
    echo "FLink Common configuration part failed !"
    exit -20
fi

echo " - Configuring host ElasticSearch config part"
. ./setupESCommon.sh $SELF_IP_ADDRESS $GLUSTER_AVAILABLE
if [[ $? != 0 ]]; then
    echo "ES Common configuration part failed !"
    exit -20
fi

echo " - Configuring host logstash config part"
. ./setupLogstashCommon.sh $SELF_IP_ADDRESS $GLUSTER_AVAILABLE
if [[ $? != 0 ]]; then
    echo "Logstash Common configuration part failed !"
    exit -20
fi


echo " - Installing setupZeppelinGlusterShares.sh to /usr/local/sbin"
sudo cp setupZeppelinGlusterShares.sh /usr/local/sbin/
sudo chmod 755 /usr/local/sbin/setupZeppelinGlusterShares.sh


echo " - Building container zeppelin"
build_container zeppelin zeppelin /tmp/zeppelin_install_log

echo " - Creating shared directory"
sudo mkdir -p /var/run/zeppelin
sudo chown -R spark /var/run/zeppelin
sudo mkdir -p /var/log/zeppelin
sudo chown -R spark /var/log/zeppelin
sudo mkdir -p /var/lib/zeppelin
sudo mkdir -p /var/lib/zeppelin/notebooks
sudo mkdir -p /var/lib/zeppelin/data
sudo chown -R spark /var/lib/zeppelin

#sudo mkdir -p /usr/local/etc/zeppelin
#sudo chown -R zeppelin /usr/local/etc/zeppelin

# create and start container
echo " - Running docker container"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -d --name zeppelin \
        -v /var/log/zeppelin:/var/log/zeppelin \
        -v /var/run/zeppelin:/var/run/zeppelin \
        -v /var/lib/zeppelin:/var/lib/zeppelin \
        -i \
        -t eskimo:zeppelin bash >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -2

#        -v /var/lib/zeppelin:/var/lib/zeppelin \
#        -v /usr/local/etc/zeppelin:/usr/local/etc/zeppelin \

# connect to container
#docker exec -it zeppelin bash


echo " - Configuring zeppelin container (config spark script)"
docker exec zeppelin bash /scripts/inContainerSetupSparkCommon.sh $spark_user_id $SELF_IP_ADDRESS \
        | tee -a /tmp/zeppelin_install_log 2>&1
if [[ `tail -n 1 /tmp/zeppelin_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat /tmp/zeppelin_install_log
    exit -100
fi

echo " - Configuring zeppelin container (config flink script)"
docker exec zeppelin bash /scripts/inContainerSetupFlinkCommon.sh $flink_user_id $SELF_IP_ADDRESS \
        | tee -a /tmp/zeppelin_install_log 2>&1
if [[ `tail -n 1 /tmp/zeppelin_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat /tmp/zeppelin_install_log
    exit -100
fi

echo " - Configuring zeppelin container"
docker exec zeppelin bash /scripts/inContainerSetupZeppelin.sh $SELF_IP_ADDRESS | tee -a /tmp/zeppelin_install_log 2>&1
if [[ `tail -n 1 /tmp/zeppelin_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat /tmp/zeppelin_install_log
    exit -101
fi

#echo " - TODO"
#docker exec -it zeppelin TODO

# Hack for btrfs support : need to unmount gluster shares otherwise cp command goes nuts
# https://github.com/moby/moby/issues/38252
if [[ `cat /etc/fstab | grep ' / ' | grep 'btrfs'` != "" ]]; then
    echo " - Hack for BTRFS : need to unmount gluster shares before copying files to container"
    if [[ `grep /var/lib/spark/data /etc/mtab` != "" ]]; then
        echo "   + umounting /var/lib/spark/data"
        sudo umount /var/lib/spark/data
    fi
    if [[ `grep /var/lib/spark/eventlog /etc/mtab` != "" ]]; then
        echo "   + umounting /var/lib/spark/eventlog"
        sudo umount /var/lib/spark/eventlog
    fi
    if [[ `grep /var/lib/flink/data /etc/mtab` != "" ]]; then
        echo "   + umounting /var/lib/flink/data"
        sudo umount /var/lib/flink/data
    fi
    if [[ `grep /var/lib/flink/completed_jobs /etc/mtab` != "" ]]; then
        echo "   + umounting /var/lib/flink/completed_jobs"
        sudo umount /var/lib/flink/completed_jobs
    fi
    if [[ `grep /var/lib/logstash/data /etc/mtab` != "" ]]; then
        echo "   + umounting /var/lib/logstash/data"
        sudo umount /var/lib/logstash/data
    fi
    if [[ `grep /var/lib/zeppelin/data /etc/mtab` != "" ]]; then
        echo "   + umounting /var/lib/zeppelin/data"
        sudo umount /var/lib/zeppelin/data
    fi
fi

echo " - Copying Topology Injection Script (Spark)"
docker cp $SCRIPT_DIR/inContainerInjectTopologySpark.sh zeppelin:/usr/local/sbin/inContainerInjectTopologySpark.sh >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? /tmp/zeppelin_install_log -20

docker exec --user root zeppelin bash -c "chmod 755 /usr/local/sbin/inContainerInjectTopologySpark.sh" >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? /tmp/zeppelin_install_log -21

echo " - Copying Topology Injection Script (Flink)"
docker cp $SCRIPT_DIR/inContainerInjectTopologyFlink.sh zeppelin:/usr/local/sbin/inContainerInjectTopologyFlink.sh >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? /tmp/zeppelin_install_log -20

docker exec --user root zeppelin bash -c "chmod 755 /usr/local/sbin/inContainerInjectTopologyFlink.sh" >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? /tmp/zeppelin_install_log -21

echo " - Copying Service Start Script"
docker cp $SCRIPT_DIR/inContainerStartService.sh zeppelin:/usr/local/sbin/inContainerStartService.sh >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? /tmp/zeppelin_install_log -22

docker exec --user root zeppelin bash -c "chmod 755 /usr/local/sbin/inContainerStartService.sh" >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? /tmp/zeppelin_install_log -24

echo " - Copying settingsInjector.sh Script"
docker cp $SCRIPT_DIR/settingsInjector.sh zeppelin:/usr/local/sbin/settingsInjector.sh >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? /tmp/zeppelin_install_log -23

docker exec --user root zeppelin bash -c "chmod 755 /usr/local/sbin/settingsInjector.sh" >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? /tmp/zeppelin_install_log -24



echo " - Copying Topology Injection Script (Zeppelin)"
docker cp $SCRIPT_DIR/inContainerInjectTopologyZeppelin.sh zeppelin:/usr/local/sbin/inContainerInjectTopologyZeppelin.sh >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -20

docker exec --user root zeppelin bash -c "chmod 755 /usr/local/sbin/inContainerInjectTopologyZeppelin.sh" >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -21


# TODO : if /usr/local/lib/bin/logstash-cli is found, then copy it to container
# FIXME


echo " - Committing changes to local template and exiting container zeppelin"
commit_container zeppelin /tmp/zeppelin_install_log

echo " - Installing and checking systemd service file"
install_and_check_service_file zeppelin /tmp/zeppelin_install_log

echo " - Waiting for Zeppelin availability"
function wait_forZeppelin() {
    for i in `seq 0 1 60`; do
        sleep 2
        eval `curl -w "\nZEPPELIN_HTTP_CODE=%{http_code}" "http://localhost:38080/api/notebook" 2>/dev/null | grep ZEPPELIN_HTTP_CODE`
        if [[ $ZEPPELIN_HTTP_CODE == 200 ]]; then
            echo " - Zeppelin is available."
            break
        fi
    done
}

wait_forZeppelin

## FIXME . temporary hack replacing the API import below by an raw import of the samples to address the bug in zeppelin
## import in 0.9-SNAPSHOT
#echo " - Importing Zeppelin Sample notebooks"
#sleep 5 # wait a little more
#for i in `find ./samples/`; do
#    if [[ ! -d $i ]]; then
#        echo "   + importing $i"
#        curl -XPOST -H "Content-Type: application/json" \
#                http://localhost:38080/api/notebook/import \
#                -d @"$i" >> /tmp/zeppelin_install_log 2>&1
#        fail_if_error $? "/tmp/zeppelin_install_log" -21
#    fi
#done

echo " - HACK stopping zeppellin"
sudo systemctl stop zeppelin >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? /tmp/zeppelin_install_log -30

sleep 5

echo " - HACK raw import of samples"
sudo cp ./HACK_temp_samples/eskimo_samples.tgz /var/lib/zeppelin/notebooks/
cd /var/lib/zeppelin/notebooks/

echo " - HACK extract of samples"
sudo tar xvfz eskimo_samples.tgz
sudo chown -R spark *
sudo rm -Rf eskimo_samples.tgz

echo " - HACK restarting zeppellin"
sudo systemctl start zeppelin >> /tmp/zeppelin_install_log 2>&1
fail_if_error $? /tmp/zeppelin_install_log -31


