package android.util;

public class Base64 {
    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    public static String encodeToString(byte[] input, int flags) {
        return java.util.Base64.getEncoder().encodeToString(input);
    }
    public static String encodeToString(byte[] input, int offset, int len, int flags) {
        byte[] subset = new byte[len];
        System.arraycopy(input, offset, subset, 0, len);
        return java.util.Base64.getEncoder().encodeToString(subset);
    }
    public static byte[] encode(byte[] input, int flags) {
        return java.util.Base64.getEncoder().encode(input);
    }
    public static byte[] decode(String str, int flags) {
        return java.util.Base64.getDecoder().decode(str);
    }
    public static byte[] decode(byte[] input, int flags) {
        return java.util.Base64.getDecoder().decode(input);
    }
}
