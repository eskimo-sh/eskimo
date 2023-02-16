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

FLINK_USER_ID=$1
if [[ $FLINK_USER_ID == "" ]]; then
    echo " - Didn't get FLINK User ID as argument"
    exit 1
fi

FLINK_CONTAINER_TAG=$2


echo "-- SETTING UP FLINK (COMMON PART) --------------------------------------"

echo " - Creating flink user (if not exist) in container"
set +e
flink_user_id=$(id -u flink 2>/dev/null)
set -e
if [[ $flink_user_id == "" ]]; then
    useradd -u $FLINK_USER_ID flink
elif [[ $flink_user_id != $FLINK_USER_ID ]]; then
    echo "Docker FLINK USER ID is $flink_user_id while requested USER ID is $FLINK_USER_ID"
    exit 2
fi

echo " - Enabling flink user to mount gluster shares (sudo)"
sudo bash -c "echo \"flink  ALL = NOPASSWD: /bin/bash /usr/local/sbin/inContainerMountGluster.sh *\" >> /etc/sudoers.d/flink"
sudo bash -c "echo \"flink  ALL = NOPASSWD: /bin/chmod 777 /var/lib/flink/completed_jobs\" >> /etc/sudoers.d/flink"
sudo bash -c "echo \"flink  ALL = NOPASSWD: /bin/chmod 777 /var/lib/flink/data\" >> /etc/sudoers.d/flink"
sudo bash -c "echo \"flink  ALL = NOPASSWD: /bin/bash /usr/local/sbin/glusterMountChecker.sh\" >> /etc/sudoers.d/flink"

echo " - Enabling flink to run kube_do"
sudo bash -c "echo \"flink  ALL = NOPASSWD:SETENV: /bin/bash /usr/local/sbin/import-hosts.sh\" >> /etc/sudoers.d/flink"

echo " - Creating user flink home directory"
mkdir -p /home/flink
mkdir -p /home/flink/.kube
chown flink /home/flink

echo " - Presetting /usr/local/lib/flink/conf/ to flink"
sudo chown -R flink. /usr/local/lib/flink/conf/
sudo chmod -R 777 /usr/local/lib/flink/conf/

echo " - Creating gluster infrastructure directories"
sudo mkdir -p /var/log/gluster/
sudo mkdir -p /var/lib/gluster/

sudo chmod -R 777 /var/log/gluster/
sudo chmod -R 777 /var/lib/gluster/


echo " - Simlinking flink binaries to /usr/local/bin"
for i in $(ls -1 /usr/local/lib/flink/bin); do
    create_binary_wrapper /usr/local/lib/flink/bin/$i /usr/local/bin/$i
done

echo " - Simlinking flink log to /var/log/"
sudo rm -Rf /usr/local/lib/flink/log
sudo ln -s /var/log/flink/log /usr/local/lib/flink/log


# The default directory used for storing the data files and meta data of checkpoints in a Flink supported
# filesystem.
# The storage path must be accessible from all participating processes/nodes(i.e. all TaskManagers and JobManagers).
sed -i s/"# state.savepoints.dir: hdfs:\/\/namenode-host:port\/flink-savepoints"/"state.savepoints.dir: file:\/\/\/var\/lib\/flink\/data\/savepoints"/g \
        /usr/local/lib/flink/conf/flink-conf.yaml

# The default directory for savepoints.
# Used by the state backends that write savepoints to file systems (MemoryStateBackend, FsStateBackend, RocksDBStateBackend).
sed -i s/"# state.checkpoints.dir: hdfs:\/\/namenode-host:port\/flink-checkpoints"/"state.checkpoints.dir: file:\/\/\/var\/lib\/flink\/data\/ckeckpoints"/g \
        /usr/local/lib/flink/conf/flink-conf.yaml

# Directory to upload completed jobs to.
sed -i s/"#jobmanager.archive.fs.dir: hdfs:\/\/\/completed-jobs\/"/"jobmanager.archive.fs.dir: file:\/\/\/var\/lib\/flink\/completed_jobs\/"/g \
        /usr/local/lib/flink/conf/flink-conf.yaml

# Comma separated list of directories to monitor for completed jobs.
sed -i s/"#historyserver.archive.fs.dir: hdfs:\/\/\/completed-jobs\/"/"historyserver.archive.fs.dir: file:\/\/\/var\/lib\/flink\/completed_jobs\/"/g \
        /usr/local/lib/flink/conf/flink-conf.yaml

# uncomment
sed -i s/"#rest.port: 8081"/"rest.port: 8081"/g /usr/local/lib/flink/conf/flink-conf.yaml


echo " - Creating docker-entrypoint.sh"
cat > /tmp/docker-entrypoint.sh <<- "EOF"
#!/usr/bin/env bash

args=("$@")

if [ "$1" = "help" ]; then
    printf "Usage: $(basename "$0") (operator|webhook)\n"
    printf "    Or $(basename "$0") help\n\n"
    exit 0
