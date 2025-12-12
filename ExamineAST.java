import ASTNodes.*;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.util.List;

public class ExamineAST {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: ExamineAST <luac-file>");
            System.exit(1);
        }

        String fileName = args[0];

        // generate parse tree
        System.out.println("Parsing " + fileName + "...");
        LuaLexer lexer = new LuaLexer(CharStreams.fromFileName(fileName));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        
        // First, let's see what tokens we get
        System.out.println("Lexing tokens...");
        tokens.fill();
        System.out.println("Total tokens: " + tokens.getNumberOfOnChannelTokens());
        System.out.println("First 10 tokens:");
        for (int i = 0; i < Math.min(10, tokens.size()); i++) {
            org.antlr.v4.runtime.Token tok = tokens.get(i);
            System.out.println("  Token " + i + ": type=" + tok.getType() + " text='" + tok.getText() + "'");
        }
        
        LuaParser parser = new LuaParser(tokens);
        
        // Add error listener to capture parsing errors
        parser.removeErrorListeners();
        parser.addErrorListener(new org.antlr.v4.runtime.BaseErrorListener() {
            @Override
            public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer, 
                                  Object offendingSymbol, int line, int charPositionInLine, 
                                  String msg, org.antlr.v4.runtime.RecognitionException e) {
                System.out.println("SYNTAX ERROR at line " + line + ":" + charPositionInLine + " - " + msg);
            }
        });
        
        LuaParser.ChunkContext parseTree = parser.chunk();

        // generate AbstractSyntaxTree
        System.out.println("Building AST...");
        System.out.println("Parse tree: " + parseTree);
        System.out.println("Parse tree text: " + parseTree.getText());
        System.out.println("Parse tree child count: " + parseTree.getChildCount());
        
        Node root = new BuildASTVisitor().visitChunk(parseTree);
        
        System.out.println("\nRoot node: " + root);
        System.out.println("Root node class: " + root.getClass().getSimpleName());
        
        if (root instanceof Block) {
            Block b = (Block) root;
            System.out.println("Block statements: " + b.stmts);
            System.out.println("Block statements size: " + b.stmts.size());
        }
        
        System.out.println("\n=== AST Structure ===");
        printNodeInfo(root, 0);
    }
    
    private static void printNodeInfo(Node node, int depth) {
        String indent = "";
        for (int i = 0; i < depth; i++) indent += "  ";
        
        if (node instanceof Block) {
            Block b = (Block) node;
            System.out.println(indent + "Block with " + b.stmts.size() + " statements");
            for (Statement stmt : b.stmts) {
                printNodeInfo((Node)stmt, depth + 1);
            }
            if (b.ret != null) {
                System.out.println(indent + "  Return:");
                printNodeInfo((Node)b.ret, depth + 2);
            }
        } else if (node instanceof Function) {
            Function f = (Function) node;
            String name = f.name != null ? f.name.toString() : "<anonymous>";
            System.out.println(indent + "Function: " + name);
            if (f.block != null) {
                printNodeInfo(f.block, depth + 1);
            }
        } else if (node instanceof LocalDeclare) {
            LocalDeclare ld = (LocalDeclare) node;
            System.out.print(indent + "LocalDeclare: ");
            if (ld.names != null) {
                System.out.print(ld.names.toString());
            }
            System.out.println();
        } else if (node instanceof Assign) {
            Assign a = (Assign) node;
            System.out.println(indent + "Assignment");
        } else if (node instanceof FunctionCall) {
            FunctionCall fc = (FunctionCall) node;
            System.out.println(indent + "FunctionCall: " + fc.varOrExp.line());
        } else if (node instanceof If) {
            System.out.println(indent + "If statement");
        } else if (node instanceof ForStep) {
            System.out.println(indent + "ForStep");
        } else if (node instanceof Return) {
            System.out.println(indent + "Return");
        } else {
            System.out.println(indent + node.getClass().getSimpleName());
        }
    }
}
