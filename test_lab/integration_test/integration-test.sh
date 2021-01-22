#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

set -e

# UTILITY FUNCTIONS
# ======================================================================================================================

echo_date() {
    echo $(date +"%Y-%m-%d %H:%M:%S")" $@"
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

query_eskimo() {
    URL=$1
    data=$2
    method=$3
    if [[ "$method" == "" ]]; then
        method="GET"
    fi

    rm -Rf eskimo-call-success
    rm -Rf eskimo-call-error

    if [[ "$data" != "" ]]; then
        curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -m 3600 \
            -X$method http://$BOX_IP/$URL \
            -d "$data" \
            > eskimo-call-success \
            2> eskimo-call-error
    else
        curl \
            -b $SCRIPT_DIR/cookies \
            -m 3600 \
            -X$method http://$BOX_IP/$URL \
            > eskimo-call-success \
            2> eskimo-call-error
    fi

    # send result
    if [[ $(cat eskimo-call-success) != "" ]]; then
        cat eskimo-call-success
    else
        cat eskimo-call-error
    fi

    rm -Rf eskimo-call-success
    rm -Rf eskimo-call-error
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
        echo_date "Couldn't successfully call eskimo URL : $URL"
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
            jq -r ' .tasks | .[] | select (.name|contains("zeppelin_spark")) | select (.state == "TASK_RUNNING") | .statuses | .[] | select (.state == "TASK_RUNNING")')
        if [[ $spark_exec_status != "" ]]; then
            #echo_date "   + Found zeppelin spark executor running on $(echo $spark_exec_status | jq -r '.container_status | .network_infos | .[] | .ip_addresses | .[] | .ip_address')"
            echo_date "   + Found zeppelin spark executor running"
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
            jq -r ' .tasks | .[] | select (.name|contains("zeppelin_spark")) | select (.state == "TASK_RUNNING") | .statuses | .[] | select (.state == "TASK_RUNNING")')
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

    if [[ -z $FAST_REPACKAGE ]]; then
        mvn clean install >> /tmp/integration-test.log 2>&1
    else
        mvn clean install -DskipTests >> /tmp/integration-test.log 2>&1
    fi

    __returned_to_saved_dir
}

__dp_build_box() {

    # bring VM test-integration
    echo_date " - Destroying any previously existing VM $1"
    set +e
    vagrant destroy --force $1 >> /tmp/integration-test.log 2>&1
    set -e

    echo_date " - Building Build VM $1"

    echo_date "   + Bringing Build VM up"
    vagrant up $1 >> /tmp/integration-test.log 2>&1

    echo_date "   + Updating the appliance"
    vagrant ssh -c "sudo yum update -y" $1 >> /tmp/integration-test.log 2>&1

    if [[ $2 == "MASTER" ]]; then

        # Docker part
        # ----------------------------------------------------------------------------------------------------------------------

        echo_date "   + Install required packages for docker "
        vagrant ssh -c "sudo yum install -y yum-utils device-mapper-persistent-data lvm2" $1 >> /tmp/integration-test.log 2>&1

        echo_date "   + set up the stable docker repository."
        vagrant ssh -c "sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo" $1 >> /tmp/integration-test.log 2>&1

        echo_date "   + Install the latest version of Docker CE and containerd"
        vagrant ssh -c "sudo yum install -y docker-ce docker-ce-cli containerd.io" $1 >> /tmp/integration-test.log 2>&1

        echo_date "   + Enabling docker service"
        vagrant ssh -c "sudo systemctl enable docker" $1 >> /tmp/integration-test.log 2>&1

        echo_date "   + Starting docker service"
        vagrant ssh -c "sudo systemctl start docker" $1 >> /tmp/integration-test.log 2>&1

        echo_date "   + Adding current user to docker group"
        vagrant ssh -c "sudo usermod -a -G docker vagrant" $1 >> /tmp/integration-test.log 2>&1

        # Eskimo dependencies part
        # ----------------------------------------------------------------------------------------------------------------------

        echo_date "   + Installing utilities"
        vagrant ssh -c "sudo yum install -y wget git gcc glibc-static" $1 >> /tmp/integration-test.log 2>&1

        echo_date "   + Installing Java"
        vagrant ssh -c "sudo yum install -y java-11-openjdk" $1 >> /tmp/integration-test.log 2>&1
    fi
}

