#!/bin/bash
if [[ $1 == "" ]]; then
    echo "Expecting Gluster share name as first argument"
    exit -1
fi
export SHARE_NAME=$1

if [[ $2 == "" ]]; then
    echo "Expecting share full path as second argument"
    exit -2
fi
export SHARE_PATH=$2

if [[ $3 == "" ]]; then
    echo "Expecting share user name as third argument"
    exit -3
fi
export SHARE_USER=$3

# Ensure gluster is available
if [[ -f /etc/eskimo_topology.sh && `cat /etc/eskimo_topology.sh  | grep MASTER_GLUSTER` == "" ]]; then
    echo "ERROR: No Gluster master is defined"
    exit -20
fi

echo " - Proceeding with gluster mount of $SHARE_PATH"
/usr/local/sbin/gluster_mount_internal.sh $SHARE_NAME $SHARE_PATH $SHARE_USER `/usr/bin/id -u $SHARE_USER`
