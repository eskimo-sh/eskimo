#!/usr/bin/env bash


DEBUG=1

if [[ $1 == "" ]]; then
    echo "Expecting service to be handled to be passed as first argument"
    exit -1
fi
export SERVICE=$1

export SETTINGS_FILE=/etc/eskimo_services-config.json
if [[ ! -f $SETTINGS_FILE ]]; then
    if [[ $2 == "" ]]; then
        echo "file $SETTINGS_FILE doesn't exist and no other settings file given as second argument"
        exit -2
    fi
    export SETTINGS_FILE=$2
fi

echo " - Injecting Service settings"
echo "   + Using file $SETTINGS_FILE"
echo "   + Working on service $SERVICE"

if [[ $DEBUG == 1 ]]; then echo "   = finding filenames"; fi

IFS=$'\n'
for configFile in `jq -c  ".configs | .[] | .configs | .[] | select (.service==\"$SERVICE\") | {filename,propertyType,propertyFormat}" $SETTINGS_FILE`; do

    #if [[ $DEBUG == 1 ]]; then echo "   $configFile"; fi

    export filename=`echo $configFile | jq -r ' .filename'`
    export propertyType=`echo $configFile | jq -r ' .propertyType'`
    export propertyFormat=`echo $configFile | jq -r ' .propertyFormat'`

    if [[ $DEBUG == 1 ]]; then echo "   = Processing properties for \"$filename\""; fi

    IFS=$'\n'
    for property in `jq -c  ".configs | .[] | .configs | .[] | select (.service==\"$SERVICE\" and .filename==\"$filename\") | .properties | .[] | select (.value) " $SETTINGS_FILE`; do

        export name=`echo $property | jq -r ' .name'`
        export value=`echo $property | jq -r ' .value'`

        if [[ $DEBUG == 1 ]]; then echo "   = Found property $name : $value"; fi

    done



done