build_box() {
    if [[ -z $MULTIPLE_NODE ]]; then
        __dp_build_box integration-test MASTER
    else
        __dp_build_box integration-test1 MASTER
        __dp_build_box integration-test2
        __dp_build_box integration-test3
        __dp_build_box integration-test4
    fi
}

install_eskimo() {

    # Eskimo installation part
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - Cleanup before installation"

    echo_date "   + removing any previous upload of eskimo on VM"
    vagrant ssh -c "rm -Rf eskimo.tar.gz" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    echo_date "   + removing eskimo installation folder"
    vagrant ssh -c "sudo rm -Rf /usr/local/lib/eskimo*" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    echo_date "   + removing eskimo SystemD unit file"
    vagrant ssh -c "sudo rm -Rf /lib/systemd/system/eskimo.service" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    echo_date "   + reload systemD daemon"
    vagrant ssh -c "sudo systemctl daemon-reload" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    echo_date " - Finding Eskimo package"
    eskimo_package=$(find $SCRIPT_DIR/../../target -name 'eskimo*bin*.tar.gz' 2> /dev/null)
    if [[ $eskimo_package == "" ]]; then
        echo_date "Couldn't find any eskimo package in $SCRIPT_DIR/../../target"
        exit 1
    fi

    echo_date " - Uploading eskimo"
    vagrant upload $eskimo_package /home/vagrant/eskimo.tar.gz $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    echo_date " - Extracting eskimo"
    vagrant ssh -c "tar xvfz eskimo.tar.gz" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    echo_date " - Find eskimo folder name"
    eskimo_folder=$(vagrant ssh -c "ls /home/vagrant | grep eskimo | grep -v gz" $TARGET_MASTER_VM 2> /dev/null | sed -e 's/\r//g')
    if [[ $eskimo_folder == "" ]]; then
        echo_date "Couldn't get eskimo folder name"
        exit 2
    fi

    echo_date " - Installing Eskimo"
    vagrant ssh -c "sudo mv /home/vagrant/$eskimo_folder /usr/local/lib" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    echo_date " - Giving back eskimo installation folder to root"
    vagrant ssh -c "sudo chown root. /usr/local/lib/$eskimo_folder" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    echo_date " - Changing eskimo port to 80"
    vagrant ssh -c "sudo sed -i s/\"server.port=9191\"/\"server.port=80\"/g /usr/local/lib/$eskimo_folder/conf/eskimo.properties" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    echo_date " - Installing Systemd file"
    vagrant ssh -c "sudo bash /usr/local/lib/$eskimo_folder/bin/utils/__install-eskimo-systemD-unit-file.sh -f" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    echo_date " - Uploading packages distrib packages"
    for i in $(find "$SCRIPT_DIR"/../../packages_distrib -name '*.tar.gz'); do
        filename=$(basename $i)
        vagrant upload $i /usr/local/lib/$eskimo_folder/packages_distrib/$filename $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1
    done

    echo_date " - Removing eskimo archive from home folder"
    vagrant ssh -c "rm -Rf /home/vagrant/eskimo.tar.gz" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    echo_date " - Reload systemD daemon"
}

initial_setup_eskimo() {

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
    echo_date " - CALL Saving setup"
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

}

