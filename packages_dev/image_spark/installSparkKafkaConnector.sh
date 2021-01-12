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

saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
mkdir -p /tmp/spark_streaming_kafka_deps
cd /tmp/spark_streaming_kafka_deps

# Dependencies I need :
# (Note : obtained by :
#   - /usr/local/bin/run-example --packages org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.3 SparkPi and
#   - /usr/local/bin/run-example --packages org.apache.spark:spark-streaming-kafka-0-10_2.11:2.4.3 SparkPi
# )
#https://repo1.maven.org/maven2/org/apache/spark/spark-sql-kafka-0-10_2.11/2.4.3/spark-sql-kafka-0-10_2.11-2.4.3.jar
#https://repo1.maven.org/maven2/org/apache/spark/spark-streaming-kafka-0-10_2.11/2.4.3/spark-streaming-kafka-0-10_2.11-2.4.3.jar
#https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/2.0.0/kafka-clients-2.0.0.jar
#https://repo1.maven.org/maven2/org/spark-project/spark/unused/1.0.0/unused-1.0.0.jar

# These are needed as well but already in /usr/local/lib/spark/jars as it seems:
#https://repo1.maven.org/maven2/org/lz4/lz4-java/1.4.0/lz4-java-1.4.0.jar
#https://repo1.maven.org/maven2/org/xerial/snappy/snappy-java/1.1.7.3/snappy-java-1.1.7.3.jar
#https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.16/slf4j-api-1.7.16.jar

# Rather hardcoded list of dependencies to be fetched for spark streaming !
spark_streaming_dependencies="
    https://repo1.maven.org/maven2/org/apache/spark/spark-sql-kafka-"$SPARK_STREAMING_KAFKA_CON_VERSION"_"$SCALA_VERSION"/$SPARK_VERSION/spark-sql-kafka-"$SPARK_STREAMING_KAFKA_CON_VERSION"_"$SCALA_VERSION"-$SPARK_VERSION.jar
    https://repo1.maven.org/maven2/org/apache/spark/spark-streaming-kafka-"$SPARK_STREAMING_KAFKA_CON_VERSION"_"$SCALA_VERSION"/$SPARK_VERSION/spark-streaming-kafka-"$SPARK_STREAMING_KAFKA_CON_VERSION"_"$SCALA_VERSION"-$SPARK_VERSION.jar
    https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/"$SPARK_STREAMING_KAFKA_CLIENT_VERSION"/kafka-clients-"$SPARK_STREAMING_KAFKA_CLIENT_VERSION".jar
    https://repo1.maven.org/maven2/org/spark-project/spark/unused/"$SPARK_UNUSED_VERSION"/unused-"$SPARK_UNUSED_VERSION".jar"


for i in $spark_streaming_dependencies; do
    echo " - Downloading $i"
    wget $i > /tmp/kafka_install_log 2>&1
    if [[ $? != 0 ]]; then
        echo " -> Failed to downolad $i"
        exit -1
    fi
done

for i in `ls -1`; do
    echo " - Installing $i"

    sudo cp $i /usr/local/lib/spark/jars/
    if [[ $? != 0 ]]; then
        echo " -> Failed to install $i"
        exit -2
    fi
done

sudo rm -Rf /tmp/spark_streaming_kafka_deps
returned_to_saved_dir



# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"


