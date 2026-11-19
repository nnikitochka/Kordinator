package org.airsonic.player.taglib;

import org.airsonic.player.util.StringUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspTagException;
import jakarta.servlet.jsp.tagext.BodyTagSupport;

import java.io.IOException;
import java.util.Locale;

/**
 * Converts a byte-count to a formatted string suitable for display to the user, with respect
 * to the current locale.
 * <p/>
 * For instance:
 * <ul>
 * <li><code>format(918)</code> returns <em>"918 B"</em>.</li>
 * <li><code>format(98765)</code> returns <em>"96 KB"</em>.</li>
 * <li><code>format(1238476)</code> returns <em>"1.2 MB"</em>.</li>
 * </ul>
 * This class assumes that 1 KB is 1024 bytes.
 *
 * @author Sindre Mehus
 */
public class FormatBytesTag extends BodyTagSupport {

    private long bytes;

    public int doStartTag() {
        return EVAL_BODY_BUFFERED;
    }

    public int doEndTag() throws JspException {
        HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
        Locale locale = (Locale) request.getAttribute("airsonic.locale");
        if (locale == null) {
            locale = request.getLocale();
        }
        String result = StringUtil.formatBytes(bytes, locale);

        try {
            pageContext.getOut().print(result);
        } catch (IOException x) {
            throw new JspTagException(x);
        }
        return EVAL_PAGE;
    }

    public void release() {
        bytes = 0L;
        super.release();
    }

    public long getBytes() {
        return bytes;
    }

    public void setBytes(long bytes) {
        this.bytes = bytes;
    }
}
