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


echo "-- INSTALLING FLINK -----------------------------------------------------------"

if [ -z "$FLINK_VERSION" ]; then
    echo "Need to set FLINK_VERSION environment variable before calling this script !"
    exit 1
fi

if [ -z "$SCALA_VERSION" ]; then
    echo "Need to set SCALA_VERSION environment variable before calling this script !"
    exit 1
fi

if [ -z "$FLINK_HADOOP_VERSION" ]; then
    echo "Need to set FLINK_HADOOP_VERSION environment variable before calling this script !"
    exit 1
fi

if [ -z "$ES_VERSION_MAJOR_FOR_FLINK" ]; then
    echo "Need to set ES_VERSION_MAJOR_FOR_FLINK environment variable before calling this script !"
    exit 1
fi

echo " - Changing to temp directory"
rm -Rf /tmp/flink_setup
mkdir -p /tmp/flink_setup
cd /tmp/flink_setup || (echo "Couldn't change to /tmp/flink_setup" && exit 200)

echo " - Updating dependencies for libmesos "
sudo DEBIAN_FRONTEND=noninteractive apt-get -y install \
            libcurl4-nss-dev libsasl2-dev libsasl2-modules maven libapr1-dev libsvn-dev zlib1g-dev \
            > /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -2

echo " - Downloading flink-$FLINK_VERSION"
wget https://www-eu.apache.org/dist/flink/flink-$FLINK_VERSION/flink-$FLINK_VERSION-bin-scala_$SCALA_VERSION.tgz > /tmp/flink_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad flink-$FLINK_VERSION from http://www.apache.org/. Trying archive site"
    wget https://archive.apache.org/dist/flink/flink-$FLINK_VERSION/flink-$FLINK_VERSION-bin-scala_$SCALA_VERSION.tgz > /tmp/flink_install_log 2>&1
    if [[ $? != 0 ]]; then
        echo " -> Failed to downolad flink-$FLINK_VERSION from http://www.apache.org/. Trying to download from niceideas.ch"
        wget https://niceideas.ch/mes/flink-$FLINK_VERSION-bin-scala_$SCALA_VERSION.tgz > /tmp/flink_install_log 2>&1
        fail_if_error $? "/tmp/flink_install_log" -1
    fi
fi

echo " - Extracting flink-$FLINK_VERSION"
tar -xvf flink-$FLINK_VERSION-bin-scala_$SCALA_VERSION.tgz > /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -2

echo " - Downloading optional components"
wget https://repo.maven.apache.org/maven2/org/apache/flink/flink-avro/$FLINK_VERSION/flink-avro-$FLINK_VERSION.jar > /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -21

wget https://repo.maven.apache.org/maven2/org/apache/flink/flink-csv/$FLINK_VERSION/flink-csv-$FLINK_VERSION.jar > /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -22

wget https://repo.maven.apache.org/maven2/org/apache/flink/flink-json/$FLINK_VERSION/flink-json-$FLINK_VERSION.jar > /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -23

wget https://repo.maven.apache.org/maven2/org/apache/flink/flink-shaded-hadoop-2-uber/$FLINK_HADOOP_VERSION/flink-shaded-hadoop-2-uber-$FLINK_HADOOP_VERSION.jar > /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -24

echo " - Installing optional components"
mv flink-avro-$FLINK_VERSION.jar flink-$FLINK_VERSION/lib/
mv flink-csv-$FLINK_VERSION.jar flink-$FLINK_VERSION/lib/
mv flink-json-$FLINK_VERSION.jar flink-$FLINK_VERSION/lib/
mv flink-shaded-hadoop-2-uber-$FLINK_HADOOP_VERSION.jar flink-$FLINK_VERSION/lib/

#echo " - HACK - fixing flink dist for zeppelin"
#mv flink-$FLINK_VERSION/lib/flink-dist-$FLINK_VERSION.jar flink-$FLINK_VERSION/lib/flink-dist_$SCALA_VERSION-$FLINK_VERSION.jar

