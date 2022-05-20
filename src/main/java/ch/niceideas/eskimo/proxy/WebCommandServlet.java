package ch.niceideas.eskimo.proxy;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.ServicesInstallStatusWrapper;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.model.service.proxy.WebCommand;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.utils.ReturnStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

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

    @SuppressWarnings("deprecation")
    @Override
    protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
            throws ServletException, IOException {

        servletResponse.setContentType("application/json");

        try {

            // 1. Identify command ID I need to execute from argument
            String reqURI = servletRequest.getRequestURI();
            String servletPath = servletRequest.getServletPath();

            String command = reqURI.substring(reqURI.indexOf(servletPath) + servletPath.length() + 1);

            // 2. Find matching command
            WebCommand webCommand = Arrays.stream(servicesDefinition.listUIServices())
                    .map(servicesDefinition::getService)
                    .map (Service::getWebCommands)
                    .flatMap(List::stream)
                    .filter(wc -> wc.getId().equals(command))
                    .findFirst().orElseThrow(() -> new IllegalStateException("More than one webCommand found with ID " + command));

            // 3. Find node running target service
            ServicesInstallStatusWrapper installStatus = configurationService.loadServicesInstallationStatus();
            String serviceNode = installStatus.getFirstNode(webCommand.getService().getName());
            if (StringUtils.isBlank(serviceNode)) {
                throw new IllegalStateException("Couldn't find any node running servuce " + webCommand.getService().getName());
            }

            // 3. Execute command
            String result = sshCommandService.runSSHCommand(serviceNode, webCommand.getCommand());

            // 4. Return result or error in expected json format
            JsonWrapper returnObject = new JsonWrapper("{}");
            returnObject.setValueForPath("value", result);
            servletResponse.getOutputStream().write(returnObject.getFormattedValue().getBytes(StandardCharsets.UTF_8));

        } catch (FileException | SetupException | IllegalStateException | SSHCommandException e) {
            logger.error (e, e);
            servletResponse.getOutputStream().write(ReturnStatusHelper.createErrorStatus(e).getBytes(StandardCharsets.UTF_8));
        }
    }

}
