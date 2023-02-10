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


echo "-- INSTALLING ES-HADOOP (Spark to ES connector) -------------------------------"

if [ -z "$ES_VERSION" ]; then
    echo "Need to set ES_VERSION environment variable before calling this script !"
    exit 1
fi
if [ -z "$SPARK_VERSION" ]; then
    echo "Need to set SPARK_VERSION environment variable before calling this script !"
    exit 1
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
sudo rm -Rf /tmp/eshadoop_setup
mkdir -p /tmp/eshadoop_setup
cd /tmp/eshadoop_setup || (echo "Couldn't change to /tmp/eshadoop_setup" && exit 200)


echo " - Creating a dummy pom.xml to proceed with downloading ES-Hadoop spark connector"
echo "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"
         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">
    <modelVersion>4.0.0</modelVersion>

    <groupId>ch.niceideas.spark</groupId>
    <artifactId>connectorsDownloadProject</artifactId>
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
            <groupId>org.elasticsearch</groupId>
            <artifactId>elasticsearch-spark-30_$SCALA_VERSION</artifactId>
            <version>$ES_VERSION</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>" > pom.xml

echo " - Proceeding with download of connectors and dependencies"
mvn dependency:copy-dependencies > /tmp/esh_install_log 2>&1
fail_if_error $? "/tmp/esh_install_log" -25

echo " - Copying connectors with dependencies to spark distribution folder"
cd target/dependency/ || (echo "Couldn't cd to target dependency directory" && exit 1)


echo " - Installing eshadoop spark (to spark jar folder)"
sudo chown root.staff -R *
sudo cp elasticsearch-spark-30_$SCALA_VERSION-$ES_VERSION.jar /usr/local/lib/spark-$SPARK_VERSION/jars/
if [[ $? != 0 ]]; then
    echo " -> Failed to find jar elasticsearch-spark-30_$SCALA_VERSION-$ES_VERSION.jar - name must have change ..."
    exit 2
fi

echo " - Cleaning up maven repository"
sudo rm -Rf $HOME/.m2/repository

echo " - Cleaning build directory"
sudo rm -Rf /tmp/eshadoop_setup
returned_to_saved_dir


# Caution : the in container setup script must mandatorily finish with this log"
echo "$IN_CONTAINER_INSTALL_SUCESS_MESSAGE"