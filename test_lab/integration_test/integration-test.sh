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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

set -e

sudo rm -Rf /tmp/integration-test.log

echo_date() {
    echo $(date +"%Y-%m-%d %H:%M:%S")" $@"
}

# UTILITY FUNCTIONS
# ======================================================================================================================

usage() {
    echo "Usage:"
    echo "    -h  Display this help message."
    echo "    -d  prepare VM for Demo"
}

check_for_virtualbox() {
    if [ -x "$(command -v VBoxManage)" ]; then
        echo_date "Found virtualbox : "$(VBoxManage -v)
    else

        if [[ -f /etc/debian_version ]]; then
            if [[ $(dpkg-query -l '*virtualbox*' | grep ii) == "" ]]; then
                echo_date "This setup requires VirtualBox installed and ready on the host machine"
                exit 12
            fi
        else
            # works for both RHEL and suse
            if [[ $(rpm -qa | grep 'virtualbox') == "" ]]; then
                echo_date "This setup requires VirtualBox installed and ready on the host machine"
                exit 13
            fi
        fi
    fi
}

check_for_vagrant() {
    if [ -x "$(command -v vagrant)" ]; then
        echo_date "Found vagrant : "$(vagrant -v)
    else
        echo "Vagrant is not available on system"
        echo_date 100
    fi
}

call_eskimo() {
    URL=$1
    data=$2
    method=$3
    if [[ "$method" == "" ]]; then
        method="POST"
    fi

    rm -Rf eskimo-call-result

    if [[ "$data" != "" ]]; then
        curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -m 3600 \
            -X$method http://$BOX_IP/$URL \
            -d "$data" \
            > eskimo-call-result 2> /dev/null
    else
        curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -m 3600 \
            -X$method http://$BOX_IP/$URL \
            > eskimo-call-result 2> /dev/null
    fi

    # test result
    if [[ $(cat eskimo-call-result | jq -r '.status') != "OK" ]]; then
        echo_date "Couldn't successfully call eskimo URK $URL !"
        echo_date "Got result"
        cat eskimo-call-result
        exit 9
    fi

    rm -Rf eskimo-call-result
}

wait_for_taskmanager_registered() {

    echo_date " - Now waiting for mesos to report flink taskmanager"
    for attempt in $(seq 1 30); do
        sleep 10
        spark_exec_status=$(curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XPOST http://$BOX_IP/mesos-master/tasks \
            2> /dev/null |
            jq -r ' .tasks | .[] | select (.name|contains("taskmanager")) | select (.state == "TASK_RUNNING") | .statuses | .[] | select (.state == "TASK_RUNNING")')
        if [[ $spark_exec_status != "" ]]; then
            echo_date "   + Found zeppelin flink taskmanager running on $(echo $spark_exec_status | jq -r '.container_status | .network_infos | .[] | .ip_addresses | .[] | .ip_address')"
            break
        fi
        if [[ $attempt == 30 ]]; then
            echo_date "Could not get flink taskmanager up and running within 300 seconds. Crashing"

            return 1
        fi
    done

    return 0
}

wait_for_taskmanager_unregistered() {

    echo_date " - Waiting for mesos to unregister taskmanager (not to compromise other tests)"
    for attempt in $(seq 1 30); do
        sleep 10
        spark_exec_status=$(curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XPOST http://$BOX_IP/mesos-master/tasks \
            2> /dev/null |
            jq -r ' .tasks | .[] | select (.name|contains("taskmanager")) | select (.state == "TASK_RUNNING") | .statuses | .[] | select (.state == "TASK_RUNNING")')
        if [[ $spark_exec_status == "" ]]; then
            echo_date "   + No Zeppelin Flink taskmanager found anymore, can continue ..."
            break
        fi
        if [[ $attempt == 30 ]]; then
            echo_date "Mesos did not unregister executor within 300 seconds. Crashing"
            #exit 41
        fi
    done
}

