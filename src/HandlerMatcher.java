import ASTNodes.*;
import LuaVM.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Manages handler signatures and attempts to match them against VM handlers
 */
public class HandlerMatcher {
    private List<HandlerSignature> signatures;
    private static final Logger logger = Logger.getLogger(HandlerMatcher.class.getName());
    
    public HandlerMatcher() {
        this.signatures = new ArrayList<>();
        initializeSignatures();
    }
    
    private void initializeSignatures() {
        // Arithmetic operations
        signatures.add(new ArithmeticHandlerSignature(VMOp.ADD, BinaryExpression.Operator.ADD, "Addition operation"));
        signatures.add(new ArithmeticHandlerSignature(VMOp.SUB, BinaryExpression.Operator.SUB, "Subtraction operation"));
        signatures.add(new ArithmeticHandlerSignature(VMOp.MUL, BinaryExpression.Operator.MUL, "Multiplication operation"));
        signatures.add(new ArithmeticHandlerSignature(VMOp.DIV, BinaryExpression.Operator.REAL_DIV, "Division operation"));
        signatures.add(new ArithmeticHandlerSignature(VMOp.MOD, BinaryExpression.Operator.MOD, "Modulo operation"));
        signatures.add(new ArithmeticHandlerSignature(VMOp.POW, BinaryExpression.Operator.POW, "Power operation"));
        
        // Simple assignments
        signatures.add(new SimpleAssignHandlerSignature(VMOp.MOVE, SimpleAssignHandlerSignature.PatternType.VAR_TO_VAR, "Move operation"));
        signatures.add(new SimpleAssignHandlerSignature(VMOp.LOADK, SimpleAssignHandlerSignature.PatternType.STACK_TO_CONST, "Load constant"));
        signatures.add(new SimpleAssignHandlerSignature(VMOp.GETGLOBAL, SimpleAssignHandlerSignature.PatternType.STACK_TO_ENV, "Get global"));
        signatures.add(new SimpleAssignHandlerSignature(VMOp.GETUPVAL, SimpleAssignHandlerSignature.PatternType.STACK_TO_UPVAL, "Get upvalue"));
        signatures.add(new SimpleAssignHandlerSignature(VMOp.NEWTABLE, SimpleAssignHandlerSignature.PatternType.TABLE_CONSTRUCT, "New table"));
        signatures.add(new SimpleAssignHandlerSignature(VMOp.UNM, SimpleAssignHandlerSignature.PatternType.UNARY_OP, "Unary minus"));
        signatures.add(new SimpleAssignHandlerSignature(VMOp.NOT, SimpleAssignHandlerSignature.PatternType.UNARY_OP, "Logical not"));
        signatures.add(new SimpleAssignHandlerSignature(VMOp.LEN, SimpleAssignHandlerSignature.PatternType.UNARY_OP, "Length operator"));
        
        // Load operations with specialized patterns
        signatures.add(new LoadHandlerSignature(VMOp.LOADBOOL, LoadHandlerSignature.LoadType.LOADBOOL, "Load boolean"));
        signatures.add(new LoadHandlerSignature(VMOp.LOADNIL, LoadHandlerSignature.LoadType.LOADNIL, "Load nil"));
        signatures.add(new LoadHandlerSignature(VMOp.SETLIST, LoadHandlerSignature.LoadType.SETLIST, "Set list"));
        
        // Comparisons
        signatures.add(new ComparisonHandlerSignature(VMOp.EQ, BinaryExpression.Operator.EQ, 3, "Equality comparison"));
        signatures.add(new ComparisonHandlerSignature(VMOp.LT, BinaryExpression.Operator.LT, 3, "Less than comparison"));
        signatures.add(new ComparisonHandlerSignature(VMOp.LTE, BinaryExpression.Operator.LTE, 3, "Less than or equal comparison"));
        
        // Control flow operations
        signatures.add(new ControlFlowHandlerSignature(VMOp.JUMP, ControlFlowHandlerSignature.ControlFlowType.JUMP, "Jump operation"));
        signatures.add(new ControlFlowHandlerSignature(VMOp.CALL, ControlFlowHandlerSignature.ControlFlowType.CALL, "Function call"));
        signatures.add(new ControlFlowHandlerSignature(VMOp.TAILCALL, ControlFlowHandlerSignature.ControlFlowType.TAILCALL, "Tail call"));
        signatures.add(new ControlFlowHandlerSignature(VMOp.RETURN, ControlFlowHandlerSignature.ControlFlowType.RETURN, "Return from function"));
        signatures.add(new ControlFlowHandlerSignature(VMOp.FORLOOP, ControlFlowHandlerSignature.ControlFlowType.FORLOOP, "For loop iteration"));
        signatures.add(new ControlFlowHandlerSignature(VMOp.FORPREP, ControlFlowHandlerSignature.ControlFlowType.FORPREP, "For loop preparation"));
        signatures.add(new ControlFlowHandlerSignature(VMOp.TFORLOOP, ControlFlowHandlerSignature.ControlFlowType.TFORLOOP, "Table for loop"));
        signatures.add(new ControlFlowHandlerSignature(VMOp.CLOSE, ControlFlowHandlerSignature.ControlFlowType.CLOSE, "Close variable scope"));
        signatures.add(new ControlFlowHandlerSignature(VMOp.CLOSURE, ControlFlowHandlerSignature.ControlFlowType.CLOSURE, "Create closure"));
        signatures.add(new ControlFlowHandlerSignature(VMOp.VARARG, ControlFlowHandlerSignature.ControlFlowType.VARARG, "Variable arguments"));
    }
    
