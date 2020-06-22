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


BOX_IP=192.168.10.41

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

set -e

sudo rm -Rf /tmp/integration-test.log

# This function ensures that VirtualBox is available on host machine (the one running eskimo)
# VirtualBox is required to build mesos.
check_for_virtualbox() {
    if [ -x "$(command -v VBoxManage)" ]; then
        echo "Found virtualbox : "`VBoxManage -v`
    else

        if [[ -f /etc/debian_version ]]; then
            if [[ `dpkg-query -l '*virtualbox*' | grep ii` == "" ]]; then
                echo "This setup requires VirtualBox installed and ready on the host machine"
                exit 12
            fi
        else
            # workds for both RHEL and suse
            if [[ `rpm -qa | grep 'virtualbox'` == "" ]]; then
                echo "This setup requires VirtualBox installed and ready on the host machine"
                exit 13
            fi
        fi
    fi
}

call_eskimo(){
    URL=$1
    data=$2

    rm -Rf eskimo-call-result

    if [[ "$data" != "" ]]; then
        curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -m 3600 \
            -XPOST http://$BOX_IP/$URL \
            -d "$data" \
            > eskimo-call-result 2>/dev/null
    else
        curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -m 3600 \
            -XPOST http://$BOX_IP/$URL \
            > eskimo-call-result 2>/dev/null
    fi

    # test result
    if [[ `cat eskimo-call-result | jq -r '.status'` != "OK" ]]; then
        echo "Couldn't successfuly cal eskimo URK $URL !"
        echo "Got result"
        cat eskimo-call-result
        exit 9
    fi

    rm -Rf eskimo-call-result
}

check_for_virtualbox

# bring VM test-integration
echo " - Destroying any previously existing VM"
set +e
vagrant destroy --force integration-test >> /tmp/integration-test.log 2>&1
set -e

echo " - Bringing Build VM up"
vagrant up integration-test >> /tmp/integration-test.log 2>&1

echo " - Updating the appliance"
vagrant ssh -c "sudo yum update -y" integration-test  >> /tmp/integration-test.log 2>&1


# Docker part
# ----------------------------------------------------------------------------------------------------------------------

echo " - Install required packages for docker "
vagrant ssh -c "sudo yum install -y yum-utils device-mapper-persistent-data lvm2" integration-test  >> /tmp/integration-test.log 2>&1

echo " - set up the stable docker repository."
vagrant ssh -c "sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo" integration-test  >> /tmp/integration-test.log 2>&1

echo " - Install the latest version of Docker CE and containerd"
vagrant ssh -c "sudo yum install -y docker-ce docker-ce-cli containerd.io" integration-test  >> /tmp/integration-test.log 2>&1

echo " - Enabling docker service"
vagrant ssh -c "sudo systemctl enable docker" integration-test  >> /tmp/integration-test.log 2>&1

echo " - Starting docker service"
vagrant ssh -c "sudo systemctl start docker" integration-test  >> /tmp/integration-test.log 2>&1

echo " - Adding current user to docker group"
vagrant ssh -c "sudo usermod -a -G docker vagrant" integration-test  >> /tmp/integration-test.log 2>&1


# Eskimo dependencies part
# ----------------------------------------------------------------------------------------------------------------------

echo " - Installing utilities"
vagrant ssh -c "sudo yum install -y wget git gcc glibc-static" integration-test  >> /tmp/integration-test.log 2>&1

echo " - Installing Java"
vagrant ssh -c "sudo yum install -y java-11-openjdk" integration-test  >> /tmp/integration-test.log 2>&1


# Eskimo installation part
# ----------------------------------------------------------------------------------------------------------------------

echo " - Finding Eskim package"
eskimo_package=`find $SCRIPT_DIR/../../target -name 'eskimo*bin*.tar.gz' 2>/dev/null`
if [[ $eskimo_package == "" ]]; then
    echo "Couldn't find any eskimo package in $SCRIPT_DIR/../../target"
    exit 1
fi

echo " - Uploading eskimo"
vagrant upload $eskimo_package /home/vagrant/eskimo.tar.gz integration-test  >> /tmp/integration-test.log 2>&1

echo " - Extracting eskimo"
vagrant ssh -c "tar xvfz eskimo.tar.gz" integration-test  >> /tmp/integration-test.log 2>&1