elif [ "$1" = "operator" ]; then
    echo "Starting Operator"

    exec java -cp /$FLINK_KUBERNETES_SHADED_JAR:/$OPERATOR_JAR $LOG_CONFIG org.apache.flink.kubernetes.operator.FlinkOperator
elif [ "$1" = "webhook" ]; then
    echo "Starting Webhook"

    # Adds the operator shaded jar on the classpath when the webhook starts
    exec java -cp /$FLINK_KUBERNETES_SHADED_JAR:/$OPERATOR_JAR:/$WEBHOOK_JAR $LOG_CONFIG org.apache.flink.kubernetes.operator.admission.FlinkOperatorWebhook
fi

args=("${args[@]}")

# Running command in pass-through mode
exec "${args[@]}"
EOF
sudo mkdir -p /usr/local/lib/flink/kubernetes/
sudo mv /tmp/docker-entrypoint.sh /usr/local/lib/flink/kubernetes/docker-entrypoint.sh
chmod 755 /usr/local/lib/flink/kubernetes/


sudo bash -c "echo -e \"\n\n\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"#==============================================================================\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"# Eskimo Kubernetes Configuration part \"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"#==============================================================================\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"

# FIXME
sudo bash -c "echo -e \"kubernetes.context: eskimo\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"

if [[ $FLINK_CONTAINER_TAG == "" ]]; then
    echo " - Finding last flink-runtime container tag"
    . /etc/eskimo_topology.sh
    FLINK_CONTAINER_TAG=0
    TAGS=$(curl -XGET http://$MASTER_KUBE_MASTER_1:5000/v2/flink-runtime/tags/list 2>/dev/null | jq -r -c  ".tags | .[]" 2>/dev/null)
    if [[ $? == 0 ]]; then
        for tag in $TAGS; do
            if [[ $tag != "latest" ]]; then
                if [[ "$FLINK_CONTAINER_TAG" -lt "$tag" ]]; then
                    export FLINK_CONTAINER_TAG=$tag
                fi
            fi
        done
    fi
    if [[ ( $FLINK_CONTAINER_TAG == "" || $FLINK_CONTAINER_TAG == 0 ) && -z $TEST_MODE ]]; then
        echo "Couldn't find last flink-runtime image tag "
        exit 1
    fi
fi

sudo bash -c "echo -e \"kubernetes.container.image: kubernetes.registry:5000/flink-runtime:$FLINK_CONTAINER_TAG\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"kubernetes.namespace: eskimo\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"kubernetes.jobmanager.replicas: 1\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"kubernetes.config.file: /home/flink/.kube/config\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"kubernetes.cluster-id: flink-runtime\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"

sed -E -i s/"#?jobmanager.rpc.address: .*"/"jobmanager.rpc.address: flink-runtime.eskimo.svc.cluster.eskimo"/g /usr/local/lib/flink/conf/flink-conf.yaml
sed -E -i s/"#?rest.bind-address: .*"/"rest.bind-address: 0.0.0.0"/g /usr/local/lib/flink/conf/flink-conf.yaml
sed -E -i s/"#?rest.address: .*"/"rest.address: flink-runtime-rest.eskimo.svc.cluster.eskimo"/g /usr/local/lib/flink/conf/flink-conf.yaml

sudo bash -c "echo -e \"blob.server.port: 6124\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"
sudo bash -c "echo -e \"taskmanager.rpc.port: 50100\"  >> /usr/local/lib/flink/conf/flink-conf.yaml"

echo " - Symlinking entrypoint to /docker-entrypoint.sh"
sudo ln -s /usr/local/sbin/eskimo-flink-entrypoint.sh /docker-entrypoint.sh

echo " - Enabling flink to change configuration at runtime"
sudo chown -R flink. /usr/local/lib/flink/conf/
sudo chmod -R 777 /usr/local/lib/flink/conf/

echo " - Copying configuration over to host /var/lib/flink/config for kube configmaps "
sudo mkdir -p /var/lib/flink/config
sudo chown -R flink. /var/lib/flink/config

sudo bash -c "cat /usr/local/lib/flink/conf/flink-conf.yaml | grep -v '^$' | grep -v '^#' > /var/lib/flink/config/flink-conf.yaml"
sudo bash -c "echo 'kubernetes.internal.jobmanager.entrypoint.class: org.apache.flink.kubernetes.entrypoint.KubernetesSessionClusterEntrypoint' >> /var/lib/flink/config/flink-conf.yaml"
sudo bash -c "echo 'execution.target: kubernetes-session' >> /var/lib/flink/config/flink-conf.yaml"
sudo bash -c "echo 'internal.cluster.execution-mode: NORMAL' >> /var/lib/flink/config/flink-conf.yaml"

cp /usr/local/lib/flink/conf/log4j-console.properties /var/lib/flink/config/
cp /usr/local/lib/flink/conf/logback-console.xml /var/lib/flink/config/



# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_CONFIG_SUCESS_MESSAGE"
