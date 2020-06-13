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


# ----------------------------------------------------------------------------------------------------------------------
# CAUTION : UNFORTUNATELY THIS FILE IS MIANTAINED OVER MULTIPLE LOCATIONS : IN FLINK AND IN ZEPPELIN !!!
# ----------------------------------------------------------------------------------------------------------------------

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"


echo "-- INSTALLING FLINK -----------------------------------------------------------"

if [ -z "$FLINK_VERSION" ]; then
    echo "Need to set FLINK_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
mkdir -p /tmp/flink_setup
cd /tmp/flink_setup

echo " - Updating dependencies for libmesos "
sudo DEBIAN_FRONTEND=noninteractive apt-get -y install \
            libcurl4-nss-dev libsasl2-dev libsasl2-modules maven libapr1-dev libsvn-dev zlib1g-dev \
            > /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -2

echo " - Downloading flink-$FLINK_VERSION"
wget https://www-eu.apache.org/dist/flink/flink-$FLINK_VERSION/flink-$FLINK_VERSION-bin-scala_$SCALA_VERSION.tgz > /tmp/flink_install_log 2>&1


if [[ $? != 0 ]]; then
    echo " -> Failed to downolad flink-$FLINK_VERSION from http://www.apache.org/. Trying to download from niceideas.ch"
    wget http://niceideas.ch/mes/flink-$FLINK_VERSION-bin-scala_$SCALA_VERSION.tgz >> /tmp/flink_install_log 2>&1
    fail_if_error $? "/tmp/flink_install_log" -1
fi

echo " - Extracting flink-$FLINK_VERSION"
tar -xvf flink-$FLINK_VERSION-bin-scala_$SCALA_VERSION.tgz > /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -2

echo " - Downloading optional components"
wget https://repo.maven.apache.org/maven2/org/apache/flink/flink-avro/$FLINK_VERSION/flink-avro-$FLINK_VERSION.jar >> /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -21

wget https://repo.maven.apache.org/maven2/org/apache/flink/flink-csv/$FLINK_VERSION/flink-csv-$FLINK_VERSION.jar >> /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -22

wget https://repo.maven.apache.org/maven2/org/apache/flink/flink-json/$FLINK_VERSION/flink-json-$FLINK_VERSION.jar >> /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -23

wget https://repo.maven.apache.org/maven2/org/apache/flink/flink-shaded-hadoop-2-uber/$FLINK_HADOOP_VERSION/flink-shaded-hadoop-2-uber-$FLINK_HADOOP_VERSION.jar >> /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -24

echo " - Installing optional components"
mv flink-avro-$FLINK_VERSION.jar flink-$FLINK_VERSION/lib/
mv flink-csv-$FLINK_VERSION.jar flink-$FLINK_VERSION/lib/
mv flink-json-$FLINK_VERSION.jar flink-$FLINK_VERSION/lib/
mv flink-shaded-hadoop-2-uber-$FLINK_HADOOP_VERSION.jar flink-$FLINK_VERSION/lib/

echo " - Creating a dummy pom.xml to proceed with downloading kafka and elasticsearch connectors"
mkdir /tmp/flink_download_connectors
cd /tmp/flink_download_connectors
echo "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"
         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">
    <modelVersion>4.0.0</modelVersion>

    <groupId>ch.niceideas.flink</groupId>
    <artifactId>connecorsDownloadProject</artifactId>
    <version>$FLINK_VERSION</version>
    <packaging>jar</packaging>
    <name>minimal-pom</name>
    <url>http://maven.apache.org</url>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <java.version>1.8</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.apache.flink</groupId>
            <artifactId>flink-connector-kafka_2.11</artifactId>
            <version>$FLINK_VERSION</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
            <artifactId>flink-connector-elasticsearch"$ES_VERSION_MAJOR"_2.11</artifactId>
            <version>$FLINK_VERSION</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>" > pom.xml

echo " - Proceeding with download of connectors and dependencies"
mvn dependency:copy-dependencies >> /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -25

echo " - Copying connectors with dependencies to flink distribution folder"
cd target/dependency/
cp * /tmp/flink_setup/flink-$FLINK_VERSION/lib/

echo " - Changing directory back to flink setup"
cd /tmp/flink_setup

echo " - Installing flink"
sudo chown root.staff -R flink-$FLINK_VERSION
sudo mv flink-$FLINK_VERSION /usr/local/lib/flink-$FLINK_VERSION

echo " - symlinking /usr/local/lib/flink/ to /usr/local/lib/flink-$FLINK_VERSION"
sudo ln -s /usr/local/lib/flink-$FLINK_VERSION /usr/local/lib/flink

echo " - Checking Flink Installation"
/usr/local/lib/flink/bin/flink run /usr/local/lib/flink/examples/batch/WordCount.jar > /tmp/flink_run_log 2>&1 &
EXAMPLE_PID=$!
fail_if_error $? "/tmp/flink_run_log" -3
sleep 5
if [[ `ps | grep $EXAMPLE_PID` == "" ]]; then
    echo "Flink process not started successfully !"
    exit 10
fi

sudo rm -Rf /tmp/flink_download_connectors
sudo rm -Rf /tmp/flink_setup
returned_to_saved_dir



# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"