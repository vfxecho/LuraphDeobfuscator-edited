import ASTNodes.Node;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class DecodeChunkMetadataSmokeTest {
    public static void main(String[] args) throws Exception {
        String fileName = args.length > 0 ? args[0] : "luraphtest.luac";

        LuaLexer lexer = new LuaLexer(CharStreams.fromFileName(fileName));
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
