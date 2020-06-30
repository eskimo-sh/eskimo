@ECHO OFF

REM
REM This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
REM well to this individual file than to the Eskimo Project as a whole.
REM
REM Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

IF "%1" == "" (
    echo "Need source password to be encoded in argument"
    exit 2
)

java -version
IF %ERRORLEVEL% NEQ 0 (
    if "%JAVA_HOME%"== "" (
        echo "No java in path neither JAVA_HOME defined. Impossible to launch eskimo"
        exit 1
    ) else (
        set PATH=%JAVA_HOME%\bin;%PATH%
    )
)

SET scriptpath=%~dp0

SET drive=%CD:~0,3%

FOR /f "tokens=*" %%G IN ('dir /b %scriptpath%..\..\lib\eskimo*jar') DO set JAR_FILE=%%G

cd /D %drive%
cd %scriptpath%..

REM encoding UTF-8 is required to parse SSH command results properly.

%JAVA_HOME%\bin\java ^
    -Xmx256m ^
    -Dfile.encoding=UTF-8 ^
    -classpath %scriptpath%..\..\lib\%JAR_FILE% ^
    ch.niceideas.eskimo.utils.EncodedPasswordGenerator %1

