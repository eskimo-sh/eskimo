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


package ch.niceideas.eskimo.proxy;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.service.ServiceDefinition;
import ch.niceideas.eskimo.model.service.proxy.WebCommand;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.utils.ReturnStatusHelper;
import org.apache.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.MimeTypeUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class WebCommandServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(WebCommandServlet.class);

    private final ServicesDefinition servicesDefinition;
    private final SSHCommandService sshCommandService;
    private final ConfigurationService configurationService;

    public WebCommandServlet(
            ServicesDefinition servicesDefinition,
            SSHCommandService sshCommandService,
            ConfigurationService configurationService) {
        this.servicesDefinition = servicesDefinition;
        this.sshCommandService = sshCommandService;
        this.configurationService = configurationService;

    }

    @Override
    protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
            throws ServletException, IOException {

        servletResponse.setContentType(MimeTypeUtils.APPLICATION_JSON.toString());

        try {

            // 1. Identify command ID I need to execute from argument
            String reqURI = servletRequest.getRequestURI();
            String servletPath = servletRequest.getServletPath();

            String command = reqURI.substring(reqURI.indexOf(servletPath) + servletPath.length() + 1);

            // 2. Find matching command
            WebCommand webCommand = Arrays.stream(servicesDefinition.listUIServices())
                    .map(servicesDefinition::getServiceDefinition)
                    .map(ServiceDefinition::getWebCommands)
                    .flatMap(List::stream)
                    .filter(wc -> wc.getId().equals(command))
                    .findFirst().orElseThrow(() -> new IllegalStateException("No webCommand found with ID " + command));

            // 3. Find node running target service
            ServicesInstallStatusWrapper installStatus = configurationService.loadServicesInstallationStatus();
            Node serviceNode = Optional.ofNullable(installStatus.getFirstNode(webCommand.getTarget()))
                    .orElseThrow(() -> new IllegalStateException("Couldn't find any node running servuce " + webCommand.getTarget().getName()));

            // 6. Ensure user has required Role (if any is required)
            String requiredRole = webCommand.getRole();
            if (StringUtils.isNotBlank(requiredRole)) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(requiredRole))) {
                    throw new IllegalStateException("User is lacking role " + requiredRole);
                }
            }

            // 5. Execute command
            String result = sshCommandService.runSSHCommand(serviceNode, webCommand.getCommand());

            // 6. Return result or error in expected json format
            JsonWrapper returnObject = JsonWrapper.empty();
            returnObject.setValueForPath("value", result);
            servletResponse.getOutputStream().write(returnObject.getFormattedValue().getBytes(StandardCharsets.UTF_8));

        } catch (FileException | SetupException | IllegalStateException | SSHCommandException e) {
            logger.error (e, e);
            servletResponse.getOutputStream().write(ReturnStatusHelper.createErrorStatus(e).getBytes(StandardCharsets.UTF_8));
        }
    }

}
