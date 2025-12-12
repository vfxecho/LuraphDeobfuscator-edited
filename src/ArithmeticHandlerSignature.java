import ASTNodes.*;
import LuaVM.*;
import java.util.*;

/**
 * Signature for arithmetic operations (ADD, SUB, MUL, DIV, MOD, POW)
 * These handlers typically have a similar structure with constant loading
 */
public class ArithmeticHandlerSignature extends BaseHandlerSignature {
    private final BinaryExpression.Operator expectedOperator;
    
    public ArithmeticHandlerSignature(VMOp targetOpcode, BinaryExpression.Operator operator, String description) {
        super(targetOpcode, description);
        this.expectedOperator = operator;
    }
    
    @Override
    public boolean matches(Function fn) {
        List<Statement> statements = getStatements(fn);
        
        // Arithmetic handlers typically have around 7-8 statements
        // with 2 If statements for constant loading and 1 final assign
        if (statements.size() < 6 || statements.size() > 10) {
            return false;
        }
        
        // Count If statements that match load constant pattern
        int loadConstantCount = 0;
        for (Statement stmt : statements) {
            if (stmt instanceof If && isLoadConstantPattern((If)stmt)) {
                loadConstantCount++;
            }
        }
        
        // Should have exactly 2 load constant patterns for binary arithmetic
        if (loadConstantCount != 2) {
            return false;
        }
        
        // Find the final assignment (should be last or second-to-last statement)
        Statement finalAssign = null;
        for (int i = statements.size() - 1; i >= 0; i--) {
            if (statements.get(i) instanceof Assign) {
                finalAssign = statements.get(i);
                break;
            }
        }
        
        if (finalAssign == null) {
            return false;
        }
        
        // The final assignment should be a binary operation
        if (!isBinaryOpAssign((Assign)finalAssign, "stack", expectedOperator)) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public String analyze(Function fn) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Arithmetic Handler Analysis for %s:\n", targetOpcode));
        sb.append(analyzeStructure(fn));
        
        List<Statement> statements = getStatements(fn);
        sb.append(String.format("Expected %d load constant patterns, found %d\n", 2, 
                countLoadConstantPatterns(statements)));
        
        Statement finalAssign = findFinalAssign(statements);
        if (finalAssign != null) {
            Assign assign = (Assign)finalAssign;
            if (assign.exprs.exprs.get(0) instanceof BinaryExpression) {
                BinaryExpression expr = (BinaryExpression)assign.exprs.exprs.get(0);
                sb.append(String.format("Final operation: %s (expected: %s)\n", 
                        expr.operator, expectedOperator));
            }
        }
        
        return sb.toString();
    }
    
    @Override
    public Set<VMOperand> getStackOperands() {
        Set<VMOperand> operands = new HashSet<>();
        operands.add(VMOperand.A); // Result destination
        operands.add(VMOperand.B); // First operand source
        operands.add(VMOperand.C); // Second operand source
        return operands;
    }
    
    @Override
    public boolean hasLoops() {
        return false; // Arithmetic handlers don't loop
    }
    
    @Override
    public boolean hasConditionals() {
        return true; // They have constant loading conditionals
    }
    
    @Override
    public BinaryExpression.Operator getArithmeticOperator() {
        return expectedOperator;
    }
    
    private int countLoadConstantPatterns(List<Statement> statements) {
        int count = 0;
        for (Statement stmt : statements) {
            if (stmt instanceof If && isLoadConstantPattern((If)stmt)) {
                count++;
            }
        }
        return count;
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