echo " - Find eskimo folder name"
eskimo_folder=`vagrant ssh -c "ls /home/vagrant | grep eskimo | grep -v gz" integration-test 2>/dev/null | sed -e 's/\r//g'`
if [[ $eskimo_folder == "" ]]; then
    echo "Couldn't get eskimo folder name"
    exit 2
fi

echo " - Installing Eskimo"
vagrant ssh -c "sudo mv /home/vagrant/$eskimo_folder /usr/local/lib" integration-test  >> /tmp/integration-test.log 2>&1

echo " - Giving back eskimo installation folder to root"
vagrant ssh -c "sudo chown root. /usr/local/lib/$eskimo_folder" integration-test  >> /tmp/integration-test.log 2>&1

echo " - Chaning eskimo port to 80"
vagrant ssh -c "sudo sed -i s/\"server.port=9191\"/\"server.port=80\"/g /usr/local/lib/$eskimo_folder/conf/eskimo.properties" integration-test  >> /tmp/integration-test.log 2>&1

echo " - Installing Systemd file"
vagrant ssh -c "sudo bash /usr/local/lib/$eskimo_folder/bin/utils/__install-eskimo-systemD-unit-file.sh -f" integration-test  >> /tmp/integration-test.log 2>&1

echo " - Uploading packages distrib packages"
for i in `find $SCRIPT_DIR/../../packages_distrib -name '*.tar.gz'`; do
    filename=`basename $i`
    vagrant upload $i /usr/local/lib/$eskimo_folder/packages_distrib/$filename integration-test  >> /tmp/integration-test.log 2>&1
done


# Eskimo setup
# ----------------------------------------------------------------------------------------------------------------------

# login
echo " - CALL performing login"
curl \
    -c $SCRIPT_DIR/cookies \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -XPOST http://$BOX_IP/login \
    -d 'username=admin&password=password' \
    >> /tmp/integration-test.log 2>&1

# fetch status now and test it
echo " - CALL Fetching status"
status=`curl -b $SCRIPT_DIR/cookies http://$BOX_IP/get-status 2>/dev/null`
if [[ `echo $status | jq -r '.status'` != "OK" ]]; then
    echo "Couldn't successfuly fetch status !"
    echo "Got status : $status"
    exit 4
fi
# should be clear setup before setup is applied
if [[ `echo $status | jq -r '.clear'` != "setup" ]]; then
    echo "Didn't get expected 'clear' status"
    echo "Got status : $status"
    exit 5
fi

# upload setup config
echo " - CALL Saving setuo"
call_eskimo \
    "save-setup" \
