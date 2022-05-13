
#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

echo " - Loading Topology"
. /etc/eskimo_topology.sh

echo " - Adapting configuration in file elasticsearch.yml"

# I was using node name previously, but now the problem is that a node has to be known by the same name it has
# in node.name and the same that is declared in cluster.initial_master_nodes ...
# And the problem is that I only know the master as it is IP address
#sed -i s/"#node.name: node-1"/"node.name: $SELF_IP_ADDRESS"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml
sed -i s/"#node.name: node-1"/"node.name: $ESKIMO_NODE_NAME.elasticsearch.default.svc.cluster.eskimo"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml

echo "   + Using node.name=$ESKIMO_NODE_NAME.elasticsearch.default.svc.cluster.eskimo"


#echo " - Building reference list of masters"
#export ES_MASTERS=""
#export number_of_es_nodes=0
#echo "   + Checking situation"
#if [[ `echo $ALL_NODES_LIST | grep ','` == "" ]]; then
#    echo "   + Single master: $ALL_NODES_LIST"
#    export ES_MASTERS="\"$ALL_NODES_LIST\""
#    export number_of_es_nodes=1
#else
#    echo "   + Multiple masters: $ALL_NODES_LIST"
#    export cnt=0
#    for i in $(echo $ALL_NODES_LIST | /bin/sed "s/,/ /g"); do
#        # taking 10 first only
#        if [[ $cnt -lt 10 ]]; then
#            if [[ "$ES_MASTERS" == "" ]]; then
#                export ES_MASTERS="\"$i\""
#            else
#                export ES_MASTERS="$ES_MASTERS, \"$i\""
#            fi
#        fi

#        export cnt=`expr $cnt + 1`
#        export number_of_es_nodes=`expr $number_of_es_nodes + 1`
#    done
#fi


echo " - Adapting configuration in file elasticsearch.yml - enabling discovery of master"

export number_of_es_nodes=$ESKIMO_NODE_COUNT
export ES_MASTERS=""
for i in `seq 0 $(($ESKIMO_NODE_COUNT-1))`; do
    if [[ "$ES_MASTERS" == "" ]]; then
        export ES_MASTERS="\"elasticsearch-$i.elasticsearch.default.svc.cluster.eskimo\""
    else
        export ES_MASTERS="$ES_MASTERS, \"elasticsearch-$i.elasticsearch.default.svc.cluster.eskimo\""
    fi
done


echo "   + Using ES_MASTERS=$ES_MASTERS"

# EX 6.x
sed -i s/"#discovery.zen.ping.unicast.hosts: \[\"host1\", \"host2\"\]"/"discovery.zen.ping.unicast.hosts: \[$ES_MASTERS\]"/g \
    /usr/local/lib/elasticsearch/config/elasticsearch.yml

# ES 7.x / 8.x
sed -i s/"#discovery.seed_hosts: \[\"host1\", \"host2\"\]"/"discovery.seed_hosts: \[$ES_MASTERS\]"/g \
    /usr/local/lib/elasticsearch/config/elasticsearch.yml
sed -i s/"#cluster.initial_master_nodes: \[\"node-1\", \"node-2\"\]"/"cluster.initial_master_nodes: \[$ES_MASTERS\]"/g \
    /usr/local/lib/elasticsearch/config/elasticsearch.yml


# Compute number of elasticsearch nodes and set minimum master nodes for discovery
if [ $number_of_es_nodes -gt 2 ]; then
    number_of_master_nodes=$((number_of_es_nodes / 2 + 1))

    echo " - Setting discovery.zen.minimum_master_nodes to $number_of_master_nodes"

    # ES 6.x
    sed -i s/"#discovery.zen.minimum_master_nodes: 3"/"discovery.zen.minimum_master_nodes: $number_of_master_nodes"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml
    # other variant
    sed -i s/"#discovery.zen.minimum_master_nodes: "/"discovery.zen.minimum_master_nodes: $number_of_master_nodes"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml

    # ES 7.x
    sed -i s/"#gateway.recover_after_nodes: 3"/"gateway.recover_after_nodes: $number_of_master_nodes"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml
    # other variant
    sed -i s/"#gateway.recover_after_nodes: "/"gateway.recover_after_nodes: $number_of_master_nodes"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml

    # ES 8.x
    if [[ `grep "discovery.zen.minimum_master_nodes" /usr/local/lib/elasticsearch/config/elasticsearch.yml` == "" && \
          `grep "gateway.recover_after_nodes" /usr/local/lib/elasticsearch/config/elasticsearch.yml` == "" ]]; then

        bash -c "echo \"gateway.recover_after_data_nodes: $number_of_master_nodes\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"
    fi
else

    echo " - Setting discovery.zen.minimum_master_nodes to 1"

    # ES 6.x
    sed -i s/"#discovery.zen.minimum_master_nodes: 3"/"discovery.zen.minimum_master_nodes: 1"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml
    # other variant
    sed -i s/"#discovery.zen.minimum_master_nodes: "/"discovery.zen.minimum_master_nodes: 1"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml

    # ES 7.x
    sed -i s/"#gateway.recover_after_nodes: 3"/"gateway.recover_after_nodes: 1"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml
    # other variant
    sed -i s/"#gateway.recover_after_nodes: "/"gateway.recover_after_nodes: 1"/g /usr/local/lib/elasticsearch/config/elasticsearch.yml

    # ES 8.x
    if [[ `grep "discovery.zen.minimum_master_nodes" /usr/local/lib/elasticsearch/config/elasticsearch.yml` == "" && \
          `grep "gateway.recover_after_nodes" /usr/local/lib/elasticsearch/config/elasticsearch.yml` == "" ]]; then

        bash -c "echo \"gateway.recover_after_data_nodes: 1\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"
    fi
fi

#echo " - Addressing issue with multiple interfaces but only one global"
#bash -c "echo -e \"\n# If you set a network.host that results in multiple bind addresses yet rely on a specific address\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"
#bash -c "echo \"# for node-to-node communication, you should explicitly set network.publish_host.\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"
#bash -c "echo \"network.publish_host: $SELF_IP_ADDRESS\" >> /usr/local/lib/elasticsearch/config/elasticsearch.yml"

echo " - Applying eskimo memory settings from topology in jvm.options"
echo "# Eskimo memory settings" > /usr/local/lib/elasticsearch/config/jvm.options.d/eskimo.options
if [[ $MEMORY_ELASTICSEARCH != "" ]]; then
    echo "-Xms"$MEMORY_ELASTICSEARCH"m" >> /usr/local/lib/elasticsearch/config/jvm.options.d/eskimo.options
    echo "-Xmx"$MEMORY_ELASTICSEARCH"m" >> /usr/local/lib/elasticsearch/config/jvm.options.d/eskimo.options
else
    echo "-Xms1g" >> /usr/local/lib/elasticsearch/config/jvm.options.d/eskimo.options
    echo "-Xmx1g" >> /usr/local/lib/elasticsearch/config/jvm.options.d/eskimo.options
fi