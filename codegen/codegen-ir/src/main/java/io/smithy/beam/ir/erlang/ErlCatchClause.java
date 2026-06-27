package io.smithy.beam.ir.erlang;

public final class ErlCatchClause {
    private final ErlPattern exceptionClass;
    private final ErlPattern reason;
    private final ErlPattern stackOrNull;
    private final ErlExpr body;

    public ErlCatchClause(
            ErlPattern exceptionClass,
            ErlPattern reason,
            ErlPattern stackOrNull,
            ErlExpr body) {
        this.exceptionClass = exceptionClass;
        this.reason = reason;
        this.stackOrNull = stackOrNull;
        this.body = body;
    }

    public static ErlCatchClause catchClause(
            ErlPattern exceptionClass, ErlPattern reason, ErlExpr body) {
        return new ErlCatchClause(exceptionClass, reason, null, body);
    }

    public ErlPattern exceptionClass() {
        return exceptionClass;
    }

    public ErlPattern reason() {
        return reason;
    }

    public ErlPattern stackOrNull() {
        return stackOrNull;
    }

    public ErlExpr body() {
        return body;
    }

    public String asString() {
        StringBuilder sb = new StringBuilder();
        sb.append(exceptionClass.asString()).append(':').append(reason.asString());
        if (stackOrNull != null) {
            sb.append(':').append(stackOrNull.asString());
        }
        sb.append(" -> ").append(body.asString());
        return sb.toString();
    }
}
