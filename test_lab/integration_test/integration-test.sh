
#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

    # Specific zeppelin case :
    if [[ $(cat eskimo-call-result | grep '"code":"ERROR"') != "" ]]; then
        echo_date "Couldn't successfully call eskimo URL (zeppelin notebook): $URL"
        echo_date "Got result"
        cat eskimo-call-result
        exit 10
    fi

    rm -Rf eskimo-call-result
}

wait_for_taskmanager_registered() {

    echo_date " - Now waiting for Kubernetes to report flink taskmanager"
    for attempt in $(seq 1 60); do
        sleep 10
        set +e
        spark_exec_status=$(vagrant ssh -c \
                "sudo /usr/local/bin/kubectl get pod 2>/dev/null" \
                $TARGET_MASTER_VM \
                | grep 'flink-runtime-taskmanager' | grep Running)
        set -e
        if [[ $spark_exec_status != "" ]]; then
            echo_date "   + Found zeppelin flink taskmanager running"
            break
        fi
        if [[ $attempt == 60 ]]; then
            echo_date "Could not get flink taskmanager up and running within 600 seconds. Crashing"

            return 1
        fi
    done

    return 0
}

wait_for_taskmanager_unregistered() {

    echo_date " - Waiting for Kubernetes to unregister taskmanager (not to compromise other tests)"
    for attempt in $(seq 1 60); do
        sleep 10
        set +e
        spark_exec_status=$(vagrant ssh -c \
                    "sudo /usr/local/bin/kubectl get pod" \
                    $TARGET_MASTER_VM \
                    | grep 'flink-runtime-taskmanager' | grep Running  )
        set -e
        if [[ $spark_exec_status == "" ]]; then
            echo_date "   + No Zeppelin Flink taskmanager found anymore, can continue ..."
            break
        fi
        if [[ $attempt == 60 ]]; then
            echo_date "Kubernetes did not unregister executor within 600 seconds. Crashing"
            #exit 41
        fi
    done
}

wait_for_executor_registered() {

    echo_date " - Now waiting for Kubernetes to report spark executor"
    for attempt in $(seq 1 60); do
        sleep 10
        set +e
        spark_exec_status=$(vagrant ssh -c \
                  "sudo /usr/local/bin/kubectl get pod 2>/dev/null" \
                  $TARGET_MASTER_VM \
                  | grep -E 'zeppelin-spark|spark-integration' | grep Running  )
        set -e
        if [[ $spark_exec_status != "" ]]; then
            echo_date "   + Found zeppelin spark executor running"
            break
        fi
        if [[ $attempt == 60 ]]; then
            echo_date "Could not get spark executor up and running within 600 seconds. Crashing"

            return 1
        fi
    done

    return 0
}

