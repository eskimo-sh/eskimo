REM
REM This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
REM well to this individual file than to the Eskimo Project as a whole.
REM
REM Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
REM Author : eskimo.sh / https://www.eskimo.sh
REM
REM Eskimo is available under a dual licensing model : commercial and GNU AGPL.
REM If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
REM terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
REM Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
REM any later version.
REM Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
REM commercial license.
REM
REM Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
REM WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
REM Affero Public License for more details.
REM
REM You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
REM see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
REM Boston, MA, 02110-1301 USA.
REM
REM You can be released from the requirements of the license by purchasing a commercial license. Buying such a
REM commercial license is mandatory as soon as :
REM - you develop activities involving Eskimo without disclosing the source code of your own product, software,
REM * *   platform, use cases or scripts.
REM - you deploy eskimo as part of a commercial product, platform or software.
REM For more information, please contact eskimo.sh at https://www.eskimo.sh
REM
REM The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
REM Software


rem This has to be executed from within the eskimo/libs directrory

#mvn install:install-file -Dfile=./org/jenkins-ci/trilead-ssh2/build-217-jenkins-16/trilead-ssh2-build-217-jenkins-16.jar -DpomFile=./org/jenkins-ci/trilead-ssh2/build-217-jenkins-16/trilead-ssh2-build-217-jenkins-16.pom
mvn install:install-file -Dfile=./org/jenkins-ci/trilead-ssh2/build-217-jenkins-27/trilead-ssh2-build-217-jenkins-27.jar -DpomFile=./org/jenkins-ci/trilead-ssh2/build-217-jenkins-27/trilead-ssh2-build-217-jenkins-27.pom

rem alternatively use keyTrust created (See README in root folder)
rem mvn install:install-file -Djavax.net.ssl.trustStore=C:\data\mavenKeystore -Djavax.net.ssl.trustStorePassword=changeit -Dfile=./org/jenkins-ci/trilead-ssh2/build-217-jenkins-16/trilead-ssh2-build-217-jenkins-16.jar -DpomFile=./org/jenkins-ci/trilead-ssh2/build-217-jenkins-16/trilead-ssh2-build-217-jenkins-16.pom