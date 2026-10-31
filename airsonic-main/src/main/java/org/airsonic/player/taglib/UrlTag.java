package org.airsonic.player.taglib;

import org.airsonic.player.filter.ParameterDecodingFilter;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.CharUtils;
import org.apache.taglibs.standard.tag.common.core.UrlSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspTagException;
import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.tagext.BodyTagSupport;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a URL with optional query parameters. Similar to 'c:url', but
 * you may specify which character encoding to use for the URL query
 * parameters. If no encoding is specified, the following steps are performed:
 * <ul>
 * <li>Parameter values are encoded as the hexadecimal representation of the UTF-8 bytes of the original string.</li>
 * <li>Parameter names are prepended with the suffix "Utf8Hex"</li>
 * <li>Note: Nothing is done with the parameter name or value if the value only contains ASCII alphanumeric characters.</li>
 * </ul>
 * <p/>
 * (The problem with c:url is that is uses the same encoding as the http response,
 * but most(?) servlet container assumes that ISO-8859-1 is used.)
 */
public class UrlTag extends BodyTagSupport {

    private String DEFAULT_ENCODING = "Utf8Hex";
    private static final Logger LOG = LoggerFactory.getLogger(UrlTag.class);

    private String var;
    private String value;
    private String encoding = DEFAULT_ENCODING;
    private final List<Parameter> parameters = new ArrayList<>();

    public int doStartTag() {
        parameters.clear();
        return EVAL_BODY_BUFFERED;
    }

    public int doEndTag() throws JspException {

        // Rewrite and encode the url.
        String result = formatUrl();

        // Store or print the output
        if (var != null)
            pageContext.setAttribute(var, result, PageContext.PAGE_SCOPE);
        else try {
            pageContext.getOut().print(result);
        } catch (IOException x) {
            throw new JspTagException(x);
        }

        return EVAL_PAGE;
    }

    private String formatUrl() throws JspException {
        String baseUrl = UrlSupport.resolveUrl(value, null, pageContext);

        StringBuilder result = new StringBuilder();
        result.append(baseUrl);
        if (!parameters.isEmpty()) {
            result.append('?');

            for (int i = 0; i < parameters.size(); i++) {
                Parameter parameter = parameters.get(i);
                try {
                    result.append(parameter.name());
                    if (isUtf8Hex() && !isAsciiAlphaNumeric(parameter.value())) {
                        result.append(ParameterDecodingFilter.PARAM_SUFFIX);
                    }

                    result.append('=');
                    if (parameter.value() != null) {
                        result.append(encode(parameter.value()));
                    }
                    if (i < parameters.size() - 1) {
                        result.append("&");
                    }

                } catch (UnsupportedEncodingException x) {
                    throw new JspTagException(x);
                }
            }
        }
        return result.toString();
    }

    private String encode(String str) throws UnsupportedEncodingException {
        if (isUtf8Hex()) {
            if (isAsciiAlphaNumeric(str)) {
                return str;
            }

            try {
                return StringUtil.utf8HexEncode(str);
            } catch (Exception x) {
                LOG.error("Failed to utf8hex-encode the string '{}'.", str, x);
                return str;
            }
        }

        return URLEncoder.encode(str, encoding);
    }

    private boolean isUtf8Hex() {
        return DEFAULT_ENCODING.equals(encoding);
    }

    private boolean isAsciiAlphaNumeric(String str) {
        if (str == null) {
            return true;
        }

        for (int i = 0; i < str.length(); i++) {
            if (!CharUtils.isAsciiAlphanumeric(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public void release() {
        var = null;
        value = null;
        encoding = DEFAULT_ENCODING;
        parameters.clear();
        super.release();
    }

    public void addParameter(String name, String value) {
        parameters.add(new Parameter(name, value));
    }

    public String getVar() {
        return var;
    }

    public void setVar(String var) {
        this.var = var;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * A URL query parameter.
     */
    private record Parameter(String name, String value) {
    }
}
