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


import ch.niceideas.common.utils.StringUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.Timeout;
import org.apache.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.util.*;

public class ProxyServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(ProxyServlet.class);

    public static final String COOKIE            = "Cookie";
    public static final String COOKIE2           = "Cookie2";
    public static final String SET_COOKIE        = "Set-Cookie";
    public static final String SET_COOKIE2       = "Set-Cookie2";

    private static final int MAX_CONNECTION_PER_ROUTE = 5;

    /* INIT PARAMETER NAME CONSTANTS */

    public static final String SESSION_HTTP_CLIENT = "SESSION_HTTP_CLIENT";

    /**
     * A boolean parameter name to enable logging of input and target URLs to the servlet log.
     */
    public static final String P_LOG = "log";

    /**
     * A boolean parameter name to enable forwarding of the client IP
     */
    public static final String P_FORWARDEDFOR = "forwardip";

    /**
     * A boolean parameter name to keep HOST parameter as-is
     */
    public static final String P_PRESERVEHOST = "preserveHost";

    /**
     * A boolean parameter name to keep COOKIES as-is
     */
    public static final String P_PRESERVECOOKIES = "preserveCookies";

    /**
     * A boolean parameter name to have auto-handle redirects
     */
    public static final String P_HANDLEREDIRECTS = "http.protocol.handle-redirects"; // ClientPNames.HANDLE_REDIRECTS

    /**
     * A integer parameter name to set the socket connection timeout (millis)
     */
    public static final String P_CONNECTTIMEOUT = "http.socket.timeout"; // CoreConnectionPNames.SO_TIMEOUT

    /**
     * A integer parameter name to set the socket read timeout (millis)
     */
    public static final String P_READTIMEOUT = "http.read.timeout";

    /**
     * A integer parameter name to set the connection request timeout (millis)
     */
    public static final String P_CONNECTIONREQUESTTIMEOUT = "http.connectionrequest.timeout";

    /**
     * A integer parameter name to set max connection number
     */
    public static final String P_MAXCONNECTIONS = "http.maxConnections";

    /**
     * A boolean parameter whether to use JVM-defined system properties to configure various networking aspects.
     */
    public static final String P_USESYSTEMPROPERTIES = "useSystemProperties";

    /**
     * The parameter name for the target (destination) URI to proxy to.
     */
    protected static final String P_TARGET_URI = "targetUri";
    protected static final String ATTR_TARGET_URI =
            ProxyServlet.class.getSimpleName() + ".targetUri";
    protected static final String ATTR_TARGET_HOST =
            ProxyServlet.class.getSimpleName() + ".targetHost";

    private static final Map<String, String> overridingClientHheaders = new HashMap<>() {{
            put ("accept-encoding", "gzip, deflate");
        }};

    /* MISC */

    protected boolean doLog = false;
    protected boolean doForwardIP = true;

    /**
     * Don't mess with these settings !!!
     */
    protected boolean doSendUrlFragment = true;
    protected boolean doPreserveHost = false;
    protected boolean doPreserveCookies = false;
    protected boolean doHandleRedirects = false;
    protected boolean useSystemProperties = true;
    protected int connectTimeout = -1;
    protected int readTimeout = -1;
    protected int connectionRequestTimeout = -1;
    protected int maxConnections = -1;

    //These next 3 are cached here, and should only be referred to in initialization logic. See the
    // ATTR_* parameters.
    /**
     * From the configured parameter "targetUri".
     */
    protected String targetUri;
    protected URI targetUriObj;
    protected HttpHost targetHost;

    protected String getTargetUri(HttpServletRequest servletRequest) {
        return (String) servletRequest.getAttribute(ATTR_TARGET_URI);
    }

    protected HttpHost getTargetHost(HttpServletRequest servletRequest) {
        return (HttpHost) servletRequest.getAttribute(ATTR_TARGET_HOST);
    }

    /**
     * Reads a configuration parameter. By default it reads servlet init parameters but
     * it can be overridden.
     */
    protected String getConfigParam(String key) {
        return getServletConfig().getInitParameter(key);
    }

    @Override
    public void init() throws ServletException {

        String doLogStr = getConfigParam(P_LOG);
        if (StringUtils.isNotBlank(doLogStr)) {
            this.doLog = Boolean.parseBoolean(doLogStr);
        }

        String doForwardIPString = getConfigParam(P_FORWARDEDFOR);
        if (StringUtils.isNotBlank(doForwardIPString)) {
            this.doForwardIP = Boolean.parseBoolean(doForwardIPString);
        }

        String preserveHostString = getConfigParam(P_PRESERVEHOST);
        if (StringUtils.isNotBlank(preserveHostString)) {
            this.doPreserveHost = Boolean.parseBoolean(preserveHostString);
        }

        String preserveCookiesString = getConfigParam(P_PRESERVECOOKIES);
        if (StringUtils.isNotBlank(preserveCookiesString)) {
            this.doPreserveCookies = Boolean.parseBoolean(preserveCookiesString);
        }

        String handleRedirectsString = getConfigParam(P_HANDLEREDIRECTS);
        if (StringUtils.isNotBlank(handleRedirectsString)) {
            this.doHandleRedirects = Boolean.parseBoolean(handleRedirectsString);
        }

        String connectTimeoutString = getConfigParam(P_CONNECTTIMEOUT);
        if (StringUtils.isNotBlank(connectTimeoutString)) {
            this.connectTimeout = Integer.parseInt(connectTimeoutString);
        }

        String readTimeoutString = getConfigParam(P_READTIMEOUT);
        if (StringUtils.isNotBlank(readTimeoutString)) {
            this.readTimeout = Integer.parseInt(readTimeoutString);
        }

        String connectionRequestTimeoutString = getConfigParam(P_CONNECTIONREQUESTTIMEOUT);
        if (StringUtils.isNotBlank(connectionRequestTimeoutString)) {
            this.connectionRequestTimeout = Integer.parseInt(connectionRequestTimeoutString);
        }

        String maxConnectionsString = getConfigParam(P_MAXCONNECTIONS);
        if (StringUtils.isNotBlank(maxConnectionsString)) {
            this.maxConnections = Integer.parseInt(maxConnectionsString);
        }

        String useSystemPropertiesString = getConfigParam(P_USESYSTEMPROPERTIES);
        if (StringUtils.isNotBlank(useSystemPropertiesString)) {
            this.useSystemProperties = Boolean.parseBoolean(useSystemPropertiesString);
        }

        initTarget();//sets target*
    }

    protected RequestConfig buildRequestConfig() {
        return RequestConfig.custom()
                .setRedirectsEnabled(doHandleRedirects)
                .setCookieSpec(StandardCookieSpec.IGNORE) // we handle them in the servlet instead
                .setConnectTimeout(Timeout.defaultsToDisabled(connectTimeout == -1 ? null : Timeout.ofMilliseconds(connectTimeout)))
                .setResponseTimeout(Timeout.defaultsToDisabled(readTimeout == -1 ? null : Timeout.ofMilliseconds(readTimeout)))
                .setCircularRedirectsAllowed(true)
                .setConnectionRequestTimeout(Timeout.defaultsToDisabled(connectionRequestTimeout == -1 ? null : Timeout.ofMilliseconds(connectionRequestTimeout)))
                .build();
    }

    protected SocketConfig buildSocketConfig() {

        if (readTimeout < 1) {
            return null;
        }

        return SocketConfig.custom()
                .setSoTimeout(Timeout.ofMilliseconds(readTimeout))
                .build();
    }

    protected void initTarget() throws ServletException {
        targetUri = getConfigParam(P_TARGET_URI);
        if (targetUri == null)
            throw new ServletException(P_TARGET_URI + " is required.");
        //test it's valid
        try {
            targetUriObj = new URI(targetUri);
        } catch (Exception e) {
            throw new ServletException("Trying to process targetUri init parameter: " + e, e);
        }
        targetHost = URIUtils.extractHost(targetUriObj);
    }

    protected HttpClient createHttpClient() {

        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultSocketConfig(buildSocketConfig())
                .setMaxConnPerRoute(MAX_CONNECTION_PER_ROUTE)
                .setMaxConnTotal(maxConnections)
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.LAX)
                .setConnPoolPolicy(PoolReusePolicy.LIFO)
                .build();

        HttpClientBuilder clientBuilder = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(buildRequestConfig());

        if (useSystemProperties)
            clientBuilder.useSystemProperties();

        return clientBuilder.build();
    }

    protected HttpClient getHttpClient(HttpSession session) {

        HttpClient proxyClient = (HttpClient) session.getAttribute(SESSION_HTTP_CLIENT);
        if (proxyClient == null) {
            proxyClient = createHttpClient();
            session.setAttribute(SESSION_HTTP_CLIENT, proxyClient);
        }

        return proxyClient;
    }

    public static class ProxySessionListener implements HttpSessionListener {

        @Override
        public void sessionDestroyed(HttpSessionEvent se) {
            HttpClient proxyClient = (HttpClient) se.getSession().getAttribute(SESSION_HTTP_CLIENT);
            if (proxyClient != null) {
                if (proxyClient instanceof Closeable) {
                    try {
                        ((Closeable) proxyClient).close();
                    } catch (IOException e) {
                        logger.error("While destroying servlet, shutting down HttpClient: " + e, e);
                    }
                }
            }
        }
    }

    @Override
    protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
            throws ServletException, IOException {

        //initialize request attributes from caches if unset by a subclass by this point
        if (servletRequest.getAttribute(ATTR_TARGET_URI) == null) {
            servletRequest.setAttribute(ATTR_TARGET_URI, targetUri);
        }
        if (servletRequest.getAttribute(ATTR_TARGET_HOST) == null) {
            servletRequest.setAttribute(ATTR_TARGET_HOST, targetHost);
        }

        // Make the Request
        //note: we won't transfer the protocol version because I'm not sure it would truly be compatible
        String method = servletRequest.getMethod();
        String proxyRequestUri = rewriteUrlFromRequest(servletRequest);

        ClassicHttpRequest proxyRequest;
        //spec: RFC 2616, sec 4.3: either of these two headers signal that there is a message body.
        if ( (servletRequest.getHeader(HttpHeaders.CONTENT_LENGTH) != null
                && !servletRequest.getHeader(HttpHeaders.CONTENT_LENGTH).trim().equals("0")) ||
                servletRequest.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
            proxyRequest = newProxyRequestWithEntity(method, proxyRequestUri, servletRequest);
        } else {
            proxyRequest = new BasicClassicHttpRequest(method, proxyRequestUri);
        }

        copyRequestHeaders(servletRequest, proxyRequest);

        overridingClientHheaders.keySet().forEach(
                key -> proxyRequest.setHeader(key, overridingClientHheaders.get(key)));

        setXForwardedForHeader(servletRequest, proxyRequest);

        ClassicHttpResponse proxyResponse = null;
        try {
            // Execute the request
            proxyResponse = doExecute(servletRequest, proxyRequest);

            // Process the response:

            // Pass the response code. This method with the "reason phrase" is deprecated but it's the
            //   only way to pass the reason along too.
            int statusCode = proxyResponse.getCode();

            servletResponse.setStatus(statusCode, proxyResponse.getReasonPhrase());

            // Copying response headers to make sure SESSIONID or other Cookie which comes from the remote
            // server will be saved in client when the proxied url was redirected to another one.
            // See issue [#51](https://github.com/mitre/HTTP-Proxy-Servlet/issues/51)
            copyResponseHeaders(proxyResponse, servletRequest, servletResponse);

            if (statusCode == HttpServletResponse.SC_NOT_MODIFIED) {
                // 304 needs special handling.  See:
                // http://www.ics.uci.edu/pub/ietf/http/rfc1945.html#Code304
                // Don't send body entity/content!
                servletResponse.setIntHeader(HttpHeaders.CONTENT_LENGTH, 0);
            } else {
                // Send the content to the client
                copyResponseEntity(proxyResponse, servletResponse, proxyRequest, servletRequest);
            }

        } catch (Exception e) {
            handleRequestException(proxyRequest, e);
        } finally {
            // make sure the entire entity was consumed, so the connection is released
            if (proxyResponse != null) {
                EntityUtils.consumeQuietly(proxyResponse.getEntity());
                proxyResponse.close();
            }
            //Note: Don't need to close servlet outputStream:
            // http://stackoverflow.com/questions/1159168/should-one-call-close-on-httpservletresponse-getoutputstream-getwriter
        }
    }

    protected void handleRequestException(HttpRequest proxyRequest, Exception e) throws ServletException, IOException {
        //abort request, according to best practice with HttpClient
        /*
        if (proxyRequest instanceof AbortableHttpRequest) {
            AbortableHttpRequest abortableHttpRequest = (AbortableHttpRequest) proxyRequest;
            abortableHttpRequest.abort();
        }
        */
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        }
        if (e instanceof ServletException) {
            throw (ServletException) e;
        }
        if (e instanceof IOException) {
            throw (IOException) e;
        }
        throw new ProxyServletRuntimeException(e);
    }

    public static class ProxyServletRuntimeException extends RuntimeException {
        public ProxyServletRuntimeException (Exception e) {
            super (e);
        }
    }

    protected ClassicHttpResponse doExecute(HttpServletRequest servletRequest, ClassicHttpRequest proxyRequest) throws IOException {
        if (doLog) {
            log("proxy " + servletRequest.getMethod() + " uri: " + servletRequest.getRequestURI() + " -- " +
                    proxyRequest.getRequestUri());
        }
        return getHttpClient(servletRequest.getSession()).execute(getTargetHost(servletRequest), proxyRequest);
    }

    protected ClassicHttpRequest newProxyRequestWithEntity(String method, String proxyRequestUri,
                                                    HttpServletRequest servletRequest)
            throws IOException {

        BasicClassicHttpRequest eProxyRequest =
                new BasicClassicHttpRequest(method, proxyRequestUri);

        // Add the input entity (streamed)
        //  note: we don't bother ensuring we close the servletInputStream since the container handles it
        eProxyRequest.setEntity(
                new InputStreamEntity(servletRequest.getInputStream(),
                getContentLength(servletRequest),
                        ContentType.create (
                                !servletRequest.getContentType().contains(";") ?
                                        servletRequest.getContentType() :
                                        servletRequest.getContentType().substring(0, servletRequest.getContentType().indexOf(";")))));
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

    /**
     * These are the "hop-by-hop" headers that should not be copied.
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
     * I use an HttpClient HeaderGroup class instead of Set&lt;String&gt; because this
     * approach does case insensitive lookup faster.
     */
    protected static final HeaderGroup hopByHopHeaders;

    static {
        hopByHopHeaders = new HeaderGroup();
        String[] headers = new String[]{
                "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
                "TE", "Trailers", "Transfer-Encoding", "Upgrade"};
        for (String header : headers) {
            hopByHopHeaders.addHeader(new BasicHeader(header, null));
        }
    }

    /**
     * Copy request headers from the servlet client to the proxy request.
     * This is easily overridden to add your own.
     */
    protected void copyRequestHeaders(HttpServletRequest servletRequest, HttpRequest proxyRequest) {
        // Get an Enumeration of all of the header names sent by the client
        Enumeration<String> enumerationOfHeaderNames = servletRequest.getHeaderNames();
        while (enumerationOfHeaderNames.hasMoreElements()) {
            String headerName = enumerationOfHeaderNames.nextElement();
            copyRequestHeader(servletRequest, proxyRequest, headerName);
        }
    }

    /**
     * Copy a request header from the servlet client to the proxy request.
     * This is easily overridden to filter out certain headers if desired.
     */
    protected void copyRequestHeader(HttpServletRequest servletRequest, HttpRequest proxyRequest,
                                     String headerName) {
        //Instead the content-length is effectively set via InputStreamEntity
        if (headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH))
            return;
        if (hopByHopHeaders.containsHeader(headerName))
            return;

        Enumeration<String> headers = servletRequest.getHeaders(headerName);
        while (headers.hasMoreElements()) {//sometimes more than one value
            String headerValue = headers.nextElement();
            // In case the proxy host is running multiple virtual servers,
            // rewrite the Host header to ensure that we get content from
            // the correct virtual server
            if (!doPreserveHost && headerName.equalsIgnoreCase(HttpHeaders.HOST)) {
                HttpHost host = getTargetHost(servletRequest);
                headerValue = host.getHostName();
                if (host.getPort() != -1)
                    headerValue += ":" + host.getPort();
            } else if (!doPreserveCookies && headerName.equalsIgnoreCase(COOKIE)) {
                headerValue = getRealCookie(headerValue);
            }
            proxyRequest.addHeader(headerName, headerValue);
        }
    }

    private void setXForwardedForHeader(HttpServletRequest servletRequest,
                                        HttpRequest proxyRequest) {
        if (doForwardIP) {
            String forHeaderName = "X-Forwarded-For";
            String forHeader = servletRequest.getRemoteAddr();
            String existingForHeader = servletRequest.getHeader(forHeaderName);
            if (existingForHeader != null) {
                forHeader = existingForHeader + ", " + forHeader;
            }
            proxyRequest.setHeader(forHeaderName, forHeader);

            String protoHeaderName = "X-Forwarded-Proto";
            String protoHeader = servletRequest.getScheme();
            proxyRequest.setHeader(protoHeaderName, protoHeader);
        }
    }

    /**
     * Copy proxied response headers back to the servlet client.
     */
    protected void copyResponseHeaders(HttpResponse proxyResponse, HttpServletRequest servletRequest,
                                       HttpServletResponse servletResponse) {
        for (Header header : proxyResponse.getHeaders()) {
            copyResponseHeader(servletRequest, servletResponse, header);
        }
    }

    /**
     * Copy a proxied response header back to the servlet client.
     * This is easily overwritten to filter out certain headers if desired.
     */
    protected void copyResponseHeader(HttpServletRequest servletRequest,
                                      HttpServletResponse servletResponse, Header header) {
        String headerName = header.getName();
        if (hopByHopHeaders.containsHeader(headerName))
            return;
        String headerValue = header.getValue();
        if (headerName.equalsIgnoreCase(SET_COOKIE) ||
                headerName.equalsIgnoreCase(SET_COOKIE2)) {
            copyProxyCookie(servletRequest, servletResponse, headerValue);
        } else if (headerName.equalsIgnoreCase(HttpHeaders.LOCATION)) {
            // LOCATION Header may have to be rewritten.
            servletResponse.addHeader(headerName, rewriteUrlFromResponse(servletRequest, headerValue));
        } else {
            servletResponse.addHeader(headerName, headerValue);
        }
    }

    /**
     * Copy cookie from the proxy to the servlet client.
     * Replaces cookie path to local path and renames cookie to avoid collisions.
     */
    protected void copyProxyCookie(HttpServletRequest servletRequest,
                                   HttpServletResponse servletResponse, String headerValue) {
        //build path for resulting cookie
        String path = servletRequest.getContextPath(); // path starts with / or is empty string
        path += servletRequest.getServletPath(); // servlet path starts with / or is empty string
        if (path.isEmpty()) {
            path = "/";
        }

        for (HttpCookie cookie : HttpCookie.parse(headerValue)) {
            //set cookie name prefixed w/ a proxy value so it won't collide w/ other cookies
            String proxyCookieName = doPreserveCookies ? cookie.getName() : getCookieNamePrefix() + cookie.getName();
            Cookie servletCookie = new Cookie(proxyCookieName, cookie.getValue());
            servletCookie.setComment(cookie.getComment());
            servletCookie.setMaxAge((int) cookie.getMaxAge());
            servletCookie.setPath(path); //set to the path of the proxy servlet
            // don't set cookie domain
            servletCookie.setSecure(cookie.getSecure());
            servletCookie.setVersion(cookie.getVersion());
            servletCookie.setHttpOnly(cookie.isHttpOnly());
            servletResponse.addCookie(servletCookie);
        }
    }

    /**
     * Take any client cookies that were originally from the proxy and prepare them to send to the
     * proxy.  This relies on cookie headers being set correctly according to RFC 6265 Sec 5.4.
     * This also blocks any local cookies from being sent to the proxy.
     */
    protected String getRealCookie(String cookieValue) {
        StringBuilder escapedCookie = new StringBuilder();
        String[] cookies = cookieValue.split("[;,]");
        for (String cookie : cookies) {
            String[] cookieSplit = cookie.split("=");
            if (cookieSplit.length == 2) {
                String cookieName = cookieSplit[0].trim();
                if (cookieName.startsWith(getCookieNamePrefix())) {
                    cookieName = cookieName.substring(getCookieNamePrefix().length());
                    if (escapedCookie.length() > 0) {
                        escapedCookie.append("; ");
                    }
                    escapedCookie.append(cookieName).append("=").append(cookieSplit[1].trim());
                }
            }
        }
        return escapedCookie.toString();
    }

    /**
     * The string prefixing rewritten cookies.
     */
    protected String getCookieNamePrefix() {
        return "!Proxy!" + getServletConfig().getServletName();
    }

    /**
     * Copy response body data (the entity) from the proxy to the servlet client.
     */
    protected void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponse servletResponse,
                                      HttpRequest proxyRequest, HttpServletRequest servletRequest)
            throws IOException {
        if (proxyResponse instanceof HttpEntityContainer) {
            HttpEntity entity = ((HttpEntityContainer)proxyResponse).getEntity();
            if (entity != null) {
                OutputStream servletOutputStream = servletResponse.getOutputStream();
                entity.writeTo(servletOutputStream);
            }
        }
    }

    /**
     * Reads the request URI from {@code servletRequest} and rewrites it, considering targetUri.
     * It's used to make the new request.
     */
    protected String rewriteUrlFromRequest(HttpServletRequest servletRequest) {
        StringBuilder uri = new StringBuilder(500);
        uri.append(getTargetUri(servletRequest));
        // Handle the path given to the servlet
        String pathInfo = rewritePathInfoFromRequest(servletRequest);
        if (pathInfo != null) {//ex: /my/path.html
            // getPathInfo() returns decoded string, so we need encodeUriQuery to encode "%" characters

            if (pathInfo.startsWith("/") && uri.toString().endsWith("/")) {
                pathInfo = pathInfo.substring(1);
            }

            uri.append(encodeUriQuery(pathInfo, true));
        }

        return returnRewriteUrl(servletRequest, uri);
    }

    protected String returnRewriteUrl(HttpServletRequest servletRequest, StringBuilder uri) {
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
        return uri.toString();
    }

    protected String rewriteQueryStringFromRequest(HttpServletRequest servletRequest, String queryString) {
        return queryString;
    }

    /**
     * Allow overrides of {@link javax.servlet.http.HttpServletRequest#getPathInfo()}.
     * Useful when url-pattern of servlet-mapping (web.xml) requires manipulation.
     */
    protected String rewritePathInfoFromRequest(HttpServletRequest servletRequest) {
        return servletRequest.getPathInfo();
    }

    /**
     * For a redirect response from the target server, this translates {@code theUrl} to redirect to
     * and translates it to one the original client can use.
     */
    protected String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl) {

        // CAUTION : THIS METHOD IS OVERRIDEN IN ServicesProxyServlet
        // DON'T DO ANY MODIFICATION HERE, IT'S USELESS

        final String targetUri = getTargetUri(servletRequest);
        if (theUrl.startsWith(targetUri)) {
             /*
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
            if ((pos = curUrl.indexOf("://")) >= 0
                    // Skip the authority part
                    // + 3 to skip the separator between protocol and authority
                    && (pos = curUrl.indexOf("/", pos + 3)) >= 0) {
                // Trim everything after the authority part.
                curUrl.setLength(pos);
            }
            // Context path starts with a / if it is not blank
            curUrl.append(servletRequest.getContextPath());
            // Servlet path starts with a / if it is not blank
            curUrl.append(servletRequest.getServletPath());
            curUrl.append(theUrl, targetUri.length(), theUrl.length());
            return curUrl.toString();
        }
        return theUrl;
    }

    /**
     * The target URI as configured. Not null.
     */
    public String getTargetUri() {
        return targetUri;
    }

    /**
     * Encodes characters in the query or fragment part of the URI.
     *
     * <p>Unfortunately, an incoming URI sometimes has characters disallowed by the spec.  HttpClient
     * insists that the outgoing proxied request has a valid URI because it uses Java's {@link URI}.
     * To be more forgiving, we must escape the problematic characters.  See the URI class for the
     * spec.
     *
     * @param in            example: name=value&amp;foo=bar#fragment
     * @param encodePercent determine whether percent characters need to be encoded
     */
    protected CharSequence encodeUriQuery(CharSequence in, boolean encodePercent) {
        //Note that I can't simply use URI.java to encode because it will escape pre-existing escaped things.
        StringBuilder outBuf = null;
        Formatter formatter = null;
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            boolean escape = true;
            if (c < 128) {
                if (asciiQueryChars.get(c) && !(encodePercent && c == '%')) {
                    escape = false;
                }
            } else if (!Character.isISOControl(c) && !Character.isSpaceChar(c)) {//not-ascii
                escape = false;
            }
            if (!escape) {
                if (outBuf != null)
                    outBuf.append(c);
            } else {
                //escape
                if (outBuf == null) {
                    outBuf = new StringBuilder(in.length() + 5 * 3);
                    outBuf.append(in, 0, i);
                    formatter = new Formatter(outBuf);
                }
                //leading %, 0 padded, width 2, capital hex
                formatter.format("%%%02X", (int) c);
            }
        }
        return outBuf != null ? outBuf : in;
    }

    protected static final BitSet asciiQueryChars;

    static {
        char[] cUnreserved = "_-!.~'()*".toCharArray();//plus alphanum
        char[] cPunct = ",;:$&+=".toCharArray();
        char[] cReserved = "?/[]@".toCharArray();//plus punct

        asciiQueryChars = new BitSet(128);
        for (char c = 'a'; c <= 'z'; c++) asciiQueryChars.set(c);
        for (char c = 'A'; c <= 'Z'; c++) asciiQueryChars.set(c);
        for (char c = '0'; c <= '9'; c++) asciiQueryChars.set(c);
        for (char c : cUnreserved) asciiQueryChars.set(c);
        for (char c : cPunct) asciiQueryChars.set(c);
        for (char c : cReserved) asciiQueryChars.set(c);

        asciiQueryChars.set('%');//leave existing percent escapes in place
    }

}
