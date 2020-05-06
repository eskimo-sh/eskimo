/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
Author : eskimo.sh / https://www.eskimo.sh

Eskimo is available under a dual licensing model : commercial and GNU AGPL.
If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
any later version.
Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
commercial license.

Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Affero Public License for more details.

You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
Boston, MA, 02110-1301 USA.

You can be released from the requirements of the license by purchasing a commercial license. Buying such a
commercial license is mandatory as soon as :
- you develop activities involving Eskimo without disclosing the source code of your own product, software,
  platform, use cases or scripts.
- you deploy eskimo as part of a commercial product, platform or software.
For more information, please contact eskimo.sh at https://www.eskimo.sh

The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
Software.
*/


var nodesConfigPropertyRE = /([a-zA-Z\-_]+)([0-9]*)/;
var ipAddressCheck = /[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+(-[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+){0,1}/;

var NODE_ID_FIELD = "node_id";


function parseProperty (key) {

    var matcher = key.match(nodesConfigPropertyRE);

    if (matcher != null && matcher[2] != null && matcher[2] != "") {
        return { "serviceName" : matcher[1], "nodeNumber" : parseInt (matcher[2]) };

    } else if (matcher != null && matcher[1] != null && matcher[1] != "") {
        return { "serviceName" : matcher[1] };

    } else {
        return null;
    }
}

function checkNodesSetup (setupConfig, uniqueServices, mandatoryServices, servicesConfiguration, servicesDependencies) {

    // check IP addresses and ranges configuration
    var nodeCount = checkIPAddressesAndRanges(setupConfig, uniqueServices);

    // foolproof bug check : make sure all ids are within node count
    checkIDSWithinNodeRanges(setupConfig, nodeCount);

    // foolproof bug check : make sure no marathon service can be selected here
    checkNoMarathonServicesSelected(setupConfig, servicesConfiguration);

    // enforce mandatory services
    enforceMandatoryServices(mandatoryServices, servicesConfiguration, nodeCount, setupConfig);

    // check service dependencies
    enforceDependencies(setupConfig, servicesDependencies);
}

function enforceDependencies(setupConfig, servicesDependencies) {

    // check service dependencies
    for (var key in setupConfig) {
        var re = /([a-zA-Z\-_]+)([0-9]*)/;

        var property = parseProperty(key);
        if (property != null) {

            var nodeNbr = -1;
            if (property != null && property.nodeNumber != null) {
                nodeNbr = parseInt(property.nodeNumber);
            } else {
                var nbr = setupConfig[key];
                nodeNbr = parseInt(nbr);
            }

            if (property.serviceName != NODE_ID_FIELD) {

                var serviceDeps = servicesDependencies[property.serviceName];

                for (var i = 0; i < serviceDeps.length; i++) {
                    var dependency = serviceDeps[i];

                    // I want the dependency on same node
                    if (dependency.mes == "SAME_NODE") {

                        enforceDependencySameNode(setupConfig, dependency, nodeNbr, property.serviceName);
                    }

                    // I want the dependency somewhere
                    else if (dependency.mandatory) {

                        // ensure count of dependencies are available
                        enforceMandatoryDependency(dependency, setupConfig, nodeNbr, property.serviceName);
                    }
                }
            }
        }
    }
}

function enforceMandatoryDependency(dependency, setupConfig, nodeNbr, serviceName) {

    // ensure count of dependencies are available
    var expectedCount = dependency.numberOfMasters;
    var actualCount = 0;

    for (var otherKey in setupConfig) {

        var otherProperty = parseProperty(otherKey);
        if (otherProperty != null) {

            if (otherProperty.serviceName == dependency.masterService) {

                // RANDOM_NODE_AFTER wants a different node, I need to check IPs
                if (nodeNbr != null && dependency.mes == "RANDOM_NODE_AFTER") {

                    var otherNodeNbr = otherProperty.nodeNumber;
                    if (otherNodeNbr == nodeNbr) {
                        continue;
                    }
                }

                actualCount++;
            }
        }
    }

    if (actualCount < expectedCount) {
        throw "Inconsistency found : Service " + serviceName + " expects " + expectedCount
        + " " + dependency.masterService + " instance(s). " +
        "But only " + actualCount + " has been found !";
    }
}

function enforceDependencySameNode(setupConfig, dependency, nodeNbr, serviceName) {
    var serviceFound = false;

    for (var otherKey in setupConfig) {

        var otherProperty = parseProperty(otherKey);
        if (otherProperty != null) {

            if (otherProperty.serviceName == dependency.masterService) {

                var otherNodeNbr = -1;
                if (otherProperty.nodeNumber != null) {
                    otherNodeNbr = otherProperty.nodeNumber;
                } else {
                    var otherNbr = setupConfig[otherKey];
                    otherNodeNbr = parseInt(otherNbr);
                }
                if (otherNodeNbr == nodeNbr) {
                    serviceFound = true;
                }
            }
        }
    }

    if (!serviceFound && dependency.mandatory) {
        throw "Inconsistency found : Service " + serviceName + " was expecting a service " +
        dependency.masterService + " on same node, but none were found !";
    }
}

function enforceMandatoryServices(mandatoryServices, servicesConfiguration, nodeCount, setupConfig) {

    // enforce mandatory services
    for (var i = 0; i < mandatoryServices.length; i++) {
        var mandatoryServiceName = mandatoryServices[i];

        var serviceConfig = servicesConfiguration[mandatoryServiceName];
        if (serviceConfig.conditional == "NONE" ||
            (serviceConfig.conditional == "MULTIPLE_NODES") && nodeCount > 1) {

            var foundNodes = 0;
            // just make sure it is installed on every node
            for (var key in setupConfig) {

                var property = parseProperty(key);
                if (property != null && property.serviceName == mandatoryServiceName) {
                    foundNodes++;
                }
            }

            if (foundNodes != nodeCount) {
                throw "Inconsistency found : service " + mandatoryServiceName
                        + " is mandatory on all nodes but some nodes are lacking it.";
            }
        }
    }
}

function checkNoMarathonServicesSelected(setupConfig, servicesConfiguration) {

    // foolproof bug check : make sure no marathon service can be selected here
    for (var key in setupConfig) {

        var property = parseProperty(key);
        if (property != null) {

            if (property != null && property.serviceName != NODE_ID_FIELD) {
                var serviceConfig = servicesConfiguration[property.serviceName];

                if (serviceConfig == null || serviceConfig.marathon) {
                    throw "Inconsistency found : service " + property.serviceName
                            + " is either undefined or a marathon service which should not be selectable here."
                }
            }
        }
    }
}

function checkIDSWithinNodeRanges(setupConfig, nodeCount) {

    // foolproof bug check : make sure all ids are within node count
    for (var key in setupConfig) {
        //var re = /([a-zA-Z\-_]+)([0-9]+)/;
        var property = parseProperty(key);
        if (property.nodeNumber != null) {
            if (property.nodeNumber > nodeCount) {
                throw "Inconsistency found : got key " + key + " which is greater than node number " + nodeCount;
            }
        } else {
            var nbr = setupConfig[key];
            if (parseInt(nbr) > nodeCount) {
                throw "Inconsistency found : got key " + key + " with nbr " + nbr
                        + " which is greater than node number " + nodeCount;
            }
        }
    }
}

function checkIPAddressesAndRanges(setupConfig, uniqueServices) {

    var nodeCount = 0;

    // check IP addresses and ranges configuration
    for (var key in setupConfig) {
        if (key.indexOf(NODE_ID_FIELD) > -1) {
            nodeCount++;
            var nodeNbr = parseInt(key.substring(NODE_ID_FIELD.length));
            var ipAddress = setupConfig[key];
            if (ipAddress == null || ipAddress == "") {
                throw "Node " + key.substring(NODE_ID_FIELD.length) + " has no IP configured."
            } else {

                var match = ipAddress.match(ipAddressCheck);

                if (match != null) {

                    if (match[1] != null && match[1] != "") { // then its a range

                        for (var j = 0; j < uniqueServices.length; j++) {

                            var uniqueServiceName = uniqueServices[j];

                            // just make sure it is installed on every node
                            for (var otherKey in setupConfig) {

                                var otherProperty = parseProperty(otherKey);
                                if (otherProperty != null) {

                                    if (otherProperty.serviceName == uniqueServiceName) {

                                        var otherNodeNbr = parseInt(setupConfig[otherKey]);
                                        console.log("  - " + otherNodeNbr + " - " + nodeNbr);
                                        if (otherNodeNbr == nodeNbr) {
                                            throw "Node " + key.substring(NODE_ID_FIELD.length)
                                                    + " is a range an declares service " + otherProperty.serviceName
                                                    + " which is a unique service, hence forbidden on a range.";
                                        }
                                    }
                                }
                            }
                        }
                    }

                } else {
                    throw "Node " + key.substring(NODE_ID_FIELD.length) + " has IP configured as " + ipAddress
                            + " which is not an IP address or a range.";
                }
            }
        }
    }
    return nodeCount;
}
