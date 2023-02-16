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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


SPARK_USER_ID=$1
if [[ $SPARK_USER_ID == "" ]]; then
    echo " - Didn't get SPARK User ID as argument"
    exit 2
fi

SPARK_CONTAINER_TAG=$2


echo "-- SETTING UP SPARK (COMMON PART) --------------------------------------"

echo " - Creating spark user (if not exist) in container"
set +e
spark_user_id=$(id -u spark 2>/dev/null)
set -e
if [[ $spark_user_id == "" ]]; then
    useradd -u $SPARK_USER_ID spark
elif [[ $spark_user_id != $SPARK_USER_ID ]]; then
    echo "Docker SPARK USER ID is $spark_user_id while requested USER ID is $SPARK_USER_ID"
    exit 2
fi

echo " - Enabling spark user to mount gluster shares (sudo)"
sudo bash -c "echo \"spark  ALL = NOPASSWD: /bin/bash /usr/local/sbin/inContainerMountGluster.sh *\" >> /etc/sudoers.d/spark"

echo " - Enabling spark user to create /var/lib/spark/tmp and chown it"
sudo bash -c "echo \"spark  ALL = NOPASSWD: /bin/mkdir /var/lib/spark/tmp\" >> /etc/sudoers.d/spark"
sudo bash -c "echo \"spark  ALL = NOPASSWD: /bin/mkdir -p /var/lib/spark/tmp\" >> /etc/sudoers.d/spark"
sudo bash -c "echo \"spark  ALL = NOPASSWD: /bin/chown spark /var/lib/spark/tmp\" >> /etc/sudoers.d/spark"
sudo bash -c "echo \"spark  ALL = NOPASSWD: /bin/chown -R spark /var/lib/spark\" >> /etc/sudoers.d/spark"
sudo bash -c "echo \"spark  ALL = NOPASSWD: /bin/chown -R spark. /var/lib/spark\" >> /etc/sudoers.d/spark"
sudo bash -c "echo \"spark  ALL = NOPASSWD: /bin/bash /usr/local/sbin/glusterMountChecker.sh\" >> /etc/sudoers.d/spark"

echo " - Enabling spark run kube_do"
sudo bash -c "echo \"spark  ALL = NOPASSWD:SETENV: /bin/bash /usr/local/sbin/import-hosts.sh\" >> /etc/sudoers.d/spark"

echo " - Enabling spark user to use host_spark"
sudo bash -c "echo \"spark  ALL = NOPASSWD: /bin/rm -Rf /var/lib/spark\" >> /etc/sudoers.d/spark"
sudo bash -c "echo \"spark  ALL = NOPASSWD: /bin/ln -s /var/lib/host_spark /var/lib/spark\" >> /etc/sudoers.d/spark"


echo " - Creating user spark home directory"
mkdir -p /home/spark
mkdir -p /home/spark/.kube
chown -R spark /home/spark

echo " - Creating gluster infrastructure directories"
sudo mkdir -p /var/log/gluster/
sudo mkdir -p /var/lib/gluster/

sudo chmod -R 777 /var/log/gluster/
sudo chmod -R 777 /var/lib/gluster/


echo " - Simlinking spark binaries to /usr/local/bin"
# shellcheck disable=SC2006
for i in `ls -1 /usr/local/lib/spark/bin`; do
    create_binary_wrapper /usr/local/lib/spark/bin/$i /usr/local/bin/$i
done

echo " - Simlinking spark system binaries to /usr/local/sbin"
sudo ln -s /usr/local/lib/spark/sbin/slaves.sh /usr/local/sbin/slaves.sh
for i in $(ls -1 /usr/local/lib/spark/sbin/spark*); do
    create_binary_wrapper $i "/usr/local/sbin/$(basename $i)"
done
for i in $(ls -1 /usr/local/lib/spark/sbin/start*); do
    create_binary_wrapper $i "usr/local/sbin/spark-$(basename $i)"
done
for i in $(ls -1 /usr/local/lib/spark/sbin/stop*); do
    create_binary_wrapper $i "/usr/local/sbin/spark-$(basename $i)"
done

