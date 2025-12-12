import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

public class TestLexer {
    public static void main(String[] args) throws Exception {
        String fileName = "luraphtest_fixed.luac";
        LuaLexer lexer = new LuaLexer(CharStreams.fromFileName(fileName));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        for (Token t : tokens.getTokens()) {
            System.out.println(t);
        }
    }
}
