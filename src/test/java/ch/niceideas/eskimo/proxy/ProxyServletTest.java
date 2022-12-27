package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.test.infrastructure.HttpObjectsHelper;
import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletRunner;
import com.meterware.servletunit.ServletUnitClient;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.impl.io.DefaultClassicHttpResponseFactory;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.*;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Timeout;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ProxyServletTest {

    private static final Logger logger = Logger.getLogger(ProxyServletTest.class);

    /**
     * From Apache httpcomponents/httpclient. Note httpunit has a similar thing called PseudoServlet but it is
     * not as good since you can't even make it echo the request back.
     */
    protected LocalTestServer localTestServer;

    /**
     * From Meterware httpunit.
     */
    protected ServletRunner servletRunner;
    private ServletUnitClient sc;

    protected String targetBaseUri;
    protected String sourceBaseUri;

    protected final String servletName = ProxyServlet.class.getName();
    protected final String servletPath = "/proxyMe";

    //note: we don't include fragments:   "/p?#f","/p?#" because
    //  user agents aren't supposed to send them. HttpComponents has behaved
    //  differently on sending them vs not sending them.
    private static final String[] testUrlSuffixes = new String[]{
            "", "/pathInfo", "/pathInfo/%23%25abc", "?q=v", "/p?q=v",
            "/p?query=note:Leitbild",//colon  Issue#4
            "/p?id=p%20i", "/p%20i" // encoded space in param then in path
    };


    @BeforeEach
    public void setUp() throws Exception {
        localTestServer = new LocalTestServer(null, null);
        localTestServer.register("/targetPath*", new RequestInfoHandler());//matches /targetPath and /targetPath/blahblah
        localTestServer.start();

        servletRunner = new ServletRunner();

        Properties servletProps = new Properties();
        servletProps.setProperty("http.protocol.handle-redirects", "false");
        servletProps.setProperty(ProxyServlet.P_LOG, "true");
        servletProps.setProperty(ProxyServlet.P_FORWARDEDFOR, "true");
        setUpServlet(servletProps);

        sc = servletRunner.newClient();
        sc.getClientProperties().setAutoRedirect(false);//don't want httpunit itself to redirect

    }

    protected void setUpServlet(Properties servletProps) {
        targetBaseUri = "http://localhost:" + localTestServer.getServiceAddress().getPort() + "/targetPath";
        servletProps.setProperty("targetUri", targetBaseUri);
        servletRunner.registerServlet(servletPath + "/*", servletName, servletProps);//also matches /proxyMe (no path info)
        sourceBaseUri = "http://localhost/proxyMe";//localhost:0 is hard-coded in ServletUnitHttpRequest
    }

    @AfterEach
    public void tearDown() throws Exception {
        servletRunner.shutDown();
        localTestServer.stop();
    }

    @Test
    public void testGet() throws Exception {
        for (String urlSuffix : testUrlSuffixes) {
            execAssert(makeGetMethodRequest(sourceBaseUri + urlSuffix));
        }
    }

    @Test
    public void testPost() throws Exception {
        for (String urlSuffix : testUrlSuffixes) {
            execAndAssert(makePostMethodRequest(sourceBaseUri + urlSuffix));
        }
    }

    @Test
    public void testRedirect() throws IOException, SAXException {
        final String COOKIE_SET_HEADER = "Set-Cookie";
        localTestServer.register("/targetPath*", (request, response, context) -> {
            response.setHeader(HttpHeaders.LOCATION, request.getFirstHeader("xxTarget").getValue());
            response.setHeader(COOKIE_SET_HEADER, "JSESSIONID=1234; path=/;");
            response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
        });//matches /targetPath and /targetPath/blahblah
        GetMethodWebRequest request = makeGetMethodRequest(sourceBaseUri + "/%64%69%72%2F");
        assertRedirect(request, "/dummy", "/dummy");//TODO represents a bug to fix
        assertRedirect(request, targetBaseUri + "/dummy?a=b", sourceBaseUri + "/dummy?a=b");
        // %-encoded Redirects must be rewritten
        assertRedirect(request, targetBaseUri + "/sample%20url", sourceBaseUri + "/sample%20url");
        assertRedirect(request, targetBaseUri + "/sample%20url?a=b", sourceBaseUri + "/sample%20url?a=b");
        assertRedirect(request, targetBaseUri + "/sample%20url?a=b#frag", sourceBaseUri + "/sample%20url?a=b#frag");
        assertRedirect(request, targetBaseUri + "/sample+url", sourceBaseUri + "/sample+url");
        assertRedirect(request, targetBaseUri + "/sample+url?a=b", sourceBaseUri + "/sample+url?a=b");
        assertRedirect(request, targetBaseUri + "/sample+url?a=b#frag", sourceBaseUri + "/sample+url?a=b#frag");
        assertRedirect(request, targetBaseUri + "/sample+url?a+b=b%20c#frag%23", sourceBaseUri + "/sample+url?a+b=b%20c#frag%23");
        // Absolute redirects to 3rd parties must pass-through unchanged
        assertRedirect(request, "http://blackhole.org/dir/file.ext?a=b#c", "http://blackhole.org/dir/file.ext?a=b#c");
    }

    private void assertRedirect(GetMethodWebRequest request, String origRedirect, String resultRedirect) throws IOException, SAXException {
        request.setHeaderField("xxTarget", origRedirect);
        WebResponse rsp = sc.getResponse(request);

        assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, rsp.getResponseCode());
        assertEquals("", rsp.getText());
        String gotLocation = rsp.getHeaderField(HttpHeaders.LOCATION);
        assertEquals(resultRedirect, gotLocation);
        assertTrue(rsp.getHeaderField("Set-Cookie").startsWith("!Proxy!" + servletName + "JSESSIONID=1234;path=" + servletPath));
    }

    @Test
    public void testSendFile() throws Exception {
        //TODO test with url parameters (i.e. a=b); but HttpUnit is faulty so we can't
        final PostMethodWebRequest request = new PostMethodWebRequest(
                rewriteMakeMethodUrl("http://localhost/proxyMe"), true);//true: mime encoded
        InputStream data = new ByteArrayInputStream("testFileData".getBytes("UTF-8"));
        request.selectFile("fileNameParam", "fileName", data, "text/plain");
        WebResponse rsp = execAndAssert(request);
        assertTrue(rsp.getText().contains("Content-Type: multipart/form-data; boundary="));
    }

    @Test
    public void testProxyWithUnescapedChars() throws Exception {
        execAssert(makeGetMethodRequest(sourceBaseUri + "?fq={!f=field}"), "?fq=%7B!f=field%7D");//has squiggly brackets
        execAssert(makeGetMethodRequest(sourceBaseUri + "?fq=%7B!f=field%7D"));//already escaped; don't escape twice
    }

    /**
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHopByHopHeadersOnSource() throws Exception {
        //"Proxy-Authenticate" is a hop-by-hop header
        final String HEADER = "Proxy-Authenticate";
        localTestServer.register("/targetPath*", new RequestInfoHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
                assertNull(request.getFirstHeader(HEADER));
                response.setHeader(HEADER, "from-server");
                super.handle(request, response, context);
            }
        });

        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);
        req.getHeaders().put(HEADER, "from-client");
        WebResponse rsp = execAndAssert(req, "");
        assertNull(rsp.getHeaderField(HEADER));
    }

    @Test
    public void testWithExistingXForwardedFor() throws Exception {
        final String FOR_HEADER = "X-Forwarded-For";

        localTestServer.register("/targetPath*", new RequestInfoHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
                Header xForwardedForHeader = request.getFirstHeader(FOR_HEADER);
                assertEquals("192.168.1.1, 127.0.0.1", xForwardedForHeader.getValue());
                super.handle(request, response, context);
            }
        });

        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);
        req.setHeaderField(FOR_HEADER, "192.168.1.1");
        execAndAssert(req, "");
    }

    @Test
    public void testEnabledXForwardedFor() throws Exception {
        final String FOR_HEADER = "X-Forwarded-For";
        final String PROTO_HEADER = "X-Forwarded-Proto";

        localTestServer.register("/targetPath*", new RequestInfoHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
                Header xForwardedForHeader = request.getFirstHeader(FOR_HEADER);
                Header xForwardedProtoHeader = request.getFirstHeader(PROTO_HEADER);
                assertEquals("127.0.0.1", xForwardedForHeader.getValue());
                assertEquals("http", xForwardedProtoHeader.getValue());
                super.handle(request, response, context);
            }
        });

        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);
        execAndAssert(req, "");
    }

    @Test
    public void testCopyRequestHeaderToProxyRequest() throws Exception {
        final String HEADER = "HEADER_TO_TEST";

        localTestServer.register("/targetPath*", new RequestInfoHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
                Header headerToTest = request.getFirstHeader(HEADER);
                assertEquals("VALUE_TO_TEST", headerToTest.getValue());

                super.handle(request, response, context);
            }
        });

        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);
        req.setHeaderField(HEADER, "VALUE_TO_TEST");

        execAndAssert(req, "");
    }

    @Test
    public void testCopyProxiedRequestHeadersToResponse() throws Exception {
        final String HEADER = "HEADER_TO_TEST";

        localTestServer.register("/targetPath*", new RequestInfoHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
                response.setHeader(HEADER, "VALUE_TO_TEST");
                super.handle(request, response, context);
            }
        });

        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);

        WebResponse rsp = execAndAssert(req, "");
        assertEquals("VALUE_TO_TEST", rsp.getHeaderField(HEADER));
    }

    @Test
    public void testSetCookie() throws Exception {
        final String HEADER = "Set-Cookie";
        localTestServer.register("/targetPath*", new RequestInfoHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
                response.setHeader(HEADER, "JSESSIONID=1234; Path=/proxy/path/that/we/dont/want; Expires=Wed, 13 Jan 2021 22:23:01 GMT; Domain=.foo.bar.com; HttpOnly");
                super.handle(request, response, context);
            }
        });

        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);
        WebResponse rsp = execAndAssert(req, "");
        // note httpunit doesn't set all cookie fields, ignores max-agent, secure, etc.
        assertTrue(rsp.getHeaderField(HEADER).startsWith("!Proxy!" + servletName + "JSESSIONID=1234;path=" + servletPath));
    }

    @Test
    public void testSetCookie2() throws Exception {
        final String HEADER = "Set-Cookie2";
        localTestServer.register("/targetPath*", new RequestInfoHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
                response.setHeader(HEADER, "JSESSIONID=1234; Path=/proxy/path/that/we/dont/want; Max-Age=3600; Domain=.foo.bar.com; Secure");
                super.handle(request, response, context);
            }
        });

        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);
        WebResponse rsp = execAndAssert(req, "");
        // note httpunit doesn't set all cookie fields, ignores max-agent, secure, etc.
        // also doesn't support more than one header of same name so I can't test this working on two cookies
        assertTrue(rsp.getHeaderField("Set-Cookie").startsWith("!Proxy!" + servletName + "JSESSIONID=1234;path=" + servletPath));
    }

    @Test
    public void testPreserveCookie() throws Exception {
        servletRunner = new ServletRunner();

        Properties servletProps = new Properties();
        servletProps.setProperty("http.protocol.handle-redirects", "false");
        servletProps.setProperty(ProxyServlet.P_LOG, "true");
        servletProps.setProperty(ProxyServlet.P_FORWARDEDFOR, "true");
        servletProps.setProperty(ProxyServlet.P_PRESERVECOOKIES, "true");
        setUpServlet(servletProps);

        sc = servletRunner.newClient();
        sc.getClientProperties().setAutoRedirect(false);//don't want httpunit itself to redirect

        final String HEADER = "Set-Cookie";
        localTestServer.register("/targetPath*", new RequestInfoHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
                response.setHeader(HEADER, "JSESSIONID=1234; Path=/proxy/path/that/we/dont/want; Expires=Wed, 13 Jan 2021 22:23:01 GMT; Domain=.foo.bar.com; HttpOnly");
                super.handle(request, response, context);
            }
        });

        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);
        WebResponse rsp = execAndAssert(req, "");
        // note httpunit doesn't set all cookie fields, ignores max-agent, secure, etc.
        assertTrue(rsp.getHeaderField(HEADER).startsWith("JSESSIONID=1234;path=" + servletPath));
    }

    @Test
    public void testSetCookieHttpOnly() throws Exception { //See GH #50
        final String HEADER = "Set-Cookie";
        localTestServer.register("/targetPath*", new RequestInfoHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
                response.setHeader(HEADER, "JSESSIONID=1234; Path=/proxy/path/that/we/dont/want/; HttpOnly");
                super.handle(request, response, context);
            }
        });

        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);
        WebResponse rsp = execAndAssert(req, "");
        // note httpunit doesn't set all cookie fields, ignores max-agent, secure, etc.
        assertTrue (rsp.getHeaderField(HEADER).startsWith("!Proxy!" + servletName + "JSESSIONID=1234;path=" + servletPath));
    }

    @Test
    public void testSendCookiesToProxy() throws Exception {
        final StringBuffer captureCookieValue = new StringBuffer();
        final String HEADER = "Cookie";
        localTestServer.register("/targetPath*", new RequestInfoHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
                captureCookieValue.append(request.getHeaders(HEADER)[0].getValue());
                super.handle(request, response, context);
            }
        });

        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);
        req.setHeaderField(HEADER,
                "LOCALCOOKIE=ABC; " +
                        "!Proxy!" + servletName + "JSESSIONID=1234; " +
                        "!Proxy!" + servletName + "COOKIE2=567; " +
                        "LOCALCOOKIELAST=ABCD");
        execAndAssert(req, "");
        assertEquals("JSESSIONID=1234; COOKIE2=567", captureCookieValue.toString());
    }

    /**
     * If we're proxying a remote service that tries to set cookies, we need to make sure the cookies are not captured
     * by the httpclient in the ProxyServlet, otherwise later requests from ALL users will all access the remote proxy
     * with the same cookie as the first user
     */
    @Test
    public void testMultipleRequestsWithDiffCookies() throws Exception {
        final AtomicInteger requestCounter = new AtomicInteger(1);
        final StringBuffer captureCookieValue = new StringBuffer();
        localTestServer.register("/targetPath*", new RequestInfoHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
                // there shouldn't be a cookie sent since each user request in this test is logging in for the first time
                if (request.getFirstHeader("Cookie") != null) {
                    captureCookieValue.append(request.getFirstHeader("Cookie"));
                } else {
                    response.setHeader("Set-Cookie", "JSESSIONID=USER_" + requestCounter.getAndIncrement() + "_SESSION");
                }
                super.handle(request, response, context);
            }
        });

        // user one logs in for the first time to a proxied web service
        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);
        execAndAssert(req, "");
        assertEquals("", captureCookieValue.toString());
        assertEquals("USER_1_SESSION", sc.getCookieValue("!Proxy!" + servletName + "JSESSIONID"));

        // user two logs in for the first time to a proxied web service
        sc.clearContents(); // clear httpunit cookies since we want to login as a different user
        req = makeGetMethodRequest(sourceBaseUri);
        execAndAssert(req, "");
        assertEquals("", captureCookieValue.toString());
        assertEquals("USER_2_SESSION", sc.getCookieValue("!Proxy!" + servletName + "JSESSIONID"));
    }

    @Test
    public void testRedirectWithBody() throws Exception {
        final String CONTENT = "-This-Shall-Not-Pass-";
        localTestServer.register("/targetPath/test", (request, response, context) -> {
            // Redirect to the requested URL with / appended
            response.setHeader(HttpHeaders.LOCATION, targetBaseUri + "/test/");
            response.setCode(HttpStatus.SC_MOVED_TEMPORARILY);
            response.setHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
            // Set body of the response. We need it not-empty for the test.
            response.setEntity(new ByteArrayEntity(CONTENT.getBytes(StandardCharsets.UTF_8), ContentType.create("text/plain")));
        });
        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri + "/test");
        // We expect a redirect with a / at the end
        WebResponse rsp = sc.getResponse(req);

        // Expect the same status code as the handler.
        assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, rsp.getResponseCode());
        // Expect exact Content-Length
        assertEquals(String.valueOf(CONTENT.length()), rsp.getHeaderField(HttpHeaders.CONTENT_LENGTH));
        // Expect exact response
        assertEquals(CONTENT, rsp.getText());
        assertEquals(sourceBaseUri + "/test/", rsp.getHeaderField(HttpHeaders.LOCATION));
    }

    @Test
    public void testPreserveHost() throws Exception {
        servletRunner = new ServletRunner();

        Properties servletProps = new Properties();
        servletProps.setProperty("http.protocol.handle-redirects", "false");
        servletProps.setProperty(ProxyServlet.P_LOG, "true");
        servletProps.setProperty(ProxyServlet.P_FORWARDEDFOR, "true");
        servletProps.setProperty(ProxyServlet.P_PRESERVEHOST, "true");
        setUpServlet(servletProps);

        sc = servletRunner.newClient();
        sc.getClientProperties().setAutoRedirect(false);//don't want httpunit itself to redirect

        final String HEADER = "Host";
        final String[] proxyHost = new String[1];
        localTestServer.register("/targetPath*", new RequestInfoHandler() {
            @Override
            public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
                proxyHost[0] = request.getHeaders(HEADER)[0].getValue();
                super.handle(request, response, context);
            }
        });

        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);
        req.setHeaderField(HEADER, "SomeHost");
        execAndAssert(req, "");
        assertEquals("SomeHost", proxyHost[0]);
    }

    @Test
    public void testUseSystemProperties() throws Exception {
        System.setProperty("http.proxyHost", "foo.blah.nonexisting.dns.name");
        servletRunner = new ServletRunner();

        Properties servletProps = new Properties();
        servletProps.setProperty(ProxyServlet.P_LOG, "true");
        servletProps.setProperty(ProxyServlet.P_USESYSTEMPROPERTIES, "true");
        // Must use a non-local URL because localhost is in http.nonProxyHosts by default.
        targetBaseUri = "http://www.google.com";
        servletProps.setProperty(ProxyServlet.P_TARGET_URI, targetBaseUri);
        servletRunner.registerServlet(servletPath + "/*", servletName, servletProps);

        sc = servletRunner.newClient();
        sc.getClientProperties().setAutoRedirect(false);//don't want httpunit itself to redirect

        GetMethodWebRequest req = makeGetMethodRequest(sourceBaseUri);
        try {
            execAssert(req);
            fail("UnknownHostException expected.");
        } catch (UnknownHostException e) {
            // Expected assuming that our proxy host defined above does not exist.
        } finally {
            System.clearProperty("http.proxyHost");
        }
    }

    private WebResponse execAssert(GetMethodWebRequest request, String expectedUri) throws Exception {
        return execAndAssert(request, expectedUri);
    }

    private WebResponse execAssert(GetMethodWebRequest request) throws Exception {
        return execAndAssert(request, null);
    }

    private WebResponse execAndAssert(PostMethodWebRequest request) throws Exception {
        request.setParameter("abc", "ABC");

        WebResponse rsp = execAndAssert(request, null);

        assertTrue(rsp.getText().contains("ABC"));
        return rsp;
    }

    protected WebResponse execAndAssert(WebRequest request, String expectedUri) throws Exception {
        WebResponse rsp = sc.getResponse(request);

        assertEquals(HttpStatus.SC_OK, rsp.getResponseCode());
        //HttpUnit doesn't pass the message; not a big deal
        //assertEquals("TESTREASON",rsp.getResponseMessage());
        final String text = rsp.getText();
        assertTrue(text.startsWith("REQUESTLINE:"));

        String expectedTargetUri = getExpectedTargetUri(request, expectedUri);
        String expectedFirstLine = "REQUESTLINE: " + (request instanceof GetMethodWebRequest ? "GET" : "POST");
        expectedFirstLine += " " + expectedTargetUri + " HTTP/1.1";

        String firstTextLine = text.substring(0, text.indexOf(System.getProperty("line.separator")));

        assertEquals(expectedFirstLine, firstTextLine);

        // Assert all headers are present, and therefore checks the case has been preserved (see GH #65)
        Dictionary<?, ?> headers = request.getHeaders();
        Enumeration<?> headerNameEnum = headers.keys();
        while (headerNameEnum.hasMoreElements()) {
            String headerName = (String) headerNameEnum.nextElement();
            assertTrue(text.contains(headerName));
        }

        return rsp;
    }

    protected String getExpectedTargetUri(WebRequest request, String expectedUri) throws MalformedURLException, URISyntaxException {
        if (expectedUri == null)
            expectedUri = request.getURL().toString().substring(sourceBaseUri.length());
        return new URI(this.targetBaseUri).getPath() + expectedUri;
    }

    protected GetMethodWebRequest makeGetMethodRequest(final String url) {
        return makeMethodRequest(url, GetMethodWebRequest.class);
    }

    private PostMethodWebRequest makePostMethodRequest(final String url) {
        return makeMethodRequest(url, PostMethodWebRequest.class);
    }

    //Fixes problems in HttpUnit in which I can't specify the query string via the url. I don't want to use
    // setParam on a get request.
    @SuppressWarnings({"unchecked"})
    private <M> M makeMethodRequest(String incomingUrl, Class<M> clazz) {
        logger.info("Making request to url " + incomingUrl);
        final String url = rewriteMakeMethodUrl(incomingUrl);
        String urlNoQuery;
        final String queryString;
        int qIdx = url.indexOf('?');
        if (qIdx == -1) {
            urlNoQuery = url;
            queryString = null;
        } else {
            urlNoQuery = url.substring(0, qIdx);
            queryString = url.substring(qIdx + 1);

        }
        //WARNING: Ugly! Groovy could do this better.
        if (clazz == PostMethodWebRequest.class) {
            return (M) new PostMethodWebRequest(urlNoQuery) {
                @Override
                public String getQueryString() {
                    return queryString;
                }

                @Override
                protected String getURLString() {
                    return url;
                }
            };
        } else if (clazz == GetMethodWebRequest.class) {
            return (M) new GetMethodWebRequest(urlNoQuery) {
                @Override
                public String getQueryString() {
                    return queryString;
                }

                @Override
                protected String getURLString() {
                    return url;
                }
            };
        }
        throw new IllegalArgumentException(clazz.toString());
    }

    //subclass extended
    protected String rewriteMakeMethodUrl(String url) {
        return url;
    }

    /**
     * Writes all information about the request back to the response.
     */
    protected static class RequestInfoHandler implements HttpRequestHandler {

        public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintWriter pw = new PrintWriter(baos, false);
            /*
            request.getRequestLine();
            */
            final String rl = request.getMethod() + " " + request.getRequestUri() + " " + request.getVersion();
            pw.println("REQUESTLINE: " + rl);

            for (Header header : request.getHeaders()) {
                pw.println(header.getName() + ": " + header.getValue());
            }
            pw.println("BODY: (below)");
            pw.flush();//done with pw now

            HttpEntity entity = request.getEntity();
            if (entity != null) {
                byte[] body = EntityUtils.toByteArray(entity);
                baos.write(body);
            }

            response.setEntity(new ByteArrayEntity(baos.toByteArray(), ContentType.create("text/plain")));

            response.setCode(200);
            response.setReasonPhrase("TESTREASON");
        }
    }

    protected static class LocalTestServer {

        public final static String ORIGIN = "LocalTestServer/1.1";

        /**
         * The local address to bind to.
         * The host is an IP number rather than "localhost" to avoid surprises
         * on hosts that map "localhost" to an IPv6 address or something else.
         * The port is 0 to let the system pick one.
         */
        public final static InetSocketAddress TEST_SERVER_ADDR =
                new InetSocketAddress("127.0.0.1", 0);

        /** The request handler registry. */
        private final RequestHandlerRegistry<HttpRequestHandler> handlerRegistry;

        private final HttpService httpservice;

        /** Optional SSL context */
        private final SSLContext sslcontext;

        /** Optional flag whether to force SSL context */
        private final boolean forceSSLAuth;

        /** Optional set of enabled SSL/TLS protocols */
        private final String[] enabledProtocols;

        /** The server socket, while being served. */
        private volatile ServerSocket servicedSocket;

        /** The request listening thread, while listening. */
        private volatile ListenerThread listenerThread;

        /** Set of active worker threads */
        private final Set<Worker> workers;

        /** The number of connections this accepted. */
        private final AtomicInteger acceptedConnections = new AtomicInteger(0);

        private volatile int timeout;

        @SuppressWarnings("unchecked")
        public LocalTestServer(
                final HttpProcessor proc,
                final ConnectionReuseStrategy reuseStrat,
                final HttpResponseFactory<ClassicHttpResponse> responseFactory,
                final SSLContext sslcontext,
                final boolean forceSSLAuth,
                final String[] enabledProtocols) {
            super();
            this.handlerRegistry = new RequestHandlerRegistry<>();
            this.workers = Collections.synchronizedSet(new HashSet<>());
            this.httpservice = new HttpService(
                    proc != null ? proc : newProcessor(),
                    handlerRegistry,
                    reuseStrat != null ? reuseStrat: newConnectionReuseStrategy(),
                    responseFactory != null ? responseFactory: newHttpResponseFactory());
            this.sslcontext = sslcontext;
            this.forceSSLAuth = forceSSLAuth;
            this.enabledProtocols = enabledProtocols;
        }

        public LocalTestServer(
                final HttpProcessor proc,
                final ConnectionReuseStrategy reuseStrat) {
            this(proc, reuseStrat, null, null, false, null);
        }

        /**
         * Obtains an HTTP protocol processor with default interceptors.
         *
         * @return  a protocol processor for server-side use
         */
        protected HttpProcessor newProcessor() {
            return new DefaultHttpProcessor(
                    new ResponseDate(),
                    new ResponseServer(ORIGIN),
                    new ResponseContent(),
                    new ResponseConnControl());
        }

        protected ConnectionReuseStrategy newConnectionReuseStrategy() {
            return DefaultConnectionReuseStrategy.INSTANCE;
        }

        @SuppressWarnings("unchecked")
        protected HttpResponseFactory newHttpResponseFactory() {
            return DefaultClassicHttpResponseFactory.INSTANCE;
        }


        /**
         * Registers a handler with the local registry.
         *
         * @param pattern   the URL pattern to match
         * @param handler   the handler to apply
         */
        public void register(final String pattern, final HttpRequestHandler handler) {
            handlerRegistry.register(null, pattern, handler);
        }

        /**
         * Starts this test server.
         */
        public void start() throws Exception {
            Asserts.check(servicedSocket == null, "Already running");
            final ServerSocket ssock;
            if (sslcontext != null) {
                final SSLServerSocketFactory sf = sslcontext.getServerSocketFactory();
                final SSLServerSocket sslsock = (SSLServerSocket) sf.createServerSocket();
                if (forceSSLAuth) {
                    sslsock.setNeedClientAuth(true);
                } else {
                    sslsock.setWantClientAuth(true);
                }
                if (enabledProtocols != null) {
                    sslsock.setEnabledProtocols(enabledProtocols);
                }
                ssock = sslsock;
            } else {
                ssock = new ServerSocket();
            }

            ssock.setReuseAddress(true); // probably pointless for port '0'
            ssock.bind(TEST_SERVER_ADDR);
            servicedSocket = ssock;

            listenerThread = new ListenerThread();
            listenerThread.setDaemon(false);
            listenerThread.start();
        }

        /**
         * Stops this test server.
         */
        public void stop() throws Exception {
            if (servicedSocket == null) {
                return; // not running
            }
            final ListenerThread t = listenerThread;
            if (t != null) {
                t.shutdown();
            }
            synchronized (workers) {
                for (final Worker worker : workers) {
                    worker.shutdown();
                }
            }
        }

        public void awaitTermination(final long timeMs) throws InterruptedException {
            if (listenerThread != null) {
                listenerThread.join(timeMs);
            }
        }

        /**
         * Obtains the local address the server is listening on
         *
         * @return the service address
         */
        public InetSocketAddress getServiceAddress() {
            final ServerSocket ssock = servicedSocket; // avoid synchronization
            Asserts.notNull(ssock, "Not running");
            return (InetSocketAddress) ssock.getLocalSocketAddress();
        }

        /**
         * Creates an instance of {@link DefaultBHttpServerConnection} to be used
         * in the Worker thread.
         * <p>
         * This method can be overridden in a super class in order to provide
         * a different implementation of the {@link DefaultBHttpServerConnection}.
         *
         * @return DefaultBHttpServerConnection.
         */
        protected DefaultBHttpServerConnection createHttpServerConnection() {
            return new DefaultBHttpServerConnection("http", Http1Config.DEFAULT);
        }

        /**
         * The request listener.
         * Accepts incoming connections and launches a service thread.
         */
        class ListenerThread extends Thread {

            private volatile Exception exception;

            ListenerThread() {
                super();
            }

            @Override
            public void run() {
                try {
                    while (!interrupted()) {
                        final Socket socket = servicedSocket.accept();
                        acceptedConnections.incrementAndGet();
                        final DefaultBHttpServerConnection conn = createHttpServerConnection();
                        conn.bind(socket);
                        conn.setSocketTimeout(Timeout.ofMilliseconds(timeout));
                        // Start worker thread
                        final Worker worker = new Worker(conn);
                        workers.add(worker);
                        worker.setDaemon(true);
                        worker.start();
                    }
                } catch (final Exception ex) {
                    this.exception = ex;
                } finally {
                    try {
                        servicedSocket.close();
                    } catch (final IOException ignore) {
                    }
                }
            }

            public void shutdown() {
                interrupt();
                try {
                    servicedSocket.close();
                } catch (final IOException ignore) {
                }
            }

            public Exception getException() {
                return this.exception;
            }

        }

        class Worker extends Thread {

            private final HttpServerConnection conn;

            private volatile Exception exception;

            public Worker(final HttpServerConnection conn) {
                this.conn = conn;
            }

            @Override
            public void run() {
                logger.info ("Running worker");
                final HttpContext context = new BasicHttpContext();
                try {
                    while (this.conn.isOpen() && !Thread.interrupted()) {
                        httpservice.handleRequest(this.conn, context);
                    }
                } catch (final Exception ex) {
                    logger.error (ex, ex);
                    this.exception = ex;
                } finally {
                    workers.remove(this);
                    try {
                        this.conn.close();;
                    } catch (final IOException ignore) {
                    }
                }
            }

            public void shutdown() {
                interrupt();
                try {
                    this.conn.close();
                } catch (final IOException ignore) {
                }
            }

            public Exception getException() {
                return this.exception;
            }

        }
    }

    @Test
    public void testRewriteUrlFromRequest() {
        // ensure no double slashes situation
        String test = new ProxyServlet().rewriteUrlFromRequest(HttpObjectsHelper.createHttpServletRequest("cerebro"));
        assertEquals("http://localhost:9090/cerebro/statistics?server=192.168.10.13", test);
    }
}