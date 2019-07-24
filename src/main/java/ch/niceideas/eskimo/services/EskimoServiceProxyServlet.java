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

package ch.niceideas.eskimo.services;

import org.apache.http.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicNameValuePair;
import org.mitre.dsmiley.httpproxy.ProxyServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

public class EskimoServiceProxyServlet extends ProxyServlet {

    private final ProxyConfigService proxyConfig;

    public EskimoServiceProxyServlet (ProxyConfigService proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    /** Copy response body data (the entity) from the proxy to the servlet client. */
    @Override
    protected void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponse servletResponse,
                                      HttpRequest proxyRequest, HttpServletRequest servletRequest)
            throws IOException {
        HttpEntity entity = proxyResponse.getEntity();

        String serviceName = getServiceName(servletRequest);

        boolean isText = false;
        if (entity != null && entity.getContentType() != null) {
            isText = Arrays.stream(entity.getContentType().getElements())
                    .map(HeaderElement::getName)
                    .anyMatch(name -> name.contains("text") || name.contains("javascript"));
        }

        if (!isText) {
            if (entity != null) {
                OutputStream servletOutputStream = servletResponse.getOutputStream();
                entity.writeTo(servletOutputStream);
            }

        } else {
            if (entity != null) {
                OutputStream servletOutputStream = servletResponse.getOutputStream();

                BufferedWriter write = new BufferedWriter(new OutputStreamWriter(servletOutputStream));

                BufferedReader read = new BufferedReader(new InputStreamReader(entity.getContent()));
                String line = null;
                while ((line = read.readLine()) != null) {
                    line = line.replaceAll("src=\"/", "src=\"/"+ serviceName +"/");
                    line = line.replaceAll("action=\"/", "action=\"/"+ serviceName +"/");
                    line = line.replaceAll("href=\"/", "href=\"/"+ serviceName +"/");
                    line = line.replaceAll("/api/v1","/"+ serviceName +"/api/v1");
                    line = line.replaceAll("\"/static/", "\"/"+ serviceName +"/static/");
                    line = line.replaceAll("uiroot}}/history", "uiroot}}/"+ serviceName +"/history");
                    write.write(line);
                    write.write("\n");
                }

                write.close();
                read.close();
            }
        }
    }

    @Override
    protected HttpHost getTargetHost(HttpServletRequest servletRequest) {
        String serviceName = getServiceName(servletRequest);
        return proxyConfig.getServerHost(serviceName);
    }

    @Override
    protected String getTargetUri(HttpServletRequest servletRequest) {
        String serviceName = getServiceName(servletRequest);
        return proxyConfig.getServerURI(serviceName);
    }

    private String getServiceName(HttpServletRequest servletRequest) {
        String uri = servletRequest.getRequestURI();
        return uri.substring(1, uri.indexOf("/", 2));
    }

    @Override
    protected void initTarget() throws ServletException {
        // do nothing
    }

    @Override
    protected HttpRequest newProxyRequestWithEntity(String method, String proxyRequestUri,
                                                    HttpServletRequest servletRequest)
            throws IOException {
        HttpEntityEnclosingRequest eProxyRequest =
                new BasicHttpEntityEnclosingRequest(method, proxyRequestUri);
        if("application/x-www-form-urlencoded".equals(servletRequest.getContentType()) || getContentLength(servletRequest) == 0){
            List<NameValuePair> formparams = new ArrayList<NameValuePair>();
            Enumeration<String> paramNames = servletRequest.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String name = paramNames.nextElement();
                String value = servletRequest.getParameter(name);
                formparams.add(new BasicNameValuePair(name, value));
            }
            if(formparams.size() != 0){
                UrlEncodedFormEntity urlEncodedFormEntity = new UrlEncodedFormEntity(formparams, "UTF-8");
                eProxyRequest.setEntity(urlEncodedFormEntity);
            }
        }else{
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

    @Override
    protected String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl) {
        //TODO document example paths
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
            curUrl.append(servletRequest.getContextPath());
            // Servlet path starts with a / if it is not blank
            curUrl.append(servletRequest.getServletPath()).append("/");
            curUrl.append(theUrl, targetUri.length(), theUrl.length());
            return curUrl.toString();
        }
        return theUrl;
    }

}