wait_for_executor_unregistered() {

    echo_date " - Waiting for Kubernetes to unregister executor (not to compromise other tests)"
    for attempt in $(seq 1 60); do
        sleep 10
        set +e
        spark_exec_status=$(vagrant ssh -c \
                  "sudo /usr/local/bin/kubectl get pod" \
                  $TARGET_MASTER_VM \
                  | grep -E 'zeppelin-spark|spark-integration' | grep Running  )
        set -e
        if [[ $spark_exec_status == "" ]]; then
            echo_date "   + No Zeppelin Spark executor found anymore, can continue ..."
            break
        fi
        if [[ $attempt == 60 ]]; then
            echo_date "Kubernetes did not unregister executor within 600 seconds. Crashing"
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
    trap __returned_to_saved_dir ERR

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
        vagrant ssh -c "sudo yum install -y wget git gcc glibc" $1 >> /tmp/integration-test.log 2>&1

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
    for i in $(find "$SCRIPT_DIR"/../../packages_distrib -maxdepth 1 -name '*.tar.gz'); do
        filename=$(basename $i)
        vagrant upload $i /usr/local/lib/$eskimo_folder/packages_distrib/$filename $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1
    done

    echo_date " - Removing eskimo archive from home folder"
    vagrant ssh -c "rm -Rf /home/vagrant/eskimo.tar.gz" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1

    echo_date " - Reload systemD daemon"
    vagrant ssh -c "sudo systemctl daemon-reload" $TARGET_MASTER_VM >> /tmp/integration-test.log 2>&1
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
        -d 'eskimo-username=admin&eskimo-password=password' \
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
    "setup-kube-origin":"build",
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
        -d 'eskimo-username=admin&eskimo-password=password' \
        >> /tmp/integration-test.log 2>&1

#    # fetch status now and test it
#    echo_date " - CALL Fetching status"
#    status=$(curl -b $SCRIPT_DIR/cookies http://$BOX_IP/get-status 2> /dev/null)
#    if [[ $(echo $status | jq -r '.status') != "OK" ]]; then
#        echo "Couldn't successfuly fetch status !"
#        echo "Got status : $status"
#        exit 4
#    fi
#    # should be clear setup before setup is applied
#    if [[ $(echo $status | jq -r '.clear') != "nodes" ]]; then
#        echo "Didn't get expected 'clear' status"
#        echo "Got status : $status"
#        exit 5
#    fi

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
           \"cerebro-Xms\": \"\",
           \"cerebro-Xmx\": \"\",
           \"elasticsearch-Xms\": \"\",
           \"elasticsearch-Xmx\": \"\",
           \"elasticsearch-action---destructive_requires_name\": \"\",
           \"elasticsearch-index---refresh_interval\": \"\",
           \"elasticsearch-index---number_of_replicas\": \"\",
           \"elasticsearch-index---number_of_shards\": \"\",
           \"flink-runtime-jobmanager---memory---process---size\": \"\",
           \"flink-runtime-taskmanager---memory---process---size:\": \"\",
           \"flink-runtime-parallelism---default\": \"\",
           \"flink-runtime-taskmanager---numberOfTaskSlots\": \"\",
           \"gluster-target---volumes\": \"spark_eventlog,spark_data,flink_data,kafka_data,flink_completed_jobs,logstash_data,kubernetes_registry,kubernetes_ssl\",
           \"grafana-admin_user\": \"\",
           \"grafana-admin_password\": \"\",
           \"kafka-Xms\": \"\",
           \"kafka-Xmx\": \"\",
           \"kafka-num---network---threads\": \"\",
           \"kafka-num---io---threads\": \"\",
           \"kafka-socket---send---buffer---bytes\": \"\",
           \"kafka-socket---receive---buffer---bytes\": \"\",
           \"kafka-socket---request---max---bytes\": \"\",
           \"kafka-num---partitions\": \"\",
           \"kafka-log---retention---hours\": \"\",
           \"kibana-max-old-space-size\": \"\",
           \"logstash-Xms\": \"\",
           \"logstash-Xmx\": \"\",
           \"spark-runtime-spark---driver---memory\": \"\",
           \"spark-runtime-spark---rpc---numRetries\": \"\",
           \"spark-runtime-spark---rpc---retry---wait\": \"\",
           \"spark-runtime-spark---scheduler---mode\": \"\",
           \"spark-runtime-spark---locality---wait\": \"\",
           \"spark-runtime-spark---dynamicAllocation---executorIdleTimeout\": \"\",
           \"spark-runtime-spark---dynamicAllocation---cachedExecutorIdleTimeout\": \"\",
           \"spark-runtime-spark---dynamicAllocation---shuffleTracking---timeout\": \"\",
           \"spark-runtime-spark---dynamicAllocation---schedulerBacklogTimeout\": \"\",
           \"spark-runtime-spark---executor---memory\": \"\",
           \"zeppelin-Xmx\": \"\",
           \"zeppelin-zeppelin_note_isolation\": \"\",
           \"zookeeper-Xms\": \"600m\",
           \"zookeeper-Xmx\": \"700m\"
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
        "node_id1":"192.168.56.41",
        "kube-master":"1",
        "kube-slave1":"on",
        "zookeeper":"1",
        "gluster1":"on",
        "ntp1":"on",
        "etcd1":"on",
        "prometheus1":"on",
        "logstash-cli1":"on",
        "spark-cli1":"on",
        "kafka-cli1":"on",
        "flink-cli1":"on"
        }'
    else
        call_eskimo \
            "save-nodes-config" \
            '{
        "node_id1":"192.168.10.51",
        "node_id2":"192.168.10.52",
        "node_id3":"192.168.10.53",
        "node_id4":"192.168.10.54",
        "kube-master":"1",
        "zookeeper":"1",
        "etcd1":"on",
        "etcd2":"on",
        "etcd3":"on",
        "etcd4":"on",
        "kube-slave1":"on",
        "kube-slave2":"on",
        "kube-slave3":"on",
        "kube-slave4":"on",
        "gluster1":"on",
        "gluster2":"on",
        "gluster3":"on",
        "gluster4":"on",
        "ntp1":"on",
        "ntp2":"on",
        "ntp3":"on",
        "ntp4":"on",
        "prometheus1":"on",
        "prometheus2":"on",
        "prometheus3":"on",
        "prometheus4":"on",
        "logstash-cli1":"on",
        "logstash-cli2":"on",
        "logstash-cli3":"on",
        "logstash-cli4":"on",
        "spark-cli1":"on",
        "spark-cli2":"on",
        "spark-cli3":"on",
        "spark-cli4":"on",
        "kafka-cli1":"on",
        "kafka-cli2":"on",
        "kafka-cli3":"on",
        "kafka-cli4":"on",
        "flink-cli1":"on",
        "flink-cli2":"on",
        "flink-cli3":"on",
        "flink-cli4":"on"
        }'
    fi

    # Now need to apply command
    echo_date " - CALL applying nodes config"
    call_eskimo \
        "apply-nodes-config"

    # Apply kubernetes config
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - CALL saving kubernetes config"
    call_eskimo \
        "save-kubernetes-services-config" \
        '{
          "cerebro_install": "on",
          "cerebro_cpu": "0.2",
          "cerebro_ram": "800M",
          "elasticsearch_install": "on",
          "elasticsearch_cpu": "0.3",
          "elasticsearch_ram": "1024M",
          "flink-runtime_install": "on",
          "flink-runtime_cpu": "0.3",
          "flink-runtime_ram": "1.2G",
          "grafana_install": "on",
          "grafana_cpu": "0.2",
          "grafana_ram": "800M",
          "kafka_install": "on",
          "kafka_cpu": "0.2",
          "kafka_ram": "1G",
          "kafka-manager_install": "on",
          "kafka-manager_cpu": "0.1",
          "kafka-manager_ram": "1G",
          "kibana_install": "on",
          "kibana_cpu": "0.2",
          "kibana_ram": "1024M",
          "kubernetes-dashboard_install": "on",
          "kubernetes-dashboard_cpu": "0.1",
          "kubernetes-dashboard_ram": "1G",
          "logstash_install": "on",
          "logstash_cpu": "0.3",
          "logstash_ram": "1G",
          "spark-history-server_install": "on",
          "spark-history-server_cpu": "0.1",
          "spark-history-server_ram": "1G",
          "spark-runtime_install": "on",
          "spark-runtime_cpu": "0.3",
          "spark-runtime_ram": "1.2G",
          "zeppelin_install": "on",
          "zeppelin_cpu": "0.3",
          "zeppelin_ram": "3G"
        }'

    # Now need to apply command
    echo_date " - CALL applying kubernetes config"
    call_eskimo \
        "apply-kubernetes-services-config"
}

