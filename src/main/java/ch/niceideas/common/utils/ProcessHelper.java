/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */

package ch.niceideas.common.utils;

import ch.niceideas.common.exceptions.CommonBusinessException;
import ch.niceideas.eskimo.utils.PumpThread;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;


public class ProcessHelper {

    private static final Logger logger = Logger.getLogger(ProcessHelper.class);

    private ProcessHelper() {}

    public static String exec(String cmd) {
        try {
            return exec(cmd.split(" +"), false);
        } catch (ProcessHelperException e) {
            logger.error (e, e);
        }
        return "";
    }

    public static String exec(String[] cmd, boolean throwExceptions) throws ProcessHelperException {
        return execProc (new ProcessBuilder(cmd), throwExceptions);
    }
    
    public static String exec(String cmd, boolean throwExceptions) throws ProcessHelperException {
        return execProc (new ProcessBuilder(cmd.split(" +")), throwExceptions);
    }
    
    private static String execProc(ProcessBuilder pb, boolean throwExceptions) throws ProcessHelperException {
        try {

            Process proc = pb.start();

            int returnCode;
            StringBuilder outputBuilder = new StringBuilder();

            try (ByteArrayOutputStream baosIn = new ByteArrayOutputStream();
                 ByteArrayOutputStream baosErr = new ByteArrayOutputStream()) {

                try (PumpThread ignoredIn = new PumpThread(proc.getInputStream(), baosIn);
                     PumpThread ignoredErr = new PumpThread(proc.getErrorStream(), baosErr)) {

                    returnCode = proc.waitFor();
                }

                outputBuilder.append(baosIn.toString(StandardCharsets.UTF_8));
                outputBuilder.append(baosErr.toString(StandardCharsets.UTF_8));
            }

            proc.destroy();

            if (throwExceptions && returnCode != 0) {
                if (outputBuilder.length() > 0) {
                    throw new ProcessHelperException(outputBuilder.toString());
                } else {
                    throw new ProcessHelperException("Process exited in error.");
                }
            }

            return outputBuilder.toString();

        } catch (IOException e) {
            logger.error (e, e);
            throw new ProcessHelperException (e.getMessage(), e);

        } catch (InterruptedException e) {
            logger.error (e, e);
            Thread.currentThread().interrupt();
            throw new ProcessHelperException (e.getMessage(), e);
        }
    }

    public static class ProcessHelperException extends CommonBusinessException {

        private static final long serialVersionUID = 100537626321527736L;

        public ProcessHelperException(String message, Throwable under) {
            super(message, under);
        }

        public ProcessHelperException(String message) {
            super(message);
        }
    }
}
