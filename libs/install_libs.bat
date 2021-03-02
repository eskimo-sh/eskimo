
rem This has to be executed from within the eskimo/libs directrory

#mvn install:install-file -Dfile=./org/jenkins-ci/trilead-ssh2/build-217-jenkins-16/trilead-ssh2-build-217-jenkins-16.jar -DpomFile=./org/jenkins-ci/trilead-ssh2/build-217-jenkins-16/trilead-ssh2-build-217-jenkins-16.pom
mvn install:install-file -Dfile=./org/jenkins-ci/trilead-ssh2/build-217-jenkins-27/trilead-ssh2-build-217-jenkins-27.jar -DpomFile=./org/jenkins-ci/trilead-ssh2/build-217-jenkins-27/trilead-ssh2-build-217-jenkins-27.pom

rem alternatively use keyTrust created (See README in root folder)
rem mvn install:install-file -Djavax.net.ssl.trustStore=C:\data\mavenKeystore -Djavax.net.ssl.trustStorePassword=changeit -Dfile=./org/jenkins-ci/trilead-ssh2/build-217-jenkins-16/trilead-ssh2-build-217-jenkins-16.jar -DpomFile=./org/jenkins-ci/trilead-ssh2/build-217-jenkins-16/trilead-ssh2-build-217-jenkins-16.pom