setup_eskimo() {

    sleep 10

    # login again
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
    if [[ $(echo $status | jq -r '.clear') != "nodes" ]]; then
        echo "Didn't get expected 'clear' status"
        echo "Got status : $status"
        exit 5
    fi

    # Services configuration
    # ----------------------------------------------------------------------------------------------------------------------

    if [[ -z $MULTIPLE_NODE ]]; then
        export additional_RAM="5000"
    else
        export additional_RAM="4000"
    fi

    # upload services config
    echo_date " - CALL saving services config"
    call_eskimo \
        "save-services-settings" \
        "{
    \"elasticsearch-bootstrap-memory_lock\":\"\",
    \"elasticsearch-action-destructive_requires_name\":\"\",
    \"elasticsearch-index-refresh_interval\":\"\",
    \"elasticsearch-index-number_of_replicas\":\"\",
    \"elasticsearch-index-number_of_shards\":\"\",
    \"flink-app-master-jobmanager-heap-size\":\"\",
    \"flink-app-master-taskmanager-heap-size\":\"\",
    \"flink-app-master-parallelism-default\":\"\",
    \"flink-app-master-mesos-resourcemanager-tasks-cpus\":\"\",
    \"flink-app-master-mesos-resourcemanager-tasks-mem\":\"\",
    \"flink-app-master-taskmanager-numberOfTaskSlots\":\"\",
    \"kafka-num-network-threads\":\"\",
    \"kafka-num-io-threads\":\"\",
    \"kafka-socket-send-buffer-bytes\":\"\",
    \"kafka-socket-receive-buffer-bytes\":\"\",
    \"kafka-socket-request-max-bytes\":\"\",
    \"kafka-num-partitions\":\"\",
    \"kafka-log-retention-hours\":\"\",
    \"marathon-task_launch_timeout\":\"\",
    \"mesos-agent-cpu_additional\":\"\",
    \"mesos-agent-ram_additional\":\"$additional_RAM\",
    \"spark-executor-spark-driver-memory\":\"\",
    \"spark-executor-spark-rpc-numRetries\":\"\",
    \"spark-executor-spark-rpc-retry-wait\":\"\",
    \"spark-executor-spark-scheduler-mode\":\"\",
    \"spark-executor-spark-locality-wait\":\"\",
    \"spark-executor-spark-dynamicAllocation-executorIdleTimeout\":\"\",
    \"spark-executor-spark-dynamicAllocation-cachedExecutorIdleTimeout\":\"\",
    \"spark-executor-spark-executor-memory\":\"\"
    }"

    # Now need to apply command
    echo_date " - CALL applying services config"
    call_eskimo \
        "apply-services-settings"

    # Apply nodes config
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - CALL saving nodes config"

    if [[ -z $MULTIPLE_NODE ]]; then
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
    else
        call_eskimo \
            "save-nodes-config" \
            '{
        "node_id1":"192.168.10.51",
        "node_id2":"192.168.10.52",
        "node_id3":"192.168.10.53",
        "node_id4":"192.168.10.54",
        "flink-app-master":"2",
        "marathon":"2",
        "mesos-master":"1",
        "zookeeper":"1",
        "elasticsearch1":"on",
        "elasticsearch2":"on",
        "elasticsearch3":"on",
        "elasticsearch4":"on",
        "flink-worker1":"on",
        "flink-worker2":"on",
        "flink-worker3":"on",
        "flink-worker4":"on",
        "gluster1":"on",
        "gluster2":"on",
        "gluster3":"on",
        "gluster4":"on",
        "kafka1":"on",
        "kafka2":"on",
        "kafka3":"on",
        "kafka4":"on",
        "logstash1":"on",
        "logstash2":"on",
        "logstash3":"on",
        "logstash4":"on",
        "mesos-agent1":"on",
        "mesos-agent2":"on",
        "mesos-agent3":"on",
        "mesos-agent4":"on",
        "ntp1":"on",
        "ntp2":"on",
        "ntp3":"on",
        "ntp4":"on",
        "prometheus1":"on",
        "prometheus2":"on",
        "prometheus3":"on",
        "prometheus4":"on",
        "spark-executor1":"on",
        "spark-executor2":"on",
        "spark-executor3":"on",
        "spark-executor4":"on"
        }'
    fi

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

