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

export DONT_MANAGE_IPS_AND_HOSTS=true

echo " - Injecting topology (Spark)"
. /usr/local/sbin/inContainerInjectTopologySpark.sh

echo " - Injecting topology (Flink)"
. /usr/local/sbin/inContainerInjectTopologyFlink.sh

echo " - Injecting topology (Zeppelin)"
. /usr/local/sbin/inContainerInjectTopologyZeppelin.sh

echo " - Creating required directory /var/lib/spark/tmp (as spark)"
sudo /bin/mkdir -p /var/lib/spark/tmp
sudo /bin/chown -R spark /var/lib/spark

echo " - Creating required directory /var/run/spark/zeppelin (as spark)"
sudo /bin/mkdir -p /var/run/spark/zeppelin
sudo /bin/chown spark /var/run/spark/zeppelin

echo " - Inject settings (Zeppelin)"
/usr/local/sbin/settingsInjector.sh zeppelin

echo " - Inject settings (spark-runtime)"
/usr/local/sbin/settingsInjector.sh spark-runtime

echo " - Inject settings (flink-app-master)"
/usr/local/sbin/settingsInjector.sh flink-app-master

# Zeppelin connects on gluster on same node
export MASTER_IP_ADDRESS=$SELF_IP_ADDRESS

echo " - Mounting gluster shares for zeppelin"
echo "   + mounting spark shares"
sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh spark_data /var/lib/spark/data spark
sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh spark_eventlog /var/lib/spark/eventlog spark


echo "   + Checking if needed to mount logstash shares ?"
if [[ $(curl -XGET "http://$MASTER_IP_ADDRESS:28901/egmi/command?command=volume&subcommand=list&options=" 2>/dev/null | grep "logstash") != "" ]]; then
    echo "   + mounting logstash shares"
    sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh logstash_data /var/lib/elasticsearch/logstash/data spark
fi


echo "   + Checking if needed to mount flink shares ?"
if [[ $(curl -XGET "http://$MASTER_IP_ADDRESS:28901/egmi/command?command=volume&subcommand=list&options=" 2>/dev/null | grep "flink") != "" ]]; then
    echo "   + mounting flink shares"
    sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh flink_data /var/lib/flink/data spark
    sudo /bin/bash /usr/local/sbin/inContainerMountGluster.sh flink_completed_jobs /var/lib/flink/completed_jobs spark
fi

echo " - creating zeppelin notebook service if it does not exist"
mkdir -p /var/lib/spark/data/zeppelin/notebooks


echo " - Injecting isolation configuration from settings"

# sourcing custom config
. /usr/local/lib/zeppelin/conf/eskimo_settings.conf

if [[ $zeppelin_note_isolation == "per_note" ]]; then

   sed -i s/"ESKIMO_PER_NOTE_ISOLATION"/"isolated"/g /usr/local/lib/zeppelin/conf/interpreter.json
   sed -i s/"ESKIMO_PER_USER_ISOLATION"/""/g /usr/local/lib/zeppelin/conf/interpreter.json

else

   sed -i s/"ESKIMO_PER_NOTE_ISOLATION"/"shared"/g /usr/local/lib/zeppelin/conf/interpreter.json
   sed -i s/"ESKIMO_PER_USER_ISOLATION"/"shared"/g /usr/local/lib/zeppelin/conf/interpreter.json
fi


echo " - Tuning zeppelin-site.xml"

if [[ $zeppelin_jobmanager_enable == "true" ]]; then
    sed -i s/"ESKIMO_JOBMANAGER_ENABLE"/"true"/g /usr/local/lib/zeppelin/conf/zeppelin-site.xml
else
    sed -i s/"ESKIMO_JOBMANAGER_ENABLE"/"false"/g /usr/local/lib/zeppelin/conf/zeppelin-site.xml
fi


echo " - Start glusterMountCheckerPeriodic.sh script"
/bin/bash /usr/local/sbin/glusterMountCheckerPeriodic.sh &
export GLUSTER_MOUNT_CHECKER_PID=$!

echo " - Launching Watch Dog on glusterMountCheckerPeriodic remote server"
/usr/local/sbin/containerWatchDog.sh \
     $GLUSTER_MOUNT_CHECKER_PID \
     "ps -efl | grep java | grep org.apache.zeppelin.server.ZeppelinServer | grep -v bash | sed -E 's/[0-9a-zA-Z]{1} [0-9a-zA-Z]{1} spark *([0-9]+).*/\1/'" \
     /var/log/spark/zeppelin/gluster-mount-checker-periodic-watchdog.log &

echo " - Starting zeppelin service (asynchronously)"
bash -c 'cd /home/spark && /usr/local/lib/zeppelin/bin/zeppelin.sh' &
export ZEPPELIN_PID=$!

echo " - Checking if samples neeed to be installed"
if [[ ! -f /var/lib/spark/data/zeppelin/samples_installed_flag.marker ]]; then

    echo " - Waiting for Zeppelin availability"
    function wait_forZeppelin() {
        for i in $(seq 0 1 120); do
            sleep 2
            eval "$(curl -w "\nZEPPELIN_HTTP_CODE=%{http_code}" "http://localhost:38080/api/notebook" 2>/dev/null | grep ZEPPELIN_HTTP_CODE)"
            if [[ $ZEPPELIN_HTTP_CODE == 200 ]]; then
                echo " - Zeppelin is available."
                break
            fi
        done
    }

    wait_forZeppelin

    sudo /bin/rm -Rf /tmp/zeppelin_import_log

    # import in 0.9-SNAPSHOT
    echo " - Importing Zeppelin Sample notebooks"
    set +e
    sleep 5 # wait a little more
    for i in $(find /usr/local/lib/zeppelin/eskimo_samples); do
        if [[ ! -d $i ]]; then
            echo "   + importing $i"
            curl -XPOST -H "Content-Type: application/json" \
                    http://localhost:38080/api/notebook/import \
                    -d @"$i" >> /tmp/zeppelin_import_log 2>&1
            if [[ $? != 0 ]]; then
                echo "Failed to import $i"
                cat /tmp/zeppelin_import_log
                exit 101
            fi
        fi
    done
    set -e

    touch /var/lib/spark/data/zeppelin/samples_installed_flag.marker
fi

echo " - Now waiting on zeppelin process"
wait $ZEPPELIN_PID
