import ASTNodes.*;
import ASTNodes.Number;
import LuaVM.*;
import java.util.*;

/**
 * Signature for control flow operations (JUMP, CALL, TAILCALL, RETURN, etc.)
 * These handlers typically have more complex control structures
 */
public class ControlFlowHandlerSignature extends BaseHandlerSignature {
    private final ControlFlowType controlFlowType;
    
    public enum ControlFlowType {
        JUMP,           // Unconditional jump
        CALL,           // Function call
        TAILCALL,       // Tail call (function call with return)
        RETURN,         // Return from function
        FORLOOP,        // For loop iteration
        FORPREP,        // For loop preparation
        TFORLOOP,       // Table for loop
        CLOSE,          // Close variable scope
        CLOSURE,        // Create closure
        VARARG          // Variable arguments
    }
    
    public ControlFlowHandlerSignature(VMOp targetOpcode, ControlFlowType controlFlowType, String description) {
        super(targetOpcode, description);
        this.controlFlowType = controlFlowType;
    }
    
    @Override
    public boolean matches(Function fn) {
        switch (controlFlowType) {
            case JUMP:
                return matchesJump(fn);
            case CALL:
                return matchesCall(fn);
            case TAILCALL:
                return matchesTailCall(fn);
            case RETURN:
                return matchesReturn(fn);
            case FORLOOP:
                return matchesForLoop(fn);
            case FORPREP:
                return matchesForPrep(fn);
            case TFORLOOP:
                return matchesTForLoop(fn);
            case CLOSE:
                return matchesClose(fn);
            case CLOSURE:
                return matchesClosure(fn);
            case VARARG:
                return matchesVarArg(fn);
            default:
                return false;
        }
    }
    
    private boolean matchesJump(Function fn) {
        List<Statement> statements = getStatements(fn);
        
        // JUMP typically has 6 statements
        if (statements.size() != 6) {
            return false;
        }
        
        // Final statement should be Assign with binary expression (addition)
        if (!(statements.get(5) instanceof Assign)) {
            return false;
        }
        
        Assign assign = (Assign)statements.get(5);
        if (assign.vars.vars.size() != 1 || assign.exprs.exprs.size() != 1) {
            return false;
        }
        
        Variable lhs = (Variable)assign.vars.vars.get(0);
        Expression rhs = assign.exprs.exprs.get(0);
        
        if (!(lhs instanceof Variable) || !(rhs instanceof BinaryExpression)) {
            return false;
        }
        
        BinaryExpression expr = (BinaryExpression)rhs;
        return expr.operator == BinaryExpression.Operator.ADD;
    }
    
    private boolean matchesCall(Function fn) {
        // CALL handlers have handleReturn call or specific conditional count
        If stmt = (If)fn.block.first(If.class);
        
        if (stmt != null) {
            FunctionCall call = (FunctionCall)stmt.first(FunctionCall.class);
            if (call != null && call.varOrExp.symbol().equals("handle_return")) {
                return fn.block.ret == null; // handle return doesn't have a return
            }
        }
        
        // Alternative detection: count if statements with NEQ 1
        return countIfNeq1(fn) == 2;
    }
    
    private boolean matchesTailCall(Function fn) {
        If stmt = (If)fn.block.first(If.class);
        
        if (stmt != null) {
            FunctionCall call = (FunctionCall)stmt.first(FunctionCall.class);
            if (call != null && call.varOrExp.symbol().equals("handle_return")) {
                return fn.block.ret != null; // tail call has a return
            }
        }
        
        // Alternative detection: count if statements with NEQ 1
        return countIfNeq1(fn) == 1;
    }
    
    private boolean matchesReturn(Function fn) {
        return fn.block.ret != null && hasReturnStatement(fn);
    }
    
    private boolean matchesForLoop(Function fn) {
        List<Statement> statements = getStatements(fn);
        
        // FORLOOP typically has 9 statements
        if (statements.size() != 9) {
            return false;
        }
        
        // Final statement should be If with specific structure
        if (!(statements.get(8) instanceof If)) {
            return false;
        }
        
        If ifStmt = (If)statements.get(8);
        return ifStmt.elsestmt == null && ifStmt.elseifstmt.size() == 1;
    }
    
    private boolean matchesForPrep(Function fn) {
        FunctionCall call = (FunctionCall)fn.block.first(FunctionCall.class);
        return call != null && call.varOrExp.symbol().equals("assert");
    }
    
