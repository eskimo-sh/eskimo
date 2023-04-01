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

echoerr() { echo "$@" 1>&2; }

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

echo "Building Zeppelin Image"
echo "--------------------------------------------------------------------------------"

# reinitializing log
rm -f /tmp/zeppelin_build_log

echo " - Checking if spark eskimo image is available"
if [[ $(docker images -q eskimo/spark_template:latest 2>/dev/null) == "" ]]; then
    echo " - Trying to loads spark image"
    for i in $(ls -rt ../../packages_distrib/docker_template_spark*.tar.gz | tail -1); do
        echo "   + loading image $i"
        gunzip -c $i | docker load > /tmp/zeppelin_build_log 2>&1
        if [[ $? != 0 ]]; then
            echo "Could not load base image eskimo/spark_template:latest"
            cat /tmp/zeppelin_build_log
            exit 1
        fi
    done
fi

echo " - Building image zeppelin"
build_image zeppelin_template /tmp/zeppelin_build_log

echo " - Installing a few utilities"
docker exec -i zeppelin_template apt-get install -y zip netcat sshpass > /tmp/zeppelin_build_log 2>&1
fail_if_error $? "/tmp/zeppelin_build_log" 11

echo " - Installing python"
docker exec -i zeppelin_template apt-get -y install  python3-dev python3-six python3-virtualenv python3-pip cython3 > /tmp/zeppelin_build_log 2>&1
fail_if_error $? "/tmp/zeppelin_build_log" 5

echo " - Switching python default version to 3.x"
docker exec -i zeppelin_template update-alternatives --force --install /usr/bin/python python /usr/bin/python3.9 2 > /tmp/zeppelin_build_log 2>&1
fail_if_error $? "/tmp/flink_build_log" 6


echo " - Installing python packages for datascience"
docker exec -i zeppelin_template apt-get -y install python3-sklearn python3-numpy python3-pandas python3-plotly python3-kafka python3-filelock python3-matplotlib python3-nltk python3-elasticsearch > /tmp/zeppelin_build_log 2>&1
#docker exec -i zeppelin_template pip3 install pandas numpy scikit-learn matplotlib nltk plotly filelock py4j kafka-python3 > /tmp/zeppelin_build_log 2>&1
fail_if_error $? "/tmp/zeppelin_build_log" 12

echo " - Installing GlusterFS client"
docker exec -i zeppelin_template apt-get -y install glusterfs-client > /tmp/zeppelin_build_log 2>&1
fail_if_error $? "/tmp/zeppelin_build_log" -10


echo " - Installing flink"
cp installFlink.sh __installFlinkEff.sh
docker exec -i zeppelin_template bash /scripts/__installFlinkEff.sh | tee /tmp/zeppelin_build_log 2>&1
check_in_container_install_success /tmp/zeppelin_build_log
rm -f __installFlinkEff.sh


echo " - Installing kafka"
cp installKafka.sh __installKafkaEff.sh
docker exec -i zeppelin_template bash /scripts/__installKafkaEff.sh | tee /tmp/zeppelin_build_log 2>&1
check_in_container_install_success /tmp/zeppelin_build_log
rm -f __installKafkaEff.sh

#echo " - Re-Installing OpenJDK 8 to have compiler (Keeping JDK 8 for spark 2.x for compatibility)"
#docker exec -i zeppelin_template apt-get install -y openjdk-8-jdk > /tmp/spark_build_log 2>&1
#fail_if_error $? "/tmp/zeppelin_build_log" -3


#docker exec -it zeppelin_template bash


echo " - Installing JDK 8 for flink interpreter"
docker exec -i zeppelin_template bash /scripts/installJava8JDK.sh | tee /tmp/zeppelin_build_log 2>&1
check_in_container_install_success /tmp/zeppelin_build_log

echo " - Installing zeppelin"
if [[ $ZEPPELIN_IS_SNAPSHOT == "true" ]]; then

    #docker exec -it zeppelin_template bash

    docker exec -i zeppelin_template bash /scripts/installZeppelinFromSources.sh $USER $UID | tee /tmp/zeppelin_build_log 2>&1
    check_in_container_install_success /tmp/zeppelin_build_log

    echo "   + downloading built archive to /tmp/zeppelin-$ZEPPELIN_VERSION_SNAPSHOT.tar.gz"
    docker cp zeppelin_template:/tmp/zeppelin_setup/zeppelin-$ZEPPELIN_VERSION_SNAPSHOT.tar.gz /tmp/zeppelin-$ZEPPELIN_VERSION_SNAPSHOT.tar.gz > /tmp/zeppelin_build_log 2>&1
    fail_if_error $? "/tmp/zeppelin_build_log" 110

else

    if [[ -d $SCRIPT_DIR/patches ]]; then
        echo " - Copying patches folder"
        docker cp patches zeppelin_template:/patches > /tmp/zeppelin_build_log 2>&1
        fail_if_error $? "/tmp/zeppelin_build_log" 111
    fi

    docker exec -i zeppelin_template bash /scripts/installZeppelin.sh | tee /tmp/zeppelin_build_log 2>&1
    check_in_container_install_success /tmp/zeppelin_build_log
fi

echo " - Installing zeppelin Interpreters Java dependencies"
docker exec -i zeppelin_template bash /scripts/installZeppelinInterpreterDependencies.sh | tee /tmp/zeppelin_build_log 2>&1
if [[ $(tail -n 1 /tmp/zeppelin_build_log | grep " - In container install SUCCESS") == "" ]]; then
    echo " - In container install script ended up in error"
    cat /tmp/zeppelin_build_log
    exit 105
fi

echo " - Removing useless stuff"
docker exec -i zeppelin_template bash /scripts/removeUselessStuff.sh | tee /tmp/zeppelin_build_log 2>&1
check_in_container_install_success /tmp/zeppelin_build_log

#docker exec -it zeppelin_template bash

echo " - Cleaning up image"
# Don't uninstall adwaita since it removes the java compiler
docker exec -i spark_template apt-get -y auto-remove >> /tmp/spark_build_log 2>&1

echo " - Closing and saving image zeppelin"
close_and_save_image zeppelin_template /tmp/zeppelin_build_log $ZEPPELIN_VERSION
