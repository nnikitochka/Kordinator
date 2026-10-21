/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.taglib;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.BodyTagSupport;

import java.io.IOException;

/**
 * Port of the retired jakarta-taglibs "string" library's {@code truncateNicely} tag
 * (the original taglib only supports javax.servlet and can not be used on Tomcat 10.1).
 * Truncates a string at a word boundary and appends an ellipsis.
 */
public class TruncateNicelyTag extends BodyTagSupport {

    private int lower = 0;
    private int upper = 30;
    private String appendToEnd = "...";
    private String var;

    public void setLower(int lower) {
        this.lower = lower;
    }

    public void setUpper(int upper) {
        this.upper = upper;
    }

    public void setAppendToEnd(String appendToEnd) {
        this.appendToEnd = appendToEnd;
    }

    public void setVar(String var) {
        this.var = var;
    }

    @Override
    public int doEndTag() throws JspException {
        String text = getBodyContent().getString().trim();
        String result = truncateNicely(text, lower, upper, appendToEnd);

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

    static String truncateNicely(String s, int lower, int upper, String appendToEnd) {
        if (s == null || s.length() <= upper) {
            return s;
        }

        // Search backwards from the cut for a word boundary.
        int boundary = upper;
        while (boundary > lower && !Character.isWhitespace(s.charAt(boundary))) {
            boundary--;
        }

        String result = boundary > lower ? s.substring(0, boundary) : s.substring(0, upper);
        return result + appendToEnd;
    }
}