check_all_services_up() {
    eskimo_status=$(query_eskimo "get-status")
    #echo $eskimo_status | jq -r " .nodeServicesStatus"

    all_found=true

    if [[ -z $MULTIPLE_NODE ]]; then
        for i in "service_kubernetes_192-168-56-41" \
            "service_mesos-agent_192-168-56-41" \
            "service_prometheus_192-168-56-41" \
            "service_flink-runtime_192-168-56-41" \
            "service_kibana_192-168-56-41" \
            "service_zookeeper_192-168-56-41" \
            "service_spark-runtime_192-168-56-41" \
            "service_mesos-master_192-168-56-41" \
            "service_zeppelin_192-168-56-41" \
            "service_spark-history-server_192-168-56-41" \
            "service_elasticsearch_192-168-56-41" \
            "service_logstash_192-168-56-41" \
            "service_cerebro_192-168-56-41" \
            "service_kafka_192-168-56-41" \
            "service_ntp_192-168-56-41" \
            "service_kafka-manager_192-168-56-41" \
            "service_gluster_192-168-56-41" \
            "node_alive_192-168-56-41" \
            "service_flink-app-master_192-168-56-41" \
            "service_grafana_192-168-56-41"; do
            if [[ $(echo $eskimo_status | jq -r ".nodeServicesStatus" | grep "$i" | cut -d ':' -f 2 | grep "OK") == "" ]]; then
                echo "not found $i"
                return
            fi
        done
    else
        for i in "service_kubernetes_192-168-10-52" \
            "service_mesos-agent_192-168-10-51" \
            "service_mesos-agent_192-168-10-52" \
            "service_mesos-agent_192-168-10-53" \
            "service_mesos-agent_192-168-10-54" \
            "service_prometheus_192-168-10-51" \
            "service_prometheus_192-168-10-52" \
            "service_prometheus_192-168-10-53" \
            "service_prometheus_192-168-10-54" \
            "service_flink-runtime_192-168-10-51" \
            "service_flink-runtime_192-168-10-52" \
            "service_flink-runtime_192-168-10-53" \
            "service_flink-runtime_192-168-10-54" \
            "service_kibana_" \
            "service_zookeeper_192-168-10-51" \
            "service_spark-runtime_192-168-10-51" \
            "service_spark-runtime_192-168-10-52" \
            "service_spark-runtime_192-168-10-53" \
            "service_spark-runtime_192-168-10-54" \
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

query_zeppelin_notebook () {
    nb_path=$1
    curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XGET http://$BOX_IP/zeppelin/api/v1/namespaces/default/services/zeppelin:31008/proxy/api/notebook \
            2> /dev/null |
            jq -r " .body | .[] | select (.path == \"$nb_path\") | .id"
}

run_all_zeppelin_pararaphs () {
    nb_path=$1
    nb_id=`query_zeppelin_notebook "$nb_path"`

    for i in `curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XGET http://$BOX_IP/zeppelin/api/v1/namespaces/default/services/zeppelin:31008/proxy/api/notebook/$nb_id \
            2> /dev/null | jq -r ' .body | .paragraphs | .[] | .id '`; do

        echo_date "  + running $i"
        call_eskimo \
            "zeppelin/api/v1/namespaces/default/services/zeppelin:31008/proxy/api/notebook/run/$nb_id/$i"

    done
}

clear_zeppelin_results () {
    nb_path=$1
    nb_id=`query_zeppelin_notebook "$nb_path"`

    call_eskimo \
        "zeppelin/api/v1/namespaces/default/services/zeppelin:31008/proxy/api/notebook/$nb_id/clear" \
        "" \
        "PUT"
}

run_zeppelin_pararaph () {
    nb_path=$1
    par_nbr=$2
    params=$3
    nb_id=`query_zeppelin_notebook "$nb_path"`

    cnt=0
    for i in `curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XGET http://$BOX_IP/zeppelin/api/v1/namespaces/default/services/zeppelin:31008/proxy/api/notebook/$nb_id \
            2> /dev/null | jq -r ' .body | .paragraphs | .[] | .id '`; do

        cnt=$((cnt + 1))

        if [[ $cnt == $par_nbr ]]; then
            echo_date "  + running $i"
            call_eskimo \
                "zeppelin/api/v1/namespaces/default/services/zeppelin:31008/proxy/api/notebook/run/$nb_id/$i" "$params"
            break
        fi
    done
}

get_zeppelin_paragraph_status () {
    nb_path=$1
    par_nbr=$2
    nb_id=`query_zeppelin_notebook "$nb_path"`

    cnt=0
    for i in `curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XGET http://$BOX_IP/zeppelin/api/v1/namespaces/default/services/zeppelin:31008/proxy/api/notebook/$nb_id \
            2> /dev/null | jq -r ' .body | .paragraphs | .[] | .id '`; do

        cnt=$((cnt + 1))

        if [[ $cnt == $par_nbr ]]; then

            curl \
                -b $SCRIPT_DIR/cookies \
                -H 'Content-Type: application/json' \
                -XGET http://$BOX_IP/zeppelin/api/v1/namespaces/default/services/zeppelin:31008/proxy/api/notebook/$nb_id \
                2> /dev/null | jq -r " .body | .paragraphs | .[] | select(.id==\"$i\") | .status "

            break
        fi
    done
}

start_zeppelin_pararaph () {
    nb_path=$1
    par_nbr=$2
    nb_id=`query_zeppelin_notebook "$nb_path"`

    cnt=0
    for i in `curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XGET http://$BOX_IP/zeppelin/api/v1/namespaces/default/services/zeppelin:31008/proxy/api/notebook/$nb_id \
            2> /dev/null | jq -r ' .body | .paragraphs | .[] | .id '`; do

        cnt=$((cnt + 1))

        if [[ $cnt == $par_nbr ]]; then
            echo_date "  + running $i"
            call_eskimo \
                "zeppelin/api/v1/namespaces/default/services/zeppelin:31008/proxy/api/notebook/job/$nb_id/$i"
            break
        fi
    done
}

stop_zeppelin_pararaph () {
    nb_path=$1
    par_nbr=$2
    nb_id=`query_zeppelin_notebook "$nb_path"`

    cnt=0
    for i in `curl \
            -b $SCRIPT_DIR/cookies \
            -H 'Content-Type: application/json' \
            -XGET http://$BOX_IP/zeppelin/api/v1/namespaces/default/services/zeppelin:31008/proxy/api/notebook/$nb_id \
            2> /dev/null | jq -r ' .body | .paragraphs | .[] | .id '`; do

        cnt=$((cnt + 1))

        if [[ $cnt == $par_nbr ]]; then
            echo_date "  + stopping $i"

            curl \
                    -b $SCRIPT_DIR/cookies \
                    -H 'Content-Type: application/json' \
                    -XDELETE http://$BOX_IP/zeppelin/api/v1/namespaces/default/services/zeppelin:31008/proxy/api/notebook/job/$nb_id/$i \
                    >> /tmp/integration-test.log 2>&1


            break
        fi
    done
}

run_zeppelin_data_load() {

    # Now running logstash demo zeppelin paragraphs
    # ----------------------------------------------------------------------------------------------------------------------

    # First I need to wait for zeppelin to be up and running
    echo_date " - Waiting for Zeppelin to be up and running (querying kubernetes)"
    for attempt in $(seq 1 30); do
        sleep 10
        zeppelin_status=$(vagrant ssh -c \
                "sudo /usr/local/bin/kubectl get pod -o wide" \
                $TARGET_MASTER_VM \
                | grep zeppelin | grep Running | sed s/'  *'/' '/g | cut -d ' ' -f 7)
        if [[ $zeppelin_status != "" ]]; then
            echo_date "   + Found zeppelin running on $zeppelin_status"
            break
        fi
        if [[ $attempt == 30 ]]; then
            echo_date "Could not get zeppelin up and running within 300 seconds. Crashing"
            exit 21
        fi
    done
    # Giving it a little more time to really start

    sleep 40

    echo_date " - ZEPPELIN logstash demo"
    run_all_zeppelin_pararaphs "/Logstash Demo"


    # Now running Spark ES demo zeppelin paragraphs
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - ZEPPELIN Spark ES demo "
    run_all_zeppelin_pararaphs "/Spark Integration ES"

    echo_date " - Waiting for kubernetes to unregister spark executor (not to compromise other tests)"
    for attempt in $(seq 1 60); do
        sleep 10
        set +e
        spark_exec_status=$(vagrant ssh -c \
                "sudo /usr/local/bin/kubectl get pod" \
                $TARGET_MASTER_VM \
                | grep -E 'zeppelin-spark|spark-integration' | grep Running )
        set -e
        if [[ $spark_exec_status == "" ]]; then
            echo_date "   + No Zeppelin Spark executor found anymore, can continue ..."
            break
        fi
        if [[ $attempt == 60 ]]; then
            echo_date "Kubernetes did not unregister executor within 600 seconds. Crashing"
            exit 41
        fi
    done
}

create_kafka_topics() {

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


    run_zeppelin_pararaph "/Spark Integration Kafka" 1

    # Paragraph 2
    echo_date " - ZEPPELIN spark Kafka demo - start paragraph SPARK (async)"
    start_zeppelin_pararaph "/Spark Integration Kafka" 8

    wait_for_executor_registered
    if [[ $? != 0 ]]; then
        set +e

        stop_zeppelin_pararaph "/Spark Integration Kafka" 8

        exit 31
    fi

    # Giving it a little more time to be all set up
    sleep 20

    # Paragraph 1
    echo_date " - ZEPPELIN spark Kafka demo - start paragraph python feeder (async)"
    start_zeppelin_pararaph "/Spark Integration Kafka" 5

    echo_date " - ZEPPELIN spark Kafka demo - Now expecting some result on kafka topic berka-payments-aggregate"
    vagrant ssh -c \
        "/usr/local/bin/kafka-console-consumer.sh --bootstrap-server kafka.default.svc.cluster.eskimo:9092 --topic berka-payments-aggregate --timeout-ms 120000 --max-messages 100" \
        $TARGET_MASTER_VM \
        > kafka-berka-payments-aggregate-results 2> /dev/null

    if [[ $(wc -l kafka-berka-payments-aggregate-results | cut -d ' ' -f 1) -lt 100 ]]; then
        echo_date "Failed to fetch at least 100 aggregated payments from result topic"

        set +e

        # stop both paragraphs
        stop_zeppelin_pararaph "/Spark Integration Kafka" 5

        stop_zeppelin_pararaph "/Spark Integration Kafka" 8

        rm -f kafka-berka-payments-aggregate-results

        exit 30
    fi

    rm -f kafka-berka-payments-aggregate-results

    echo_date " - ZEPPELIN spark Kafka demo - stop paragraph python feeder (async)"
    stop_zeppelin_pararaph "/Spark Integration Kafka" 5

    echo_date " - ZEPPELIN spark Kafka demo - stop paragraph SPARK (async)"
    stop_zeppelin_pararaph "/Spark Integration Kafka" 8

    run_zeppelin_pararaph "/Spark Integration Kafka" 10

    wait_for_executor_unregistered

    echo_date " - ZEPPELIN spark Kafka demo - Clearing paragraph results"
    clear_zeppelin_results "/Spark Integration Kafka"

    echo_date " - ZEPPELIN spark Kafka demo - Re-running text paragraph"
    run_zeppelin_pararaph "/Spark Integration Kafka" 1

    run_zeppelin_pararaph "/Spark Integration Kafka" 2

    run_zeppelin_pararaph "/Spark Integration Kafka" 4

    run_zeppelin_pararaph "/Spark Integration Kafka" 6
}

run_zeppelin_kafka_streams() {

    # Now running Kafka Streams demo zeppelin paragraphs
    # ----------------------------------------------------------------------------------------------------------------------

    # Paragraph 2
    echo_date " - ZEPPELIN Kafka Streams demo - start paragraph Kafka Streams (async)"

    run_zeppelin_pararaph "/Kafka Streams Demo" 3

    start_zeppelin_pararaph "/Kafka Streams Demo" 5

    # Give it a little time to start
    sleep 8

    # Check status
    if [[ `get_zeppelin_paragraph_status "/Kafka Streams Demo" 5` != "RUNNING" ]]; then

        echo_date "Failed to start Kafka Streams Demo program"

        set +e

        stop_zeppelin_pararaph "/Kafka Streams Demo" 5

        # This is actually the way to stop paragraph 5
        run_zeppelin_pararaph "/Kafka Streams Demo" 7

        exit 100
    fi

    # start paragraph 9
    echo_date " - ZEPPELIN Kafka Streams demo - start console consummer"
    start_zeppelin_pararaph "/Kafka Streams Demo" 9

    # Give it a little time to start
    sleep 5

    # Check status
    if [[ `get_zeppelin_paragraph_status "/Kafka Streams Demo" 9` != "RUNNING" ]]; then

        echo_date "Failed to start Kafka Streams Demo program - console consummer paragraph"

        set +e

        stop_zeppelin_pararaph "/Kafka Streams Demo" 5

        # This is actually the way to stop paragraph 5
        run_zeppelin_pararaph "/Kafka Streams Demo" 7

        stop_zeppelin_pararaph "/Kafka Streams Demo" 9

        exit 101
    fi

    # run paragraph 9
    echo_date " - ZEPPELIN Kafka Streams demo - publish message"
    run_zeppelin_pararaph "/Kafka Streams Demo" 11 '{"params" : { "input": "some additional text with some more words" }}'

    # Give it a little time to run
    sleep 5

    # Now stop them all

    # This is actually the way to stop paragraph 3
    echo_date " - ZEPPELIN Kafka Streams demo - Stopping all paragraphs"
    run_zeppelin_pararaph "/Kafka Streams Demo" 7

    stop_zeppelin_pararaph "/Kafka Streams Demo" 9

    # Now check results
    echo_date " - ZEPPELIN Kafka Streams demo - Checking results"
    vagrant ssh -c \
        "/usr/local/bin/kafka-console-consumer.sh --bootstrap-server kafka.default.svc.cluster.eskimo:9092 --topic streams-wordcount-output --timeout-ms 10000 --from-beginning --max-messages 100" \
        $TARGET_MASTER_VM \
        > streams-wordcount-output-results 2> /dev/null

    if [[ $(wc -l streams-wordcount-output-results | cut -d ' ' -f 1) -lt 4 ]]; then
        echo_date "Failed to fetch at least 4 word counts from result topic"
        rm -f streams-wordcount-output-results
        exit 102
    fi

    rm -f streams-wordcount-output-results

}

run_zeppelin_flink_kafka() {

    # Now running Flink Kafka demo zeppelin paragraphs
    # ----------------------------------------------------------------------------------------------------------------------

    # Paragraph 2
    echo_date " - ZEPPELIN flink Kafka demo - start paragraph FLINK (async)"

    start_zeppelin_pararaph "/Flink Integration Kafka" 7

    wait_for_taskmanager_registered
    if [[ $? != 0 ]]; then

        set +e

        stop_zeppelin_pararaph "/Flink Integration Kafka" 7

        exit 31
    fi

    # Giving it a little more time to be all set up
    sleep 15

    # Paragraph 1
    echo_date " - ZEPPELIN flink Kafka demo - start paragraph python feeder (async)"
    start_zeppelin_pararaph "/Flink Integration Kafka" 5

    echo_date " - ZEPPELIN flink Kafka demo - Now expecting some result on kafka topic berka-payments-aggregate"
    vagrant ssh -c \
        "/usr/local/bin/kafka-console-consumer.sh --bootstrap-server kafka.default.svc.cluster.eskimo:9092 --topic berka-payments-aggregate --timeout-ms 120000 --max-messages 100" \
        $TARGET_MASTER_VM \
        > kafka-berka-payments-aggregate-results 2> /dev/null

    if [[ $(wc -l kafka-berka-payments-aggregate-results | cut -d ' ' -f 1) -lt 100 ]]; then
        echo_date "Failed to fetch at least 100 aggregated payments from result topic"

        set +e

        # stop both paragraphs
        stop_zeppelin_pararaph "/Flink Integration Kafka" 5

        stop_zeppelin_pararaph "/Flink Integration Kafka" 7

        rm -f kafka-berka-payments-aggregate-results

        exit 30
    fi

    rm -f kafka-berka-payments-aggregate-results

    echo_date " - ZEPPELIN flink Kafka demo - stop paragraph python feeder (async)"
    stop_zeppelin_pararaph "/Flink Integration Kafka" 5

    echo_date " - ZEPPELIN flink Kafka demo - stop paragraph FLINK (async)"
    stop_zeppelin_pararaph "/Flink Integration Kafka" 7

    wait_for_taskmanager_unregistered

    echo_date " - ZEPPELIN flink Kafka demo - Clearing paragraph results"
    clear_zeppelin_results "/Flink Integration Kafka"

    echo_date " - ZEPPELIN flink Kafka demo - Re-running text paragraph"
    run_zeppelin_pararaph "/Flink Integration Kafka" 1

    run_zeppelin_pararaph "/Flink Integration Kafka" 2

    run_zeppelin_pararaph "/Flink Integration Kafka" 4

    run_zeppelin_pararaph "/Flink Integration Kafka" 6
}

run_zeppelin_other_notes() {

    # Run all paragraphs from all other notebooks
    # ----------------------------------------------------------------------------------------------------------------------

    echo_date " - ZEPPELIN running Spark ML Demo (Regression)"
    run_all_zeppelin_pararaphs "/Spark ML Demo (Regression)"

    wait_for_executor_unregistered

    echo_date " - ZEPPELIN running Flink Batch Demo"
    run_all_zeppelin_pararaphs "/Flink Batch Demo"

    wait_for_taskmanager_unregistered

    echo_date " - ZEPPELIN running Spark SQL Demo"
    run_all_zeppelin_pararaphs "/Spark SQL Demo"

    wait_for_executor_unregistered

    echo_date " - ZEPPELIN running Spark RDD Demo"
    run_all_zeppelin_pararaphs "/Spark RDD Demo"

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

    echo_date "   + testing kubernetes application count"
    kubernetes_apps=$(query_eskimo "kubernetes/v2/apps" | jq -r ' .apps | .[] | .id' 2> /dev/null)
    if [[ $(echo "$kubernetes_apps" | wc -l) != $EXPECTED_NBR_APPS_kubernetes ]]; then
        echo_date "Didn't find $EXPECTED_NBR_APPS_kubernetes apps in kubernetes"
        echo_date "Found apps:"
        echo "$kubernetes_apps"
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

export EXPECTED_NBR_APPS_kubernetes=6

export BOX_IP=192.168.56.41
export DOCKER_LOCAL=192.168.56.41
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
            export BOX_IP=192.168.56.51
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

    create_kafka_topics

    run_zeppelin_spark_kafka

    run_zeppelin_flink_kafka

    run_zeppelin_kafka_streams

    run_zeppelin_other_notes

    test_web_apps

    test_doc

    do_cleanup

fi

if [[ $DEMO == "demo" ]]; then
    prepare_demo
fi



#vagrant ssh -c "sudo journalctl -u eskimo" integration-test
