#!/bin/bash

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

# 0. Save previous values
unset FORMER_cpu_additional
unset FORMER_ram_additional
if [[ -f /usr/local/lib/mesos/etc/mesos/agent-resources-temporary-file.properties ]]; then
    . /usr/local/lib/mesos/etc/mesos/agent-resources-temporary-file.properties
    export FORMER_cpu_additional=$cpu_additional
    export FORMER_ram_additional=$ram_additional
fi

# 1. Create temporary file
rm -f /usr/local/lib/mesos/etc/mesos/agent-resources-temporary-file.properties
# Caution: default values have to be defined
echo "cpu_additional=1" >> /usr/local/lib/mesos/etc/mesos/agent-resources-temporary-file.properties
echo "ram_additional=128" >> /usr/local/lib/mesos/etc/mesos/agent-resources-temporary-file.properties

# 2. inject settings in temporary file
/usr/local/sbin/settingsInjector.sh mesos-agent

# 3. Use temporary file to generate resources.json file

# 3.1 Sourcing file
. /usr/local/lib/mesos/etc/mesos/agent-resources-temporary-file.properties
echo "   + additional CPU count to define: $cpu_additional"
echo "   + additional RAM in MB to define: $ram_additional"

# 3.2 If resources have been reduced, recovery is impossible. In this case, I need to restart the agent with a new agent
# ID.
if [[ "$FORMER_cpu_additional" != "" || "$FORMER_ram_additional" != "" ]]; then
    if [[ $FORMER_cpu_additional -gt $cpu_additional || $FORMER_ram_additional -gt $ram_additional ]]; then
        echo " !!! resources have been reduced in mesos agent !!!"
        echo "FORMER additional CPU : $FORMER_cpu_additional vs. CURRENT additional CPU : $cpu_additional"
        echo "FORMER additional RAM : $FORMER_ram_additional vs. CURRENT additional RAM : $ram_additional"
        echo "/var/lib/mesos/slave/meta/slaves/latest wil be deleted to force agent reset"
        rm -f /var/lib/mesos/slave/meta/slaves/latest
    fi
fi

# 3.3 Finding out about number of CPUs of the host
number_cpu=`cat /proc/cpuinfo  | grep processor | wc -l`
echo "   + physical number of CPUs: $number_cpu"

# 3.4 Finding out about amount of RAM to assign to mesos (topology)
. /etc/eskimo_topology.sh
if [[ "$MEMORY_MESOS_AGENT" == "" ]]; then
    export MEMORY_MESOS_AGENT=1000
fi
echo "   + theoretical mesos RAM in MB: $MEMORY_MESOS_AGENT"

# 3.5 Computing runtime figures
let mesos_effective_cpu="$number_cpu + $cpu_additional"
let mesos_effective_ram="$MEMORY_MESOS_AGENT + $ram_additional"


# 3.6 Create /usr/local/lib/mesos/etc/mesos/mesos-resources.json on the fly (important !)
cat > /tmp/mesos-resources.json <<- "EOF"
[
  {
    "name": "cpus",
    "type": "SCALAR",
    "scalar": {
      "value": CPU_COUNT
    }
  },
  {
    "name": "mem",
    "type": "SCALAR",
    "scalar": {
      "value": RAM_AMOUNT
    }
  }
]
EOF

mv /tmp/mesos-resources.json /usr/local/lib/mesos/etc/mesos/mesos-resources.json
chmod 755 /usr/local/lib/mesos/etc/mesos/mesos-resources.json
chown root /usr/local/lib/mesos/etc/mesos/mesos-resources.json

sed -i s/"\"value\": CPU_COUNT"/"\"value\": $mesos_effective_cpu"/g /usr/local/lib/mesos/etc/mesos/mesos-resources.json
sed -i s/"\"value\": RAM_AMOUNT"/"\"value\": $mesos_effective_ram"/g /usr/local/lib/mesos/etc/mesos/mesos-resources.json
