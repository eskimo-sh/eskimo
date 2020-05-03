#!/bin/bash

echo " - inContainerMountGluser.sh - will now mount $MOUNT_POINT"

# checking arguments
if [[ $1 == "" ]]; then
    echo "Expecting gluster volume as first argument"
    exit -11
fi
export VOLUME=$1

if [[ $2 == "" ]]; then
    echo "Expecting mount point as second argument"
    exit -12
fi
export MOUNT_POINT=$2

if [[ $2 == "" ]]; then
    echo "Expecting owner as third argument"
    exit -13
fi
export OWNER=$3

echo "   + Loading topology"
if [[ ! -f /etc/eskimo_topology.sh ]]; then
    echo "Cannot find eskimo topology file"
    exit -1
fi

. /etc/eskimo_topology.sh


if [[ ! -d $MOUNT_POINT ]]; then
    echo "   + Creating mount point: $MOUNT_POINT"
    mkdir -p $MOUNT_POINT
fi


# find out if gluster is available
if [[ -f /etc/eskimo_topology.sh && `cat /etc/eskimo_topology.sh  | grep MASTER_GLUSTER` == "" ]]; then
    echo "ERROR: No gluster master defined"
    exit -20
fi

ls /dev/fuse > /dev/null 2>&1
if [[ $? != 0 ]]; then
    echo "   + Creating fuse device"
    mknod /dev/fuse c 10 229
    if [[ $? != 0 ]]; then
        echo "FAILED to to create /dev/fuse node file"
        exit -21
    fi
fi

echo "   + Registering gluster filesystem $VOLUME on $MOUNT_POINT"
echo "$SELF_IP_ADDRESS:/$VOLUME $MOUNT_POINT glusterfs auto,rw,_netdev 0 0" >> /etc/fstab

echo "   + Mounting $MOUNT_POINT"
mount $MOUNT_POINT >> /tmp/mount_$VOLUME 2>&1
if [[ $? != 0 ]]; then
    echo "FAILED to mount gluster filesystem. Perhaps the container is not running as privileged ?"
    cat /tmp/mount_$VOLUME
    exit -20
fi

# give it a little time to actually connect the transport
sleep 4

if [[ `stat -c '%U' $MOUNT_POINT` != "$OWNER" ]]; then
    echo "   + Changing owner of $MOUNT_POINT"
    # not doing it recursively
    chown $OWNER $MOUNT_POINT
fi

echo "   + Changing rights of $MOUNT_POINT"
# not doing it recursively
chmod 777 $MOUNT_POINT

echo "   + SUCCESSFULLY mounted $MOUNT_POINT"