wait_for_executor_registered() {

    echo_date " - Now waiting for mesos to report spark executor"
    for attempt in $(seq 1 30); do
        sleep 10
        spark_exec_status=$(curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XPOST http://$BOX_IP/mesos-master/tasks \
            2> /dev/null |
            jq -r ' .tasks | .[] | select (.name|contains("Zeppelin ")) | select (.state == "TASK_RUNNING") | .statuses | .[] | select (.state == "TASK_RUNNING")')
        if [[ $spark_exec_status != "" ]]; then
            echo_date "   + Found zeppelin spark executor running on $(echo $spark_exec_status | jq -r '.container_status | .network_infos | .[] | .ip_addresses | .[] | .ip_address')"
            break
        fi
        if [[ $attempt == 30 ]]; then
            echo_date "Could not get spark executor up and running within 300 seconds. Crashing"

            return 1
        fi
    done

    return 0
}

wait_for_executor_unregistered() {

    echo_date " - Waiting for mesos to unregister executor (not to compromise other tests)"
    for attempt in $(seq 1 30); do
        sleep 10
        spark_exec_status=$(curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XPOST http://$BOX_IP/mesos-master/tasks \
            2> /dev/null |
            jq -r ' .tasks | .[] | select (.name|contains("Zeppelin ")) | select (.state == "TASK_RUNNING") | .statuses | .[] | select (.state == "TASK_RUNNING")')
        if [[ $spark_exec_status == "" ]]; then
            echo_date "   + No Zeppelin Spark executor found anymore, can continue ..."
            break
        fi
        if [[ $attempt == 30 ]]; then
            echo_date "Mesos did not unregister executor within 300 seconds. Crashing"
            exit 41
        fi
    done
}

__tmp_saved_dir=$(pwd)

__returned_to_saved_dir() {
    cd $__tmp_saved_dir
}

# BUSINESS FUNCTIONS
# ======================================================================================================================

rebuild_eskimo() {

    echo_date " - Rebuilding eskimo"

    trap __returned_to_saved_dir 15
    trap __returned_to_saved_dir EXIT

    cd $SCRIPT_DIR/../..

    mvn clean install >> /tmp/integration-test.log 2>&1

    __returned_to_saved_dir
}

build_box() {

    # bring VM test-integration
    echo_date " - Destroying any previously existing VM"
    set +e
    vagrant destroy --force integration-test >> /tmp/integration-test.log 2>&1
    set -e

    echo_date " - Bringing Build VM up"
    vagrant up integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Updating the appliance"
    vagrant ssh -c "sudo yum update -y" integration-test >> /tmp/integration-test.log 2>&1

    # Docker part
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - Install required packages for docker "
    vagrant ssh -c "sudo yum install -y yum-utils device-mapper-persistent-data lvm2" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - set up the stable docker repository."
    vagrant ssh -c "sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Install the latest version of Docker CE and containerd"
    vagrant ssh -c "sudo yum install -y docker-ce docker-ce-cli containerd.io" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Enabling docker service"
    vagrant ssh -c "sudo systemctl enable docker" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Starting docker service"
    vagrant ssh -c "sudo systemctl start docker" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Adding current user to docker group"
    vagrant ssh -c "sudo usermod -a -G docker vagrant" integration-test >> /tmp/integration-test.log 2>&1

    # Eskimo dependencies part
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - Installing utilities"
    vagrant ssh -c "sudo yum install -y wget git gcc glibc-static" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Installing Java"
    vagrant ssh -c "sudo yum install -y java-11-openjdk" integration-test >> /tmp/integration-test.log 2>&1
}

