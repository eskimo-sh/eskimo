#!/bin/sh

if [[ ! -f /etc/eskimo_topology.sh ]]; then
    echo "Could not find /etc/eskimo_topology.sh"
    exit -1
fi

. /etc/eskimo_topology.sh

# remove any previous definition
sed -i '/.* marathon.registry/d' /etc/hosts

# register new host
if [[ -z $MASTER_MARATHON_1 ]]; then
    echo "WARNING : Could not find Marathon master host"
else
    echo "$MASTER_MARATHON_1 marathon.registry" >> /etc/hosts
fi


