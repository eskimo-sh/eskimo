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

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

echo "-- INSTALLING ZEPPELIN ---------------------------------------------------------"

if [ -z "$1" ]; then
   echo "Expecting local user name to use for build as argument"
   exit 1
fi
export USER_TO_USE=$1

if [ -z "$2" ]; then
   echo "Expecting local user ID to use for build as argument"
   exit 2
fi
export UID_TO_USE=$2

if [ -z "$ZEPPELIN_VERSION" ]; then
    echo "Need to set ZEPPELIN_VERSION environment variable before calling this script !"
    exit 3
fi

if [ -z "$SPARK_VERSION_MAJOR" ]; then
    echo "Need to set SPARK_VERSION_MAJOR environment variable before calling this script !"
    exit 4
fi

if [ -z "$SCALA_VERSION" ]; then
    echo "Need to set SCALA_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=$(pwd)
function returned_to_saved_dir() {
     cd $saved_dir || return
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT
trap returned_to_saved_dir ERR


echo " - Changing to temp directory"
rm -Rf /tmp/zeppelin_build/
mkdir -p /tmp/zeppelin_build/
cd /tmp/zeppelin_build/ || (echo "Couldn't change to /tmp/zeppelin_build" && exit 200)

echo " - Install missing dependencies to build zeppelin sources"
sudo DEBIAN_FRONTEND=noninteractive apt-get -yq install libfontconfig1 r-base-dev r-cran-evaluate apt-transport-https lsb-release > /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -11

echo " - Adding node and NPM source"
sudo bash -c "curl -sL https://deb.nodesource.com/setup_10.x | sudo -E bash -" > /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -12

echo " - Installing node JS"
sudo DEBIAN_FRONTEND=noninteractive apt-get -yq install nodejs > /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -13


echo " - Creating local build user"
echo "$USER_TO_USE:x:$UID_TO_USE:$UID_TO_USE:$USER_TO_USE,,,:/home/$USER_TO_USE:/bin/bash" >> /etc/passwd

echo " - Creating home folder for local build user"
mkdir /home/$USER_TO_USE
chown -R $USER_TO_USE /home/$USER_TO_USE

echo " - Allowing local build user to use build folder"
chmod -R 777 .

echo " - Downloading Zeppelin git repository archive"
rm -Rf zeppelin
wget https://github.com/apache/zeppelin/archive/refs/heads/master.zip   > /tmp/zeppelin_install_log 2>&1
#wget https://github.com/apache/zeppelin/archive/branch-$ZEPPELIN_VERSION.zip  > /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -2

echo " - Extracting Zeppelin source code"
unzip master.zip   > /tmp/zeppelin_install_log 2>&1
#unzip branch-$ZEPPELIN_VERSION.zip   > /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -2

mv zeppelin-master zeppelin
#mv zeppelin-branch-$ZEPPELIN_VERSION zeppelin

echo " - Changing build folder owner"
chown -R $USER_TO_USE zeppelin
fail_if_error $? "/tmp/zeppelin_install_log" -1

echo " - update all pom.xml to use scala $SCALA_VERSION"
cd zeppelin/ || (echo "Couldn't cd to zeppelin/" && exit 1)
sudo -u $USER_TO_USE bash $PWD/dev/change_scala_version.sh $SCALA_VERSION > /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -2

#echo " - HACK - Fixing ElasticSearch interpreter"
#sed -i s/"hits\/total"/"hits\/total\/value"/g /tmp/zeppelin_build/zeppelin/elasticsearch/src/main/java/org/apache/zeppelin/elasticsearch/client/HttpBasedClient.java

#echo " - Setting JAVA_HOME to java-1.8.0-openjdk-amd64"
export JAVA_HOME=/usr/local/lib/jvm/openjdk-8/
export PATH=$JAVA_HOME/bin:$PATH

#echo " - Fixing Java interpreter"
#rm /etc/alternatives/java
#ln -s /usr/lib/jvm/java-8-openjdk-amd64/bin/java /etc/alternatives/java

echo " - build zeppelin with all interpreters"
for i in $(seq 1 2); do  # 2 attempts since sometimes download of packages fails
    echo "   + attempt $i"
    sudo -u $USER_TO_USE mvn clean package -DskipTests -Pspark-$SPARK_VERSION_MAJOR -Pscala-$SCALA_VERSION -Pbuild-distr  > /tmp/zeppelin_install_log 2>&1
    if [[ $? == 0 ]]; then
        echo "   + succeeded !"
        break
    else
        if [[ $i == 2 ]]; then
            echo "Connecto to container and do a 'cat /tmp/zeppelin_install_log' to get logs"
            exit 10
        fi
    fi
done

echo " - Installing with maven"
su -p $USER_TO_USE -c "mvn install -DskipTests -Pspark-$SPARK_VERSION_MAJOR -Pscala-$SCALA_VERSION -Pbuild-distr"  > /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -6

echo " - Creating setup temp directory"
mkdir -p /tmp/zeppelin_setup/

echo " - Copy Zeppelin sources to temp directory"
cp zeppelin-distribution/target/zeppelin-$ZEPPELIN_VERSION_SNAPSHOT.tar.gz /tmp/zeppelin_setup/  > /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -6

echo " - Changing to setup temp directory"
cd /tmp/zeppelin_setup/ || (cd "Couldn't cd to /tmp/zeppelin_setup/" && exit 1)

echo " - Extracting zeppelin-$ZEPPELIN_VERSION"
tar -xvf zeppelin-$ZEPPELIN_VERSION_SNAPSHOT.tar.gz > /tmp/zeppelin_install_log 2>&1
fail_if_error $? "/tmp/zeppelin_install_log" -2

echo " - Installing Zeppelin"
sudo chown root.staff -R zeppelin-$ZEPPELIN_VERSION_SNAPSHOT
sudo mv zeppelin-$ZEPPELIN_VERSION_SNAPSHOT /usr/local/lib/zeppelin-$ZEPPELIN_VERSION_SNAPSHOT

echo " - symlinking /usr/local/lib/zeppelin/ to /usr/local/lib/zeppelin-$ZEPPELIN_VERSION_SNAPSHOT/"
sudo ln -s /usr/local/lib/zeppelin-$ZEPPELIN_VERSION_SNAPSHOT /usr/local/lib/zeppelin

#echo " - Installing required interpreters"
#sudo /usr/local/lib/zeppelin/bin/install-interpreter.sh --name md,shell,jdbc,python,angular,elasticsearch,flink >> /tmp/zeppelin_install_log 2>&1
#fail_if_error $? "/tmp/zeppelin_install_log" -1

echo " - Removing unused interpreters"
rm -Rf /usr/local/lib/zeppelin/interpreter/alluxio
rm -Rf /usr/local/lib/zeppelin/interpreter/beam
rm -Rf /usr/local/lib/zeppelin/interpreter/bigquery
rm -Rf /usr/local/lib/zeppelin/interpreter/cassandra
rm -Rf /usr/local/lib/zeppelin/interpreter/geode
rm -Rf /usr/local/lib/zeppelin/interpreter/hazelcastjet
rm -Rf /usr/local/lib/zeppelin/interpreter/hbase
rm -Rf /usr/local/lib/zeppelin/interpreter/ignite
rm -Rf /usr/local/lib/zeppelin/interpreter/kylin
rm -Rf /usr/local/lib/zeppelin/interpreter/kotlin
rm -Rf /usr/local/lib/zeppelin/interpreter/ksql
rm -Rf /usr/local/lib/zeppelin/interpreter/lens
rm -Rf /usr/local/lib/zeppelin/interpreter/livy
rm -Rf /usr/local/lib/zeppelin/interpreter/neo4j
rm -Rf /usr/local/lib/zeppelin/interpreter/pig
rm -Rf /usr/local/lib/zeppelin/interpreter/sap
rm -Rf /usr/local/lib/zeppelin/interpreter/scalding
rm -Rf /usr/local/lib/zeppelin/interpreter/scio
rm -Rf /usr/local/lib/zeppelin/interpreter/submarine



echo " - Registering test cleaning traps"
export ZEPPELIN_PROC_ID=-1
function check_stop_zeppelin(){
    if [[ $ZEPPELIN_PROC_ID != -1 ]]; then
        echo " - Stopping Zeppelin !!"
        kill -15 $ZEPPELIN_PROC_ID
    fi
}
trap check_stop_zeppelin 15
trap check_stop_zeppelin EXIT
trap check_stop_zeppelin ERR


echo " - Starting Zeppelin"
/usr/local/lib/zeppelin/bin/zeppelin.sh >> /tmp/zeppelin_install_log 2>&1 &
export ZEPPELIN_PROC_ID=$!

echo " - Checking Zeppelin startup"
sleep 30
if ! kill -0 $ZEPPELIN_PROC_ID > /dev/null 2>&1; then
    echo " !! Failed to start Zeppelin !!"
    exit 8
fi

echo " - Stopping Zeppelin"
kill -15 $ZEPPELIN_PROC_ID
export ZEPPELIN_PROC_ID=-1

echo " - Cleaning up"
returned_to_saved_dir
#sudo rm -Rf /tmp/zeppelin_setup
#sudo rm -Rf /tmp/zeppelin_build
#sudo rm -Rf /home/$USER_TO_USE



if [[ ! -f /usr/local/lib/zeppelin/conf/interpreter.json ]]; then
   echo "PROBLEM : interpreter.json was not created !"
   exit 50
fi


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"


