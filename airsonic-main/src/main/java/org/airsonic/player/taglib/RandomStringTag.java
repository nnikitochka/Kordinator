package org.airsonic.player.taglib;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.BodyTagSupport;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.stream.Collectors;

/**
 * Port of the retired jakarta-taglibs "string" library's {@code randomString} tag
 * (the original taglib only supports javax.servlet and can not be used on Tomcat 10.1).
 * Generates a random lowercase string, typically used to generate unique element ids.
 */
public class RandomStringTag extends BodyTagSupport {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";

    private int count = 4;
    private String type;
    private String var;

    public void setCount(int count) {
        this.count = count;
    }

    /** The original taglib supported several character types; only "alphabet" is needed here. */
    public void setType(String type) {
        this.type = type;
    }

    public void setVar(String var) {
        this.var = var;
    }

    @Override
    public int doEndTag() throws JspException {
        String result = RANDOM.ints(count, 0, ALPHABET.length())
                .mapToObj(ALPHABET::charAt)
                .map(String::valueOf)
                .collect(Collectors.joining());

        if (var != null && !var.isEmpty()) {
            pageContext.setAttribute(var, result);
        } else {
            try {
                pageContext.getOut().print(result);
            } catch (IOException e) {
                throw new JspException(e);
            }
        }
        return EVAL_PAGE;
    }
}
