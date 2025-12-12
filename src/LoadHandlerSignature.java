import ASTNodes.*;
import ASTNodes.Number;
import LuaVM.*;
import java.util.*;

/**
 * Signature for load-style operations (LOADBOOL, LOADNIL)
 * These typically have conditional logic for loading different values
 */
public class LoadHandlerSignature extends BaseHandlerSignature {
    private final LoadType loadType;
    
    public enum LoadType {
        LOADBOOL,    // Conditional boolean loading
        LOADNIL,     // Nil loading with loop pattern
        SETLIST      // Table setting with list operations
    }
    
    public LoadHandlerSignature(VMOp targetOpcode, LoadType loadType, String description) {
        super(targetOpcode, description);
        this.loadType = loadType;
    }
    
    @Override
    public boolean matches(Function fn) {
        List<Statement> statements = getStatements(fn);
        
        switch (loadType) {
            case LOADBOOL:
                return matchesLoadBool(fn);
            case LOADNIL:
                return matchesLoadNil(fn);
            case SETLIST:
                return matchesSetList(fn);
            default:
                return false;
        }
    }
    
    private boolean matchesLoadBool(Function fn) {
        List<Statement> statements = getStatements(fn);
        
        // LOADBOOL typically has 7 statements with conditional logic
        if (statements.size() != 7) {
            return false;
        }
        
        // Should have an If statement at the end
        if (!(statements.get(5) instanceof Assign) || !(statements.get(6) instanceof If)) {
            return false;
        }
        
        Assign assign = (Assign)statements.get(5);
        If ifStmt = (If)statements.get(6);
        
        // Check if assign sets stack based on inequality
        if (assign.exprs.exprs.size() == 1 && assign.exprs.exprs.get(0) instanceof BinaryExpression) {
            BinaryExpression expr = (BinaryExpression)assign.exprs.exprs.get(0);
            if (expr.operator != BinaryExpression.Operator.NEQ) {
                return false;
            }
        } else {
            return false;
        }
        
        // Check If statement structure
        if (ifStmt.elsestmt != null || !ifStmt.elseifstmt.isEmpty()) {
            return false; // LOADBOOL should not have else clauses
        }
        
        if (ifStmt.ifstmt.first instanceof BinaryExpression) {
            BinaryExpression binOp = (BinaryExpression)ifStmt.ifstmt.first;
            if (binOp.operator != BinaryExpression.Operator.NEQ) {
                return false;
            }
        } else {
            return false;
        }
        
        return true;
    }
    
    private boolean matchesLoadNil(Function fn) {
        List<Statement> statements = getStatements(fn);
        
        // LOADNIL typically has 6 statements with a ForStep loop
        if (statements.size() != 6) {
            return false;
        }
        
        // Last statement should be ForStep
        if (!(statements.get(5) instanceof ForStep)) {
            return false;
        }
        
        ForStep forStep = (ForStep)statements.get(5);
        if (forStep.step != null) {
            return false; // LOADNIL shouldn't have a step
        }
        
        if (forStep.block.stmts.size() != 1) {
            return false;
        }
        
        Statement innerStmt = forStep.block.stmts.get(0);
        if (!(innerStmt instanceof Assign)) {
            return false;
        }
        
        Assign assign = (Assign)innerStmt;
        // Should be assigning nil to stack
        return isStackToNilAssign(assign);
    }
    
    private boolean matchesSetList(Function fn) {
        List<Statement> statements = getStatements(fn);
        
        // SETLIST typically has 8 statements with a LocalDeclare
        if (statements.size() != 8) {
            return false;
        }
        
        // Should have a LocalDeclare at position 5
        if (!(statements.get(5) instanceof LocalDeclare)) {
            return false;
        }
        
        LocalDeclare decl = (LocalDeclare)statements.get(5);
        if (decl.exprs.exprs.size() != 1 || !(decl.exprs.exprs.get(0) instanceof BinaryExpression)) {
            return false;
        }
        
        BinaryExpression expr = (BinaryExpression)decl.exprs.exprs.get(0);
        // Should be multiplication by 50 (SETLIST batch size)
        if (expr.operator == BinaryExpression.Operator.MUL && 
            expr.right instanceof Number && 
            ((Number)expr.right).value == 50.0) {
            return true;
        }
        
        return false;
    }
    
    private boolean isStackToNilAssign(Assign assign) {
        if (assign.vars.vars.size() == 1 && assign.exprs.exprs.size() == 1) {
            Variable lhs = (Variable)assign.vars.vars.get(0);
            Expression rhs = assign.exprs.exprs.get(0);
            
            return lhs.symbol().equals("stack") && rhs instanceof Nil;
        }
        return false;
    }
    
    @Override
    public String analyze(Function fn) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Load Handler Analysis for %s:\n", targetOpcode));
        sb.append(analyzeStructure(fn));
        sb.append(String.format("Load type: %s\n", loadType));
        
        List<Statement> statements = getStatements(fn);
        if (statements.size() > 0) {
            sb.append(String.format("Final statement type: %s\n", 
                    statements.get(statements.size() - 1).getClass().getSimpleName()));
        }
        
        return sb.toString();
    }
    
    @Override
    public Set<VMOperand> getStackOperands() {
        Set<VMOperand> operands = new HashSet<>();
        operands.add(VMOperand.A); // Destination and value source
        operands.add(VMOperand.B); // Often used in LOADBOOL
        operands.add(VMOperand.C); // Often used in LOADBOOL
        return operands;
    }
    
    @Override
    public boolean hasLoops() {
        return loadType == LoadType.LOADNIL; // Only LOADNIL uses loops
    }
    
    @Override
    public boolean hasConditionals() {
        return loadType == LoadType.LOADBOOL; // Only LOADBOOL uses conditionals
    }
    
    @Override
    public BinaryExpression.Operator getArithmeticOperator() {
        if (loadType == LoadType.SETLIST) {
            return BinaryExpression.Operator.MUL; // SETLIST uses multiplication
        }
        return null;
    }
    
    @Override
    protected Function getFunction() {
        throw new UnsupportedOperationException("Not used in this context");
    }
}