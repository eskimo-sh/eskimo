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
sudo rm -f /tmp/spark_executor_install_log

# build

# Initially this was a Hack for BTRFS support :
#   - need to unmount gluster shares otherwise cp command goes nuts
#   - https://github.com/moby/moby/issues/38252
# But eventually I need to do this in anyway to make sure everything is preoperly re-installed
# I need to make sure I'm doing this before attempting to recreate the directories
preinstall_unmount_gluster_share /var/lib/spark/eventlog
preinstall_unmount_gluster_share /var/lib/spark/data

echo " - Configuring host spark common part"
. ./setupCommon.sh
if [[ $? != 0 ]]; then
    echo "Common configuration part failed !"
    exit -20
fi

echo " - Installing setupSparkGlusterShares.sh to /usr/local/sbin"
sudo cp setupSparkGlusterShares.sh /usr/local/sbin/
sudo chmod 755 /usr/local/sbin/setupSparkGlusterShares.sh



# spark executor part
# ----------------------------------------------------------------------------------------------------------------------
echo " ---> Spark executor part"

echo " - Building docker container for spark executor"
build_container spark-executor spark /tmp/spark_executor_install_log

# create and start container
echo " - Running docker container to configure spark executor"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -d \
        -v /var/log/spark:/var/log/spark \
        -v /var/lib/spark:/var/lib/spark \
        --name spark-executor \
        -i \
        -t eskimo:spark-executor bash >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? "/tmp/spark_executor_install_log" -2

# connect to container
#docker exec -it spark bash

echo " - Configuring spark-executor container (config script)"
docker exec spark-executor bash /scripts/inContainerSetupSparkCommon.sh $spark_user_id $SELF_IP_ADDRESS \
        | tee -a /tmp/spark_executor_install_log 2>&1
