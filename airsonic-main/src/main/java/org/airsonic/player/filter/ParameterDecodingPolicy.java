package org.airsonic.player.filter;

import org.airsonic.player.util.StringUtil;

import java.util.function.Consumer;

/** Framework-neutral rules for decoding legacy UTF-8-hex request parameters. */
public final class ParameterDecodingPolicy {
    public static final String PARAM_SUFFIX = "Utf8Hex";

    public boolean isEncodedParameter(String name) {
        return name.endsWith(PARAM_SUFFIX);
    }

    public String decodedParameterName(String name) {
        return isEncodedParameter(name) ? name.replace(PARAM_SUFFIX, "") : name;
    }

    public String[] decodeValues(String[] values, Consumer<String> malformedValueHandler) {
        if (values == null) {
            return null;
        }
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            try {
                result[i] = StringUtil.utf8HexDecode(values[i]);
            } catch (Exception e) {
                malformedValueHandler.accept(values[i]);
                result[i] = values[i];
            }
        }
        return result;
    }
}