'{
"setup_storage":"/var/lib/eskimo",
"ssh_username":"eskimo",
"filename-ssh-key":"ssh_key",
"content-ssh-key":"-----BEGIN OPENSSH PRIVATE KEY-----\r\nb3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAABFwAAAAdzc2gtcn\r\nNhAAAAAwEAAQAAAQEA3ojkjT6HoRjuoYjspClIdBOy8av1tYM2MV1UI9kHJBmxKangTU0G\r\nuM5s5iUNwUdhHffouomozeZvBt7XzrZrN5lO4dZzDAWc70KwmH1VteDfEaBmdp/ZEIjmvu\r\nslErY872U6x15S6kpHfLaIJ5n7e9aCKcxEQLVzhHU/ybOKtQQMlXl3VCe+p1vUF9q7cpLo\r\n+VFjMESNDOpsixhXAJ4n7VDA9XLd3T3vqev8eCxfPXhP9bFmW/hnhLHGSNEYT1WLYH+/xR\r\n1v/b64EiIwzOUK/3vpSP5EAO0wlORkhlEE2PVxf3w3wsmPvSLq4NCm/MFJxbK0T4a2S4qg\r\n1FexNiBfFwAAA8jwOZHB8DmRwQAAAAdzc2gtcnNhAAABAQDeiOSNPoehGO6hiOykKUh0E7\r\nLxq/W1gzYxXVQj2QckGbEpqeBNTQa4zmzmJQ3BR2Ed9+i6iajN5m8G3tfOtms3mU7h1nMM\r\nBZzvQrCYfVW14N8RoGZ2n9kQiOa+6yUStjzvZTrHXlLqSkd8tognmft71oIpzERAtXOEdT\r\n/Js4q1BAyVeXdUJ76nW9QX2rtykuj5UWMwRI0M6myLGFcAniftUMD1ct3dPe+p6/x4LF89\r\neE/1sWZb+GeEscZI0RhPVYtgf7/FHW/9vrgSIjDM5Qr/e+lI/kQA7TCU5GSGUQTY9XF/fD\r\nfCyY+9Iurg0Kb8wUnFsrRPhrZLiqDUV7E2IF8XAAAAAwEAAQAAAQAPfZP7SQkD68pgsDlY\r\nzA7hFaX1MLUv52xUT1zWCft3RdqRPeQBPYVkQ+pMsvOcKq3V+jXXFlIL0yiTX9vB5ezct+\r\n1HxzfG9HUSKqBEXSUkPf0JKxM22rWvcvgs/g1cmhbvyyomSqiW6ojDY6liqFNbMXlqE3AE\r\n2RyrccX48miLZRWHv3AidiBW16lDOQypDFJ7HFS+FMoPW5o0VitjqkPbE4FQd1etv7F39f\r\nxqdoJ9MHk9pNrl6GiTucTRN2ws064Qw/D40Ta9/Qk4KkShWq9l/TV1DOJzYCw17o1thNbV\r\nphRel1wxR0MbovorqyYH5h+i4Stu/5iox7MTBmEFm+ZhAAAAgHgug0Ins1wlsfjFjvCkRc\r\nLUxMZsUYr45QG8JFNd4XzACWalfyMXyMlxNH9VWk2ctx+i8zzcNXBw5HJzA4Zxy8BTFz1a\r\nEHrT6Uegzbeu37+XMOxnDBg1ssvRFK+XckYm6QcroCJA0jNOeSb3fJ7m91kT2aBwgoi0jd\r\nYjOEDsYzO/AAAAgQDxqk0cITmYa4qICUOb5pokUa7cFfzogmEzfsttkCe4fLR9ck3TpCjO\r\nB4Mc/LR97g8baP7PBvi0V3rnESF1fDnL5kTf19uVsJkBwVgPJwnmGL3azL8a0jXahJv5PA\r\ndH/099m3MB6YOS8MULx41Rx/4YfcqjszS3wNoBTQPz9FknKQAAAIEA67wYDIfDO2Qb0BqU\r\n4pl/IryTm/RfNBOzq8CF8NsaGH9ZPVdWaeUrLDQd4ZbpvdyoB9/3zrtH1mjm7sMtJRyEEr\r\nptDpKQU8fUKhIxc7XU0SOJrPEPfZDiO1i92WKglcQpJngyWvYlwWCeo83htVRnMFOlRQNn\r\nOaqn4JUMzm3VXD8AAAATYmFkdHJhc2hAYmFkYm9va25ldw==\r\n-----END OPENSSH PRIVATE KEY-----\r\n",
"setup-mesos-origin":"build",
"setup-services-origin":"build"
}'


# Services configuration
# ----------------------------------------------------------------------------------------------------------------------

# upload services config
echo " - CALL saving services config"
call_eskimo \
    "save-services-settings" \
'{
"elasticsearch-bootstrap-memory_lock":"",
"elasticsearch-action-destructive_requires_name":"",
"elasticsearch-index-refresh_interval":"",
"elasticsearch-index-number_of_replicas":"",
"elasticsearch-index-number_of_shards":"",
"flink-app-master-jobmanager-heap-size":"",
"flink-app-master-taskmanager-heap-size":"",
"flink-app-master-parallelism-default":"",
"flink-app-master-mesos-resourcemanager-tasks-cpus":"",
"flink-app-master-mesos-resourcemanager-tasks-mem":"",
"flink-app-master-taskmanager-numberOfTaskSlots":"",
"kafka-num-network-threads":"",
"kafka-num-io-threads":"",
"kafka-socket-send-buffer-bytes":"",
"kafka-socket-receive-buffer-bytes":"",
"kafka-socket-request-max-bytes":"",
"kafka-num-partitions":"",
"kafka-log-retention-hours":"",
"marathon-task_launch_timeout":"",
"mesos-agent-cpu_additional":"",
"mesos-agent-ram_additional":"4000",
"spark-executor-spark-driver-memory":"",
"spark-executor-spark-rpc-numRetries":"",
"spark-executor-spark-rpc-retry-wait":"",
"spark-executor-spark-scheduler-mode":"",
"spark-executor-spark-locality-wait":"",
"spark-executor-spark-dynamicAllocation-executorIdleTimeout":"",
"spark-executor-spark-dynamicAllocation-cachedExecutorIdleTimeout":"",
"spark-executor-spark-executor-memory":""
}'

