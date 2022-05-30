/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.service.proxy.PageScripter;
import ch.niceideas.eskimo.model.service.proxy.ProxyReplacement;
import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.model.service.proxy.ReplacementContext;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.apache.http.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

public class ServicesProxyServlet extends ProxyServlet {

    private static final Logger logger = Logger.getLogger(ServicesProxyServlet.class);
    public static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";

    private final ProxyManagerService proxyManagerService;

    private final ServicesDefinition servicesDefinition;

    private final String configuredContextPath;

    public ServicesProxyServlet(ProxyManagerService proxyManagerService, ServicesDefinition servicesDefinition, String configuredContextPath) {
        this.configuredContextPath = configuredContextPath;
        this.proxyManagerService = proxyManagerService;
        this.servicesDefinition = servicesDefinition;
    }

    @Override
    public void init() throws ServletException {
        // I am removing the possibility to change these. A lot of these settings mess up with most services
        /*
        super.init();
        */
        doPreserveHost = true;
    }

    private String getServiceName(HttpServletRequest servletRequest) {
        String uri = servletRequest.getRequestURI();
        if (StringUtils.isBlank(configuredContextPath)) {
            int indexOfSlash = uri.indexOf('/', 2);
            if (indexOfSlash < 0) {
                throw new IllegalArgumentException("No / found in URI " + uri);
            }
            return uri.substring(1, indexOfSlash);
        } else {
            int indexOfContextPath = uri.indexOf(configuredContextPath);
            int startIndex = indexOfContextPath + configuredContextPath.length();
            if (!configuredContextPath.endsWith("/")) {
                startIndex = startIndex + 1;
            }
            return uri.substring(startIndex, uri.indexOf('/', startIndex + 1));
        }
    }

    private String getContextPath () {
        if (StringUtils.isBlank(configuredContextPath)) {
            return "";
        } else {
            return ((configuredContextPath.startsWith("/") ?
                    configuredContextPath.substring(1) : configuredContextPath)
                    + (configuredContextPath.endsWith("/") ?
                    "" : "/"));
        }
    }

    private String getPrefixPath(HttpServletRequest servletRequest, String contextPathPrefix) {
        String serviceName = getServiceName(servletRequest);
        Service service = servicesDefinition.getService(serviceName);

        if (service == null) {
            throw new IllegalStateException("Couldn't find service " + serviceName + " in service definition.");
        }

        if (service.isUnique()) {
            return contextPathPrefix + serviceName;
        } else {

            String targetHost = proxyManagerService.extractHostFromPathInfo(servletRequest.getPathInfo());
            return contextPathPrefix + serviceName + "/" + targetHost.replace(".", "-");
        }
    }

    @Override
    protected HttpHost getTargetHost(HttpServletRequest servletRequest) {
        String serviceName = getServiceName(servletRequest);

        Service service = servicesDefinition.getService(serviceName);

        String serviceId = serviceName;
        if (!service.isUnique()) {
            String targetHost = proxyManagerService.extractHostFromPathInfo(servletRequest.getPathInfo());
            serviceId = service.getServiceId(targetHost);
        }

        return proxyManagerService.getServerHost(serviceId);
    }

    @Override
    protected String getTargetUri(HttpServletRequest servletRequest) {
        String serviceName = getServiceName(servletRequest);
        return proxyManagerService.getServerURI(serviceName, servletRequest.getPathInfo());
    }

    private StringBuilder buildRequestUriPath(HttpServletRequest servletRequest) {
        StringBuilder uri = new StringBuilder(500);

        String serviceName = getServiceName(servletRequest);
        Service service = servicesDefinition.getService(serviceName);

        uri.append(getTargetUri(servletRequest));

        // Handle the path given to the servlet
        String pathInfo = rewritePathInfoFromRequest(servletRequest);
        if (pathInfo != null) {//ex: /my/path.html

            if (pathInfo.startsWith("/")) {
                pathInfo = pathInfo.substring(1);
            }

            // Need to remove host from pathInfo
            if (!service.isUnique()) {
                int slashIndex = pathInfo.indexOf('/');
                pathInfo = slashIndex > -1 ? pathInfo.substring(slashIndex) : "";
            }

            // getPathInfo() returns decoded string, so we need encodeUriQuery to encode "%" characters
            uri.append(encodeUriQuery(pathInfo, true));
        }
        return uri;
    }

    @Override
    protected void initTarget() throws ServletException {
        // do nothing
    }

