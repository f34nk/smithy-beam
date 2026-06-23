package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlGuard implements IrObject {
    private final String function;
    private final List<ErlExpr> args;

    public ErlGuard(String function, List<ErlExpr> args) {
        this.function = function;
        this.args = List.copyOf(args);
    }

    public String function() {
        return function;
    }

    public List<ErlExpr> args() {
        return args;
    }

    @Override
    public List<String> lines() {
        StringBuilder sb = new StringBuilder(function).append('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args.get(i).asString());
        }
        sb.append(')');
        return List.of(sb.toString());
    }
}
