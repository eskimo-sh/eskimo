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

set -e

#echo " - Handling default indices settings (before framworked settings injection) "
if [[ $(echo $ALL_NODES_LIST_kube_slave | grep ',') == "" ]]; then
    export HAS_MASTER=0
else
    export HAS_MASTER=1
fi


# 1. Handling properties from Configuration file
#index.number_of_shards=10
#index.number_of_replicas=2
#index.refresh_interval=10s

for property in $(grep -F "=" /usr/local/lib/elasticsearch/config/elasticsearch-index-defaults.properties); do

    key=$(echo $property | cut -d '=' -f 1)
    value=$(echo $property | cut -d '=' -f 2)

    #echo $key:$value

    if [[ $value != "DEFAULT" ]]; then
        if [[ $key == "index.number_of_shards" ]]; then
            export NUMBER_OF_SHARDS=$value
        elif [[ $key == "index.number_of_replicas" ]]; then
            export NUMBER_OF_REPLICAS=$value
        elif [[ $key == "index.refresh_interval" ]]; then
            export REFRESH_INTERVAL=$value
        fi
    fi
done

# 2. Injecting defaults if required
if [[ $NUMBER_OF_SHARDS == "" ]]; then
    export NUMBER_OF_SHARDS=5
fi

if [[ $NUMBER_OF_REPLICAS == "" ]]; then
    if [[ $HAS_MASTER == 0 ]]; then
        # Single node deployment : no replica (only master)
        export NUMBER_OF_REPLICAS=0
    else
        # Multiple node deployment : at least 1 replica by default
        export NUMBER_OF_REPLICAS=1
    fi
fi

if [[ $REFRESH_INTERVAL == "" ]]; then
    export REFRESH_INTERVAL=10s
fi


# 3. Inject settings in default index template
# (Only on master node)

# curl -XGET 'http://localhost:9200/_template/index_defaults?pretty'
# use jq to parse !


default_template=$(curl -XGET 'http://localhost:9200/_template/index_defaults?pretty' 2>/dev/null)

CURRENT_NUMBER_OF_SHARDS=$(echo $default_template | jq -r ".index_defaults | .settings | .index | .number_of_shards")
CURRENT_NUMBER_OF_REPLICAS=$(echo $default_template | jq -r ".index_defaults | .settings | .index | .number_of_replicas")
CURRENT_REFRESH_INTERVAL=$(echo $default_template | jq -r ".index_defaults | .settings | .index | .refresh_interval")

if [[ $CURRENT_NUMBER_OF_SHARDS != $NUMBER_OF_SHARDS ]]; then
    echo "    + Need to change default number of shards from $CURRENT_NUMBER_OF_SHARDS to $NUMBER_OF_SHARDS"
    export CHANGE="true"
fi

if [[ $CURRENT_NUMBER_OF_REPLICAS != $NUMBER_OF_REPLICAS ]]; then
    echo "    + Need to change default number of replicas from $CURRENT_NUMBER_OF_REPLICAS to $NUMBER_OF_REPLICAS"
    export CHANGE="true"
fi

if [[ $CURRENT_REFRESH_INTERVAL != $REFRESH_INTERVAL ]]; then
    echo "    + Need to change default number of replicas from $CURRENT_REFRESH_INTERVAL to $REFRESH_INTERVAL"
    export CHANGE="true"
fi

if [[ $CHANGE == "true" ]]; then
    echo " - Updating default index template with :"
    echo -e "{
      \"number_of_replicas\" : \"$NUMBER_OF_REPLICAS\",
      \"number_of_shards\" : \"$NUMBER_OF_SHARDS\",
      \"refresh_interval\" : \"$REFRESH_INTERVAL\"
    }"


    curl -XPUT 'http://localhost:9200/_template/index_defaults ' \
        -H 'Content-Type: application/json' \
        -d "
    {
        \"index_patterns\": [\"*\"],
        \"order\" : 0,
        \"settings\": {
          \"number_of_replicas\" : \"$NUMBER_OF_REPLICAS\",
          \"number_of_shards\" : \"$NUMBER_OF_SHARDS\",
          \"refresh_interval\" : \"$REFRESH_INTERVAL\"
        }
    }"
fi