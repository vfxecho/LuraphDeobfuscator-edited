package ASTNodes;

import java.math.BigInteger;

public class Number extends Literal {
    public final double value;

    public Number(String literalValue) {
        String clean = literalValue.replaceAll("_", "");
        if (clean.length() > 2 && (clean.startsWith("0b") || clean.startsWith("0B"))) {
            value = new BigInteger(clean.substring(2), 2).doubleValue();
        }
        else if (clean.length() > 2 && (clean.startsWith("0x") || clean.startsWith("0X"))) {
            // Check if it's already a hex float
            boolean isHexFloat = clean.indexOf('p') >= 0 || clean.indexOf('P') >= 0 || clean.indexOf('.') >= 0;
            if (isHexFloat) {
                 value = Double.parseDouble(clean);
            } else {
                 value = Double.parseDouble(clean + "p0");
            }
        }
        else {
            value = Double.parseDouble(clean);
        }
        value = LuaNumberParser.parse(literalValue);
    }

    public Number(double literalValue) {
        value = literalValue;
    }

    @Override
    public String line() {
        return toString();
    }

    @Override
    public String toString() {
        return Double.toString(value);
    }

    @Override
    public boolean matches(Node obj) {
        if (super.matches(obj)) {
            return value == ((Number)obj).value;
        }

        return false;
    }

    @Override
    public Number clone() {
        return new Number(value);
    }

    @Override
    public Node accept(IASTVisitor visitor) {
        return visitor.visit(this);
    }
}