# Now need to apply command
echo " - CALL applying services config"
call_eskimo \
    "apply-services-settings"


# Apply nodes config
# ----------------------------------------------------------------------------------------------------------------------

echo " - CALL saving npdes config"
call_eskimo \
    "save-nodes-config" \
'{
"node_id1":"172.17.0.1",
"flink-app-master":"1",
"marathon":"1",
"mesos-master":"1",
"zookeeper":"1",
"elasticsearch1":"on",
"flink-worker1":"on",
"gluster1":"on",
"kafka1":"on",
"logstash1":"on",
"mesos-agent1":"on",
"ntp1":"on",
"prometheus1":"on",
"spark-executor1":"on"
}'

# Now need to apply command
echo " - CALL applying nodes config"
call_eskimo \
    "apply-nodes-config"


# Apply marathon config
# ----------------------------------------------------------------------------------------------------------------------

echo " - CALL saving marathon config"
call_eskimo \
    "save-marathon-services-config" \
'{
"cerebro_install":"on",
"gdash_install":"on",
"grafana_install":"on",
"kafka-manager_install":"on",
"kibana_install":"on",
"spark-history-server_install":"on",
"zeppelin_install":"on"
}'

# Now need to apply command
echo " - CALL applying marathon config"
call_eskimo \
    "apply-marathon-services-config"

# Now running logstash demo zeppelin paragraphs
# ----------------------------------------------------------------------------------------------------------------------

# Paragraph 1
echo " - ZEPPELIN logstash demo - paragraph 1"
call_eskimo \
    "zeppelin/api/notebook/run/2EURNXTM1/paragraph_1592048236546_788859320"

# Paragraph 2
echo " - ZEPPELIN logstash demo - paragraph 2"
call_eskimo \
    "zeppelin/api/notebook/run/2EURNXTM1/paragraph_1573144840643_524948953"

# Paragraph 3
echo " - ZEPPELIN logstash demo - paragraph 3"
call_eskimo \
    "zeppelin/api/notebook/run/2EURNXTM1/paragraph_1573145219945_2112967654"


# Now running Spark ES demo zeppelin paragraphs
# ----------------------------------------------------------------------------------------------------------------------

# Paragraph 1
echo " - ZEPPELIN Spark ES demo - paragraph 1"
call_eskimo \
    "zeppelin/api/notebook/run/2FBRKBJYP/paragraph_1591581944840_1580489422"


# creating required kafka topics
# ----------------------------------------------------------------------------------------------------------------------

echo " - Creating topic berka-payments"
vagrant ssh -c "/usr/local/bin/kafka-topics.sh --create --replication-factor 1 --partitions 4 --zookeeper localhost:2181 --topic berka-payments" integration-test  >> /tmp/integration-test.log 2>&1

echo " - Creating topic berka-payments-aggregate"
vagrant ssh -c "/usr/local/bin/kafka-topics.sh --create --replication-factor 1 --partitions 4 --zookeeper localhost:2181 --topic berka-payments-aggregate" integration-test

# Now running Spark Kafka demo zeppelin paragraphs
# ----------------------------------------------------------------------------------------------------------------------

# TODO this is difficult since I need to let both paragraph run asynchronously for a little time, starting the spark one
# and waiting for the mesos process to be launched (need to query mesos-master)

# TODO

#http://192.168.10.41/zeppelin/#/notebook/2EW78UWA7


# Now running Flink Kafka demo zeppelin paragraphs
# ----------------------------------------------------------------------------------------------------------------------

# TODO


# Additional tests
# ----------------------------------------------------------------------------------------------------------------------

# TODO Ensure Kibana dashboard exist and has data
# (TODO find out how to do this ? API ?)

# get logs
#vagrant ssh -c "sudo journalctl -u eskimo" integration-test