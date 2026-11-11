package com.skycong.httprawlog.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * HTTP 报文日志的字符集选择。
 *
 * <p>Servlet 容器在响应没有显式 charset 时，{@code getCharacterEncoding()} 往往是
 * ISO-8859-1（Tomcat 默认值），但 JSON/XML 和大量现代接口实际写的是 UTF-8。
 * 直接按容器编码解码会把中文变成乱码，因此按以下优先级选择：
 *
 * <ol>
 *   <li>{@code Content-Type} 中显式的 {@code charset=}；</li>
 *   <li>JSON/XML 没有 charset 时按 UTF-8；</li>
 *   <li>容器/调用方声明的字符集（非 ISO-8859-1/US-ASCII）；</li>
 *   <li>声明为 ISO-8859-1/US-ASCII（或未声明）时，若字节是包含非 ASCII 的严格 UTF-8，按 UTF-8；</li>
 *   <li>最后回退到声明值或 UTF-8。</li>
 * </ol>
 *
 * @since 1.1.0
 */
public final class CharsetSupport {

    private CharsetSupport() {
    }

    /**
     * 选择用于解码日志 body 的字符集。
     *
     * @param contentType       完整 Content-Type，可为 null
     * @param declaredEncoding  容器/调用方声明的编码，可为 null
     * @param bytes             原始字节
     * @param length            有效字节数
     * @return 字符集，永不为 null
     */
    public static Charset resolve(String contentType, String declaredEncoding, byte[] bytes, int length) {
        Charset fromContentType = charsetFromContentType(contentType);
        if (fromContentType != null) {
            return fromContentType;
        }

        // JSON (application/json, *+json) / XML 的默认编码都是 UTF-8。
        if (isJsonOrXml(contentType)) {
            return StandardCharsets.UTF_8;
        }

        Charset declared = toCharset(declaredEncoding);
        if (declared != null && !isSingleByteFallback(declared)) {
            return declared;
        }

        // Tomcat 在未设置 charset 时会返回 ISO-8859-1，部分容器可能返回 US-ASCII；
        // 但 body 很可能是 UTF-8。
        if (isValidUtf8WithNonAscii(bytes, length)) {
            return StandardCharsets.UTF_8;
        }
        return declared != null ? declared : StandardCharsets.UTF_8;
    }

    /**
     * 选择业务侧 {@code HttpServletRequest#getReader()} 使用的字符集。
     *
     * <p>与日志解码不同，这里不做 UTF-8 字节启发式，避免同一个请求中
     * “业务 reader”和“容器原始 reader”读到不同字符：
     *
     * <ol>
     *   <li>{@code Content-Type} 中显式的 {@code charset=}；</li>
     *   <li>JSON/XML 缺省 UTF-8；</li>
     *   <li>容器声明的编码；</li>
     *   <li>未声明时按 Servlet 规范回退 ISO-8859-1。</li>
     * </ol>
     *
     * @param contentType       完整 Content-Type，可为 null
     * @param declaredEncoding  容器/调用方声明的编码，可为 null
     * @return 字符集，永不为 null
     */
    public static Charset resolveForReader(String contentType, String declaredEncoding) {
        Charset explicit = charsetFromContentType(contentType);
        if (explicit != null) {
            return explicit;
        }
        if (isJsonOrXml(contentType)) {
            return StandardCharsets.UTF_8;
        }
        Charset declared = toCharset(declaredEncoding);
        return declared != null ? declared : StandardCharsets.ISO_8859_1;
    }

    private static boolean isSingleByteFallback(Charset charset) {
        return StandardCharsets.ISO_8859_1.equals(charset) || StandardCharsets.US_ASCII.equals(charset);
    }

    /**
     * 从 Content-Type 中解析 charset 参数。
     *
     * @param contentType 完整 Content-Type
     * @return 字符集；没有 charset 或 charset 非法时返回 null
     */
    public static Charset charsetFromContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return null;
        }
        String[] parts = contentType.split(";");
        for (int i = 1; i < parts.length; i++) {
            String parameter = parts[i].trim();
            int eq = parameter.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (!"charset".equalsIgnoreCase(parameter.substring(0, eq).trim())) {
                continue;
            }
            String value = parameter.substring(eq + 1).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return toCharset(value);
        }
        return null;
    }

    /**
     * 是否为 JSON/XML 媒体类型。JSON/XML 规范在缺省 charset 时使用 UTF-8。
     */
    private static boolean isJsonOrXml(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return false;
        }
        int semicolon = contentType.indexOf(';');
        String mediaType = (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType)
                .trim().toLowerCase(Locale.ROOT);
        return "application/json".equals(mediaType)
                || "text/json".equals(mediaType)
                || mediaType.endsWith("+json")
                || "application/xml".equals(mediaType)
                || "text/xml".equals(mediaType)
                || mediaType.endsWith("+xml");
    }

    private static Charset toCharset(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Charset.forName(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 严格校验 UTF-8，并至少包含一个完整的非 ASCII 字符。
     *
     * <p>日志 body 可能被上限截断，因此末尾不完整的多字节序列允许存在，
     * 但必须至少已经出现一个完整的非 ASCII 字符，避免把真正的 ISO-8859-1
     * 单字节文本（如 café 的 0xE9）误判为 UTF-8。
     */
    private static boolean isValidUtf8WithNonAscii(byte[] bytes, int length) {
        if (bytes == null || length <= 0) {
            return false;
        }
        boolean completeNonAscii = false;
        int i = 0;
        while (i < length) {
            int b = bytes[i] & 0xFF;
            if (b < 0x80) {
                i++;
                continue;
            }

            int continuationBytes;
            int minimumCodePoint;
            if (b >= 0xC2 && b <= 0xDF) {
                continuationBytes = 1;
                minimumCodePoint = 0x80;
            } else if (b >= 0xE0 && b <= 0xEF) {
                continuationBytes = 2;
                minimumCodePoint = 0x800;
            } else if (b >= 0xF0 && b <= 0xF4) {
                continuationBytes = 3;
                minimumCodePoint = 0x10000;
            } else {
                return false;
            }

            // body 被日志上限截断：只接受“后续剩余字节全是 continuation”的情况。
            if (i + continuationBytes >= length) {
                for (int j = i + 1; j < length; j++) {
                    if ((bytes[j] & 0xC0) != 0x80) {
                        return false;
                    }
                }
                return completeNonAscii;
            }

            int codePoint = b & (0xFF >> (continuationBytes + 2));
            for (int j = 1; j <= continuationBytes; j++) {
                int c = bytes[i + j] & 0xFF;
                if ((c & 0xC0) != 0x80) {
                    return false;
                }
                codePoint = (codePoint << 6) | (c & 0x3F);
            }

            if (codePoint < minimumCodePoint
                    || (codePoint >= 0xD800 && codePoint <= 0xDFFF)
                    || codePoint > 0x10FFFF) {
                return false;
            }
            completeNonAscii = true;
            i += continuationBytes + 1;
        }
        return completeNonAscii;
    }
}
