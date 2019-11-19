#!/usr/bin/env bash


export framework_id=""

function usage() {
    echo "Usage:"
    echo "    mesos-cli -h                Display this help message."
    echo "    mesos-cli list_frameworks   List running frameworks"
    echo "    mesos-cli kill_framework -f [framework_id]"
    echo "                                Kill framework [framework_id]"
}

function load_topology() {

    if [[ ! -f /etc/eskimo_topology.sh ]]; then
        echo "Topology file /etc/eskimo_topology.sh couldn't be found"
        exit -10
    fi

    . /etc/eskimo_topology.sh
}

function list_frameworks() {

    load_topology

    curl -XGET http://$MASTER_MESOS_MASTER_1:5050/master/frameworks 2>/tmp/list_frameworks_results | jq -r  ".frameworks | .[] | (.id + \" : \" + .name)"

    if [[ `cat /tmp/list_frameworks_results` != "" ]]; then
       echo "Couldn't list frameworks. Command failed with :"
       cat /tmp/list_frameworks_results
    fi
}

function kill_framework() {

    load_topology

    curl -XPOST http://$MASTER_MESOS_MASTER_1:5050/master/teardown -d "frameworkId=$1"  2>/tmp/kill_framework_results

    if [[ $? != 0 || `cat /tmp/kill_framework_results` != "" ]]; then
        echo "Couldn't kill framework. Command failed with :"
        cat /tmp/kill_framework_results
    fi
}



# Parse options to the `mesos-cli` command
while getopts ":h" opt; do
    case ${opt} in
        h )
            usage
            exit 0
        ;;
        : )
            usage
            exit 1
        ;;
        \? )
           echo "Invalid Option: -$OPTARG" 1>&2
           exit 1
         ;;
    esac
done
shift $((OPTIND -1))

subcommand=$1;
shift  # Remove 'mesos-cli' from the argument list
case "$subcommand" in

    list_frameworks)
        list_frameworks
    ;;

    # Parse options to the kill_framework sub command
    kill_framework)

        # Process package options
        while getopts ":f:" opt; do
            case ${opt} in
                f )
                    export framework_id=$OPTARG
                    kill_framework $framework_id
                ;;
                \? )
                    echo "Invalid Option: -$OPTARG" 1>&2
                    exit 1
                ;;
                : )
                    echo "Invalid Option: -$OPTARG requires an argument" 1>&2
                    usage
                    exit 5
                ;;
            esac
        done
        if [ $OPTIND -eq 1 ]; then
            echo "Invalid Option: kill_framework requires an argument" 1>&2
            usage
            exit 6
        fi
        shift $((OPTIND -1))
        ;;

    *)
        usage
        exit -2
    ;;
esac