echo " - Simlinking spark logs to /var/log/"
sudo mkdir -p /var/log/spark
sudo rm -Rf /usr/local/lib/spark/logs
sudo ln -s /var/log/spark /usr/local/lib/spark/logs
sudo chown -R spark /var/log/spark
sudo chmod -R 777 /var/log/spark

echo " - Defining SPARK_HOME variable"
# etc profile
sudo bash -c "echo -e \"\n# Environment variables required for spark\" >> /etc/profile"
sudo bash -c "echo -e \"\nexport SPARK_HOME=/usr/local/lib/spark/\" >> /etc/profile"
# etc bash.bashrc
sudo bash -c "echo -e \"\n# Environment variables required for spark\" >> /etc/bash.bashrc"
sudo bash -c "echo -e \"\nexport SPARK_HOME=/usr/local/lib/spark/\" >> /etc/bash.bashrc"

echo " - Creating spark environment file"
sudo rm -Rf /usr/local/lib/spark/conf/spark-env.sh

sudo bash -c "echo -e \"\n#Important configuration directories \"  >> /usr/local/lib/spark/conf/spark-env.sh"
sudo bash -c "echo -e \"export SPARK_CONF_DIR=/usr/local/lib/spark/conf\"  >> /usr/local/lib/spark/conf/spark-env.sh"
sudo bash -c "echo -e \"export SPARK_LOG_DIR=/usr/local/lib/spark/logs\"  >> /usr/local/lib/spark/conf/spark-env.sh"
sudo bash -c "echo -e \"export KUBECONFIG=/home/spark/.kube/config\"  >> /usr/local/lib/spark/conf/spark-env.sh"


echo " - Creating spark default configuration file"
sudo rm -Rf /usr/local/lib/spark/conf/spark-defaults.conf

sudo bash -c "echo -e \"\n#Activating EventLog stuff\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.eventLog.enabled=true\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.eventLog.dir=/var/lib/spark/eventlog\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"\n#Default serializer\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.serializer=org.apache.spark.serializer.KryoSerializer\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"\n#Limiting the driver (client) memory\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.driver.memory=800m\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

# XXX change to 'cluster' to attempt to run spark driver in pod as well
sudo bash -c "echo -e \"spark.submit.deployMode=client\" >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"\n# Number of times to retry before an RPC task gives up. \"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"#An RPC task will run at most times of this number.\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.rpc.numRetries=5\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"\n# Duration for an RPC ask operation to wait before retrying.\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.rpc.retry.wait=5s\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"\n#The scheduling mode between jobs submitted to the same SparkContext.\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"#Can be FIFO or FAIR. FAIR Seem not to work well with mesos\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"#(FIFO is the default BTW ...)\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.scheduler.mode=FAIR\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"\n#How long to wait to launch a data-local task before giving up and launching it on a less-local node.\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.locality.wait=20s\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"


echo " - Configuring dynamic allocation and external shuffle service"
sudo bash -c "echo -e \"\n# Configuring dynamic allocation\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"# (See Spark configuration page online for more information)\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.dynamicAllocation.enabled=true\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"#(Caution here : small values cause issues. I have executors killed with 10s for instance)\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.dynamicAllocation.executorIdleTimeout=200s\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.dynamicAllocation.cachedExecutorIdleTimeout=300s\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.dynamicAllocation.schedulerBacklogTimeout=5s\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"\n# Configuring spark shuffle service (required for dynamic allocation)\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.dynamicAllocation.shuffleTracking.enabled=true\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.dynamicAllocation.shuffleTracking.timeout=300s\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"\n# Directory to use for scratch space in Spark, including map output files and RDDs that get stored on disk. \"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"# Spark Mesos Shuffle service and spark executors need to have access to the same folder there cross containers. \"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.local.dir=/var/lib/spark/tmp/\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