install_eskimo() {

    # Eskimo installation part
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - Cleanup before installation"
    vagrant ssh -c "rm -Rf eskimo.tar.gz" integration-test >> /tmp/integration-test.log 2>&1
    vagrant ssh -c "sudo rm -Rf /usr/local/lib/eskimo*" integration-test >> /tmp/integration-test.log 2>&1
    vagrant ssh -c "sudo rm -Rf /lib/systemd/system/eskimo.service" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Finding Eskimo package"
    eskimo_package=$(find $SCRIPT_DIR/../../target -name 'eskimo*bin*.tar.gz' 2> /dev/null)
    if [[ $eskimo_package == "" ]]; then
        echo_date "Couldn't find any eskimo package in $SCRIPT_DIR/../../target"
        exit 1
    fi

    echo_date " - Uploading eskimo"
    vagrant upload $eskimo_package /home/vagrant/eskimo.tar.gz integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Extracting eskimo"
    vagrant ssh -c "tar xvfz eskimo.tar.gz" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Find eskimo folder name"
    eskimo_folder=$(vagrant ssh -c "ls /home/vagrant | grep eskimo | grep -v gz" integration-test 2> /dev/null | sed -e 's/\r//g')
    if [[ $eskimo_folder == "" ]]; then
        echo_date "Couldn't get eskimo folder name"
        exit 2
    fi

    echo_date " - Installing Eskimo"
    vagrant ssh -c "sudo mv /home/vagrant/$eskimo_folder /usr/local/lib" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Giving back eskimo installation folder to root"
    vagrant ssh -c "sudo chown root. /usr/local/lib/$eskimo_folder" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Chaning eskimo port to 80"
    vagrant ssh -c "sudo sed -i s/\"server.port=9191\"/\"server.port=80\"/g /usr/local/lib/$eskimo_folder/conf/eskimo.properties" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Installing Systemd file"
    vagrant ssh -c "sudo bash /usr/local/lib/$eskimo_folder/bin/utils/__install-eskimo-systemD-unit-file.sh -f" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Uploading packages distrib packages"
    for i in $(find "$SCRIPT_DIR"/../../packages_distrib -name '*.tar.gz'); do
        filename=$(basename $i)
        vagrant upload $i /usr/local/lib/$eskimo_folder/packages_distrib/$filename integration-test >> /tmp/integration-test.log 2>&1
    done

    echo_date " - Removing eskimo archive from home folder"
    vagrant ssh -c "rm -Rf /home/vagrant/eskimo.tar.gz" integration-test >> /tmp/integration-test.log 2>&1
}