check_all_services_up() {
    eskimo_status=$(query_eskimo "get-status")
    #echo $eskimo_status | jq -r " .nodeServicesStatus"

    all_found=true

    if [[ -z $MULTIPLE_NODE ]]; then
        for i in "service_marathon_172-17-0-1" \
            "service_mesos-agent_172-17-0-1" \
            "service_prometheus_172-17-0-1" \
            "service_flink-worker_172-17-0-1" \
            "service_kibana_172-17-0-1" \
            "service_zookeeper_172-17-0-1" \
            "service_spark-executor_172-17-0-1" \
            "service_mesos-master_172-17-0-1" \
            "service_zeppelin_172-17-0-1" \
            "service_spark-history-server_172-17-0-1" \
            "service_elasticsearch_172-17-0-1" \
            "service_logstash_172-17-0-1" \
            "service_cerebro_172-17-0-1" \
            "service_kafka_172-17-0-1" \
            "service_ntp_172-17-0-1" \
            "service_kafka-manager_172-17-0-1" \
            "service_gluster_172-17-0-1" \
            "node_alive_172-17-0-1" \
            "service_flink-app-master_172-17-0-1" \
            "service_grafana_172-17-0-1"; do
            if [[ $(echo $eskimo_status | jq -r ".nodeServicesStatus" | grep "$i" | cut -d ':' -f 2 | grep "OK") == "" ]]; then
                echo "not found $i"
                return
            fi
        done
    else
        for i in "service_marathon_192-168-10-52" \
            "service_mesos-agent_192-168-10-51" \
            "service_mesos-agent_192-168-10-52" \
            "service_mesos-agent_192-168-10-53" \
            "service_mesos-agent_192-168-10-54" \
            "service_prometheus_192-168-10-51" \
            "service_prometheus_192-168-10-52" \
            "service_prometheus_192-168-10-53" \
            "service_prometheus_192-168-10-54" \
            "service_flink-worker_192-168-10-51" \
            "service_flink-worker_192-168-10-52" \
            "service_flink-worker_192-168-10-53" \
            "service_flink-worker_192-168-10-54" \
            "service_kibana_" \
            "service_zookeeper_192-168-10-51" \
            "service_spark-executor_192-168-10-51" \
            "service_spark-executor_192-168-10-52" \
            "service_spark-executor_192-168-10-53" \
            "service_spark-executor_192-168-10-54" \
            "service_mesos-master_192-168-10-51" \
            "service_zeppelin_" \
            "service_spark-history-server_" \
            "service_elasticsearch_192-168-10-51" \
            "service_elasticsearch_192-168-10-52" \
            "service_elasticsearch_192-168-10-53" \
            "service_elasticsearch_192-168-10-54" \
            "service_logstash_192-168-10-51" \
            "service_logstash_192-168-10-52" \
            "service_logstash_192-168-10-53" \
            "service_logstash_192-168-10-54" \
            "service_cerebro_" \
            "service_kafka_192-168-10-51" \
            "service_kafka_192-168-10-52" \
            "service_kafka_192-168-10-53" \
            "service_kafka_192-168-10-54" \
            "service_ntp_192-168-10-51" \
            "service_ntp_192-168-10-52" \
            "service_ntp_192-168-10-53" \
            "service_ntp_192-168-10-54" \
            "service_kafka-manager_" \
            "service_gluster_192-168-10-51" \
            "service_gluster_192-168-10-52" \
            "service_gluster_192-168-10-53" \
            "service_gluster_192-168-10-54" \
            "node_alive_192-168-10-51" \
            "node_alive_192-168-10-52" \
            "node_alive_192-168-10-53" \
            "node_alive_192-168-10-54" \
            "service_flink-app-master_192-168-10-52" \
            "service_grafana_"; do
            if [[ $(echo $eskimo_status | jq -r ".nodeServicesStatus" | grep "$i" | cut -d ':' -f 2 | grep "OK") == "" ]]; then
                echo "not found $i"
                return
            fi
        done
    fi

    echo "OK"
}

