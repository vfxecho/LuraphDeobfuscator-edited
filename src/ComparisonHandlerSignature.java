import ASTNodes.*;
import LuaVM.*;
import java.util.*;

/**
 * Signature for comparison operations (EQ, LT, LTE)
 * These handlers typically have multiple conditional checks
 */
public class ComparisonHandlerSignature extends BaseHandlerSignature {
    private final BinaryExpression.Operator comparisonOperator;
    private final int expectedIfStatements;
    
    public ComparisonHandlerSignature(VMOp targetOpcode, BinaryExpression.Operator operator, int expectedIfStatements, String description) {
        super(targetOpcode, description);
        this.comparisonOperator = operator;
        this.expectedIfStatements = expectedIfStatements;
    }
    
    @Override
    public boolean matches(Function fn) {
        List<Statement> statements = getStatements(fn);
        
        // Comparison handlers typically have more statements due to multiple conditionals
        if (statements.size() < 7 || statements.size() > 12) {
            return false;
        }
        
        // Should have multiple If statements for constant loading and comparison logic
        List<Node> ifStatements = fn.block.getChildren(If.class);
        if (ifStatements.size() != expectedIfStatements) {
            return false;
        }
        
        // Check for load constant patterns
        int loadConstantCount = 0;
        for (Node node : ifStatements) {
            If ifStmt = (If)node;
            if (isLoadConstantPattern(ifStmt)) {
                loadConstantCount++;
            }
        }
        
        // Should have 2 load constant patterns
        if (loadConstantCount != 2) {
            return false;
        }
        
        // Look for the comparison logic in the last If statement
        If lastIf = (If)ifStatements.get(ifStatements.size() - 1);
        return hasComparisonLogic(lastIf);
    }
    
    private boolean hasComparisonLogic(If ifStmt) {
        if (!(ifStmt.ifstmt.first instanceof BinaryExpression)) {
            return false;
        }
        
        BinaryExpression condition = (BinaryExpression)ifStmt.ifstmt.first;
        
        // For EQ: condition should be something like (expr1 ~= 0) where expr1 is (var1 == var2)
        if (targetOpcode == VMOp.EQ) {
            if (condition.operator != BinaryExpression.Operator.NEQ) {
                return false;
            }
            
            if (!(condition.left instanceof BinaryExpression) || !(condition.right instanceof Variable)) {
                return false;
            }
            
            BinaryExpression comparison = (BinaryExpression)condition.left;
            return comparison.operator == BinaryExpression.Operator.EQ;
        }
        
        // For LT: condition should be something like (expr1 ~= 0) where expr1 is (var1 < var2)
        if (targetOpcode == VMOp.LT) {
            if (condition.operator != BinaryExpression.Operator.NEQ) {
                return false;
            }
            
            if (!(condition.left instanceof BinaryExpression) || !(condition.right instanceof Variable)) {
                return false;
            }
            
            BinaryExpression comparison = (BinaryExpression)condition.left;
            return comparison.operator == BinaryExpression.Operator.LT;
        }
        
        // For LTE: condition should be something like (expr1 ~= 0) where expr1 is (var1 <= var2)
        if (targetOpcode == VMOp.LTE) {
            if (condition.operator != BinaryExpression.Operator.NEQ) {
                return false;
            }
            
            if (!(condition.left instanceof BinaryExpression) || !(condition.right instanceof Variable)) {
                return false;
            }
            
            BinaryExpression comparison = (BinaryExpression)condition.left;
            return comparison.operator == BinaryExpression.Operator.LTE;
        }
        
        return false;
    }
    
    @Override
    public String analyze(Function fn) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Comparison Handler Analysis for %s:\n", targetOpcode));
        sb.append(analyzeStructure(fn));
        
        List<Node> ifStatements = fn.block.getChildren(If.class);
        sb.append(String.format("If statements: %d (expected: %d)\n", ifStatements.size(), expectedIfStatements));
        
        int loadConstantCount = 0;
        for (Node node : ifStatements) {
            if (isLoadConstantPattern((If)node)) {
                loadConstantCount++;
            }
        }
        sb.append(String.format("Load constant patterns: %d\n", loadConstantCount));
        
        return sb.toString();
    }
    
    @Override
    public Set<VMOperand> getStackOperands() {
        Set<VMOperand> operands = new HashSet<>();
        operands.add(VMOperand.A); // Result destination
        operands.add(VMOperand.B); // First comparison operand
        operands.add(VMOperand.C); // Second comparison operand
        return operands;
    }
    
    @Override
    public boolean hasLoops() {
        return false; // Comparison handlers don't loop
    }
    
    @Override
    public boolean hasConditionals() {
        return true; // They are all about conditionals
    }
    
    @Override
    public BinaryExpression.Operator getArithmeticOperator() {
        return null; // Comparison handlers don't do arithmetic
    }
    
    @Override
    protected Function getFunction() {
        throw new UnsupportedOperationException("Not used in this context");
    }
}