    /**
     * Attempt to identify the VM operation for the given function
     * @param fn the function to identify
     * @param opcodeIndex the opcode index for reporting
     * @return the identified VMOp or null if no match found
     */
    public VMOp identifyHandler(Function fn, int opcodeIndex) {
        logger.log(Level.FINE, String.format("Attempting to identify handler at opcode index %d", opcodeIndex));
        
        for (HandlerSignature signature : signatures) {
            if (signature.matches(fn)) {
                logger.log(Level.INFO, String.format("Matched handler at index %d: %s", opcodeIndex, signature));
                return signature.getTargetOpcode();
            }
        }
        
        // Log detailed analysis for failed identification
        logFailedIdentification(fn, opcodeIndex);
        return null;
    }
    
    /**
     * Get detailed analysis of why all signatures failed to match
     * @param fn the function to analyze
     * @param opcodeIndex the opcode index
     * @return detailed analysis string
     */
    public String getDetailedAnalysis(Function fn, int opcodeIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Detailed analysis for handler at opcode index %d:\n", opcodeIndex));
        sb.append("=============================================================\n");
        
        for (HandlerSignature signature : signatures) {
            sb.append(String.format("\nTesting signature: %s\n", signature));
            sb.append("Signature analysis:\n");
            sb.append(signature.analyze(fn));
            sb.append("Match result: ");
            if (signature.matches(fn)) {
                sb.append("MATCH\n");
            } else {
                sb.append("NO MATCH\n");
            }
            sb.append("-------------------------------------------------------------\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Get analysis for a specific signature
     * @param signature the signature to test
     * @param fn the function to analyze
     * @return analysis string
     */
    public String analyzeSignature(HandlerSignature signature, Function fn) {
        return signature.analyze(fn);
    }
    
    /**
     * Check if any signature in our registry matches the given VMOp
     * @param opcode the VM operation to check
     * @return true if we have a signature for this opcode
     */
    public boolean hasSignatureFor(VMOp opcode) {
        return signatures.stream().anyMatch(s -> s.getTargetOpcode() == opcode);
    }
    
    /**
     * Get all registered signatures
     * @return list of all signatures
     */
    public List<HandlerSignature> getAllSignatures() {
        return new ArrayList<>(signatures);
    }
    
    /**
     * Get signatures that match the given function
     * @param fn the function to test
     * @return list of matching signatures
     */
    public List<HandlerSignature> getMatchingSignatures(Function fn) {
        List<HandlerSignature> matches = new ArrayList<>();
        for (HandlerSignature signature : signatures) {
            if (signature.matches(fn)) {
                matches.add(signature);
            }
        }
        return matches;
    }
    
    private void logFailedIdentification(Function fn, int opcodeIndex) {
        logger.log(Level.WARNING, String.format("Failed to identify handler at opcode index %d", opcodeIndex));
        
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, getDetailedAnalysis(fn, opcodeIndex));
        } else {
            // Log a shorter summary if fine logging is disabled
            logger.log(Level.INFO, String.format("Handler structure: %d statements, %d ifs, %d assigns", 
                    fn.block.stmts.size(),
                    fn.block.getChildren(If.class).size(),
                    fn.block.getChildren(Assign.class).size()));
        }
    }
    
    /**
     * Add a new signature to the matcher
     * @param signature the signature to add
     */
    public void addSignature(HandlerSignature signature) {
        signatures.add(signature);
    }
    
    /**
     * Remove a signature from the matcher
     * @param opcode the VM operation to remove
     * @return true if a signature was removed
     */
    public boolean removeSignature(VMOp opcode) {
        return signatures.removeIf(s -> s.getTargetOpcode() == opcode);
    }
    
    /**
     * Get statistics about signature coverage
     * @return coverage statistics
     */
    public Map<String, Object> getCoverageStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSignatures", signatures.size());
        
        // Count signatures by category
        long arithmeticCount = signatures.stream()
                .filter(s -> s instanceof ArithmeticHandlerSignature).count();
        long simpleAssignCount = signatures.stream()
                .filter(s -> s instanceof SimpleAssignHandlerSignature).count();
        long comparisonCount = signatures.stream()
                .filter(s -> s instanceof ComparisonHandlerSignature).count();
        
        stats.put("arithmeticSignatures", arithmeticCount);
        stats.put("simpleAssignSignatures", simpleAssignCount);
        stats.put("comparisonSignatures", comparisonCount);
        stats.put("otherSignatures", signatures.size() - arithmeticCount - simpleAssignCount - comparisonCount);
        
        return stats;
    }
}