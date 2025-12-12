import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LuaSourcePreprocessor {
    private static final Pattern HEADER_COMMENT_WITH_CODE_SAME_LINE =
            Pattern.compile("\\A(--[^\\r\\n]*?)(\\s+)(return\\b)", Pattern.DOTALL);

    private LuaSourcePreprocessor() {
    }

    public static String preprocess(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        if (!source.startsWith("--")) {
            return source;
        }

        Matcher matcher = HEADER_COMMENT_WITH_CODE_SAME_LINE.matcher(source);
        if (!matcher.find()) {
            return source;
        }

        String whitespaceBetween = matcher.group(2);
        if (whitespaceBetween.indexOf('\n') >= 0 || whitespaceBetween.indexOf('\r') >= 0) {
            return source;
        }

        return matcher.group(1) + "\n" + source.substring(matcher.start(3));
    }
}
