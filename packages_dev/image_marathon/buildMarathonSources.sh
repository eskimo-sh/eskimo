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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "Building Marathon Package from sources"
echo "--------------------------------------------------------------------------------"

# reinitializing log
rm -f /tmp/marathon_source_build_log

echo " - Building image marathon"

if [[ -z "$NO_BASE_IMAGE" ]]; then
    echo " - Checking if base eskimo image is available"
    if [[ `docker images -q eskimo:base-eskimo_template 2>/dev/null` == "" ]]; then
        echo " - Trying to load base eskimo image"
        for i in `ls -rt ../../packages_distrib/docker_template_base-eskimo*.tar.gz | tail -1`; do
            echo "   + loading image $i"
            gunzip -c $i | docker load >  /tmp/marathon_source_build_log 2>&1
            fail_if_error $?  /tmp/marathon_source_build_log -10
        done
    fi
fi

echo " - Deleting any previous containers"
if [[ `docker ps -a -q -f name=marathon_source` != "" ]]; then
    docker stop marathon_source > /dev/null 2>&1
    docker container rm marathon_source > /dev/null 2>&1
fi

echo " - building docker image marathon_source"
docker build --iidfile id_file --tag eskimo:"marathon_source" - < Dockerfile.build  >  /tmp/marathon_source_build_log 2>&1
fail_if_error $?  /tmp/marathon_source_build_log -11

export TMP_FOLDER=/tmp
if [[ ! -z "$BUILD_TEMP_FOLDER" ]]; then
    export TMP_FOLDER=$BUILD_TEMP_FOLDER

    echo " - making sure I can write in $BUILD_TEMP_FOLDER"
    touch $BUILD_TEMP_FOLDER/test >  /tmp/marathon_source_build_log 2>&1
    fail_if_error $?  /tmp/marathon_source_build_log -11
fi

echo " - Starting container ""marathon_source"
# create and start container
docker run \
        --privileged \
        -v $PWD:/scripts \
        -v $PWD/../common:/common  \
        -v $TMP_FOLDER:/tmp \
        -d --name marathon_source \
        -i \
        -t eskimo:"marathon_source" bash  >  /tmp/marathon_source_build_log 2>&1
fail_if_error $?  /tmp/marathon_source_build_log -12

echo " - Ensuring image was well started"
if [[ -z $TEST_MODE ]]; then
    for i in `seq 1 5`; do
        sleep 1
        if [[ `docker ps -q -f name=marathon_source` != "" ]]; then
            break
        fi
        if [[ $i == 5 ]]; then
            echo "- Failed to start docker container marathon_source in 4 seconds"
            exit 4
        fi
    done
fi



# HACK : AS LONG AS SPARK DOESNT SUPPORT JAVA 11, I am building outside of eskimo base system
# Hence the need to repeat eskimo base system installation here
# ----------------------------------------------------------------------------------------------------------------------
echo " - (Hack) Creating missing directory /usr/share/man/man1/"
docker exec -i marathon_source mkdir -p /usr/share/man/man1/ > /tmp/marathon_source_build_log 2>&1
fail_if_error $? "/tmp/marathon_source_build_log" -2

echo " - Updating the packages"
docker exec -i marathon_source apt-get update > /tmp/marathon_source_build_log 2>&1
fail_if_error $? "/tmp/marathon_source_build_log" -2

echo " - Upgrading the appliance"
docker exec -i -e DEBIAN_FRONTEND=noninteractive marathon_source apt-get -yq upgrade > /tmp/marathon_source_build_log 2>&1
fail_if_error $? "/tmp/marathon_source_build_log" -2

echo " - Installing required utility tools for eskimo framework"
docker exec -i marathon_source apt-get install -y tar wget git unzip curl moreutils procps sudo net-tools jq apt-transport-https ca-certificates > /tmp/marathon_source_build_log 2>&1
fail_if_error $? "/tmp/marathon_source_build_log" -2
# ----------------------------------------------------------------------------------------------------------------------

echo " - Installing OpenJDK 8 (Keeping JDK 8 for marathon build for compatibility)"
docker exec -i marathon_source apt-get install -y openjdk-8-jdk > /tmp/marathon_source_build_log 2>&1
fail_if_error $? "/tmp/marathon_source_build_log" -3

echo " - Installing Marathon build tools "
docker exec -i marathon_source apt-get install -y maven gnupg > /tmp/marathon_source_build_log 2>&1
fail_if_error $? "/tmp/marathon_source_build_log" -3


echo " - Building marathon"
docker exec -it marathon_source bash /scripts/buildMarathonFromSources.sh | tee /tmp/marathon_source_build_log 2>&1
if [[ `tail -n 1 /tmp/marathon_source_build_log | grep " - In container install SUCCESS"` == "" ]]; then
    echo " - In container install script ended up in error"
    cat /tmp/marathon_source_build_log
    exit 103
fi

echo " - Copying result tarball from container"
docker cp -L marathon_source:/tmp/marathon_source_build/marathon/target/universal/marathon-"$MARATHON_VERSION_SOURCES_GITHUB".tgz \
       marathon-"$MARATHON_VERSION_SOURCES_GITHUB".tgz  > /tmp/marathon_source_build_log 2>&1
fail_if_error $? "/tmp/marathon_source_build_log" -3

#echo " - TODO"
#docker exec -it marathon_source bash


echo " - Cleaning up image"
docker exec -i marathon_source apt-get remove -y git gcc adwaita-icon-theme >> /tmp/marathon_source_build_log 2>&1
docker exec -i marathon_source apt-get -y auto-remove >> /tmp/marathon_source_build_log 2>&1


echo " - Cleaning build directory"
sudo rm -Rf /tmp/marathon_source_build

echo " - Closing and saving image marathon"
close_and_save_image marathon_source /tmp/marathon_source_build_log $MARATHON_VERSION_SHORT