wait_all_services_up() {

    echo_date " - CALL ensuring services are up and OK"

    for i in seq 1 60; do

        all_service_status=$(check_all_services_up)
        if [[ $all_service_status == "OK" ]]; then
            return
        fi

        if [[ $i == 30 ]]; then
            echo_date "No all services managed to come up in 600 seconds - $all_service_status no found."
            exit 50
        fi

        sleep 10

    done
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
        $TARGET_MASTER_VM \
        >> /tmp/integration-test.log 2>&1

    echo_date " - Creating topic berka-payments-aggregate"
    vagrant ssh -c \
        "/usr/local/bin/kafka-topics.sh --create --replication-factor 1 --partitions 4 --zookeeper localhost:2181 --topic berka-payments-aggregate" \
        $TARGET_MASTER_VM \
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
        $TARGET_MASTER_VM \
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
        $TARGET_MASTER_VM \
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

    # Run all paragraphs from all other notebooks
    # ----------------------------------------------------------------------------------------------------------------------

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

do_cleanup() {

    # Cleanup (for demo preparation)
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - Delete spark and flink checkpoint locations"
    vagrant ssh -c "sudo rm -Rf /var/lib/spark/data/checkpoints/*" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    # EDIT : no keeping these in the end
    #echo_date " - Delete all berka indices except berka-payments and berka-transactions"

    #echo_date "   + Delete berka-account"
    #vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-account" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    #echo_date "   + Delete berka-card"
    #vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-card" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    #echo_date "   + Delete berka-disp"
    #vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-disp" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    #echo_date "   + Delete berka-district"
    #vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-district" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    #echo_date "   + Delete berka-client"
    #vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-client" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    #echo_date "   + Delete berka-loan"
    #vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-loan" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    #echo_date "   + Delete berka-order"
    #vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-order" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    #echo_date "   + Delete berka-trans"
    echo_date " - Delete berka index berka-trans"
    vagrant ssh -c "curl -XDELETE http://localhost:9200/berka-trans" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

}

test_web_apps() {

    # Testing web apps
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - Testing web applications"

    echo_date "   + testing Kibana answering (on berka dashboard)"
    if [[ $(query_eskimo "kibana/api/saved_objects/dashboard/df24fd20-a7f9-11ea-956a-630da1c33bca" | grep migrationVersion) == "" ]]; then
        echo_date "Couldn't reach Kibana dashboard"
        exit 101
    fi

    echo_date "   + testing cerebro"
    if [[ $(query_eskimo "cerebro/" | grep 'ng-app="cerebro"') == "" ]]; then
        echo_date "Couldn't reach Cerebro"
        exit 102
    fi

    echo_date "   + testing grafana (on system dashboard)"
    if [[ $(query_eskimo "grafana/api/dashboards/db/eskimo-system-wide-monitoring" | grep meta) == "" ]]; then
        echo_date "Couldn't reach Grafana system dashboard"
        exit 103
    fi

    echo_date "   + testing marathon application count"
    marathon_apps=$(query_eskimo "marathon/v2/apps" | jq -r ' .apps | .[] | .id' 2> /dev/null)
    if [[ $(echo "$marathon_apps" | wc -l) != $EXPECTED_NBR_APPS_MARATHON ]]; then
        echo_date "Didn't find $EXPECTED_NBR_APPS_MARATHON apps in marathon"
        echo_date "Found apps:"
        echo "$marathon_apps"
        exit 104
    fi

    echo_date "   + testing spark history server"
    if [[ $(query_eskimo "spark-history-server/" | grep "Show incomplete applications") == "" ]]; then
        echo_date "Couldn't reach Spark History Server"
        exit 105
    fi

    echo_date "   + testing EGMI"
    echo_date "     - TODO"
    #if [[ $(query_eskimo "gluster/" | grep "a simple dashboard for GlusterFS") == "" ]]; then
    #    echo_date "Couldn't reach EGMI"
    #    exit 106
    #fi

    echo_date "   + testing kafka-manager"
    if [[ $(query_eskimo "kafka-manager/" | grep "clusters/Eskimo") == "" ]]; then
        echo_date "Couldn't reach Kafka-Manager"
        exit 106
    fi

}

# Additional tests
# ----------------------------------------------------------------------------------------------------------------------

test_doc(){

    # Test documentation is packaged and available
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - Testing Eskimo user guide"
    if [[ `query_eskimo "docs/eskimo-guide.html" | grep "<p>Eskimo is available under a dual licensing model"` == "" ]]; then
        echo "!! Couldnt get eskimo user guide"
        exit 111
    fi

    echo_date " - Testing Eskimo service dev guide"
    if [[ `query_eskimo "docs/service-dev-guide.html" | grep "<p>Eskimo is available under a dual licensing model"` == "" ]]; then
        echo "!! Couldnt get eskimo service dev guide"
        exit 112
    fi

    return
}

prepare_demo() {

    # [Optionally] Prepare demo
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - Loading Kibana flights sample data"
    curl  \
            -b $SCRIPT_DIR/cookies \
            -m 3600 \
            -H "kbn-version: 7.6.2" \
            -H "Content-Length: 0" \
            -H "Content-Type: application/json" \
            -H "Host: $BOX_IP" \
            -H "Origin: $BOX_IP" \
            -H "Referer: http://$BOX_IP/kibana/app/kibana" \
            -XPOST http://$BOX_IP/kibana/api/sample_data/flights \
            > kibana-flight-import \
            2> kibana-flight-import-error

    if [[ `cat kibana-flight-import | grep "elasticsearchIndicesCreated"` == "" ]]; then
        echo "!! Couldn't import kibana flights object"
        exit 113
    fi

    rm -f kibana-flight-import
    rm -f kibana-flight-import-error

    echo_date " - Loading Kibana Logs sample data"
    curl  \
            -b $SCRIPT_DIR/cookies \
            -m 3600 \
            -H "kbn-version: 7.6.2" \
            -H "Content-Length: 0" \
            -H "Content-Type: application/json" \
            -H "Host: $BOX_IP" \
            -H "Origin: $BOX_IP" \
            -H "Referer: http://$BOX_IP/kibana/app/kibana" \
            -XPOST http://$BOX_IP/kibana/api/sample_data/logs \
            > kibana-logs-import \
            2> kibana-logs-import-error

    if [[ `cat kibana-logs-import | grep "elasticsearchIndicesCreated"` == "" ]]; then
        echo "!! Couldn't import kibana logs object"
        exit 113
    fi

    rm -f kibana-logs-import
    rm -f kibana-logs-import-error

    echo_date " - Loading Kibana ecommerce sample data"
    curl  \
            -b $SCRIPT_DIR/cookies \
            -m 3600 \
            -H "kbn-version: 7.6.2" \
            -H "Content-Length: 0" \
            -H "Content-Type: application/json" \
            -H "Host: $BOX_IP" \
            -H "Origin: $BOX_IP" \
            -H "Referer: http://$BOX_IP/kibana/app/kibana" \
            -XPOST http://$BOX_IP/kibana/api/sample_data/ecommerce \
            > kibana-ecommerce-import \
            2> kibana-ecommerce-import-error

    if [[ `cat kibana-ecommerce-import | grep "elasticsearchIndicesCreated"` == "" ]]; then
        echo "!! Couldn't import kibana ecommerce object"
        exit 113
    fi

    rm -f kibana-ecommerce-import
    rm -f kibana-ecommerce-import-error

    echo_date " - Ensure all dashboards are available"

    echo_date "   + Fetching dashboards from Kibana"
    curl  \
            -b $SCRIPT_DIR/cookies \
            -m 3600 \
            -H "kbn-version: 7.6.2" \
            -H "Content-Length: 0" \
            -H "Content-Type: application/json" \
            -H "Host: $BOX_IP" \
            -H "Origin: $BOX_IP" \
            -H "Referer: http://$BOX_IP/kibana/app/kibana" \
            -XGET http://$BOX_IP/kibana/api/saved_objects/_find?default_search_operator=AND\&page=1\&per_page=1000\&search_fields=title%5E3\&search_fields=description\&type=dashboard \
            > kibana-dashboard-check \
            2> kibana-dashboard-check-error

    echo_date "   + Extracting dashboard names"
    cat kibana-dashboard-check | jq -r '.saved_objects | .[] | select(.type=="dashboard") | .attributes | .title' > kibana-dashboards

    echo_date "   + Ensure all dashboards are found"

    if [[ `cat kibana-dashboards | grep '\[eCommerce\] Revenue Dashboard'` == "" ]]; then
        echo "!! Cannot find [eCommerce] Revenue Dashboard"
        exit 121
    fi

    if [[ `cat kibana-dashboards | grep 'berka-transactions'` == "" ]]; then
        echo "!! Cannot find berka-transactions"
        exit 122
    fi

    if [[ `cat kibana-dashboards | grep '\[Flights\] Global Flight Dashboard'` == "" ]]; then
        echo "!! Cannot find [eCommerce] Revenue Dashboard"
        exit 123
    fi

    if [[ `cat kibana-dashboards | grep '\[Logs\] Web Traffic'` == "" ]]; then
        echo "!! Cannot find [eCommerce] Revenue Dashboard"
        exit 124
    fi

    rm -f kibana-dashboard-check
    rm -f kibana-dashboard-check-error
    rm -f kibana-dashboards

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

    echo_date " - Switching to Demo Mode"
    vagrant ssh -c "sudo sed -i s/\"eskimo.demoMode=false\"/\"eskimo.demoMode=true\"/g /usr/local/lib/$eskimo_folder/conf/eskimo.properties" integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Zeroing disk before shutdown"
    vagrant ssh -c "dd if=/dev/zero of=/tmp/empty.dd bs=1048576; rm /tmp/empty.dd " integration-test >> /tmp/integration-test.log 2>&1

    echo_date " - Stopping VM"
    vagrant halt integration-test >> /tmp/integration-test.log 2>&1

    echo_date "!!! You can now export the DemoVM using Virtual Box "
}

# get logs
#vagrant ssh -c "sudo journalctl -u eskimo" integration-test

usage() {
    echo "Usage:"
    echo "    -h  Display this help message."
    echo "    -d  After the build, prepare the VM for DemoVM"
    echo "    -r  Re-install eskimo on existing VM"
    echo "    -f  Fast repackage"
    echo "    -m  Test on multiple nodes"
    echo "    -n  Don't rebuild the software (use last build)"
}

export EXPECTED_NBR_APPS_MARATHON=6

export BOX_IP=192.168.10.41
export TARGET_MASTER_VM="integration-test"

sudo rm -Rf /tmp/integration-test.log

# Parse options to the integration-test script
while getopts ":hdrfmn" opt; do
    case ${opt} in
        h)
            usage
            exit 0
            ;;
        d)
            export DEMO=demo
            ;;
        r)
            export REBUILD_ONLY=rebuild
            ;;
        f)
            export FAST_REPACKAGE=fast
            ;;
        n)
            export DONT_REBUILD=dont
            ;;
        m)
            export MULTIPLE_NODE=multiple
            export TARGET_MASTER_VM="integration-test1"
            export BOX_IP=192.168.10.51
            ;;
        :)
            break
            ;;
        \?)
            echo "Invalid Option: -$OPTARG" 1>&2
            exit 1
            ;;
    esac
done

if [[ ! -z $DEMO && ! -z $MULTIPLE_NODE ]]; then
    echo "Demo and Multiple nodes are exckusive "
    exit 70
fi

check_for_virtualbox

check_for_vagrant

if [[ -z $DONT_REBUILD ]]; then
    rebuild_eskimo
fi

if [[ -z $REBUILD_ONLY ]]; then
    build_box
fi

install_eskimo

initial_setup_eskimo

if [[ -z $REBUILD_ONLY ]]; then

    setup_eskimo

    wait_all_services_up

    run_zeppelin_data_load

    run_zeppelin_spark_kafka

    run_zeppelin_flink_kafka

    run_zeppelin_other_notes

    test_web_apps

    test_doc

    do_cleanup
fi

if [[ $DEMO == "demo" ]]; then
    prepare_demo
fi

#vagrant ssh -c "sudo journalctl -u eskimo" integration-test