if [[ $SPARK_CONTAINER_TAG == "" ]]; then
    echo " - Finding last spark-runtime container tag"
    . /etc/eskimo_topology.sh
    SPARK_CONTAINER_TAG=0
    TAGS=$(curl -XGET http://$MASTER_KUBE_MASTER_1:5000/v2/spark-runtime/tags/list 2>/dev/null | jq -r -c  ".tags | .[]" 2>/dev/null)
    if [[ $? == 0 ]]; then
        for tag in $TAGS; do
            if [[ $tag != "latest" ]]; then
                if [[ "$SPARK_CONTAINER_TAG" -lt "$tag" ]]; then
                    export SPARK_CONTAINER_TAG=$tag
                fi
            fi
        done
    fi
    if [[ ( $SPARK_CONTAINER_TAG == "" || $SPARK_CONTAINER_TAG == 0 ) && -z $TEST_MODE ]]; then
        echo "Couldn't find last spark-runtime image tag "
        exit 1
    fi
fi


echo " - Defining Eskimo Spark docker container"
sudo bash -c "echo -e \"\n#Defining docker image to be used for spark executors\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.container.image=kubernetes.registry:5000/spark-runtime:$SPARK_CONTAINER_TAG\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"spark.kubernetes.file.upload.path=/var/lib/spark/data\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.driver.podTemplateFile=/usr/local/lib/spark/conf/spark-pod-template.yaml\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.executor.podTemplateFile=/usr/local/lib/spark/conf/spark-pod-template.yaml\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.authenticate.driver.serviceAccountName=eskimo\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.namespace=eskimo\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"spark.kubernetes.executor.volumes.hostPath.eskimotopology.mount.path=/etc/eskimo_topology.sh\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.executor.volumes.hostPath.eskimotopology.options.path=/etc/eskimo_topology.sh\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.executor.volumes.hostPath.eskimotopology.mount.readOnly=true\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"spark.kubernetes.executor.volumes.hostPath.eskimoservicessettings.mount.path=/etc/eskimo_services-settings.json\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.executor.volumes.hostPath.eskimoservicessettings.options.path=/etc/eskimo_services-settings.json\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.executor.volumes.hostPath.eskimoservicessettings.mount.readOnly=true\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"spark.kubernetes.driver.volumes.hostPath.eskimotopology.mount.path=/etc/eskimo_topology.sh\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.driver.volumes.hostPath.eskimotopology.options.path=/etc/eskimo_topology.sh\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.driver.volumes.hostPath.eskimotopology.mount.readOnly=true\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

sudo bash -c "echo -e \"spark.kubernetes.driver.volumes.hostPath.eskimoservicessettings.mount.path=/etc/eskimo_services-settings.json\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.driver.volumes.hostPath.eskimoservicessettings.options.path=/etc/eskimo_services-settings.json\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"
sudo bash -c "echo -e \"spark.kubernetes.driver.volumes.hostPath.eskimoservicessettings.mount.readOnly=true\"  >> /usr/local/lib/spark/conf/spark-defaults.conf"

echo " - Creating hive-site.xml configuration file"
cat > /tmp/hive-site.xml <<- "EOF"
<?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
<property>
  <name>javax.jdo.option.ConnectionURL</name>
  <value>jdbc:derby:;databaseName=/var/lib/spark/metastore_db;create=true</value>
  <description>JDBC connect string for a JDBC metastore in /var/lib/spark/metastore_db</description>
</property>
<property>
  <name>javax.jdo.option.ConnectionDriverName</name>
  <value>org.apache.derby.jdbc.EmbeddedDriver</value>
</property>
</configuration>
EOF
sudo mv /tmp/hive-site.xml /usr/local/lib/spark/conf/

echo " - Creating spark-pod-template.yaml"
cat > /tmp/spark-pod-template.yaml <<- "EOF"
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: spark
    image: kubernetes.registry:5000/spark-runtime
    securityContext:
      privileged: true
      allowPrivilegeEscalation: true
      runAsUser: 3302
      runAsGroup: 3302
  #hostNetwork: true
EOF
sudo mv /tmp/spark-pod-template.yaml /usr/local/lib/spark/conf/
chmod 755 /usr/local/lib/spark/conf/


echo " - Enabling spark to change configuration at runtime"
chown -R spark. "/usr/local/lib/spark/conf/"

echo " - Copying log4j.properties from template"
if [[ -f /usr/local/lib/spark/conf/log4j2.properties.template ]]; then
    sudo cp /usr/local/lib/spark/conf/log4j2.properties.template /usr/local/lib/spark/conf/log4j2.properties
else
    sudo cp /usr/local/lib/spark/conf/log4j.properties.template /usr/local/lib/spark/conf/log4j.properties
fi


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"