    @Override
    protected HttpRequest newProxyRequestWithEntity(String method, String proxyRequestUri,
                                                    HttpServletRequest servletRequest) throws IOException {

        HttpEntityEnclosingRequest eProxyRequest = new BasicHttpEntityEnclosingRequest(method, proxyRequestUri);

        if (APPLICATION_X_WWW_FORM_URLENCODED.equals(servletRequest.getContentType()) || getContentLengthOverride(servletRequest) == 0){
            List<NameValuePair> formparams = new ArrayList<>();
            Enumeration<String> paramNames = servletRequest.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String name = paramNames.nextElement();
                String value = servletRequest.getParameter(name);
                formparams.add(new BasicNameValuePair(name, value));
            }
            if (!formparams.isEmpty()){
                UrlEncodedFormEntity urlEncodedFormEntity = new UrlEncodedFormEntity(formparams, "UTF-8");
                eProxyRequest.setEntity(urlEncodedFormEntity);
            }
        } else {
            eProxyRequest.setEntity(
                    new InputStreamEntity(servletRequest.getInputStream(), getContentLengthOverride(servletRequest)));
        }
        return eProxyRequest;
    }

    // Get the header value as a long in order to more correctly proxy very large requests
    private long getContentLengthOverride(HttpServletRequest request) {
        String contentLengthHeader = request.getHeader("Content-Length");
        if (contentLengthHeader != null) {
            return Long.parseLong(contentLengthHeader);
        }
        return -1L;
    }

    private String getAppRootUrl (HttpServletRequest servletRequest) {
        return servletRequest.getScheme() + "://"
                + servletRequest.getServerName() + ":" + servletRequest.getServerPort()
                + (StringUtils.isNotBlank(servletRequest.getContextPath()) ? "/" + servletRequest.getContextPath() : "");
    }

    /** Copy response body data (the entity) from the proxy to the servlet client. */
    @Override
    protected void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponse servletResponse,
                                      HttpRequest proxyRequest, HttpServletRequest servletRequest)
            throws IOException {

        String serviceName = getServiceName(servletRequest);
        Service service = servicesDefinition.getService(serviceName);

        HttpEntity entity = proxyResponse.getEntity();

        String contextPathPrefix = getContextPath ();
        String prefixPath = getPrefixPath(servletRequest, contextPathPrefix);
        String appRootUrl = getAppRootUrl (servletRequest);
        ReplacementContext context = new ReplacementContext(contextPathPrefix, prefixPath, appRootUrl);

        boolean isText = false;
        if (entity != null && entity.getContentType() != null) {
            isText = Arrays.stream(entity.getContentType().getElements())
                    .map(HeaderElement::getName)
                    .anyMatch(name -> name.contains("text") || name.contains("javascript") || name.contains("json"));
        }

        if (!isText) {
            if (entity != null) {
                OutputStream servletOutputStream = servletResponse.getOutputStream();
                entity.writeTo(servletOutputStream);
            }

        } else {

            String inputString = StreamUtils.getAsString(entity.getContent());

            String resultString = performReplacements(service, servletRequest.getRequestURI(), context, inputString);

            byte[] result = resultString.getBytes();

            // overwrite content length header
            servletResponse.setIntHeader(HttpHeaders.CONTENT_LENGTH, result.length);

            OutputStream servletOutputStream = servletResponse.getOutputStream();
            StreamUtils.copy(new ByteArrayInputStream(result), servletOutputStream);
        }
    }

    String performReplacements(Service service, String requestURI, ReplacementContext context, String input) {
        if (service.getUiConfig().isApplyStandardProxyReplacements()) {

            input = input.replace("src=\"/", "src=\"/" + context.getPrefixPath() + "/");
            input = input.replace("action=\"/", "action=\"/" + context.getPrefixPath() + "/");
            input = input.replace("href=\"/", "href=\"/" + context.getPrefixPath() + "/");
            input = input.replace("href='/", "href='/" + context.getPrefixPath() + "/");
            input = input.replace("url(\"/", "url(\"/" + context.getPrefixPath() + "/");
            input = input.replace("url('/", "url('/" + context.getPrefixPath() + "/");
            input = input.replace("url(/", "url(/" + context.getPrefixPath() + "/");
            input = input.replace("/api/v1", "/" + context.getPrefixPath() + "/api/v1");
            input = input.replace("\"/static/", "\"/" + context.getPrefixPath() + "/static/");
        }

        for (ProxyReplacement replacement : service.getUiConfig().getProxyReplacements()) {
            input = replacement.performReplacement(input, requestURI, context);
        }

        for (PageScripter scripter : service.getUiConfig().getPageScripters()) {
            if (requestURI.endsWith(scripter.getResourceUrl())) {
                logger.info ("Applying " + scripter.getResourceUrl());
                String script = scripter.getScript();
                script = context.getResolved(script);
                input = input.replace("</body>", "<script>" + script + "</script></body>");
            }
        }

        for (String key : proxyManagerService.getAllTunnelConfigKeys()) {
            ProxyTunnelConfig config = proxyManagerService.getTunnelConfig (key);
            if (config == null) {
                throw new IllegalStateException("Asked for proxy for service " + key + " - but none has been configured !");
            }
            input = input.replace(config.getNode()+":"+config.getLocalPort(), "/" + key);
        }

        return input;
    }

    /** Copy proxied response headers back to the servlet client. */
    @Override
    protected void copyResponseHeaders(HttpResponse proxyResponse, HttpServletRequest servletRequest,
                                       HttpServletResponse servletResponse) {
        for (Header header : proxyResponse.getAllHeaders()) {
            if (header.getName().equals("X-Frame-Options")) {
                servletResponse.addHeader(header.getName(), "SAMEORIGIN");
            } else {
                copyResponseHeader(servletRequest, servletResponse, header);
            }
        }
    }

    @Override
    protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
            throws ServletException, IOException {
        try {
            super.service(servletRequest, servletResponse);
        } catch (IllegalStateException | SocketException | NoHttpResponseException e) {
            logger.error (servletRequest.getRequestURI() + " - got " + e.getClass() + ":" + e.getMessage());
            servletResponse.sendError(500);
        }

    }

    /**
     * Reads the request URI from {@code servletRequest} and rewrites it, considering targetUri.
     * It's used to make the new request.
     */
    @Override
    protected String rewriteUrlFromRequest(HttpServletRequest servletRequest) {
        StringBuilder uri = buildRequestUriPath(servletRequest);

        return returnRewriteUrl(servletRequest, uri);
    }

    @Override
    protected String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl) {

        String contextPathPrefix = getContextPath ();
        String prefixPath = getPrefixPath(servletRequest, contextPathPrefix);
        String appRootUrl = getAppRootUrl (servletRequest);
        ReplacementContext context = new ReplacementContext(contextPathPrefix, prefixPath, appRootUrl);

        String rewritten = Arrays.stream(servicesDefinition.listUIServices())
                .map (servicesDefinition::getService)
                .map (service -> service.getUiConfig().getUrlRewritings())
                .flatMap(List::stream)
                .map(urlRewriting -> {
                    if (urlRewriting.matches (theUrl, context)) {
                        return urlRewriting.rewrite (theUrl, context);
                    }
                    return null;
                })
                .filter(StringUtils::isNotBlank)
                .findFirst().orElse(null);

        if (StringUtils.isNotBlank(rewritten)) {
            return rewritten;
        }

        final String targetUri = getTargetUri(servletRequest);

        if (theUrl.startsWith(targetUri)) {
            /*-
             * The URL points back to the back-end server.
             * Instead of returning it verbatim we replace the target path with our
             * source path in a way that should instruct the original client to
             * request the URL pointed through this Proxy.
             * We do this by taking the current request and rewriting the path part
             * using this servlet's absolute path and the path from the returned URL
             * after the base target URL.
             */
            StringBuffer curUrl = servletRequest.getRequestURL();//no query
            int pos;
            // Skip the protocol part
            if ((pos = curUrl.indexOf("://"))>=0) {
                // Skip the authority part
                // + 3 to skip the separator between protocol and authority
                if ((pos = curUrl.indexOf("/", pos + 3)) >=0) {
                    // Trim everything after the authority part.
                    curUrl.setLength(pos);
                }
            }
            // Context path starts with a / if it is not blank
            String contextPath = servletRequest.getContextPath();
            if (StringUtils.isNotBlank(contextPath)) {
                curUrl.append(contextPath).append("/");
            } else {
                curUrl.append("/");
            }

            // Servlet path starts with a / if it is not blank
            curUrl.append(getPrefixPath(servletRequest, "")).append("/"); // JKE

            curUrl.append(theUrl, targetUri.length(), theUrl.length());
            return curUrl.toString();
        }
        return theUrl;
    }

}