if [[ `tail -n 1 /tmp/spark_executor_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat /tmp/spark_executor_install_log
    exit -100
fi

echo " - Configuring spark-executor container"
docker exec spark-executor bash /scripts/inContainerSetupSpark.sh | tee -a /tmp/spark_executor_install_log 2>&1
if [[ `tail -n 1 /tmp/spark_executor_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat /tmp/spark_executor_install_log
    exit -101
fi

#echo " - TODO"
#docker exec -it spark TODO/tmp/logstash_install_log


echo " - Copying Topology Injection Script (common)"
docker cp $SCRIPT_DIR/inContainerInjectTopology.sh spark-executor:/usr/local/sbin/inContainerInjectTopology.sh >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? "/tmp/spark_executor_install_log" -20

docker exec --user root spark-executor bash -c "chmod 755 /usr/local/sbin/inContainerInjectTopology.sh" >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? "/tmp/spark_executor_install_log" -21

echo " - Copying settingsInjector.sh Script"
docker cp $SCRIPT_DIR/settingsInjector.sh spark-executor:/usr/local/sbin/settingsInjector.sh >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? /tmp/spark_executor_install_log -23

docker exec --user root spark-executor bash -c "chmod 755 /usr/local/sbin/settingsInjector.sh" >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? /tmp/spark_executor_install_log -24


echo " - Committing changes to local template and exiting container spark"
commit_container spark-executor /tmp/spark_executor_install_log

echo " - Copying spark command line programs docker wrappers to /usr/local/bin"
for i in `find ./spark_wrappers -mindepth 1`; do
    sudo cp $i /usr/local/bin
    filename=`echo $i | cut -d '/' -f 3`
    sudo chmod 755 /usr/local/bin/$filename
done

echo " - Creating spark-executors containers cleaner"
sudo bash -c 'echo "for i in \`docker ps -aq --no-trunc -f status=exited -f ancestor=eskimo:spark-executor\` ; do docker rm --force \$i; done" > /usr/local/sbin/clean-spark-executor-containers.sh'
sudo chmod 755 /usr/local/sbin/clean-spark-executor-containers.sh


if [[ `sudo crontab -u root -l 2>/dev/null | grep clean-spark-executor-containers.sh` == "" ]]; then
    echo " - Scheduling spark-executors containers cleaner"
    sudo rm -f /tmp/crontab
    sudo bash -c "crontab -u root -l >> /tmp/crontab 2>/dev/null"
    sudo bash -c "echo \"* * * * * bash /usr/local/sbin/clean-spark-executor-containers.sh >> /var/log/spark/clean-spark-executor-containers.log 2>&1\" >> /tmp/crontab"
    sudo crontab -u root /tmp/crontab
fi


# spark mesos shuffle service part
# ----------------------------------------------------------------------------------------------------------------------
echo " ---> Spark mesos shuffle sevice part"

echo " - Building docker container for spark mesos shuffle service"
build_container spark-mesos-shuffle-service spark /tmp/spark_executor_install_log

# create and start container
echo " - Running docker container to configure spark executor"
docker run \
        -v $PWD:/scripts \
        -v $PWD/../common:/common \
        -d \
        -v /var/log/spark:/var/log/spark \
        -v /var/lib/spark:/var/lib/spark \
        --name spark-mesos-shuffle-service \
        -i \
        -t eskimo:spark-mesos-shuffle-service bash >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? "/tmp/spark_executor_install_log" -2

# connect to container
#docker exec -it spark bash

echo " - Configuring spark-mesos-shuffle-service container (config script)"
docker exec spark-mesos-shuffle-service bash /scripts/inContainerSetupSparkCommon.sh $spark_user_id $SELF_IP_ADDRESS \
        | tee -a /tmp/spark_executor_install_log 2>&1
if [[ `tail -n 1 /tmp/spark_executor_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat /tmp/spark_executor_install_log
    exit -100
fi

echo " - Configuring spark-mesos-shuffle-service container"
docker exec spark-mesos-shuffle-service bash /scripts/inContainerSetupSparkMesosShuffleService.sh \
        | tee -a /tmp/spark_executor_install_log 2>&1
if [[ `tail -n 1 /tmp/spark_executor_install_log` != " - In container config SUCCESS" ]]; then
    echo " - In container setup script ended up in error"
    cat /tmp/spark_executor_install_log
    exit -101
fi

#echo " - TODO"
#docker exec -it spark TODO

echo " - Copying Topology Injection Script (Common)"
docker cp $SCRIPT_DIR/inContainerInjectTopology.sh spark-mesos-shuffle-service:/usr/local/sbin/inContainerInjectTopology.sh >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? "/tmp/spark_executor_install_log" -20

docker exec --user root spark-mesos-shuffle-service bash -c "chmod 755 /usr/local/sbin/inContainerInjectTopology.sh" >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? "/tmp/spark_executor_install_log" -21

echo " - Copying Topology Injection Script (Mesos Shuffle)"
docker cp $SCRIPT_DIR/inContainerInjectTopologyMesosShuffle.sh spark-mesos-shuffle-service:/usr/local/sbin/inContainerInjectTopologyMesosShuffle.sh >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? "/tmp/spark_executor_install_log" -20

docker exec --user root spark-mesos-shuffle-service bash -c "chmod 755 /usr/local/sbin/inContainerInjectTopologyMesosShuffle.sh" >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? "/tmp/spark_executor_install_log" -21

echo " - Copying settingsInjector.sh Script"
docker cp $SCRIPT_DIR/settingsInjector.sh spark-mesos-shuffle-service:/usr/local/sbin/settingsInjector.sh >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? /tmp/spark_executor_install_log -23

docker exec --user root spark-mesos-shuffle-service bash -c "chmod 755 /usr/local/sbin/settingsInjector.sh" >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? /tmp/spark_executor_install_log -24

echo " - Copying Service Start Script"
docker cp $SCRIPT_DIR/inContainerStartMesosShuffleService.sh spark-mesos-shuffle-service:/usr/local/sbin/inContainerStartMesosShuffleService.sh>> /tmp/spark_executor_install_log 2>&1
fail_if_error $? "/tmp/spark_executor_install_log" -20

docker exec --user root spark-mesos-shuffle-service bash -c "chmod 755 /usr/local/sbin/inContainerStartMesosShuffleService.sh" >> /tmp/spark_executor_install_log 2>&1
fail_if_error $? "/tmp/spark_executor_install_log" -21

echo " - Committing changes to local template and exiting container spark"
commit_container spark-mesos-shuffle-service /tmp/spark_executor_install_log


echo " - Handling spark-executor systemd file (for spark-mesos-shuffle-service)"
install_and_check_service_file spark-executor /tmp/spark_executor_install_log

