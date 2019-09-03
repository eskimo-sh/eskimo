/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.eskimo.model.ProxyReplacement;
import ch.niceideas.eskimo.model.ProxyTunnelConfig;
import ch.niceideas.eskimo.model.Service;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.apache.http.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.mitre.dsmiley.httpproxy.ProxyServlet;
import org.springframework.beans.factory.annotation.Value;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

public class ServicesProxyServlet extends ProxyServlet {

    private static final Logger logger = Logger.getLogger(ServicesProxyServlet.class);

    private final ProxyManagerService proxyManagerService;

    private final ServicesDefinition servicesDefinition;

    private final String configuredContextPath;

    public ServicesProxyServlet(ProxyManagerService proxyManagerService, ServicesDefinition servicesDefinition, String configuredContextPath) {
        this.configuredContextPath = configuredContextPath;
        this.proxyManagerService = proxyManagerService;
        this.servicesDefinition = servicesDefinition;
    }

    private String getServiceName(HttpServletRequest servletRequest) {
        String uri = servletRequest.getRequestURI();
        if (StringUtils.isBlank(configuredContextPath)) {
            return uri.substring(1, uri.indexOf("/", 2));
        } else {
            int indexOfContextPath = uri.indexOf(configuredContextPath);
            int startIndex = indexOfContextPath + configuredContextPath.length();
            if (!configuredContextPath.endsWith("/")) {
                startIndex = startIndex + 1;
            }
            return uri.substring(startIndex, uri.indexOf("/", startIndex + 1));
        }
    }

    private String getContextPath (HttpServletRequest servletRequest) {
        return StringUtils.isBlank(configuredContextPath) ?
                "" :
                ((configuredContextPath.startsWith("/") ? configuredContextPath.substring(1) : configuredContextPath)
                        + (configuredContextPath.endsWith("/") ? "" : "/"));
    }

    private String getPrefixPath(HttpServletRequest servletRequest, String contextPathPrefix) {
        String serviceName = getServiceName(servletRequest);
        Service service = servicesDefinition.getService(serviceName);



        if (service.isUnique()) {
            return contextPathPrefix + serviceName;
        } else {

            String targetHost = proxyManagerService.extractHostFromPathInfo(servletRequest.getPathInfo());
            return contextPathPrefix + serviceName + "/" + targetHost.replaceAll("\\.", "-");
        }
    }

    /*
    private String getTargetHost(HttpServletRequest servletRequest, String serviceName) {
        String uri = servletRequest.getRequestURI();
        int indexOfServiceName = uri.indexOf(serviceName);
        return uri.substring(indexOfServiceName+1, uri.indexOf("/", indexOfServiceName + 2));
    }
    */

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

