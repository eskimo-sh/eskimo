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

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * This class defines additional string functions of config use
 */
public abstract class StringUtils {

    private static final Logger logger = Logger.getLogger(StringUtils.class);
    
    private static final Pattern INTEGER_VALUE = Pattern.compile(" *[\\-+]? *[0-9]+(\\.(0)*)?");
    private static final Pattern NUMERIC_VALUE = Pattern.compile(" *[\\-+]? *[0-9]+( *\\.[0-9]+)?( *[Ee][0-9]+)?");

    private static final char[] PHRASE_ENDING_CHARS = { '.', '!', '?' };

    public static final String DEFAULT_ENCODING = "ISO-8859-1";

    private static final int LARGEST_FIELD_LENGTH = 100;

    private static final String[] SPACES = buildSpaceArray(LARGEST_FIELD_LENGTH);
    
    static final byte[] HEX_CHAR_TABLE = {
        (byte)'0', (byte)'1', (byte)'2', (byte)'3',
        (byte)'4', (byte)'5', (byte)'6', (byte)'7',
        (byte)'8', (byte)'9', (byte)'a', (byte)'b',
        (byte)'c', (byte)'d', (byte)'e', (byte)'f'
      }; 

    private StringUtils() {}

    public static String getHexString(byte[] raw) {
        byte[] hex = new byte[2 * raw.length];
        int index = 0;
    
        for (byte b : raw) {
          int v = b & 0xFF;
          hex[index++] = HEX_CHAR_TABLE[v >>> 4];
          hex[index++] = HEX_CHAR_TABLE[v & 0xF];
        }
        return new String(hex,  StandardCharsets.US_ASCII);
    }
    
    private static String[] buildSpaceArray(int n) {
        String[] result = new String[n + 1];
        result[0] = "";
        for (int i = 1; i < result.length; i++) {
            result[i] = result[i - 1] + " ";
        }
        return result;
    }

    /** A table of hex digits */
    private static final char[] hexDigit = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    /**
     * Convert a nibble to a hex character
     * 
     * @param nibble the nibble to convert.
     */
    public static char toHex(int nibble) {
        return hexDigit[(nibble & 0xF)];
    }

    /**
     * Returns the last index of any of the given chars in the given source.
     * <p>
     * 
     * If no char is found, -1 is returned.
     * <p>
     * 
     * @param source the source to check
     * @param chars the chars to find
     * 
     * @return the last index of any of the given chars in the given source, or -1
     */
    public static int lastIndexOf(String source, char[] chars) {

        // now try to find an "sentence ending" char in the text in the "findPointArea"
        int result = -1;
        for (char c : chars) {
            int pos = source.lastIndexOf(c);
            if (pos > result) {
                // found new last char
                result = pos;
            }
        }
        return result;
    }

    public static boolean isNumericValue(Object value) {        
        if (value == null) {
            return false;
        }
        
        Matcher matcher = NUMERIC_VALUE.matcher(value.toString());
        return matcher.matches();        
    }

    public static boolean isIntegerValue(Object value) {        
        if (value == null) {
            return false;
        }
        
        Matcher matcher = INTEGER_VALUE.matcher(value.toString());
        return matcher.matches();        
    }

