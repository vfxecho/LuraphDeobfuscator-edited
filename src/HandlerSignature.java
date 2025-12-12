import ASTNodes.*;
import LuaVM.*;
import java.util.*;

/**
 * Abstract base class for handler signatures that captures structural characteristics
 * of VM handlers without relying on brittle statement counts or specific ordering.
 */
public abstract class HandlerSignature {
    protected VMOp targetOpcode;
    protected String description;
    
    public HandlerSignature(VMOp targetOpcode, String description) {
        this.targetOpcode = targetOpcode;
        this.description = description;
    }
    
    public VMOp getTargetOpcode() {
        return targetOpcode;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this signature matches the given function
     * @param fn the function to test
     * @return true if this signature matches
     */
    public abstract boolean matches(Function fn);
    
    /**
     * Get detailed analysis of why this handler does/doesn't match
     * @param fn the function to analyze
     * @return analysis string
     */
    public abstract String analyze(Function fn);
    
    /**
     * Get the specific stack operands this handler reads/writes
     * @return set of VMOperand types
     */
    public abstract Set<VMOperand> getStackOperands();
    
    /**
     * Check if this handler contains loops
     * @return true if handler contains loop constructs
     */
    public abstract boolean hasLoops();
    
    /**
     * Check if this handler contains conditional logic
     * @return true if handler contains if/else constructs
     */
    public abstract boolean hasConditionals();
    
    /**
     * Get the arithmetic operator used by this handler (if any)
     * @return BinaryExpression.Operator or null
     */
    public abstract BinaryExpression.Operator getArithmeticOperator();
    
    @Override
    public String toString() {
        return String.format("%s: %s", targetOpcode, description);
    }
}