    /**
     * Allow overrides of {@link javax.servlet.http.HttpServletRequest#getPathInfo()}.
     * Useful when url-pattern of servlet-mapping (web.xml) requires manipulation.
     */
    @Override
    protected String rewritePathInfoFromRequest(HttpServletRequest servletRequest) {
        return servletRequest.getPathInfo();
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
                pathInfo = pathInfo.substring(pathInfo.indexOf("/"));
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

        if ("application/x-www-form-urlencoded".equals(servletRequest.getContentType()) || getContentLength(servletRequest) == 0){
            List<NameValuePair> formparams = new ArrayList<NameValuePair>();
            Enumeration<String> paramNames = servletRequest.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String name = paramNames.nextElement();
                String value = servletRequest.getParameter(name);
                formparams.add(new BasicNameValuePair(name, value));
            }
            if (formparams.size() != 0){
                UrlEncodedFormEntity urlEncodedFormEntity = new UrlEncodedFormEntity(formparams, "UTF-8");
                eProxyRequest.setEntity(urlEncodedFormEntity);
            }
        } else {
            eProxyRequest.setEntity(
                    new InputStreamEntity(servletRequest.getInputStream(), getContentLength(servletRequest)));
        }
        return eProxyRequest;
    }

    // Get the header value as a long in order to more correctly proxy very large requests
    private long getContentLength(HttpServletRequest request) {
        String contentLengthHeader = request.getHeader("Content-Length");
        if (contentLengthHeader != null) {
            return Long.parseLong(contentLengthHeader);
        }
        return -1L;
    }

    /** Copy response body data (the entity) from the proxy to the servlet client. */
    @Override
    protected void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponse servletResponse,
                                      HttpRequest proxyRequest, HttpServletRequest servletRequest)
            throws IOException {

        String serviceName = getServiceName(servletRequest);
        Service service = servicesDefinition.getService(serviceName);

        HttpEntity entity = proxyResponse.getEntity();

        String contextPathPrefix = getContextPath (servletRequest);

        String prefixPath = getPrefixPath(servletRequest, contextPathPrefix);

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
            if (entity != null) {

                String inputString = StreamUtils.getAsString(entity.getContent());

                String resultString = performReplacements(service, servletRequest.getRequestURI(), contextPathPrefix, prefixPath, inputString);

                byte[] result = resultString.getBytes();

                // overwrite content length header
                servletResponse.setIntHeader(HttpHeaders.CONTENT_LENGTH, result.length);

                OutputStream servletOutputStream = servletResponse.getOutputStream();
                StreamUtils.copy(new ByteArrayInputStream(result), servletOutputStream);
            }
        }
    }

    String performReplacements(Service service, String requestURI, String contextPath, String prefixPath, String input) throws IOException {
        if (service.getUiConfig().isApplyStandardProxyReplacements()) {

            input = input.replace("src=\"/", "src=\"/" + prefixPath + "/");
            input = input.replace("action=\"/", "action=\"/" + prefixPath + "/");
            input = input.replace("href=\"/", "href=\"/" + prefixPath + "/");
            input = input.replace("href='/", "href='/" + prefixPath + "/");
            input = input.replace("url(\"/", "url(\"/" + prefixPath + "/");
            input = input.replace("url('/", "url('/" + prefixPath + "/");
            input = input.replace("url(/", "url(/" + prefixPath + "/");
            input = input.replace("/api/v1", "/" + prefixPath + "/api/v1");
            input = input.replace("\"/static/", "\"/" + prefixPath + "/static/");
        }

        for (ProxyReplacement replacement : service.getUiConfig().getProxyReplacements()) {
            input = replacement.performReplacement(input, contextPath, prefixPath, requestURI);
        }

        for (String key : proxyManagerService.getAllTunnelConfigKeys()) {
            ProxyTunnelConfig config = proxyManagerService.getTunnelConfig (key);
            input = input.replaceAll(config.getRemoteAddress()+":"+config.getLocalPort(), "/" + key);
        }

        return input;
    }

    /**
     * Reads the request URI from {@code servletRequest} and rewrites it, considering targetUri.
     * It's used to make the new request.
     */
    @Override
    protected String rewriteUrlFromRequest(HttpServletRequest servletRequest) {
        StringBuilder uri = buildRequestUriPath(servletRequest);

        // Handle the query string & fragment
        String queryString = servletRequest.getQueryString();//ex:(following '?'): name=value&foo=bar#fragment
        String fragment = null;
        //split off fragment from queryString, updating queryString if found
        if (queryString != null) {
            int fragIdx = queryString.indexOf('#');
            if (fragIdx >= 0) {
                fragment = queryString.substring(fragIdx + 1);
                queryString = queryString.substring(0, fragIdx);
            }
        }

        queryString = rewriteQueryStringFromRequest(servletRequest, queryString);
        if (queryString != null && queryString.length() > 0) {
            uri.append('?');
            // queryString is not decoded, so we need encodeUriQuery not to encode "%" characters, to avoid double-encoding
            uri.append(encodeUriQuery(queryString, false));
        }

        if (doSendUrlFragment && fragment != null) {
            uri.append('#');
            // fragment is not decoded, so we need encodeUriQuery not to encode "%" characters, to avoid double-encoding
            uri.append(encodeUriQuery(fragment, false));
        }
        logger.debug ("Redirecting " + servletRequest.getRequestURI() + "  - to - " + uri);
        return uri.toString();
    }

    @Override
    protected String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl) {

        String serviceName = getServiceName(servletRequest);
        Service service = servicesDefinition.getService(serviceName);

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
                curUrl.append(contextPath + "/");
            } else {
                curUrl.append("/");
            }

            // Servlet path starts with a / if it is not blank
            //curUrl.append(servletRequest.getServletPath()).append("/"); // JKE

            curUrl.append(getPrefixPath(servletRequest, "")).append("/"); // JKE


            curUrl.append(theUrl, targetUri.length(), theUrl.length());
            return curUrl.toString();
        }
        return theUrl;
    }

}
