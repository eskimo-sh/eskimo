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

import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.service.ServiceDefinition;
import ch.niceideas.eskimo.model.service.proxy.PageScripter;
import ch.niceideas.eskimo.model.service.proxy.ProxyReplacement;
import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.model.service.proxy.ReplacementContext;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import ch.niceideas.eskimo.types.ServiceWebId;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

public class ServicesProxyServlet extends ProxyServlet {

    private static final Logger logger = Logger.getLogger(ServicesProxyServlet.class);
    public static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";

    private final ProxyManagerService proxyManagerService;

    private final ServicesDefinition servicesDefinition;

    private final String configuredContextPath;

    public ServicesProxyServlet(
            ProxyManagerService proxyManagerService,
            ServicesDefinition servicesDefinition,
            String configuredContextPath,
            int maxConnections,
            int readTimeout,
            int connectTimeout,
            int connectionRequestTimeout) {
        this.configuredContextPath = configuredContextPath;
        this.proxyManagerService = proxyManagerService;
        this.servicesDefinition = servicesDefinition;
        this.maxConnections = maxConnections;
        this.readTimeout = readTimeout;
        this.connectTimeout = connectTimeout;
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    @Override
    public void init() throws ServletException {
        // XXX I am removing the possibility to change these. A lot of these settings mess up with most services
        /*
        super.init();
        */
        this.doPreserveHost = true;

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
            if (indexOfContextPath <= -1) {
                throw new IllegalStateException("Couldn't find configured context " + configuredContextPath + " in URI " + uri);
            }
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
        Service service = Service.from(getServiceName(servletRequest));
        ServiceDefinition serviceDef = servicesDefinition.getServiceDefinition(service);

        if (serviceDef == null) {
            throw new IllegalStateException("Couldn't find service " + service + " in service definition.");
        }

        if (serviceDef.isUnique()) {
            return contextPathPrefix + service;
        } else {

            Node targetHost = proxyManagerService.extractHostFromPathInfo(servletRequest.getPathInfo());
            return contextPathPrefix + service + "/" + targetHost.getName();
        }
    }

    @Override
    protected HttpHost getTargetHost(HttpServletRequest servletRequest) {
        Service service = Service.from(getServiceName(servletRequest));

        ServiceDefinition serviceDef = servicesDefinition.getServiceDefinition(service);

        ServiceWebId serviceId = ServiceWebId.fromService(service);
        if (!serviceDef.isUnique()) {
            Node targetHost = proxyManagerService.extractHostFromPathInfo(servletRequest.getPathInfo());
            serviceId = serviceDef.getServiceId(targetHost);
        }

        return proxyManagerService.getServerHost(serviceId);
    }

    @Override
    protected String getTargetUri(HttpServletRequest servletRequest) {
        Service service = Service.from(getServiceName(servletRequest));
        return proxyManagerService.getServerURI(service, servletRequest.getPathInfo());
    }

    private StringBuilder buildRequestUriPath(HttpServletRequest servletRequest) {
        StringBuilder uri = new StringBuilder(500);

        Service service = Service.from(getServiceName(servletRequest));
        ServiceDefinition serviceDef = servicesDefinition.getServiceDefinition(service);

        uri.append(getTargetUri(servletRequest));

        // Handle the path given to the servlet
        String pathInfo = rewritePathInfoFromRequest(servletRequest);
        if (pathInfo != null) {//ex: /my/path.html

            pathInfo = FileUtils.noSlashStart(pathInfo);

            // Need to remove host from pathInfo
            if (!serviceDef.isUnique()) {
                int slashIndex = pathInfo.indexOf('/');
                pathInfo = slashIndex > -1 ? pathInfo.substring(slashIndex + 1)  : "";
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
    protected ClassicHttpRequest newProxyRequestWithEntity(String method, String proxyRequestUri,
                                                    HttpServletRequest servletRequest) throws IOException {

        BasicClassicHttpRequest eProxyRequest =
                new BasicClassicHttpRequest(method, proxyRequestUri);

        if (APPLICATION_X_WWW_FORM_URLENCODED.equals(servletRequest.getContentType()) || getContentLengthOverride(servletRequest) == 0){
            List<NameValuePair> formparams = new ArrayList<>();
            Enumeration<String> paramNames = servletRequest.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String name = paramNames.nextElement();
                String value = servletRequest.getParameter(name);
                formparams.add(new BasicNameValuePair(name, value));
            }
            if (!formparams.isEmpty()){
                UrlEncodedFormEntity urlEncodedFormEntity = new UrlEncodedFormEntity(formparams, StandardCharsets.UTF_8);
                eProxyRequest.setEntity(urlEncodedFormEntity);
            }
        } else {
            eProxyRequest.setEntity(
                    new InputStreamEntity(
                            servletRequest.getInputStream(),
                            getContentLengthOverride(servletRequest),
                            ContentType.create (!servletRequest.getContentType().contains(";") ?
                                    servletRequest.getContentType() :
                                    servletRequest.getContentType().substring(0, servletRequest.getContentType().indexOf(";")))));
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

    private String getFullServerRoot (HttpServletRequest servletRequest) {
        return getFullServerRootNoContext (servletRequest)
                + (StringUtils.isNotBlank(servletRequest.getContextPath()) ? servletRequest.getContextPath() : "");
    }

    private String getFullServerRootNoContext (HttpServletRequest servletRequest) {
        return servletRequest.getScheme() + "://"
                + servletRequest.getServerName()
                + (servletRequest.getServerPort() == 80 ?
                    "" :
                    ":" + servletRequest.getServerPort());
    }

    private String getAppRoot (HttpServletRequest servletRequest) {
        return getAppRootNoContext(servletRequest)
                + (StringUtils.isNotBlank(servletRequest.getContextPath()) ? servletRequest.getContextPath() : "");
    }

    private String getAppRootNoContext (HttpServletRequest servletRequest) {
        return "//"
                + servletRequest.getServerName()
                + (servletRequest.getServerPort() == 80 ?
                    "" :
                    ":" + servletRequest.getServerPort());
    }

    /** Copy response body data (the entity) from the proxy to the servlet client. */
    @Override
    protected void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponse servletResponse,
                                      HttpRequest proxyRequest, HttpServletRequest servletRequest)
            throws IOException {

        if (proxyResponse instanceof HttpEntityContainer) {
            Service service = Service.from(getServiceName(servletRequest));
            ServiceDefinition serviceDef = servicesDefinition.getServiceDefinition(service);

            HttpEntity entity = ((HttpEntityContainer)proxyResponse).getEntity();

            Charset encoding;
            try {
                if (entity != null && entity.getContentEncoding() != null) {
                    encoding = Charset.forName(entity.getContentEncoding());
                } else {
                    encoding = StandardCharsets.UTF_8;
                }
            } catch (UnsupportedCharsetException e) {
                logger.warn(e.getMessage());
                encoding = StandardCharsets.UTF_8;
            }

            String contextPathPrefix = getContextPath();
            String prefixPath = getPrefixPath(servletRequest, contextPathPrefix);
            ReplacementContext context = new ReplacementContext(contextPathPrefix, prefixPath,
                    getFullServerRoot(servletRequest),
                    getFullServerRootNoContext(servletRequest),
                    getAppRoot(servletRequest),
                    getAppRootNoContext(servletRequest));

            boolean isText = false;
            if (entity != null && entity.getContentType() != null) {
                String contentType = entity.getContentType();
                isText = contentType.contains("text") || contentType.contains("javascript") || contentType.contains("json");
            }

            if (!isText) {
                if (entity != null) {
                    OutputStream servletOutputStream = servletResponse.getOutputStream();
                    entity.writeTo(servletOutputStream);
                }

            } else {

                String inputString = StreamUtils.getAsString(entity.getContent(), encoding);

                String resultString = performReplacements(serviceDef, servletRequest.getRequestURI(), context, inputString);

                byte[] result = resultString.getBytes(encoding);

                // overwrite content length header
                servletResponse.setIntHeader(HttpHeaders.CONTENT_LENGTH, result.length);

                OutputStream servletOutputStream = servletResponse.getOutputStream();
                StreamUtils.copy(new ByteArrayInputStream(result), servletOutputStream);
            }
        }
    }

    String performReplacements(ServiceDefinition serviceDef, String requestURI, ReplacementContext context, String input) {
        if (serviceDef.getUiConfig().isApplyStandardProxyReplacements()) {

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

        for (ProxyReplacement replacement : serviceDef.getUiConfig().getProxyReplacements()) {
            input = replacement.performReplacement(input, requestURI, context);
        }

        for (PageScripter scripter : serviceDef.getUiConfig().getPageScripters()) {
            if (requestURI.endsWith(scripter.getResourceUrl())) {
                logger.info ("Applying " + scripter.getResourceUrl());
                String script = scripter.getScript();
                script = context.getResolved(script);
                input = input.replace("</body>", "<script>" + script + "</script></body>");
            }
        }

        for (ServiceWebId key : proxyManagerService.getAllTunnelConfigKeys()) {
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
        for (Header header : proxyResponse.getHeaders()) {
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
        ReplacementContext context = new ReplacementContext(contextPathPrefix, prefixPath,
                getFullServerRoot (servletRequest),
                getFullServerRootNoContext (servletRequest),
                getAppRoot (servletRequest),
                getAppRootNoContext (servletRequest));

        String rewritten = Arrays.stream(servicesDefinition.listUIServices())
                .map (servicesDefinition::getServiceDefinition)
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
