package ASTNodes;

public class Continue extends Statement {
    @Override
    public String line() {
        return "continue";
    }

    @Override
    public Node accept(IASTVisitor visitor) {
        visitor.visit(this);

        return this;
    }

    @Override
    public Continue clone() {
        return new Continue();
    }
}