echo " - Creating a dummy pom.xml to proceed with downloading kafka, elasticsearch and kubernetes connectors"
rm -Rf /tmp/flink_download_connectors
mkdir /tmp/flink_download_connectors
cd /tmp/flink_download_connectors || exit 1
cat > pom.xml <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>ch.niceideas.flink</groupId>
    <artifactId>connectorsDownloadProject</artifactId>
    <version>$FLINK_VERSION</version>
    <packaging>jar</packaging>
    <name>minimal-pom</name>
    <url>http://maven.apache.org</url>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <java.version>1.11</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.apache.flink</groupId>
            <!--<artifactId>flink-connector-kafka</artifactId>-->
            <artifactId>flink-connector-kafka</artifactId>
            <version>$FLINK_VERSION</version>
            <scope>test</scope>
            <exclusions>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-shaded-force-shading</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
            <artifactId>flink-connector-elasticsearch-base</artifactId>
            <version>$FLINK_CONNECTOR_ELASTICSEARCH_VERSION</version>
            <scope>test</scope>
            <exclusions>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-shaded-force-shading</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
            <artifactId>statefun-flink-harness</artifactId>
            <version>$FLINK_STATEFUN_VERSION</version>
            <exclusions>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-connector-kafka_$SCALA_VERSION</artifactId>
                </exclusion>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-connector-kinesis_$SCALA_VERSION</artifactId>
                </exclusion>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-clients_$SCALA_VERSION</artifactId>
                </exclusion>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-metrics-dropwizard</artifactId>
                </exclusion>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-streaming-java_$SCALA_VERSION</artifactId>
                </exclusion>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-runtime</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
            <artifactId>flink-ml-core</artifactId>
            <version>$FLINK_ML_VERSION</version>
            <exclusions>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-connector-files</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
            <artifactId>flink-ml-iteration</artifactId>
            <version>$FLINK_ML_VERSION</version>
            <exclusions>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-hadoop-fs</artifactId>
                </exclusion>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-table-runtime</artifactId>
                </exclusion>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-core</artifactId>
                </exclusion>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-shaded-netty</artifactId>
                </exclusion>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-annotations</artifactId>
                </exclusion>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-shaded-force-shading</artifactId>
                </exclusion>
                <exclusion>
                     <groupId>org.apache.flink</groupId>
                     <artifactId>flink-shaded-jackson</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
            <artifactId>flink-ml-lib</artifactId>
            <version>$FLINK_ML_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
             <artifactId>flink-core</artifactId>
             <version>$FLINK_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
             <artifactId>flink-table-runtime</artifactId>
             <version>$FLINK_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
             <artifactId>flink-annotations</artifactId>
             <version>$FLINK_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
             <artifactId>flink-connector-files</artifactId>
             <version>$FLINK_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
             <artifactId>flink-connector-files</artifactId>
             <version>$FLINK_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
             <artifactId>flink-clients</artifactId>
             <version>$FLINK_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
             <artifactId>flink-runtime</artifactId>
             <version>$FLINK_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
             <artifactId>flink-streaming-java</artifactId>
             <version>$FLINK_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
             <artifactId>flink-streaming-scala_$SCALA_VERSION</artifactId>
             <version>$FLINK_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
             <artifactId>flink-metrics-dropwizard</artifactId>
             <version>$FLINK_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
            <artifactId>flink-table-api-scala_$SCALA_VERSION</artifactId>
            <version>$FLINK_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.flink</groupId>
            <artifactId>flink-table-api-scala-bridge_$SCALA_VERSION</artifactId>
            <version>$FLINK_VERSION</version>
        </dependency>
    </dependencies>
</project>
EOF

#        <dependency>
#            <groupId>org.apache.flink</groupId>
#            <artifactId>flink-kubernetes-operator</artifactId>
#            <version>$FLINK_KUBE_OPERATOR_VERSION</version>
#        </dependency>


echo " - Proceeding with download of connectors and dependencies"
mvn dependency:copy-dependencies > /tmp/flink_install_log 2>&1
fail_if_error $? "/tmp/flink_install_log" -25

echo " - Copying connectors with dependencies to flink distribution folder"
cd target/dependency/  || exit 2
#cp * /tmp/flink_setup/flink-$FLINK_VERSION/lib/
# omiting log4j-api
find ./ ! -name 'log4j-api*' -exec cp -t /tmp/flink_setup/flink-$FLINK_VERSION/lib/ {} + 2>/dev/null

echo " - Changing directory back to flink setup"
cd /tmp/flink_setup  || exit 3

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
if ! kill -0 $EXAMPLE_PID > /dev/null 2>&1; then
    echo "Flink process not started successfully !"
    exit 10
fi

sudo rm -Rf /tmp/flink_download_connectors
sudo rm -Rf /tmp/flink_setup

echo " - Cleaning up maven repository"
sudo rm -Rf $HOME/.m2/repository

echo " - Cleanup container"
sudo rm -Rf /usr/share/doc/openjdk-11-jre-headless


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"