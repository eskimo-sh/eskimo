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


const nodesConfigPropertyRE = /([a-zA-Z\-_]+)([0-9]*)/;
const ipAddressCheck = /[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+(-[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)?/;

const NODE_ID_FIELD = "node_id";


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

function checkNodesSetup (nodesConfig, uniqueServices, mandatoryServices, servicesConfiguration, servicesDependencies) {

    // check IP addresses and ranges configuration
    var nodeCount = checkIPAddressesAndRanges(nodesConfig, uniqueServices);

    // foolproof bug check : make sure all ids are within node count
    checkIDSWithinNodeRanges(nodesConfig, nodeCount);

    // foolproof bug check : make sure no marathon service can be selected here
    checkNoMarathonServicesSelected(nodesConfig, servicesConfiguration);

    // enforce mandatory services
    enforceMandatoryServices(mandatoryServices, servicesConfiguration, nodeCount, nodesConfig);

    // check service dependencies
    enforceDependencies(nodesConfig, servicesDependencies);
}

function enforceDependencies(nodesConfig, servicesDependencies) {

    // check service dependencies
    for (var key in nodesConfig) {

        var property = parseProperty(key);
        if (property != null) {

            var nodeNbr = -1;
            if (property.nodeNumber != null) {
                nodeNbr = parseInt(property.nodeNumber);
            } else {
                var nbr = nodesConfig[key];
                nodeNbr = parseInt(nbr);
            }

            if (property.serviceName != NODE_ID_FIELD) {

                var serviceDeps = servicesDependencies[property.serviceName];

                for (var i = 0; i < serviceDeps.length; i++) {
                    var dependency = serviceDeps[i];

                    // I want the dependency on same node
                    if (dependency.mes == "SAME_NODE") {

                        enforceDependencySameNode(nodesConfig, dependency, nodeNbr, property.serviceName);
                    }

                    // I want the dependency somewhere
                    else if (isMandatory (nodesConfig, dependency)) {

                        // ensure count of dependencies are available
                        enforceMandatoryDependency(dependency, nodesConfig, nodeNbr, property.serviceName);
                    }
                }
            }
        }
    }
}

function enforceMandatoryDependency(dependency, nodesConfig, nodeNbr, serviceName) {

    // ensure count of dependencies are available
    var expectedCount = dependency.numberOfMasters;
    var actualCount = 0;

    for (var otherKey in nodesConfig) {

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

function enforceDependencySameNode(nodesConfig, dependency, nodeNbr, serviceName) {
    var serviceFound = false;

    for (var otherKey in nodesConfig) {

        var otherProperty = parseProperty(otherKey);
        if (otherProperty != null) {

            if (otherProperty.serviceName == dependency.masterService) {

                var otherNodeNbr = -1;
                if (otherProperty.nodeNumber != null) {
                    otherNodeNbr = otherProperty.nodeNumber;
                } else {
                    var otherNbr = nodesConfig[otherKey];
                    otherNodeNbr = parseInt(otherNbr);
                }
                if (otherNodeNbr == nodeNbr) {
                    serviceFound = true;
                }
            }
        }
    }

    if (!serviceFound && isMandatory (nodesConfig, dependency)) {
        throw "Inconsistency found : Service " + serviceName + " was expecting a service " +
        dependency.masterService + " on same node, but none were found !";
    }
}

function isMandatory (nodesConfig, dependency) {
    if (dependency.mandatory) {
        return true;
    }

    if (dependency.conditional && dependency.conditional != "") {
        for (var key in nodesConfig) {

            var property = parseProperty(key);
            if (property != null && property.serviceName == dependency.conditional) {
                return true;
            }
        }
    }

    return false;
}

function enforceMandatoryServices(mandatoryServices, servicesConfiguration, nodeCount, nodesConfig) {

    // enforce mandatory services
    for (var i = 0; i < mandatoryServices.length; i++) {
        var mandatoryServiceName = mandatoryServices[i];

        var serviceConfig = servicesConfiguration[mandatoryServiceName];
        if (serviceConfig.conditional == "NONE" ||
            (serviceConfig.conditional == "MULTIPLE_NODES") && nodeCount > 1) {

            var foundNodes = 0;
            // just make sure it is installed on every node
            for (var key in nodesConfig) {

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

function checkNoMarathonServicesSelected(nodesConfig, servicesConfiguration) {

    // foolproof bug check : make sure no marathon service can be selected here
    for (var key in nodesConfig) {

        var property = parseProperty(key);
        if (property != null) {

            if (property.serviceName != NODE_ID_FIELD) {
                var serviceConfig = servicesConfiguration[property.serviceName];

                if (serviceConfig == null || serviceConfig.marathon) {
                    throw "Inconsistency found : service " + property.serviceName
                            + " is either undefined or a marathon service which should not be selectable here."
                }
            }
        }
    }
}

function checkIDSWithinNodeRanges(nodesConfig, nodeCount) {

    // foolproof bug check : make sure all ids are within node count
    for (var key in nodesConfig) {
        var property = parseProperty(key);
        if (property.nodeNumber != null) {
            if (property.nodeNumber > nodeCount) {
                throw "Inconsistency found : got key " + key + " which is greater than node number " + nodeCount;
            }
        } else {
            var nbr = nodesConfig[key];
            if (parseInt(nbr) > nodeCount) {
                throw "Inconsistency found : got key " + key + " with nbr " + nbr
                        + " which is greater than node number " + nodeCount;
            }
        }
    }
}

function checkIPAddressesAndRanges(nodesConfig, uniqueServices) {

    var nodeCount = 0;

    // check IP addresses and ranges configuration
    for (var key in nodesConfig) {
        if (key.indexOf(NODE_ID_FIELD) > -1) {
            nodeCount++;
            var nodeNbr = parseInt(key.substring(NODE_ID_FIELD.length));
            var ipAddress = nodesConfig[key];
            if (ipAddress == null || ipAddress == "") {
                throw "Node " + key.substring(NODE_ID_FIELD.length) + " has no IP configured."
            } else {

                var match = ipAddress.match(ipAddressCheck);

                if (match != null) {

                    if (match[1] != null && match[1] != "") { // then its a range

                        for (var j = 0; j < uniqueServices.length; j++) {

                            var uniqueServiceName = uniqueServices[j];

                            // just make sure it is installed on every node
                            for (var otherKey in nodesConfig) {

                                var otherProperty = parseProperty(otherKey);
                                if (otherProperty != null) {

                                    if (otherProperty.serviceName == uniqueServiceName) {

                                        var otherNodeNbr = parseInt(nodesConfig[otherKey]);
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
