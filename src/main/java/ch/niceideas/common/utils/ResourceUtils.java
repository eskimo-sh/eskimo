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

import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public abstract class ResourceUtils {

    private static final Logger logger = Logger.getLogger(ResourceUtils.class);

    /** Pseudo URL prefix for loading from the class path: "classpath:" */
    public static final String CLASSPATH_URL_PREFIX = "classpath:";

    /** URL protocol for a file in the file system: "file" */
    public static final String URL_PROTOCOL_FILE = "file";

    /** URL protocol for an entry from a jar file: "jar" */
    public static final String URL_PROTOCOL_JAR = "jar";

    public static final String RESOURCE_LOCATION_MUST_NOT_BE_NULL = "Resource location must not be null";
    public static final String UNRESOLVABLE_PATH_ERROR = " cannot be resolved to absolute file path because it does not reside in the file system";

    private ResourceUtils() {}

    /**
     * Return whether the given resource location is a URL: either a special "classpath" pseudo URL or a standard
     * URL.
     * 
     * @param resourceLocation the location String to check
     * @return whether the location qualifies as a URL
     * @see #CLASSPATH_URL_PREFIX
     * @see URL
     */
    public static boolean isUrl(String resourceLocation) {
        if (resourceLocation == null) {
            return false;
        }
        if (resourceLocation.startsWith(CLASSPATH_URL_PREFIX)) {
            return true;
        }
        try {
            new URL(resourceLocation);
            return true;
        } catch (MalformedURLException ex) {
            return false;
        }
    }

    /**
     * Resolve the given resource location to a <code>java.net.URL</code>.
     * <p>
     * Does not check whether the URL actually exists; simply returns the URL that the given location would
     * correspond to.
     * 
     * @param resourceLocation the resource location to resolve: either a "classpath:" pseudo URL, a "file:" URL, or
     *            a plain file path
     * @return a corresponding URL object
     * @throws FileNotFoundException if the resource cannot be resolved to a URL
     */
    public static URL getURL(String resourceLocation) throws FileNotFoundException {
        if (resourceLocation == null) {
            throw new IllegalArgumentException(RESOURCE_LOCATION_MUST_NOT_BE_NULL);
        }
        if (resourceLocation.startsWith(CLASSPATH_URL_PREFIX)) {
            String path = resourceLocation.substring(CLASSPATH_URL_PREFIX.length());
            URL url = ClassLoader.getSystemClassLoader().getResource(path);
            if (url == null) {
                url = ResourceUtils.class.getClassLoader().getResource(path);
                if (url == null) {
                    url = Thread.currentThread().getContextClassLoader().getResource(path);

                    if (url == null) {
                        throw new FileNotFoundException("class path resource [" + path + "]  cannot be resolved to URL because it does not exist");
                    }
                }
            }
            return url;
        }
        try {
            // try URL
            return new URL(resourceLocation);
        } catch (MalformedURLException ex) {
            // no URL -> treat as file path
            try {
                return new File(resourceLocation).toURI().toURL();
            } catch (MalformedURLException ex2) {
                throw new FileNotFoundException("Resource location [" + resourceLocation + "] is neither a URL not a well-formed file path");
            }
        }
    }

    /**
     * Resolve the given resource location to a <code>java.io.File</code>, i.e. to a file in the file system.
     * <p>
     * Does not check whether the fil actually exists; simply returns the File that the given location would
     * correspond to.
     * 
     * @param resourceLocation the resource location to resolve: either a "classpath:" pseudo URL, a "file:" URL, or
     *            a plain file path
     * @return a corresponding File object
     * @throws FileNotFoundException if the resource cannot be resolved to a file in the file system
     */
    public static File getFile(String resourceLocation) throws FileNotFoundException {
        return getFile (getURL(resourceLocation));
    }

    /**
     * Resolve the given resource URL to a <code>java.io.File</code>, i.e. to a file in the file system.
     * 
     * @param resourceUrl the resource URL to resolve
     * @return a corresponding File object
     * @throws FileNotFoundException if the URL cannot be resolved to a file in the file system
     */
    public static File getFile(URL resourceUrl) throws FileNotFoundException {
        if (resourceUrl == null) {
            throw new IllegalArgumentException(RESOURCE_LOCATION_MUST_NOT_BE_NULL);
        }
        
        if (URL_PROTOCOL_JAR.equals(resourceUrl.getProtocol())) {
            return new File(resourceUrl.getFile());
        }
        
        if (!URL_PROTOCOL_FILE.equals(resourceUrl.getProtocol())) {        
            throw new FileNotFoundException(resourceUrl + UNRESOLVABLE_PATH_ERROR);
        }
        try {
            return new File(toURI(resourceUrl).getSchemeSpecificPart());
        } catch (URISyntaxException ex) {
            // Fallback for URLs that are not valid URIs (should hardly ever happen).
            return new File(resourceUrl.getFile());
        }
    }


    /**
     * Search for the filenames in the various classLoader as a Resource and returns an inputStream in it should it
     * be found.
     * 
     * @param fileName the name of the file to look for.
     * @return inputstream on file
     */   
	public static InputStream getResourceAsStream(String fileName) {

        if (StringUtils.isBlank(fileName)) {
            return null;
        }
        
        InputStream inputStream = null;
        try {
            logger.debug("Trying to load configuration from file " + fileName);
            File targetFile = getFile(fileName);
            if (targetFile.exists()) {
                logger.debug(fileName + " has been found :-) Using it.");
                inputStream = new FileInputStream(targetFile);
            } else {
                logger.debug("Configuration file " + fileName + " does not exist.");
            }
        } catch (FileNotFoundException e) {            
            logger.debug(e, e);
        }
        
        if (fileName.startsWith(CLASSPATH_URL_PREFIX)) {
            fileName = fileName.substring(CLASSPATH_URL_PREFIX.length());
        }

        logger.debug("Now trying to lookup resource " + fileName + " in the classpath.");

        if (inputStream == null) {
            inputStream = FileUtils.class.getResourceAsStream(fileName);
        }

        if (inputStream == null) {
            inputStream = ResourceUtils.class.getClassLoader().getResourceAsStream(fileName);
        }

        if (inputStream == null) {
            inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
        }
        
        if (inputStream == null) {
        	logger.warn("Configuration file " + fileName + " was not found.");
        }

        return inputStream;
    }

    /**
     * Resolve the given resource URI to a <code>java.io.File</code>, i.e. to a file in the file system.
     * 
     * @param resourceUri the resource URI to resolve
     * @return a corresponding File object
     * @throws FileNotFoundException if the URL cannot be resolved to a file in the file system
     */
    public static File getFile(URI resourceUri) throws FileNotFoundException {
        if (resourceUri == null) {
            throw new IllegalArgumentException(RESOURCE_LOCATION_MUST_NOT_BE_NULL);
        }
        if (!URL_PROTOCOL_FILE.equals(resourceUri.getScheme())) {
            throw new FileNotFoundException(resourceUri + UNRESOLVABLE_PATH_ERROR);
        }
        return new File(resourceUri.getSchemeSpecificPart());
    }

    /**
     * Create a URI instance for the given URL, replacing spaces with "%20" quotes first.
     * <p>
     * Furthermore, this method works on JDK 1.4 as well, in contrast to the <code>URL.toURI()</code> method.
     * 
     * @param url the URL to convert into a URI instance
     * @return the URI instance
     * @throws URISyntaxException if the URL wasn't a valid URI
     * @see URL#toURI()
     */
    public static URI toURI(URL url) throws URISyntaxException {
        return toURI(url.toString());
    }

    /**
     * Create a URI instance for the given location String, replacing spaces with "%20" quotes first.
     * 
     * @param location the location String to convert into a URI instance
     * @return the URI instance
     * @throws URISyntaxException if the location wasn't a valid URI
     */
    public static URI toURI(String location) throws URISyntaxException {
        return new URI(StringUtils.replace(location, " ", "%20"));
    }

}
