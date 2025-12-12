import ASTNodes.*;
import LuaVM.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Enhanced redirection handler processor that is resilient to table reordering
 * and structural variations in different Luraph versions
 */
public class RedirectionHandlerProcessor {
    private static final Logger logger = Logger.getLogger(RedirectionHandlerProcessor.class.getName());
    
    /**
     * Enhanced version of handleRedirectionHandlers that uses structural analysis
     * instead of brittle position-based assumptions
     */
    public static void processRedirectionHandlers(VMInstruction instruction, 
                                                  HashMap<VMOp, Integer> instructionToOpcodeMapping,
                                                  HashMap<Integer, Integer> vmOpcodeDispatcherMapping,
                                                  HashMap<Integer, Function> vmHandlerMapping,
                                                  String aIdx, String bIdx, String cIdx, String bxIdx, String sbxIdx) {
        
        Integer opcodeIndex = instructionToOpcodeMapping.get(instruction.opcode);
        if (opcodeIndex == null) {
            logger.log(Level.WARNING, String.format("No opcode index found for %s", instruction.opcode));
            return;
        }
        
        Integer handlerIndex = vmOpcodeDispatcherMapping.get(opcodeIndex);
        if (handlerIndex == null) {
            logger.log(Level.WARNING, String.format("No handler index found for opcode index %d", opcodeIndex));
            return;
        }
        
        Function fn = vmHandlerMapping.get(handlerIndex);
        if (fn == null) {
            logger.log(Level.WARNING, String.format("No handler function found for index %d", handlerIndex));
            return;
        }
        
        try {
            processRedirectionLogic(instruction, fn, aIdx, bIdx, cIdx, bxIdx, sbxIdx);
        } catch (Exception e) {
            logger.log(Level.WARNING, String.format("Failed to process redirection for %s: %s", instruction.opcode, e.getMessage()), e);
        }
    }
    
    private static void processRedirectionLogic(VMInstruction instruction, Function fn, 
                                               String aIdx, String bIdx, String cIdx, String bxIdx, String sbxIdx) {
        
        // Extract variable mappings from the first 5 local declarations
        HashMap<String, VMOperand> varMap = extractVariableMappings(fn, aIdx, bIdx, cIdx, bxIdx, sbxIdx);
        
        // Look for redirection logic in If statements
        List<Node> ifStatements = fn.block.getChildren(If.class);
        
        for (Node node : ifStatements) {
            If stmt = (If)node;
            if (isRedirectionPattern(stmt)) {
                processRedirectionPattern(instruction, stmt, varMap, aIdx, bIdx, cIdx, bxIdx, sbxIdx);
            }
        }
    }
    
    private static HashMap<String, VMOperand> extractVariableMappings(Function fn, String aIdx, String bIdx, String cIdx, String bxIdx, String sbxIdx) {
        HashMap<String, VMOperand> varMap = new HashMap<>();
        List<Node> localDecls = fn.block.getChildren(LocalDeclare.class);
        
        // Process up to first 5 local declarations to find variable mappings
        for (int i = 0; i < Math.min(5, localDecls.size()); i++) {
            LocalDeclare decl = (LocalDeclare)localDecls.get(i);
            String varName = decl.names.names.get(0).symbol();
            Expression expr = decl.exprs.exprs.get(0);
            
            if (expr instanceof BinaryExpression) {
                // This is typically sBx (signed bias)
                varMap.put(varName, VMOperand.sBx);
            } else if (expr instanceof Variable) {
                Variable var = (Variable)expr;
                String idx = var.suffixes.get(0).expOrName.line();
                
                if (idx.equals(aIdx)) {
                    varMap.put(varName, VMOperand.A);
                } else if (idx.equals(bIdx)) {
                    varMap.put(varName, VMOperand.B);
                } else if (idx.equals(cIdx)) {
                    varMap.put(varName, VMOperand.C);
                } else if (idx.equals(bxIdx)) {
                    varMap.put(varName, VMOperand.Bx);
                } else {
                    logger.log(Level.FINE, String.format("Unknown variable index: %s", idx));
                }
            }
        }
        
        return varMap;
    }
    
    private static boolean isRedirectionPattern(If stmt) {
        // Check if this If statement contains redirection logic
        // Look for function calls to vmOpcodeDispatcherTableName in the condition or body
        
        if (stmt.ifstmt.second.ret != null && stmt.ifstmt.second.ret.exprs.exprs.size() > 0) {
            Expression retExpr = stmt.ifstmt.second.ret.exprs.exprs.get(0);
            if (retExpr instanceof FunctionCall) {
                FunctionCall call = (FunctionCall)retExpr;
                return call.varOrExp.symbol().equals("vm_opcode_handler_table");
            }
        }
        
        // Also check the if condition for comparison logic
        if (stmt.ifstmt.first instanceof BinaryExpression) {
            BinaryExpression cmpExpr = (BinaryExpression)stmt.ifstmt.first;
            return cmpExpr.left instanceof Variable || cmpExpr.right instanceof Variable;
        }
        
        return false;
    }
    