    /**
     * Convert a String into something more usable for URLs
     * 
     * @param source the source string to encode
     * @return the URL encoded version
     */
    public static String urlEncode(String source) {
        String retValue;
        try {
            retValue = URLEncoder.encode(source, DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            logger.error(e, e);
            retValue = source;
        }
        return retValue;
    }

    /**
     * De-convert a String from something more usable for URLs
     * 
     * @param source the source string to encode
     * @return the URL encoded version
     */
    public static String urlDecode(String source) {
        String retValue;
        try {
            retValue = URLDecoder.decode(source, DEFAULT_ENCODING);
        } catch (UnsupportedEncodingException e) {
            logger.error(e, e);
            retValue = source;
        }
        return retValue;
    }

    /**
     * Put the first letter to lower case in the string given as argument and return the new string.
     *
     * @return Same as propertyName but with first letter lower case.
     */
    public static String firstLetterLowerCase(String original) {
        char[] charArray = original.toCharArray();
        charArray[0] = String.valueOf(charArray[0]).toLowerCase().charAt(0);
        return String.valueOf(charArray);
    }

    /**
     * Put the first letter to upper case in the string given as argument and return the new string.
     *
     * @return Same as propertyName but with first letter upper case.
     */
    public static String firstLetterUpperCase(String original) {
        char[] charArray = original.toCharArray();
        charArray[0] = String.valueOf(charArray[0]).toUpperCase().charAt(0);
        return String.valueOf(charArray);
    }

    /**
     * Get rid of the space at the tail of the string, should there be any.
     * 
     * @param original the string to be trimmed.
     * @return the trimmed value
     */
    public static String trimTrailingSpaces(String original) {
        if (original == null) {
            return null;
        }
        char[] array = original.toCharArray();
        return trimTrailingSpaces(array);
    }

    /**
     * Get rid of the space at the tail of the string, should there be any.
     * 
     * @param stringCharArray The char array representing the string to be trimmed.
     * @return the trimmed value
     */
    public static String trimTrailingSpaces(char[] stringCharArray) {
        int len = stringCharArray.length;
        while (len > 0 && (stringCharArray[len - 1] <= ' ')) {
            len--;
        }
        return new String(stringCharArray, 0, len);
    }

    /**
     * Conevrts the string given as argument in a BigDecimal value. If any error is encountered during the
     * conversion, defaultValue is returned as well.
     * 
     * @param stringValue The string to convert in a BigDecimal.
     * @param defautValue The default value to be returned insted if the conversion fails
     * @return The resulting BigDecimal value
     */
    public static BigDecimal toBigDecimal(String stringValue, BigDecimal defautValue) {
        if (isNotEmpty(stringValue)) {
            try {
                return new BigDecimal(stringValue.trim());
            } catch (NumberFormatException e) {
                logger.debug (e, e);
            }
        }
        return defautValue;
    }

    private static String handlePadEdgeCases(String value, int size) {
        if (isEmpty(value)) {
            return SPACES[size];
        }
        if (value.length() == size) {
            return value;
        }
        if (value.length() > size) {
            return value.substring(0, size);
        }
        return null;
    }

    /**
     * Pad the given value with the amount of spaces on the right.
     */
    public static String padRight(String value, int size) {
        String edgeValue = handlePadEdgeCases(value, size);
        if (StringUtils.isNotBlank(edgeValue)) {
            return edgeValue;
        }
        return value + SPACES[size - value.length()];
    }

    /**
     * Pad the given value with the amount of spaces on the left.
     */
    public static String padLeft(String value, int size) {
        String edgeValue = handlePadEdgeCases(value, size);
        if (StringUtils.isNotBlank(edgeValue)) {
            return edgeValue;
        }
        return SPACES[size - value.length()] + value;
    }

    /**
     * Returns the java String literal for the given String.
     * <p>
     * 
     * This is the form of the String that had to be written into source code using the unicode escape sequence for
     * special characters.
     * <p>
     * 
     * Example: "Ä" would be transformed to "\\u00C4".
     * <p>
     * 
     * @param s a string that may contain non-ascii characters
     * 
     * @return the java unicode escaped string Literal of the given input string
     */
    public static String toUnicodeLiteral(String s) {

        StringBuilder result = new StringBuilder();
        char[] carr = s.toCharArray();

        String unicode;
        for (char element : carr) {
            result.append("\\u");
            // append leading zeros
            unicode = Integer.toHexString(element).toUpperCase();
            result.append("0".repeat(Math.max(0, 4 - unicode.length())));
            result.append(unicode);
        }
        return result.toString();
    }

    /**
     * Returns a substring of the source, which is at most length characters long.
     * <p>
     * 
     * This is the same as calling {@link #trimToSize(String, int, String)} with the parameters
     * <code>(source, length, " ...")</code>.
     * <p>
     * 
     * @param source the string to trim
     * @param length the maximum length of the string to be returned
     * 
     * @return a substring of the source, which is at most length characters long
     */
    public static String trimToSize(String source, int length) {

        return trimToSize(source, length, length, " ...");
    }

    /**
     * Returns a substring of the source, which is at most length characters long.
     * <p>
     * 
     * If a char is cut, the given <code>suffix</code> is appended to the result.
     * <p>
     * 
     * This is almost the same as calling {@link #trimToSize(String, int, int, String)} with the parameters
     * <code>(source, length, length*, suffix)</code>. If <code>length</code> if larger then 100, then
     * <code>length* = length / 2</code>, otherwise <code>length* = length</code>.
     * <p>
     * 
     * @param source the string to trim
     * @param length the maximum length of the string to be returned
     * @param suffix the suffix to append in case the String was trimmed
     * 
     * @return a substring of the source, which is at most length characters long
     */
    public static String trimToSize(String source, int length, String suffix) {

        int area = (length > 100) ? length / 2 : length;
        return trimToSize(source, length, area, suffix);
    }

    /**
     * Returns a substring of the source, which is at most length characters long, cut in the last <code>area</code>
     * chars in the source at a sentence ending char or whitespace.
     * <p>
     * 
     * If a char is cut, the given <code>suffix</code> is appended to the result.
     * <p>
     * 
     * @param source the string to trim
     * @param length the maximum length of the string to be returned
     * @param area the area at the end of the string in which to find a sentence ender or whitespace
     * @param suffix the suffix to append in case the String was trimmed
     * 
     * @return a substring of the source, which is at most length characters long
     */
    public static String trimToSize(String source, int length, int area, String suffix) {

        if ((source == null) || (source.length() <= length)) {
            // no operation is required
            return source;
        }
        if (isEmpty(suffix)) {
            // we need an empty suffix
            suffix = "";
        }
        // must remove the length from the after sequence chars since these are always added in the end
        int modLength = length - suffix.length();
        if (modLength <= 0) {
            // we are to short, return beginning of the suffix
            return suffix.substring(0, length);
        }
        int modArea = area + suffix.length();
        if ((modArea > modLength) || (modArea < 0)) {
            // area must not be longer then max length
            modArea = modLength;
        }

        // first reduce the String to the maximum allowed length
        String findPointSource = source.substring(modLength - modArea, modLength);

        String result;
        // try to find an "sentence ending" char in the text
        int pos = lastIndexOf(findPointSource, PHRASE_ENDING_CHARS);
        if (pos >= 0) {
            // found a sentence ender in the lookup area, keep the sentence ender
            result = source.substring(0, modLength - modArea + pos + 1) + suffix;
        } else {
            // no sentence ender was found, try to find a whitespace
            pos = lastIndexOf(findPointSource, new char[] { ' ' });
            if (pos >= 0) {
                // found a whitespace, don't keep the whitespace
                result = source.substring(0, modLength - modArea + pos) + suffix;
            } else {
                // not even a whitespace was found, just cut away what's to long
                result = source.substring(0, modLength) + suffix;
            }
        }

        return result;
    }

    /**
     * <p>
     * Checks if a String is empty ("") or null.
     * </p>
     * 
     * <pre>
     * StringUtils.isEmpty(null)      = true
     * StringUtils.isEmpty(&quot;&quot;)        = true
     * StringUtils.isEmpty(&quot; &quot;)       = false
     * StringUtils.isEmpty(&quot;bob&quot;)     = false
     * StringUtils.isEmpty(&quot;  bob  &quot;) = false
     * </pre>
     * 
     * @param string the String to check, may be null
     * @return <code>true</code> if the String is empty or null
     */
    public static boolean isEmpty(String string) {
        return !org.springframework.util.StringUtils.hasLength(string);
    }

    /**
     * <p>
     * Checks if a String is whitespace, empty ("") or null.
     * </p>
     * 
     * <pre>
     * StringUtils.isBlank(null)      = true
     * StringUtils.isBlank(&quot;&quot;)        = true
     * StringUtils.isBlank(&quot; &quot;)       = true
     * StringUtils.isBlank(&quot;bob&quot;)     = false
     * StringUtils.isBlank(&quot;  bob  &quot;) = false
     * </pre>
     * 
     * @param string the String to check, may be null
     * @return <code>true</code> if the String is null, empty or whitespace
     */
    public static boolean isBlank(String string) {
        return !org.springframework.util.StringUtils.hasLength(string);
    }

    /**
     * <p>
     * Checks if a String is not empty ("") and not null.
     * </p>
     * 
     * <pre>
     * StringUtils.isNotEmpty(null)      = false
     * StringUtils.isNotEmpty(&quot;&quot;)        = false
     * StringUtils.isNotEmpty(&quot; &quot;)       = true
     * StringUtils.isNotEmpty(&quot;bob&quot;)     = true
     * StringUtils.isNotEmpty(&quot;  bob  &quot;) = true
     * </pre>
     * 
     * @param string the String to check, may be null
     * @return <code>true</code> if the String is not empty and not null
     */
    public static boolean isNotEmpty(String string) {
        return org.springframework.util.StringUtils.hasLength(string);
    }

    /**
     * <p>
     * Checks if a String is not empty (""), not null and not whitespace only.
     * </p>
     * 
     * <pre>
     * StringUtils.isNotBlank(null)      = false
     * StringUtils.isNotBlank(&quot;&quot;)        = false
     * StringUtils.isNotBlank(&quot; &quot;)       = false
     * StringUtils.isNotBlank(&quot;bob&quot;)     = true
     * StringUtils.isNotBlank(&quot;  bob  &quot;) = true
     * </pre>
     * 
     * @param string the String to check, may be null
     * @return <code>true</code> if the String is not empty and not null and not whitespace
     */
    public static boolean isNotBlank(String string) {
        return org.springframework.util.StringUtils.hasLength(string);
    }

    /**
     * Replace all occurences of a substring within a string with another string.
     * 
     * @param inString String to examine
     * @param oldPattern String to replace
     * @param newPattern String to insert
     * @return a String with the replacements
     */
    public static String replace(String inString, String oldPattern, String newPattern) {
        if (inString == null || inString.length() == 0 || oldPattern == null || oldPattern.length() == 0
                || newPattern == null) {
            return inString;
        }
        StringBuilder sbuf = new StringBuilder();
        // output StringBuffer we'll build up
        int pos = 0; // our position in the old string
        int index = inString.indexOf(oldPattern);
        // the index of an occurrence we've found, or -1
        int patLen = oldPattern.length();
        while (index >= 0) {
            sbuf.append(inString, pos, index);
            sbuf.append(newPattern);
            pos = index + patLen;
            index = inString.indexOf(oldPattern, pos);
        }
        sbuf.append(inString.substring(pos));
        // remember to append any characters to the right of a match
        return sbuf.toString();
    }

    public static int[] multipleSearch (String source, String[] searches) {
        int[] ret = new int[searches.length]; 
        int[] temp  = new int[searches.length]; 
        for (int j = 0; j < searches.length; j++) {
            ret[j] = - 1;
            temp[j] = - 1;
        }
        for (int i = 0; i < source.length(); i++) { // char by char
            for (int j = 0; j < searches.length; j++) { // every searched string
                String search = searches[j];
                if (search.charAt(0) == source.charAt(i)) {
                    temp[j] = i;
                } else if (temp[j] > -1) {
                    int index = i - temp[j];
                    if (index >= search.length()) {
                        ret[j] = temp[j];
                    } else if (search.charAt(index) != source.charAt(i)) { 
                        temp[j] = -1;
                    }
                }            
            }
        }
        return ret;
    }

    public static String reverseString (String source) {
    	for (int i = 0; i < source.length(); i++) {
    		if (source.charAt(i) == ' ') {
    			if (i < source.length() + 1) {
    				return reverseString (source.substring(i + 1)) + ' ' + source.substring(0, i);
    			}
    			break;
    		}
    	}
    	return source;
    }

    public static String toCamelCase(String source) {
        return Arrays.stream(source.split("\\-"))
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase())
                .collect(Collectors.joining());
    }
}
