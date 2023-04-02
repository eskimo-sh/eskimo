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

echo " - Injecting topology"
. /usr/local/sbin/inContainerInjectTopology.sh

echo " - Creating required directories (as elasticsearch)"
mkdir -p /var/run/elasticsearch/kibana
mkdir -p /var/log/elasticsearch/kibana

echo " - Inject settings"
/usr/local/sbin/settingsInjector.sh kibana

echo " - Starting service"
/usr/local/lib/kibana/bin/kibana | tee /var/log/elasticsearch/kibana/kibana.log &

echo " - Getting Kibana PID"
sleep 5
export KIBANA_PID=$(pgrep node)
echo " - Kibana process is $KIBANA_PID"

echo " - Waiting for Kibana to be up"
set +e
for i in $(seq 1 100); do

    sleep 5

    if [[ $(ps -p $KIBANA_PID -o comm=) == "" ]]; then
        echo "!!! Kibana process not detected up anymore. Crashing."
        exit 1
    fi

    echo "   + Querying kibana - attempt $i"
    curl -XGET http://localhost:5601/api/features > /tmp/kibana_features 2>&1
    if [[ $? == 0 ]]; then
        if [[ $(grep \"id\" /tmp/kibana_features) != "" ]]; then
            break;
        fi
    fi

    if [[ $i == 100 ]]; then
        echo "!!! Couldn't get kibana up within 500 seconds. Crashing !"
        exit 2
    fi
done

echo " - Waiting for Kibana to be running"
for i in $(seq 1 30); do

    sleep 2

    if [[ $(tail -100 /var/log/elasticsearch/kibana/kibana.log | grep -F 'Kibana is now available') != "" ]]; then
        break;
    fi

    if [[ $i == 30 ]]; then
        echo "!!! Couldn't get kibana running within 60 seconds. Crashing !"
        exit 3
    fi
done

echo " - Provisioning sample files"
# convention: dashboard files have same name as dashboard

for sample in $(find /usr/local/lib/kibana/samples/); do

    if [[ $(echo $sample | grep ndjson) != "" ]]; then

        echo "   + checking $sample"
        file_name=$(basename $sample)
        dashboard_name="${file_name%.*}"
        echo "     - dashboard is $dashboard_name"
        exist=$(curl -XGET "http://localhost:5601/api/saved_objects/_find?type=dashboard&search_fields=title&search=$dashboard_name*" 2>/dev/null | jq -r " .total")

        if [[ $exist == "0" ]]; then
            echo "   + Provisioning $sample"
            curl -X POST "http://localhost:5601/api/saved_objects/_import" -H "kbn-xsrf: true" --form file=@"$sample" > /tmp/kibana_provision 2>/dev/null
            if [[ $? != 0 ]]; then
                echo "!!! Failed to import $sample"
                cat /tmp/kibana_provision
                exit 3
            fi
            if [[ $(grep -F '"success":true' /tmp/kibana_provision) == "" ]]; then
                echo "!!! Failed to find confirmation of import of $sample"
                cat /tmp/kibana_provision
                exit 4
            fi
        fi
    fi
done

set -e

echo " - Waiting on Kibana process"
wait $KIBANA_PID