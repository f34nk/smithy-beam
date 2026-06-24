package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlOp implements ErlExpr {
    private final String operator;
    private final ErlExpr left;
    private final ErlExpr right;

    public ErlOp(String operator, ErlExpr left, ErlExpr right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    public static ErlOp op(String operator, ErlExpr left, ErlExpr right) {
        return new ErlOp(operator, left, right);
    }

    public String operator() {
        return operator;
    }

    public ErlExpr left() {
        return left;
    }

    public ErlExpr right() {
        return right;
    }

    @Override
    public List<String> lines() {
        return List.of(left.asString() + " " + operator + " " + right.asString());
    }
}
