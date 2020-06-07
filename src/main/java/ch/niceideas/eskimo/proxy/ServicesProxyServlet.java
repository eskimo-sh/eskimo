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
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.*;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.SocketException;
import java.net.URI;
import java.util.*;

@SuppressWarnings({"deprecation", "serial", "WeakerAccess"})
public class ServicesProxyServlet extends HttpServlet {


    /* INIT PARAMETER NAME CONSTANTS */

    /** A boolean parameter name to enable logging of input and target URLs to the servlet log. */
    public static final String P_LOG = "log";

    /** A boolean parameter name to enable forwarding of the client IP  */
    public static final String P_FORWARDEDFOR = "forwardip";

    /** A boolean parameter name to keep HOST parameter as-is  */
    public static final String P_PRESERVEHOST = "preserveHost";

    /** A boolean parameter name to keep COOKIES as-is  */
    public static final String P_PRESERVECOOKIES = "preserveCookies";

    /** A boolean parameter name to have auto-handle redirects */
    public static final String P_HANDLEREDIRECTS = "http.protocol.handle-redirects"; // ClientPNames.HANDLE_REDIRECTS

    /** A integer parameter name to set the socket connection timeout (millis) */
    public static final String P_CONNECTTIMEOUT = "http.socket.timeout"; // CoreConnectionPNames.SO_TIMEOUT

    /** A integer parameter name to set the socket read timeout (millis) */
    public static final String P_READTIMEOUT = "http.read.timeout";

    /** A integer parameter name to set the connection request timeout (millis) */
    public static final String P_CONNECTIONREQUESTTIMEOUT = "http.connectionrequest.timeout";

    /** A integer parameter name to set max connection number */
    public static final String P_MAXCONNECTIONS = "http.maxConnections";

    /** A boolean parameter whether to use JVM-defined system properties to configure various networking aspects. */
    public static final String P_USESYSTEMPROPERTIES = "useSystemProperties";

    /** The parameter name for the target (destination) URI to proxy to. */
    protected static final String P_TARGET_URI = "targetUri";
    protected static final String ATTR_TARGET_URI =
            ServicesProxyServlet.class.getSimpleName() + ".targetUri";
    protected static final String ATTR_TARGET_HOST =
            ServicesProxyServlet.class.getSimpleName() + ".targetHost";
    public static final String SESSION_HTTP_CLIENT = "SESSION_HTTP_CLIENT";

    /* MISC */

    protected boolean doLog = false;
    protected boolean doForwardIP = true;
    /** User agents shouldn't send the url fragment but what if it does? */
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
    /** From the configured parameter "targetUri". */
    protected String targetUri;
    protected URI targetUriObj;//new URI(targetUri)
    protected HttpHost targetHost;//URIUtils.extractHost(targetUriObj);

    private static final Logger logger = Logger.getLogger(ServicesProxyServlet.class);

    private final ProxyManagerService proxyManagerService;

    private final ServicesDefinition servicesDefinition;

    private final String configuredContextPath;

    public ServicesProxyServlet(ProxyManagerService proxyManagerService, ServicesDefinition servicesDefinition, String configuredContextPath) {
        this.configuredContextPath = configuredContextPath;
        this.proxyManagerService = proxyManagerService;
        this.servicesDefinition = servicesDefinition;
    }

    @Override
    public String getServletInfo() {
        return "A proxy servlet by David Smiley, dsmiley@apache.org";
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
        if (doLogStr != null) {
            this.doLog = Boolean.parseBoolean(doLogStr);
        }

        String doForwardIPString = getConfigParam(P_FORWARDEDFOR);
        if (doForwardIPString != null) {
            this.doForwardIP = Boolean.parseBoolean(doForwardIPString);
        }

        String preserveHostString = getConfigParam(P_PRESERVEHOST);
        if (preserveHostString != null) {
            this.doPreserveHost = Boolean.parseBoolean(preserveHostString);
        }

        String preserveCookiesString = getConfigParam(P_PRESERVECOOKIES);
        if (preserveCookiesString != null) {
            this.doPreserveCookies = Boolean.parseBoolean(preserveCookiesString);
        }

        String handleRedirectsString = getConfigParam(P_HANDLEREDIRECTS);
        if (handleRedirectsString != null) {
            this.doHandleRedirects = Boolean.parseBoolean(handleRedirectsString);
        }

        String connectTimeoutString = getConfigParam(P_CONNECTTIMEOUT);
        if (connectTimeoutString != null) {
            this.connectTimeout = Integer.parseInt(connectTimeoutString);
        }

        String readTimeoutString = getConfigParam(P_READTIMEOUT);
        if (readTimeoutString != null) {
            this.readTimeout = Integer.parseInt(readTimeoutString);
        }

        String connectionRequestTimeout = getConfigParam(P_CONNECTIONREQUESTTIMEOUT);
        if (connectionRequestTimeout != null) {
            this.connectionRequestTimeout = Integer.parseInt(connectionRequestTimeout);
        }

        String maxConnections = getConfigParam(P_MAXCONNECTIONS);
        if (maxConnections != null) {
            this.maxConnections = Integer.parseInt(maxConnections);
        }

        String useSystemPropertiesString = getConfigParam(P_USESYSTEMPROPERTIES);
        if (useSystemPropertiesString != null) {
            this.useSystemProperties = Boolean.parseBoolean(useSystemPropertiesString);
        }

        initTarget();//sets target*
    }