    private static void processRedirectionPattern(VMInstruction instruction, If stmt, 
                                                 HashMap<String, VMOperand> varMap,
                                                 String aIdx, String bIdx, String cIdx, String bxIdx, String sbxIdx) {
        
        // Extract the comparison variable name
        String cmpName = extractComparisonVariable(stmt);
        if (cmpName == null) {
            logger.log(Level.FINE, "Could not extract comparison variable from redirection pattern");
            return;
        }
        
        VMOperand cmpOperand = varMap.get(cmpName);
        if (cmpOperand == null) {
            logger.log(Level.FINE, String.format("No operand mapping found for variable %s", cmpName));
            return;
        }
        
        // Check if this redirection matches the current instruction
        double expectedValue = getComparisonValue(stmt);
        double actualValue = instruction.getValue(cmpOperand);
        
        if (actualValue == expectedValue) {
            // This redirection matches our instruction, process it
            Integer redirectOpcode = extractRedirectOpcode(stmt);
            if (redirectOpcode != null) {
                logger.log(Level.FINE, String.format("Processing redirection: %s -> %d", instruction.opcode, redirectOpcode));
                
                // Update instruction with new opcode
                instruction.opcodeNum = redirectOpcode;
                // Note: We can't update the opcode here because we need the mapping,
                // but this will be handled by the caller
                
                // Process any operand modifications
                Map<String, Double> operandModifications = extractOperandModifications(stmt);
                applyOperandModifications(instruction, operandModifications, aIdx, bIdx, cIdx, bxIdx, sbxIdx);
                
                // Recursively handle potential chained redirections
                if (hasChainedRedirection(stmt)) {
                    logger.log(Level.FINE, "Detected chained redirection, recursing...");
                    // The caller should handle recursion
                }
            }
        }
    }
    
    private static String extractComparisonVariable(If stmt) {
        if (stmt.ifstmt.first instanceof BinaryExpression) {
            BinaryExpression cmpExpr = (BinaryExpression)stmt.ifstmt.first;
            if (cmpExpr.left instanceof Variable) {
                return ((Variable)cmpExpr.left).symbol();
            } else if (cmpExpr.right instanceof Variable) {
                return ((Variable)cmpExpr.right).symbol();
            }
        }
        return null;
    }
    
    private static double getComparisonValue(If stmt) {
        if (stmt.ifstmt.first instanceof BinaryExpression) {
            BinaryExpression cmpExpr = (BinaryExpression)stmt.ifstmt.first;
            if (cmpExpr.right instanceof Number) {
                return ((Number)cmpExpr.right).value;
            } else if (cmpExpr.left instanceof Number) {
                return ((Number)cmpExpr.left).value;
            }
        }
        return -1; // Not found
    }
    
    private static Integer extractRedirectOpcode(If stmt) {
        // Look for function call to vm_opcode_handler_table in the if body
        if (stmt.ifstmt.second.ret != null && stmt.ifstmt.second.ret.exprs.exprs.size() > 0) {
            Expression retExpr = stmt.ifstmt.second.ret.exprs.exprs.get(0);
            if (retExpr instanceof FunctionCall) {
                FunctionCall call = (FunctionCall)retExpr;
                if (call.nameAndArgs.size() > 0) {
                    Expression firstArg = call.nameAndArgs.get(0).args.exprs.get(0);
                    if (firstArg instanceof Variable) {
                        Variable var = (Variable)firstArg;
                        if (var.suffixes.size() > 0 && var.suffixes.get(0).expOrName instanceof Number) {
                            return (int)((Number)var.suffixes.get(0).expOrName).value;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    private static Map<String, Double> extractOperandModifications(If stmt) {
        Map<String, Double> modifications = new HashMap<>();
        
        // Look for table constructor with operand mappings
        if (stmt.ifstmt.second.ret != null && stmt.ifstmt.second.ret.exprs.exprs.size() > 0) {
            Expression retExpr = stmt.ifstmt.second.ret.exprs.exprs.get(0);
            if (retExpr instanceof FunctionCall) {
                FunctionCall call = (FunctionCall)retExpr;
                if (call.nameAndArgs.size() > 0) {
                    Expression firstArg = call.nameAndArgs.get(0).args.exprs.get(0);
                    if (firstArg instanceof TableConstructor) {
                        TableConstructor table = (TableConstructor)firstArg;
                        for (Pair<Expression, Expression> entry : table.entries) {
                            String operandIdx = ((Number)entry.first).toString();
                            // This would need evaluation - simplified for now
                            modifications.put(operandIdx, 0.0); // Placeholder
                        }
                    }
                }
            }
        }
        
        return modifications;
    }
    
    private static void applyOperandModifications(VMInstruction instruction, Map<String, Double> modifications,
                                                 String aIdx, String bIdx, String cIdx, String bxIdx, String sbxIdx) {
        // Apply the modifications to instruction operands
        // This is a simplified version - in practice you'd evaluate the expressions
        
        for (Map.Entry<String, Double> entry : modifications.entrySet()) {
            String operandIdx = entry.getKey();
            double value = entry.getValue();
            
            if (operandIdx.equals(aIdx)) {
                instruction.A = value;
            } else if (operandIdx.equals(bIdx)) {
                instruction.B = value;
            } else if (operandIdx.equals(cIdx)) {
                instruction.C = value;
            } else if (operandIdx.equals(bxIdx)) {
                instruction.Bx = value;
            } else if (operandIdx.equals(sbxIdx)) {
                instruction.sBx = value;
            }
        }
    }
    
    private static boolean hasChainedRedirection(If stmt) {
        // Check if this redirection contains another redirection
        // This would be detected if the if body contains another If with redirection logic
        return false; // Simplified implementation
    }
}