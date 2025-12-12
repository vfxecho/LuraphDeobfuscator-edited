import ASTNodes.Node;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DecodeChunkMetadataSmokeTest {
    public static void main(String[] args) throws Exception {
        String fileName = args.length > 0 ? args[0] : "luraphtest.lua";

        byte[] inputBytes = Files.readAllBytes(Paths.get(fileName));
        if (inputBytes.length >= 4
                && (inputBytes[0] & 0xFF) == 0x1B
                && (inputBytes[1] & 0xFF) == 0x4C
                && (inputBytes[2] & 0xFF) == 0x75
                && (inputBytes[3] & 0xFF) == 0x61) {
            throw new IllegalArgumentException("Input appears to be compiled Lua bytecode (.luac). Provide Luraph-obfuscated Lua source instead.");
        }

        String inputSource = new String(inputBytes, StandardCharsets.UTF_8);
        inputSource = LuaSourcePreprocessor.preprocess(inputSource);

        LuaLexer lexer = new LuaLexer(CharStreams.fromString(inputSource, fileName));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LuaParser parser = new LuaParser(tokens);
        LuaParser.ChunkContext parseTree = parser.chunk();

        Node root = new BuildASTVisitor().visitChunk(parseTree);

        ASTOptimizerMgr optMgr = new ASTOptimizerMgr(root);
        optMgr.addOptimizer(new ASTConstantFolder());
        optMgr.addOptimizer(new ASTConstantPropagator());
        optMgr.optimize();

        ASTOptimizerMgr renameMgr = new ASTOptimizerMgr(root);
        renameMgr.addRenamer(new ASTBasicRenamer());
        renameMgr.optimize();

        LuraphDevirtualizer devirtualizer = new LuraphDevirtualizer(root);
        LuraphDevirtualizer.DecodeChunkMetadata md = devirtualizer.extractDecodeChunkMetadata();

        if (md.constantTableIdx == null) {
            throw new IllegalStateException("constantTableIdx was not resolved");
        }

        if (md.instructionTableIdx == null) {
            throw new IllegalStateException("instructionTableIdx was not resolved");
        }

        if (md.prototypeTableIdx == null) {
            throw new IllegalStateException("prototypeTableIdx was not resolved");
        }

        if (md.debugTableIdx == null) {
            throw new IllegalStateException("debugTableIdx was not resolved");
        }

        System.out.println("OK: decode_chunk metadata resolved. constantTableIdx=" + md.constantTableIdx + ", instructionTableIdx=" + md.instructionTableIdx + ", prototypeTableIdx=" + md.prototypeTableIdx + ", debugTableIdx=" + md.debugTableIdx);
    }
}