setup_eskimo() {

    # Eskimo setup
    # ----------------------------------------------------------------------------------------------------------------------

    # login
    echo_date " - CALL performing login"
    curl \
        -c $SCRIPT_DIR/cookies \
        -H 'Content-Type: application/x-www-form-urlencoded' \
        -XPOST http://$BOX_IP/login \
        -d 'username=admin&password=password' \
        >> /tmp/integration-test.log 2>&1

    # fetch status now and test it
    echo_date " - CALL Fetching status"
    status=$(curl -b $SCRIPT_DIR/cookies http://$BOX_IP/get-status 2> /dev/null)
    if [[ $(echo $status | jq -r '.status') != "OK" ]]; then
        echo "Couldn't successfuly fetch status !"
        echo "Got status : $status"
        exit 4
    fi
    # should be clear setup before setup is applied
    if [[ $(echo $status | jq -r '.clear') != "setup" ]]; then
        echo "Didn't get expected 'clear' status"
        echo "Got status : $status"
        exit 5
    fi

    # upload setup config
    echo_date " - CALL Saving setuo"
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
    echo_date " - CALL saving services config"
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
    "mesos-agent-ram_additional":"4800",
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
    echo_date " - CALL applying services config"
    call_eskimo \
        "apply-services-settings"

    # Apply nodes config
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - CALL saving nodes config"
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
    echo_date " - CALL applying nodes config"
    call_eskimo \
        "apply-nodes-config"

    # Apply marathon config
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - CALL saving marathon config"
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
    echo_date " - CALL applying marathon config"
    call_eskimo \
        "apply-marathon-services-config"
}

run_zeppelin_data_load() {

    # Now running logstash demo zeppelin paragraphs
    # ----------------------------------------------------------------------------------------------------------------------

    # First I need to wait for zeppelin to be up and running
    echo_date " - Waiting for Zeppelin to be up and running (querying mesos for TASK_RUNNING)"
    for attempt in $(seq 1 30); do
        sleep 10
        zeppelin_status=$(curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XPOST http://$BOX_IP/mesos-master/tasks \
            2> /dev/null |
            jq -r ' .tasks | .[] | select (.id|contains("zeppelin")) | .statuses | .[] | select (.state == "TASK_RUNNING")')
        if [[ $zeppelin_status != "" ]]; then
            echo_date "   + Found zeppelin running on $(echo $zeppelin_status | jq -r '.container_status | .network_infos | .[] | .ip_addresses | .[] | .ip_address')"
            break
        fi
        if [[ $attempt == 30 ]]; then
            echo_date "Could not get zeppelin up and running within 300 seconds. Crashing"
            exit 21
        fi
    done
    # Giving it a little more time to really start
    sleep 40

    # Paragraph 1
    echo_date " - ZEPPELIN logstash demo - paragraph 1"
    call_eskimo \
        "zeppelin/api/notebook/run/2EURNXTM1/paragraph_1592048236546_788859320"

    # Paragraph 2
    echo_date " - ZEPPELIN logstash demo - paragraph 2"
    call_eskimo \
        "zeppelin/api/notebook/run/2EURNXTM1/paragraph_1573144840643_524948953"

    # Paragraph 3
    echo_date " - ZEPPELIN logstash demo - paragraph 3"
    call_eskimo \
        "zeppelin/api/notebook/run/2EURNXTM1/paragraph_1573145219945_2112967654"

    # Now running Spark ES demo zeppelin paragraphs
    # ----------------------------------------------------------------------------------------------------------------------

    # Paragraph 1
    echo_date " - ZEPPELIN Spark ES demo - paragraph 1"
    call_eskimo \
        "zeppelin/api/notebook/run/2FBRKBJYP/paragraph_1591581944840_1580489422"

    echo_date " - Waiting for mesos to unregister executor (not to compromise other tests)"
    for attempt in $(seq 1 30); do
        sleep 10
        spark_exec_status=$(curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XPOST http://$BOX_IP/mesos-master/tasks \
            2> /dev/null |
            jq -r ' .tasks | .[] | select (.name|contains("Zeppelin ")) | select (.state == "TASK_RUNNING") | .statuses | .[] | select (.state == "TASK_RUNNING")')
        if [[ $spark_exec_status == "" ]]; then
            echo_date "   + No Zeppelin Spark executor found anymore, can continue ..."
            break
        fi
        if [[ $attempt == 30 ]]; then
            echo_date "Mesos did not unregister executor within 300 seconds. Crashing"
            exit 41
        fi
    done

    # creating required kafka topics
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - Creating topic berka-payments"
    vagrant ssh -c \
        "/usr/local/bin/kafka-topics.sh --create --replication-factor 1 --partitions 4 --zookeeper localhost:2181 --topic berka-payments" \
        integration-test \
        >> /tmp/integration-test.log 2>&1

    echo_date " - Creating topic berka-payments-aggregate"
    vagrant ssh -c \
        "/usr/local/bin/kafka-topics.sh --create --replication-factor 1 --partitions 4 --zookeeper localhost:2181 --topic berka-payments-aggregate" \
        integration-test \
        >> /tmp/integration-test.log 2>&1
}

run_zeppelin_spark_kafka() {

    # Now running Spark Kafka demo zeppelin paragraphs
    # ----------------------------------------------------------------------------------------------------------------------

    # Paragraph 2
    echo_date " - ZEPPELIN spark Kafka demo - start paragraph SPARK (async)"
    call_eskimo \
        "zeppelin/api/notebook/job/2EW78UWA7/paragraph_1575994043909_340690103"

    wait_for_executor_registered
    if [[ $? != 0 ]]; then
        set +e

        curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XDELETE http://$BOX_IP/zeppelin/api/notebook/job/2EW78UWA7/paragraph_1575994043909_340690103 \
            >> /tmp/integration-test.log 2>&1

        exit 31
    fi

    # Giving it a little more time to be all set up
    sleep 20

    # Paragraph 1
    echo_date " - ZEPPELIN spark Kafka demo - start paragraph python feeder (async)"
    call_eskimo \
        "zeppelin/api/notebook/job/2EW78UWA7/paragraph_1575994097569_-232881020"

    echo_date " - ZEPPELIN spark Kafka demo - Now expecting some result on kafka topic berka-payments-aggregate"
    vagrant ssh -c \
        "/usr/local/bin/kafka-console-consumer.sh --bootstrap-server 172.17.0.1:9092 --topic berka-payments-aggregate --timeout-ms 120000 --max-messages 100" \
        integration-test \
        > kafka-berka-payments-aggregate-results 2> /dev/null

    if [[ $(wc -l kafka-berka-payments-aggregate-results | cut -d ' ' -f 1) -lt 100 ]]; then
        echo_date "Failed to fetch at least 100 aggregated payments from result topic"

        set +e

        # stop both paragraphs
        curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XDELETE http://$BOX_IP/zeppelin/api/notebook/job/2EW78UWA7/paragraph_1575994097569_-232881020 \
            >> /tmp/integration-test.log 2>&1

        curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XDELETE http://$BOX_IP/zeppelin/api/notebook/job/2EW78UWA7/paragraph_1575994043909_340690103 \
            >> /tmp/integration-test.log 2>&1

        rm -f kafka-berka-payments-aggregate-results

        exit 30
    fi

    rm -f kafka-berka-payments-aggregate-results

    echo_date " - ZEPPELIN spark Kafka demo - stop paragraph python feeder (async)"
    call_eskimo \
        "zeppelin/api/notebook/job/2EW78UWA7/paragraph_1575994097569_-232881020" \
        "" \
        "DELETE"

    echo_date " - ZEPPELIN spark Kafka demo - stop paragraph SPARK (async)"
    call_eskimo \
        "zeppelin/api/notebook/job/2EW78UWA7/paragraph_1575994043909_340690103" \
        "" \
        "DELETE"

    wait_for_executor_unregistered

    echo_date " - ZEPPELIN spark Kafka demo - Clearing paragraph results"
    call_eskimo \
        "zeppelin/api/notebook/2EW78UWA7/clear" \
        "" \
        "PUT"

    echo_date " - ZEPPELIN spark Kafka demo - Re-running text paragraph"
    call_eskimo \
        "zeppelin/api/notebook/job/2EW78UWA7/paragraph_1575994158921_1992204415"

    call_eskimo \
        "zeppelin/api/notebook/job/2EW78UWA7/paragraph_1575994130002_-702893878"

    call_eskimo \
        "zeppelin/api/notebook/job/2EW78UWA7/paragraph_1575994270617_-321954293"
}

run_zeppelin_flink_kafka() {

    # Now running Flink Kafka demo zeppelin paragraphs
    # ----------------------------------------------------------------------------------------------------------------------

    # Paragraph 2
    echo_date " - ZEPPELIN flink Kafka demo - start paragraph FLINK (async)"
    call_eskimo \
        "zeppelin/api/notebook/job/2EVQ4TGH9/paragraph_1576315842571_1475844155"

    wait_for_taskmanager_registered
    if [[ $? != 0 ]]; then

        set +e

        curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XDELETE http://$BOX_IP/zeppelin/api/notebook/job/2EVQ4TGH9/paragraph_1576315842571_1475844155 \
            >> /tmp/integration-test.log 2>&1

        exit 31
    fi

    # Giving it a little more time to be all set up
    sleep 15

    # Paragraph 1
    echo_date " - ZEPPELIN flink Kafka demo - start paragraph python feeder (async)"
    call_eskimo \
        "zeppelin/api/notebook/job/2EVQ4TGH9/paragraph_1575994097569_-232881020"

    echo_date " - ZEPPELIN flink Kafka demo - Now expecting some result on kafka topic berka-payments-aggregate"
    vagrant ssh -c \
        "/usr/local/bin/kafka-console-consumer.sh --bootstrap-server 172.17.0.1:9092 --topic berka-payments-aggregate --timeout-ms 120000 --max-messages 100" \
        integration-test \
        > kafka-berka-payments-aggregate-results 2> /dev/null

    if [[ $(wc -l kafka-berka-payments-aggregate-results | cut -d ' ' -f 1) -lt 100 ]]; then
        echo_date "Failed to fetch at least 100 aggregated payments from result topic"

        set +e

        # stop both paragraphs
        curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XDELETE http://$BOX_IP/zeppelin/api/notebook/job/2EVQ4TGH9/paragraph_1575994097569_-232881020 \
            >> /tmp/integration-test.log 2>&1

        curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XDELETE http://$BOX_IP/zeppelin/api/notebook/job/2EVQ4TGH9/paragraph_1576315842571_1475844155 \
            >> /tmp/integration-test.log 2>&1

        rm -f kafka-berka-payments-aggregate-results

        exit 30
    fi

    rm -f kafka-berka-payments-aggregate-results

    echo_date " - ZEPPELIN flink Kafka demo - stop paragraph python feeder (async)"
    call_eskimo \
        "zeppelin/api/notebook/job/2EVQ4TGH9/paragraph_1575994097569_-232881020" \
        "" \
        "DELETE"

    echo_date " - ZEPPELIN flink Kafka demo - stop paragraph FLINK (async)"
    call_eskimo \
        "zeppelin/api/notebook/job/2EVQ4TGH9/paragraph_1576315842571_1475844155" \
        "" \
        "DELETE"

    wait_for_taskmanager_unregistered

    echo_date " - ZEPPELIN flink Kafka demo - Clearing paragraph results"
    call_eskimo \
        "zeppelin/api/notebook/2EVQ4TGH9/clear" \
        "" \
        "PUT"

    echo_date " - ZEPPELIN flink Kafka demo - Re-running text paragraph"
    call_eskimo \
        "zeppelin/api/notebook/job/2EVQ4TGH9/paragraph_1575994158921_1992204415"

    call_eskimo \
        "zeppelin/api/notebook/job/2EVQ4TGH9/paragraph_1575994130002_-702893878"

    call_eskimo \
        "zeppelin/api/notebook/job/2EVQ4TGH9/paragraph_1575994270617_-321954293"
}

run_zeppelin_other_notes() {

    # TODO
    #/usr/local/lib/kibana-7.6.2/src/plugins/home/server/services/sample_data/data_sets/flights

    # Run all paragraphs from all other notebooks
    # ----------------------------------------------------------------------------------------------------------------------

    # TODO Find a way to have the kibana "flights" data loaded for this one
    #echo_date " - ZEPPELIN running ElasticSearch Demo (Queries)"
    #call_eskimo \
    #    "zeppelin/api/notebook/job/2F844Y1ZM"

    echo_date " - ZEPPELIN running Spark ML Demo (Regression)"
    call_eskimo \
        "zeppelin/api/notebook/job/2EUQABDKD"

    wait_for_executor_unregistered

    echo_date " - ZEPPELIN running Flink Batch Demo"
    call_eskimo \
        "zeppelin/api/notebook/job/2ESAF6TWT"

    wait_for_taskmanager_unregistered

    echo_date " - ZEPPELIN running Spark SQL Demo"
    call_eskimo \
        "zeppelin/api/notebook/job/2ERSB4Z6P"

    wait_for_executor_unregistered

    echo_date " - ZEPPELIN running Spark RDD Demo"
    call_eskimo \
        "zeppelin/api/notebook/job/2ET3K8RBK"

    wait_for_executor_unregistered

}

# Additional tests
# ----------------------------------------------------------------------------------------------------------------------

# TODO Ensure Kibana dashboard exist and has data
# (TODO find out how to do this ? API ?)

# TODO make sure documentation is well deployed and available


do_cleanup() {

    # Cleanup (for demo preparation)
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - Delete spark and flink checkpoint locations"
    vagrant ssh -c "sudo rm -Rf /var/lib/spark/data/checkpoints/*" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Delete all berka indices except berka-payments and berka-transactions"

    echo_date "   + Delete berka-account"
    vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-account" integration-test >> /tmp/integration-test.log 2>&1

    echo_date "   + Delete berka-card"
    vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-card" integration-test >> /tmp/integration-test.log 2>&1

    echo_date "   + Delete berka-disp"
    vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-disp" integration-test >> /tmp/integration-test.log 2>&1

    echo_date "   + Delete berka-district"
    vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-district" integration-test >> /tmp/integration-test.log 2>&1

    echo_date "   + Delete berka-client"
    vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-client" integration-test >> /tmp/integration-test.log 2>&1

    echo_date "   + Delete berka-loan"
    vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-loan" integration-test >> /tmp/integration-test.log 2>&1

    echo_date "   + Delete berka-order"
    vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-order" integration-test >> /tmp/integration-test.log 2>&1

    echo_date "   + Delete berka-trans"
    vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-trans" integration-test >> /tmp/integration-test.log 2>&1

}

prepare_demo() {

    # [Optionally] Prepare demo
    # ----------------------------------------------------------------------------------------------------------------------

    # TODO Switch demo flag in config and restart eskimo

    echo_date " - Find eskimo folder name (again)"
    eskimo_folder=$(vagrant ssh -c "ls /usr/local/lib | grep eskimo" integration-test 2> /dev/null | sed -e 's/\r//g')
    if [[ $eskimo_folder == "" ]]; then
        echo_date "Couldn't get eskimo folder name"
        exit 2
    fi

    echo_date " - Replace packages_distrib content with empty files"

    echo_date "  + creating folder packages_dummy"
    vagrant ssh -c "sudo mkdir -p /usr/local/lib/$eskimo_folder/packages_dummy/" integration-test >> /tmp/integration-test.log 2>&1

    echo_date "  + creating marker files"
    vagrant ssh -c "for i in \`find /usr/local/lib/$eskimo_folder/packages_distrib -name '*.gz'\`; do sudo touch /usr/local/lib/$eskimo_folder/packages_dummy/\`basename \$i\`; done" integration-test >> /tmp/integration-test.log 2>&1

    echo_date "  + removing folder packages_distrib"
    vagrant ssh -c "sudo rm -Rf /usr/local/lib/$eskimo_folder/packages_distrib" integration-test >> /tmp/integration-test.log 2>&1

    echo_date "  + replacing folder packages_distrib"
    vagrant ssh -c "sudo mv /usr/local/lib/$eskimo_folder/packages_dummy /usr/local/lib/$eskimo_folder/packages_distrib" integration-test >> /tmp/integration-test.log 2>&1

    echo_date "  + changing owner of folder packages_distrib"
    vagrant ssh -c "sudo chown -R eskimo. /usr/local/lib/$eskimo_folder/packages_distrib" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Cleaning Yum Cache"
    vagrant ssh -c "sudo yum clean all" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Stopping VM"
    vagrant halt integration-test >> /tmp/integration-test.log 2>&1

    echo_date "!!! You can now export the DemoVM using Virtual Box "
}

# get logs
#vagrant ssh -c "sudo journalctl -u eskimo" integration-test

# Parse options to the integration-test script
while getopts ":hd" opt; do
    case ${opt} in
        h )
            usage
            exit 0
        ;;
        d )
            export DEMO=demo
            break
        ;;
        : )
            break
        ;;
        \? )
           echo "Invalid Option: -$OPTARG" 1>&2
           exit 1
         ;;
    esac
done

check_for_virtualbox

check_for_vagrant

#rebuild_eskimo

#build_box

#install_eskimo

#setup_eskimo

#run_zeppelin_data_load

#run_zeppelin_spark_kafka

#run_zeppelin_flink_kafka

#run_zeppelin_other_notes

#do_cleanup

if [[ $DEMO == "demo" ]]; then
    prepare_demo
fi