    private boolean matchesTForLoop(Function fn) {
        List<Node> binaryExpressions = fn.block.getChildren(BinaryExpression.class);
        for (Node node : binaryExpressions) {
            BinaryExpression expr = (BinaryExpression)node;
            if (expr.operator == BinaryExpression.Operator.NEQ && expr.right instanceof Nil) {
                return true;
            }
        }
        return false;
    }
    
    private boolean matchesClose(Function fn) {
        ForStep stmt1 = (ForStep)fn.block.first(ForStep.class);
        if (stmt1 != null) {
            ForIn stmt2 = (ForIn)stmt1.block.first(ForIn.class);
            if (stmt2 != null && stmt2.exprs.exprs.get(0).symbol().equals("next")) {
                return true;
            }
        }
        return false;
    }
    
    private boolean matchesClosure(Function fn) {
        FunctionCall call = (FunctionCall)fn.block.first(FunctionCall.class);
        return call != null && call.varOrExp.symbol().equals("setmetatable");
    }
    
    private boolean matchesVarArg(Function fn) {
        // Check if handler contains varargsz reference
        return new ASTSourceGenerator(fn.block).generate().contains("varargsz");
    }
    
    private boolean hasReturnStatement(Function fn) {
        for (Node node : fn.block.getChildren()) {
            if (node.first(Return.class) != null) {
                return true;
            }
        }
        return false;
    }
    
    private int countIfNeq1(Function fn) {
        int count = 0;
        List<Node> ifs = fn.block.getChildren(If.class);
        
        for (Node node : ifs) {
            If ifStmt = (If)node;
            if (ifStmt.ifstmt.first instanceof BinaryExpression) {
                BinaryExpression binexpr = (BinaryExpression)ifStmt.ifstmt.first;
                if (binexpr.operator == BinaryExpression.Operator.NEQ && binexpr.right instanceof Number) {
                    if (((Number)binexpr.right).value == 1.0) {
                        count++;
                    }
                }
            }
        }
        
        return count;
    }
    
    @Override
    public String analyze(Function fn) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Control Flow Handler Analysis for %s:\n", targetOpcode));
        sb.append(analyzeStructure(fn));
        sb.append(String.format("Control flow type: %s\n", controlFlowType));
        
        List<Node> functionCalls = fn.block.getChildren(FunctionCall.class);
        sb.append(String.format("Function calls: %d\n", functionCalls.size()));
        
        List<Node> returns = fn.block.getChildren(Return.class);
        sb.append(String.format("Return statements: %d\n", returns.size()));
        
        return sb.toString();
    }
    
    @Override
    public Set<VMOperand> getStackOperands() {
        Set<VMOperand> operands = new HashSet<>();
        
        switch (controlFlowType) {
            case JUMP:
                operands.add(VMOperand.sBx); // Jump offset
                break;
            case CALL:
            case TAILCALL:
                operands.add(VMOperand.A); // Function index
                operands.add(VMOperand.B); // Number of arguments
                operands.add(VMOperand.C); // Number of return values
                break;
            case RETURN:
                operands.add(VMOperand.A); // First return value
                operands.add(VMOperand.B); // Number of returns
                break;
            case FORLOOP:
            case FORPREP:
            case TFORLOOP:
                operands.add(VMOperand.A); // Loop counter/index
                operands.add(VMOperand.B); // Loop limit
                operands.add(VMOperand.C); // Loop step
                break;
            case CLOSE:
            case CLOSURE:
            case VARARG:
                operands.add(VMOperand.A); // Variable index
                break;
        }
        
        return operands;
    }
    
    @Override
    public boolean hasLoops() {
        return controlFlowType == ControlFlowType.FORLOOP || 
               controlFlowType == ControlFlowType.TFORLOOP ||
               controlFlowType == ControlFlowType.CLOSE;
    }
    
    @Override
    public boolean hasConditionals() {
        return controlFlowType == ControlFlowType.CALL ||
               controlFlowType == ControlFlowType.TAILCALL ||
               controlFlowType == ControlFlowType.RETURN ||
               controlFlowType == ControlFlowType.FORLOOP;
    }
    
    @Override
    public BinaryExpression.Operator getArithmeticOperator() {
        if (controlFlowType == ControlFlowType.JUMP) {
            return BinaryExpression.Operator.ADD;
        }
        return null;
    }
    
    @Override
    protected Function getFunction() {
        throw new UnsupportedOperationException("Not used in this context");
    }
}