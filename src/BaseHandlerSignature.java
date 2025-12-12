import ASTNodes.*;
import LuaVM.*;
import java.util.*;

/**
 * Base implementation of HandlerSignature with common analysis functionality
 */
public abstract class BaseHandlerSignature extends HandlerSignature {
    
    public BaseHandlerSignature(VMOp targetOpcode, String description) {
        super(targetOpcode, description);
    }
    
    @Override
    public Set<VMOperand> getStackOperands() {
        // Default implementation - subclasses should override
        return new HashSet<>();
    }
    
    @Override
    public boolean hasLoops() {
        return hasLoops(getFunction());
    }
    
    @Override
    public boolean hasConditionals() {
        return hasConditionals(getFunction());
    }
    
    @Override
    public BinaryExpression.Operator getArithmeticOperator() {
        return getArithmeticOperator(getFunction());
    }
    
    protected Function getFunction() {
        // This should be implemented by subclasses to provide the function being analyzed
        throw new UnsupportedOperationException("Subclasses must implement getFunction()");
    }
    
    protected boolean hasLoops(Function fn) {
        List<Node> forSteps = fn.block.getChildren(ForStep.class);
        List<Node> forIns = fn.block.getChildren(ForIn.class);
        List<Node> whiles = fn.block.getChildren(While.class);
        List<Node> repeats = fn.block.getChildren(Repeat.class);
        
        return !forSteps.isEmpty() || !forIns.isEmpty() || !whiles.isEmpty() || !repeats.isEmpty();
    }
    
    protected boolean hasConditionals(Function fn) {
        List<Node> ifs = fn.block.getChildren(If.class);
        return !ifs.isEmpty();
    }
    
    protected BinaryExpression.Operator getArithmeticOperator(Function fn) {
        List<Node> binaryExpressions = fn.block.getChildren(BinaryExpression.class);
        
        for (Node node : binaryExpressions) {
            BinaryExpression expr = (BinaryExpression)node;
            switch (expr.operator) {
                case ADD:
                case SUB:
                case MUL:
                case REAL_DIV:
                case INTEGER_DIV:
                case MOD:
                case POW:
                    return expr.operator;
                default:
                    break;
            }
        }
        
        return null;
    }
    
    protected List<Statement> getStatements(Function fn) {
        return fn.block.stmts;
    }
    
    protected List<Node> getChildren(Function fn) {
        return fn.block.getChildren();
    }
    
    protected boolean isStackAssign(Assign assign, String targetVar) {
        if (assign.vars.vars.size() == 1 && assign.exprs.exprs.size() == 1) {
            Variable var = (Variable)assign.vars.vars.get(0);
            return var.symbol().equals(targetVar);
        }
        return false;
    }
    
    protected boolean isVarToVarAssign(Assign assign, String fromVar, String toVar) {
        if (assign.vars.vars.size() == 1 && assign.exprs.exprs.size() == 1) {
            Variable lhs = (Variable)assign.vars.vars.get(0);
            Expression rhs = assign.exprs.exprs.get(0);
            
            if (rhs instanceof Variable) {
                Variable rhsVar = (Variable)rhs;
                return lhs.symbol().equals(toVar) && rhsVar.symbol().equals(fromVar);
            }
        }
        return false;
    }
    
    protected boolean isConstToStackAssign(Assign assign, String stackVar, String constVar) {
        if (assign.vars.vars.size() == 1 && assign.exprs.exprs.size() == 1) {
            Variable lhs = (Variable)assign.vars.vars.get(0);
            Expression rhs = assign.exprs.exprs.get(0);
            
            if (lhs.symbol().equals(stackVar) && rhs instanceof Variable) {
                Variable rhsVar = (Variable)rhs;
                return rhsVar.symbol().equals(constVar);
            }
        }
        return false;
    }
    
    protected boolean isLoadConstantPattern(If stmt) {
        // Check if this is a load-from-constants pattern
        if (stmt.elseifstmt.size() != 0)
            return false;
            
        if (stmt.ifstmt.first instanceof BinaryExpression &&
                ((BinaryExpression)stmt.ifstmt.first).operator == BinaryExpression.Operator.GT) {
            if (stmt.ifstmt.second.stmts.size() == 1 && stmt.ifstmt.second.stmts.get(0) instanceof Assign) {
                Assign loadAssign = (Assign)stmt.ifstmt.second.stmts.get(0);
                if (isStackAssign(loadAssign, "constants")) {
                    if (stmt.elsestmt != null && stmt.elsestmt.stmts.size() == 1 && stmt.elsestmt.stmts.get(0) instanceof Assign) {
                        return isStackAssign((Assign)stmt.elsestmt.stmts.get(0), "stack");
                    }
                }
            }
        }
        return false;
    }
    
    protected boolean isBinaryOpAssign(Assign assign, String targetVar, BinaryExpression.Operator op) {
        if (assign.vars.vars.size() == 1 && assign.exprs.exprs.size() == 1) {
            Variable lhs = (Variable)assign.vars.vars.get(0);
            Expression rhs = assign.exprs.exprs.get(0);
            
            if (lhs.symbol().equals(targetVar) && rhs instanceof BinaryExpression) {
                BinaryExpression binExpr = (BinaryExpression)rhs;
                return binExpr.operator == op;
            }
        }
        return false;
    }
    
    protected String analyzeStructure(Function fn) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Function has %d statements\n", getStatements(fn).size()));
        
        int ifCount = fn.block.getChildren(If.class).size();
        int assignCount = fn.block.getChildren(Assign.class).size();
        int forStepCount = fn.block.getChildren(ForStep.class).size();
        int forInCount = fn.block.getChildren(ForIn.class).size();
        int localDeclCount = fn.block.getChildren(LocalDeclare.class).size();
        
        sb.append(String.format("If statements: %d, Assigns: %d, ForStep: %d, ForIn: %d, LocalDecl: %d\n",
                ifCount, assignCount, forStepCount, forInCount, localDeclCount));
        
        BinaryExpression.Operator arithOp = getArithmeticOperator(fn);
        if (arithOp != null) {
            sb.append(String.format("Arithmetic operator: %s\n", arithOp));
        }
        
        sb.append(String.format("Has conditionals: %s, Has loops: %s\n", 
                hasConditionals(fn), hasLoops(fn)));
        
        return sb.toString();
    }
}