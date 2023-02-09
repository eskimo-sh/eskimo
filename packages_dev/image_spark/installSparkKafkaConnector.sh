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


echo "-- INSTALLING SPARK STREAMING KAFKA CONNECTOR --------------------------"

if [ -z "$SPARK_STREAMING_KAFKA_CON_VERSION" ]; then
    echo "Need to set SPARK_STREAMING_KAFKA_CON_VERSION environment variable before calling this script !"
    exit 1
fi
if [ -z "$SPARK_STREAMING_KAFKA_CLIENT_VERSION" ]; then
    echo "Need to set SPARK_STREAMING_KAFKA_CLIENT_VERSION environment variable before calling this script !"
    exit 1
fi
if [ -z "$SCALA_VERSION" ]; then
    echo "Need to set SCALA_VERSION environment variable before calling this script !"
    exit 1
fi
if [ -z "$SPARK_VERSION" ]; then
    echo "Need to set SPARK_VERSION environment variable before calling this script !"
    exit 1
fi
if [ -z "$SPARK_UNUSED_VERSION" ]; then
    echo "Need to set SPARK_UNUSED_VERSION environment variable before calling this script !"
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
rm -Rf /tmp/spark_streaming_kafka_deps
mkdir -p /tmp/spark_streaming_kafka_deps
cd /tmp/spark_streaming_kafka_deps/ || (echo "Couldn't change to /tmp/spark_streaming_kafka_deps" && exit 200)


echo "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"
         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">
    <modelVersion>4.0.0</modelVersion>

    <groupId>ch.niceideas.spark</groupId>
    <artifactId>kafkaConnectorsDownloadProject</artifactId>
    <version>$SPARK_VERSION</version>
    <packaging>jar</packaging>
    <name>minimal-pom</name>
    <url>http://maven.apache.org</url>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <java.version>1.11</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.apache.spark</groupId>
            <artifactId>spark-streaming-kafka-${SPARK_STREAMING_KAFKA_CON_VERSION}_${SCALA_VERSION}</artifactId>
            <version>$SPARK_VERSION</version>
        </dependency>
        <dependency>
            <groupId>org.apache.spark</groupId>
            <artifactId>spark-sql-kafka-${SPARK_STREAMING_KAFKA_CON_VERSION}_${SCALA_VERSION}</artifactId>
            <version>$SPARK_VERSION</version>
        </dependency>
    </dependencies>
</project>" > pom.xml


echo " - Proceeding with download of connectors and dependencies"
mvn dependency:copy-dependencies > /tmp/spark_kafka_install_log 2>&1
fail_if_error $? "/tmp/spark_kafka_install_log" -25

echo " - Copying connectors with dependencies to spark distribution folder"
cd target/dependency/ || (echo "Couldn't cd to target/dependency/" && exit 1)
# omiting log4j-api
find ./ ! -name 'log4j-api*' -exec cp -t /usr/local/lib/spark/jars/ {} + 2>/dev/null

sudo rm -Rf /tmp/spark_streaming_kafka_deps
returned_to_saved_dir

echo " - Cleaning up maven repository"
sudo rm -Rf $HOME/.m2/repository

echo " - Cleaning build directory"
rm -Rf /tmp/spark_streaming_kafka_deps
returned_to_saved_dir


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"


