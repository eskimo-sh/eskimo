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

echo " - Injecting topology (Spark)"
. /usr/local/sbin/inContainerInjectTopologySpark.sh

echo " - Injecting topology (Flink)"
. /usr/local/sbin/inContainerInjectTopologyFlink.sh

echo " - Injecting topology (Zeppelin)"
. /usr/local/sbin/inContainerInjectTopologyZeppelin.sh

echo " - Creating required directory /var/lib/spark/tmp (as spark)"
sudo /bin/mkdir /var/lib/spark/tmp
sudo /bin/chown spark /var/lib/spark/tmp

echo " - Creating required directory /var/run/spark/zeppelin (as spark)"
sudo /bin/mkdir -p /var/run/spark/zeppelin
sudo /bin/chown spark /var/run/spark/zeppelin

echo " - Basic mesos configuration for mesos Scheduler (set env vars)"
# point to your libmesos.so if you use Mesos
export MESOS_NATIVE_JAVA_LIBRARY=/usr/local/lib/mesos/lib/libmesos.so
# Set external IP for Mesos Scheduler used by Flink / Marathon / etc. to reach mesos (required for mesos callback to succeed)
export LIBPROCESS_ADVERTISE_IP=$SELF_IP_ADDRESS
# Adding it to spark-env as well
bash -c "echo -e \"\n# Set external IP for Mesos Scheduler used by Flink / Marathon / etc. to reach mesos (required for mesos callback to succeed)\"  >> /usr/local/lib/spark/conf/spark-env.sh"
bash -c "echo -e \"export LIBPROCESS_ADVERTISE_IP=$SELF_IP_ADDRESS\"  >> /usr/local/lib/spark/conf/spark-env.sh"

echo " - Inject settings (Zeppelin)"
/usr/local/sbin/settingsInjector.sh zeppelin

echo " - Inject settings (spark-executor)"
/usr/local/sbin/settingsInjector.sh spark-executor

echo " - Inject settings (flink-app-master)"
/usr/local/sbin/settingsInjector.sh flink-app-master


# Ensure gluster is available
if [[ -f /etc/eskimo_topology.sh && `cat /etc/eskimo_topology.sh  | grep MASTER_GLUSTER` == "" ]]; then
    echo "ERROR : No Gluster master defined"
    exit -20
fi

export MASTER_IP_ADDRESS=`eval echo "\$"$(echo MASTER_GLUSTER_$SELF_IP_ADDRESS | tr -d .)`

echo " - Mounting gluster shares for zeppelin"
echo "   + mounting spark shares"
sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh spark_data /var/lib/spark/data spark
sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh spark_eventlog /var/lib/spark/eventlog spark


if [[ `curl -XGET "http://$MASTER_IP_ADDRESS:18999/?command=volume&subcommand=list" 2>/dev/null | grep "logstash"` != "" ]]; then
    echo "   + mounting logstash shares"
    sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh logstash_data /var/lib/elasticsearch/logstash/data spark
fi


if [[ `curl -XGET "http://$MASTER_IP_ADDRESS:18999/?command=volume&subcommand=list" 2>/dev/null | grep "flink"` != "" ]]; then
    echo "   + mounting flink shares"
    sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh flink_data /var/lib/flink/data spark
    sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh flink_completed_jobs /var/lib/flink/completed_jobs spark
fi

echo " - ceating zeppelin notebokk service if it does not exist"
mkdir -p /var/lib/spark/data/zeppelin/notebooks

echo " - Checking if samples neeed to be installed"
if [[ ! -f /var/lib/spark/data/zeppelin/samples_installed_flag.marker ]]; then

    echo " - Installing raw samples "

    echo "   + Creating temp dir /tmp/eskimo_samples"
    mkdir /tmp/eskimo_samples

    echo "   + Copying archive there"
    cp /usr/local/lib/zeppelin/eskimo_samples.tgz /tmp/eskimo_samples/

    echo "   + changing dir for temp dir"
    cd  /tmp/eskimo_samples/

    echo "   + Extracting archive"
    tar xvfz eskimo_samples.tgz > /tmp/eskimo_samples_extract.log 2>&1;

    echo "   + for each of them, see if it needs to be copied"
    IFS=$'\n'
    for i in `ls -1 *.zpln`; do

        echo "      - extracting notebook name"
        notebook_name=`echo $i | cut -d '_' -f 1`

        echo "      - checking if $notebook_name is already installed"
        if [[ `find /var/lib/spark/data/zeppelin/notebooks/ -name "$notebook_name*" 2>/dev/null` == "" ]]; then

            echo "      - installing $notebook_name "
            cp $i /var/lib/spark/data/zeppelin/notebooks/
        fi
    done

    touch /var/lib/spark/data/zeppelin/samples_installed_flag.marker
fi

echo " - Start glusterMountCheckerPeriodic.sh script"
/bin/bash /usr/local/sbin/glusterMountCheckerPeriodic.sh &
export GLUSTER_MOUNT_CHECKER_PID=$!

echo " - Launching Watch Dog on glusterMountCheckerPeriodic remote server"
/usr/local/sbin/containerWatchDog.sh \
     $GLUSTER_MOUNT_CHECKER_PID \
     "ps -efl | grep java | grep org.apache.zeppelin.server.ZeppelinServer | grep -v bash | sed -E 's/[0-9a-zA-Z]{1} [0-9a-zA-Z]{1} spark *([0-9]+).*/\1/'" \
     /var/log/spark/zeppelin/gluster-mount-checker-periodic-watchdog.log &

echo " - Starting service"
bash -c 'cd /home/spark && /usr/local/lib/zeppelin/bin/zeppelin.sh'
