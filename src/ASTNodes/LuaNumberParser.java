package ASTNodes;

public class LuaNumberParser {
    
    public static double parse(String literalValue) {
        if (literalValue == null || literalValue.isEmpty()) {
            throw new NumberFormatException("Empty number literal");
        }
        
        String normalized = literalValue.replaceAll("_", "");
        
        if (normalized.toLowerCase().startsWith("0b")) {
            return parseBinary(normalized.substring(2));
        }
        
        if (normalized.toLowerCase().startsWith("0x")) {
            String hexPart = normalized.substring(2);
            if (hexPart.contains(".") || hexPart.toLowerCase().contains("p")) {
                return parseHexFloat(hexPart);
            } else {
                return parseHex(hexPart);
            }
        }
        
        return Double.parseDouble(normalized);
    }
    
    private static double parseBinary(String binary) {
        long value = 0;
        for (char c : binary.toCharArray()) {
            value = (value << 1) | (c - '0');
        }
        return (double) value;
    }
    
    private static double parseHex(String hex) {
        return (double) Long.parseLong(hex, 16);
    }
    
    private static double parseHexFloat(String hexFloat) {
        String lowerHex = hexFloat.toLowerCase();
        int pIndex = lowerHex.indexOf('p');
        if (pIndex == -1) {
            pIndex = lowerHex.indexOf('P');
        }
        
        if (pIndex != -1) {
            String mantissa = lowerHex.substring(0, pIndex);
            String exponent = lowerHex.substring(pIndex + 1);
            return parseHexMantissaWithExponent(mantissa, exponent);
        }
        
        return Double.parseDouble(lowerHex);
    }
    
    private static double parseHexMantissaWithExponent(String mantissa, String exponent) {
        int expValue;
        if (exponent.startsWith("+")) {
            expValue = Integer.parseInt(exponent.substring(1));
        } else if (exponent.startsWith("-")) {
            expValue = -Integer.parseInt(exponent.substring(1));
        } else {
            expValue = Integer.parseInt(exponent);
        }
        
        double value = parseHexMantissa(mantissa);
        return value * Math.pow(2.0, expValue);
    }
    
    private static double parseHexMantissa(String mantissa) {
        double result = 0.0;
        boolean afterDot = false;
        int fracPosition = 0;
        
        for (char c : mantissa.toCharArray()) {
            if (c == '.') {
                afterDot = true;
                continue;
            }
            
            int digit = Character.digit(c, 16);
            if (digit >= 0) {
                if (afterDot) {
                    fracPosition++;
                    result += digit * Math.pow(16.0, -fracPosition);
                } else {
                    result = result * 16.0 + digit;
                }
            }
        }
        
        return result;
    }
}
