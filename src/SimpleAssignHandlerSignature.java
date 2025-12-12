import ASTNodes.*;
import LuaVM.*;
import java.util.*;

/**
 * Signature for move-style handlers (MOVE, LOADK, etc.)
 * These typically have simple assignment patterns without complex conditionals
 */
public class SimpleAssignHandlerSignature extends BaseHandlerSignature {
    private final PatternType patternType;
    
    public enum PatternType {
        VAR_TO_VAR,      // stack = stack
        STACK_TO_CONST,  // stack = constants  
        STACK_TO_ENV,    // stack = environment
        STACK_TO_UPVAL,  // stack = upvalues
        TABLE_CONSTRUCT, // stack = {}
        UNARY_OP         // stack = -stack, stack = not stack, etc.
    }
    
    public SimpleAssignHandlerSignature(VMOp targetOpcode, PatternType patternType, String description) {
        super(targetOpcode, description);
        this.patternType = patternType;
    }
    
    @Override
    public boolean matches(Function fn) {
        List<Statement> statements = getStatements(fn);
        
        // Simple assignment handlers typically have 5-7 statements
        if (statements.size() < 4 || statements.size() > 8) {
            return false;
        }
        
        // Should have minimal conditionals and no loops
        if (hasLoops(fn)) {
            return false;
        }
        
        // Look for the final assignment pattern
        Statement finalAssign = findFinalAssign(statements);
        if (finalAssign == null) {
            return false;
        }
        
        return matchesAssignmentPattern((Assign)finalAssign);
    }
    
    private boolean matchesAssignmentPattern(Assign assign) {
        switch (patternType) {
            case VAR_TO_VAR:
                return isVarToVarAssign(assign, "stack", "stack");
            case STACK_TO_CONST:
                return isConstToStackAssign(assign, "stack", "constants");
            case STACK_TO_ENV:
                return isConstToStackAssign(assign, "stack", "environment");
            case STACK_TO_UPVAL:
                return isConstToStackAssign(assign, "stack", "upvalues");
            case TABLE_CONSTRUCT:
                if (isStackAssign(assign, "stack") && assign.exprs.exprs.get(0) instanceof TableConstructor) {
                    return true;
                }
                return false;
            case UNARY_OP:
                return isUnaryOperation(assign);
            default:
                return false;
        }
    }
    
    private boolean isUnaryOperation(Assign assign) {
        if (assign.vars.vars.size() == 1 && assign.exprs.exprs.size() == 1) {
            Variable lhs = (Variable)assign.vars.vars.get(0);
            Expression rhs = assign.exprs.exprs.get(0);
            
            if (!lhs.symbol().equals("stack")) {
                return false;
            }
            
            if (rhs instanceof UnaryExpression) {
                UnaryExpression unary = (UnaryExpression)rhs;
                // This will be further distinguished by the specific opcode
                return true;
            }
        }
        return false;
    }
    
    @Override
    public String analyze(Function fn) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Simple Assign Handler Analysis for %s:\n", targetOpcode));
        sb.append(analyzeStructure(fn));
        sb.append(String.format("Pattern type: %s\n", patternType));
        
        Statement finalAssign = findFinalAssign(getStatements(fn));
        if (finalAssign != null) {
            Assign assign = (Assign)finalAssign;
            sb.append(String.format("Final statement: %s\n", assign.line()));
        }
        
        return sb.toString();
    }
    
    @Override
    public Set<VMOperand> getStackOperands() {
        Set<VMOperand> operands = new HashSet<>();
        switch (patternType) {
            case VAR_TO_VAR:
                operands.add(VMOperand.A); // destination
                operands.add(VMOperand.B); // source
                break;
            case STACK_TO_CONST:
            case STACK_TO_ENV:
            case STACK_TO_UPVAL:
            case TABLE_CONSTRUCT:
            case UNARY_OP:
                operands.add(VMOperand.A); // destination
                break;
        }
        return operands;
    }
    
    @Override
    public boolean hasLoops() {
        return false; // Simple handlers don't loop
    }
    
    @Override
    public boolean hasConditionals() {
        return false; // Most simple handlers don't have conditionals
    }
    
    @Override
    public BinaryExpression.Operator getArithmeticOperator() {
        return null; // Simple handlers don't do arithmetic
    }
    
    private Statement findFinalAssign(List<Statement> statements) {
        for (int i = statements.size() - 1; i >= 0; i--) {
            if (statements.get(i) instanceof Assign) {
                return statements.get(i);
            }
        }
        return null;
    }
    
    @Override
    protected Function getFunction() {
        throw new UnsupportedOperationException("Not used in this context");
    }
}