#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

# environment parameters
if [[ -z $SETTING_INJECTOR_DEBUG ]]; then
    SETTING_INJECTOR_DEBUG=1
fi
if [[ -z $SETTING_ROOT_FOLDER ]]; then
    SETTING_ROOT_FOLDER=/usr/local/lib
fi


if [[ $1 == "" ]]; then
    echo "Expecting service to be handled to be passed as first argument"
    exit -1
fi
export SERVICE=$1

export SETTINGS_FILE=/etc/eskimo_services-settings.json
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

function echoDebug() {
    if [[ $SETTING_INJECTOR_DEBUG == 1 ]]; then
        echo "     == $1"
    fi
}

function injectVariableProperty () {
    SERVICE=$1
    filename=$2
    propertyFormat=$3
    commentPrefix=$4
    filesystemService=$5
    name=$6
    value=$7

    echoDebug "injectVariableProperty $SERVICE $filename $propertyFormat $commentPrefix $name"

    export sedValue=`echo $value | sed -e 's/[]\/$*^[]/\\&/g'`
    echoDebug "sedValue=$sedValue"

    # Search for $filename under /usr/local/lib/$SERVICE
    for i in `find $SETTING_ROOT_FOLDER/$filesystemService/ -name $filename `; do
        echo "     == processing $i"

        export searchedResult=""
        # replace variable if found
        found=0

        echoDebug "replacing $name $value"
        case "$propertyFormat" in
            "{name}: {value}")

                # replace variable using propertyFormat
                echoDebug "sed -i s/\"$name: .*$\"/\"$name: $sedValue\"/g $i"
                sed -i s/"$name: .*$"/"$name: $sedValue"/g $i
                export searchedResult="$name: $value"
                ;;

            "{name}={value}")

                # replace variable using propertyFormat
                echoDebug "sed -i s/\"$name=.*$\"/\"$name=$sedValue\"/g $i"
                sed -i s/"$name=.*$"/"$name=$sedValue"/g $i
                export searchedResult="$name=$value"
                ;;

            *)
                echo "Unknown property format $propertyFormat";
                exit -3
                ;;
        esac

        # add variable if not found
        echoDebug "check if variable is found"
        if [[ `grep "$searchedResult" $i` == "" ]]; then

            echoDebug "adding not found variable"

            echoDebug "bash -c \"echo -e \\\"$searchedResult\\\"  >> $i\""
            bash -c "echo -e \"$searchedResult\"  >> $i"

        else

            # remove comment prefix if found
            if [[ `grep "$commentPrefix$searchedResult" $i` != "" ]]; then

                export commentValue=`echo $commentPrefix$searchedResult | sed -e 's/[]\/$*^[]/\\&/g'`
                export freeValue=`echo $searchedResult | sed -e 's/[]\/$*^[]/\\&/g'`

                echoDebug " removing comment"

                echoDebug "sed -i s/\"$commentValue\"/\"$freeValue\"/g $i"
                sed -i s/"$commentValue"/"$freeValue"/g $i
            fi

        fi

        # Assess it's found as expected (using propertyFormat)
        if [[ `grep "^$freeValue" $i` == "" ]]; then
            echo "Unable to perform replacement for $SERVICE $filename $propertyFormat $commentPrefix $name"
            exit -5
        fi

    done
}

echoDebug "finding filenames"

IFS=$'\n'
for configFile in `jq -c  ".configs | .[] | .configs | .[] | select (.service==\"$SERVICE\") | {filename,propertyType,propertyFormat,commentPrefix,filesystemService}" $SETTINGS_FILE`; do

    echoDebug "$configFile"

    export filename=`echo $configFile | jq -r ' .filename'`
    export propertyType=`echo $configFile | jq -r ' .propertyType'`
    export propertyFormat=`echo $configFile | jq -r ' .propertyFormat'`
    export commentPrefix=`echo $configFile | jq -r ' .commentPrefix'`
    export filesystemService=`echo $configFile | jq -r ' .filesystemService'`

    echoDebug "Processing properties for \"$filename\""

    IFS=$'\n'
    for property in `jq -c  ".configs | .[] | .configs | .[] | select (.service==\"$SERVICE\" and .filename==\"$filename\") | .properties | .[] | select (.value) " $SETTINGS_FILE`; do

        export name=`echo $property | jq -r ' .name'`
        export value=`echo $property | jq -r ' .value'`

        # Don't overwrite values that need to be kept to default
        if [[ "$value" != "[ESKIMO_DEFAULT]" ]]; then

            echoDebug "Found property $name : $value"

            # TODO Implement additional cases as they appear
            case "$propertyType" in
                VARIABLE)
                    injectVariableProperty $SERVICE $filename $propertyFormat $commentPrefix $filesystemService $name $value
                    ;;
                *)
                    echo "Unknown property type $propertyType";
                    exit -3
                    ;;
            esac
        fi

    done

done