    /**
     * Sub-classes can override specific behaviour of {@link org.apache.http.client.config.RequestConfig}.
     */
    protected RequestConfig buildRequestConfig() {
        return RequestConfig.custom()
                .setRedirectsEnabled(doHandleRedirects)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES) // we handle them in the servlet instead
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(readTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .build();
    }

    /**
     * Sub-classes can override specific behaviour of {@link org.apache.http.config.SocketConfig}.
     */
    protected SocketConfig buildSocketConfig() {

        if (readTimeout < 1) {
            return null;
        }

        return SocketConfig.custom()
                .setSoTimeout(readTimeout)
                .build();
    }


    protected HttpClient getHttpClient(HttpSession session) {

        HttpClient proxyClient = (HttpClient) session.getAttribute(SESSION_HTTP_CLIENT);
        if (proxyClient == null) {
            proxyClient = createHttpClient();
            session.setAttribute("SESSION_HTTP_CLIENT", proxyClient);
        }

        return proxyClient;
    }

    public static class ProxySessionListener implements HttpSessionListener {

        @Override
        public void sessionDestroyed(HttpSessionEvent se) {
            HttpClient proxyClient = (HttpClient) se.getSession().getAttribute("SESSION_HTTP_CLIENT");
            if (proxyClient != null) {
                if (proxyClient instanceof Closeable) {
                    try {
                        ((Closeable) proxyClient).close();
                    } catch (IOException e) {
                        logger.error("While destroying servlet, shutting down HttpClient: "+e, e);
                    }
                } else {
                    //Older releases require we do this:
                    if (proxyClient != null)
                        proxyClient.getConnectionManager().shutdown();
                }
            }
        }
    }


    protected void handleRequestException(HttpRequest proxyRequest, Exception e) throws ServletException, IOException {
        //abort request, according to best practice with HttpClient
        if (proxyRequest instanceof AbortableHttpRequest) {
            AbortableHttpRequest abortableHttpRequest = (AbortableHttpRequest) proxyRequest;
            abortableHttpRequest.abort();
        }
        if (e instanceof RuntimeException)
            throw (RuntimeException)e;
        if (e instanceof ServletException)
            throw (ServletException)e;
        //noinspection ConstantConditions
        if (e instanceof IOException)
            throw (IOException) e;
        throw new RuntimeException(e);
    }

    // Get the header value as a long in order to more correctly proxy very large requests
    private long getContentLength(HttpServletRequest request) {
        String contentLengthHeader = request.getHeader("Content-Length");
        if (contentLengthHeader != null) {
            return Long.parseLong(contentLengthHeader);
        }
        return -1L;
    }

