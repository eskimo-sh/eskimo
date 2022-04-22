#!/usr/bin/env bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

mvn install:install-file \
        -Dfile=$SCRIPT_DIR/org/jenkins-ci/trilead-ssh2/build-217-jenkins-27/trilead-ssh2-build-217-jenkins-27.jar \
        -DpomFile=$SCRIPT_DIR/org/jenkins-ci/trilead-ssh2/build-217-jenkins-27/trilead-ssh2-build-217-jenkins-27.pom


mvn install:install-file \
        -Dfile=$SCRIPT_DIR/org/jenkins-ci/trilead-ssh2/build-217-jenkins-16/trilead-ssh2-build-217-jenkins-16.jar \
        -DpomFile=$SCRIPT_DIR/org/jenkins-ci/trilead-ssh2/build-217-jenkins-16/trilead-ssh2-build-217-jenkins-16.pom