package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlCall implements ErlExpr {
    private final ErlAtom module;
    private final String function;
    private final List<ErlExpr> args;

    public ErlCall(ErlAtom module, String function, List<ErlExpr> args) {
        this.module = module;
        this.function = function;
        this.args = List.copyOf(args);
    }

    public static ErlCall call(String module, String function, ErlExpr... args) {
        return new ErlCall(ErlAtom.atom(module), function, List.of(args));
    }

    public ErlAtom module() {
        return module;
    }

    public String function() {
        return function;
    }

    public List<ErlExpr> args() {
        return args;
    }

    @Override
    public List<String> lines() {
        StringBuilder sb = new StringBuilder();
        sb.append(module.asString()).append(':').append(function).append('(');
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