    protected void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            log(e.getMessage(), e);
        }
    }

    /** These are the "hop-by-hop" headers that should not be copied.
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
     * I use an HttpClient HeaderGroup class instead of Set&lt;String&gt; because this
     * approach does case insensitive lookup faster.
     */
    protected static final HeaderGroup hopByHopHeaders;
    static {
        hopByHopHeaders = new HeaderGroup();
        String[] headers = new String[] {
                "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
                "TE", "Trailers", "Transfer-Encoding", "Upgrade" };
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
        @SuppressWarnings("unchecked")
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

        @SuppressWarnings("unchecked")
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
                    headerValue += ":"+host.getPort();
            } else if (!doPreserveCookies && headerName.equalsIgnoreCase(org.apache.http.cookie.SM.COOKIE)) {
                headerValue = getRealCookie(headerValue);
            }
            proxyRequest.addHeader(headerName, headerValue);
        }
    }

    /** Copy a proxied response header back to the servlet client.
     * This is easily overwritten to filter out certain headers if desired.
     */
    protected void copyResponseHeader(HttpServletRequest servletRequest,
                                      HttpServletResponse servletResponse, Header header) {
        String headerName = header.getName();
        if (hopByHopHeaders.containsHeader(headerName))
            return;
        String headerValue = header.getValue();
        if (headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE) ||
                headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE2)) {
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
        if(path.isEmpty()){
            path = "/";
        }

        for (HttpCookie cookie : HttpCookie.parse(headerValue)) {
            //set cookie name prefixed w/ a proxy value so it won't collide w/ other cookies
            String proxyCookieName = doPreserveCookies ? cookie.getName() : getCookieNamePrefix(cookie.getName()) + cookie.getName();
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
        String cookies[] = cookieValue.split("[;,]");
        for (String cookie : cookies) {
            String cookieSplit[] = cookie.split("=");
            if (cookieSplit.length == 2) {
                String cookieName = cookieSplit[0].trim();
                if (cookieName.startsWith(getCookieNamePrefix(cookieName))) {
                    cookieName = cookieName.substring(getCookieNamePrefix(cookieName).length());
                    if (escapedCookie.length() > 0) {
                        escapedCookie.append("; ");
                    }
                    escapedCookie.append(cookieName).append("=").append(cookieSplit[1].trim());
                }
            }
        }
        return escapedCookie.toString();
    }

    /** The string prefixing rewritten cookies. */
    protected String getCookieNamePrefix(String name) {
        return "!Proxy!" + getServletConfig().getServletName();
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

    /** The target URI as configured. Not null. */
    public String getTargetUri() { return targetUri; }

    /**
     * Encodes characters in the query or fragment part of the URI.
     *
     * <p>Unfortunately, an incoming URI sometimes has characters disallowed by the spec.  HttpClient
     * insists that the outgoing proxied request has a valid URI because it uses Java's {@link URI}.
     * To be more forgiving, we must escape the problematic characters.  See the URI class for the
     * spec.
     *
     * @param in example: name=value&amp;foo=bar#fragment
     * @param encodePercent determine whether percent characters need to be encoded
     */
    protected static CharSequence encodeUriQuery(CharSequence in, boolean encodePercent) {
        //Note that I can't simply use URI.java to encode because it will escape pre-existing escaped things.
        StringBuilder outBuf = null;
        Formatter formatter = null;
        for(int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            boolean escape = true;
            if (c < 128) {
                if (asciiQueryChars.get((int)c) && !(encodePercent && c == '%')) {
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
                    outBuf = new StringBuilder(in.length() + 5*3);
                    outBuf.append(in,0,i);
                    formatter = new Formatter(outBuf);
                }
                //leading %, 0 padded, width 2, capital hex
                formatter.format("%%%02X",(int)c);//TODO
            }
        }
        return outBuf != null ? outBuf : in;
    }

    protected static final BitSet asciiQueryChars;
    static {
        char[] c_unreserved = "_-!.~'()*".toCharArray();//plus alphanum
        char[] c_punct = ",;:$&+=".toCharArray();
        char[] c_reserved = "?/[]@".toCharArray();//plus punct

        asciiQueryChars = new BitSet(128);
        for(char c = 'a'; c <= 'z'; c++) asciiQueryChars.set((int)c);
        for(char c = 'A'; c <= 'Z'; c++) asciiQueryChars.set((int)c);
        for(char c = '0'; c <= '9'; c++) asciiQueryChars.set((int)c);
        for(char c : c_unreserved) asciiQueryChars.set((int)c);
        for(char c : c_punct) asciiQueryChars.set((int)c);
        for(char c : c_reserved) asciiQueryChars.set((int)c);

        asciiQueryChars.set((int)'%');//leave existing percent escapes in place
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

        if (service.isUnique()) {
            return contextPathPrefix + serviceName;
        } else {

            String targetHost = proxyManagerService.extractHostFromPathInfo(servletRequest.getPathInfo());
            return contextPathPrefix + serviceName + "/" + targetHost.replaceAll("\\.", "-");
        }
    }

    protected HttpClient createHttpClient() {
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setDefaultRequestConfig(buildRequestConfig())
                .setDefaultSocketConfig(buildSocketConfig());

        clientBuilder.setMaxConnTotal(maxConnections);

        clientBuilder.setMaxConnPerRoute(2);

        if (useSystemProperties)
            clientBuilder = clientBuilder.useSystemProperties();
        return clientBuilder.build();
    }

    protected HttpResponse doExecute(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
                                     HttpRequest proxyRequest) throws IOException {
        if (doLog) {
            log("proxy " + servletRequest.getMethod() + " uri: " + servletRequest.getRequestURI() + " -- " +
                    proxyRequest.getRequestLine().getUri());
        }

        //logger.info ("--> " + servletRequest.getRequestURI());
//        if (servletRequest.getRequestURI().equals("/flink-app-master/config")) {
//
//        }

        HttpResponse response = getHttpClient(servletRequest.getSession()).execute(getTargetHost(servletRequest), proxyRequest);

        //logger.info ("<-- " + servletRequest.getRequestURI());

        return response;
    }

    @Override
    protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
            throws ServletException, IOException {

        try {

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
            HttpRequest proxyRequest;
            //spec: RFC 2616, sec 4.3: either of these two headers signal that there is a message body.
            if (servletRequest.getHeader(HttpHeaders.CONTENT_LENGTH) != null ||
                    servletRequest.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
                proxyRequest = newProxyRequestWithEntity(method, proxyRequestUri, servletRequest);
            } else {
                proxyRequest = new BasicHttpRequest(method, proxyRequestUri);
            }

            copyRequestHeaders(servletRequest, proxyRequest);

            setXForwardedForHeader(servletRequest, proxyRequest);

            HttpResponse proxyResponse = null;
            try {
                // Execute the request
                proxyResponse = doExecute(servletRequest, servletResponse, proxyRequest);

                // Process the response:

                // Pass the response code. This method with the "reason phrase" is deprecated but it's the
                //   only way to pass the reason along too.
                int statusCode = proxyResponse.getStatusLine().getStatusCode();
                //noinspection deprecation
                servletResponse.setStatus(statusCode, proxyResponse.getStatusLine().getReasonPhrase());

                // Copying response headers to make sure SESSIONID or other Cookie which comes from the remote
                // server will be saved in client when the proxied url was redirected to another one.
                // See issue [#51](https://github.com/mitre/HTTP-Proxy-Servlet/issues/51)
                copyResponseHeaders(proxyResponse, servletRequest, servletResponse);

                /* ESKIMO
                if (statusCode == HttpServletResponse.SC_NOT_MODIFIED) {
                    // 304 needs special handling.  See:
                    // http://www.ics.uci.edu/pub/ietf/http/rfc1945.html#Code304
                    // Don't send body entity/content!
                    servletResponse.setIntHeader(HttpHeaders.CONTENT_LENGTH, 0);
                } else {


                 */
                    // Send the content to the client
                    copyResponseEntity(proxyResponse, servletResponse, proxyRequest, servletRequest);

                //}

            } catch (Exception e) {
                handleRequestException(proxyRequest, e);
            } finally {
                // make sure the entire entity was consumed, so the connection is released
                if (proxyResponse != null)
                    EntityUtils.consumeQuietly(proxyResponse.getEntity());
                //Note: Don't need to close servlet outputStream:
                // http://stackoverflow.com/questions/1159168/should-one-call-close-on-httpservletresponse-getoutputstream-getwriter
            }

        } catch (IllegalStateException | SocketException | NoHttpResponseException e) {
            logger.error (servletRequest.getRequestURI() + " - got " + e.getClass() + ":" + e.getMessage());
            servletResponse.sendError(500);
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
                pathInfo = pathInfo.substring(pathInfo.indexOf('/'));
            }

            // getPathInfo() returns decoded string, so we need encodeUriQuery to encode "%" characters
            uri.append(encodeUriQuery(pathInfo, true));
        }
        return uri;
    }

    protected void initTarget() throws ServletException {
        // do nothing
    }

    protected HttpRequest newProxyRequestWithEntity(String method, String proxyRequestUri,
                                                    HttpServletRequest servletRequest) throws IOException {

        HttpEntityEnclosingRequest eProxyRequest = new BasicHttpEntityEnclosingRequest(method, proxyRequestUri);

        if ("application/x-www-form-urlencoded".equals(servletRequest.getContentType()) || getContentLengthOverride(servletRequest) == 0){
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

    /** Copy response body data (the entity) from the proxy to the servlet client. */
    protected void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponse servletResponse,
                                      HttpRequest proxyRequest, HttpServletRequest servletRequest)
            throws IOException {

        String serviceName = getServiceName(servletRequest);
        Service service = servicesDefinition.getService(serviceName);

        HttpEntity entity = proxyResponse.getEntity();

        String contextPathPrefix = getContextPath ();

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

            String inputString = StreamUtils.getAsString(entity.getContent());

            String resultString = performReplacements(service, servletRequest.getRequestURI(), contextPathPrefix, prefixPath, inputString);

            byte[] result = resultString.getBytes();

            // overwrite content length header
            servletResponse.setIntHeader(HttpHeaders.CONTENT_LENGTH, result.length);

            OutputStream servletOutputStream = servletResponse.getOutputStream();
            StreamUtils.copy(new ByteArrayInputStream(result), servletOutputStream);
        }
    }

    String performReplacements(Service service, String requestURI, String contextPath, String prefixPath, String input) {
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
            if (config == null) {
                throw new IllegalStateException("Asked for procy for service " + key + " - but none has been configured !");
            }
            input = input.replaceAll(config.getRemoteAddress()+":"+config.getLocalPort(), "/" + key);
        }

        return input;
    }

    /** Copy proxied response headers back to the servlet client. */
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

    /**
     * Reads the request URI from {@code servletRequest} and rewrites it, considering targetUri.
     * It's used to make the new request.
     */
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

    protected String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl) {

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
