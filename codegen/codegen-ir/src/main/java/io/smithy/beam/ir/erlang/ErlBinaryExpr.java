package io.smithy.beam.ir.erlang;

public final class ErlBinaryExpr implements ErlBinarySegment {
    private final ErlExpr expr;
    private final boolean binaryType;
    private final String typeSpecOrNull;

    public ErlBinaryExpr(ErlExpr expr, boolean binaryType, String typeSpecOrNull) {
        this.expr = expr;
        this.binaryType = binaryType;
        this.typeSpecOrNull = typeSpecOrNull;
    }

    public static ErlBinaryExpr expr(ErlExpr expr, boolean binaryType) {
        return new ErlBinaryExpr(expr, binaryType, null);
    }

    public static ErlBinaryExpr expr(ErlExpr expr, String typeSpec) {
        return new ErlBinaryExpr(expr, false, typeSpec);
    }

    public ErlExpr expr() {
        return expr;
    }

    public boolean binaryType() {
        return binaryType;
    }

    public String typeSpecOrNull() {
        return typeSpecOrNull;
    }

    public String asString() {
        StringBuilder sb = new StringBuilder();
        boolean wrap = expr instanceof ErlCall || expr instanceof ErlCallLocal;
        if (wrap) {
            sb.append('(');
        }
        sb.append(expr.asString());
        if (wrap) {
            sb.append(')');
        }
        if (typeSpecOrNull != null) {
            if ("binary".equals(typeSpecOrNull)) {
                sb.append("/binary");
            } else {
                sb.append(':').append(typeSpecOrNull);
            }
        } else if (binaryType) {
            sb.append("/binary");
        }
        return sb.toString();
    }
}
