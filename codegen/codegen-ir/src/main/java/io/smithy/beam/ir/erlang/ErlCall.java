package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
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

    public static ErlCall filtermap(ErlFun fun, ErlExpr listArg) {
        return call("lists", "filtermap", fun, listArg);
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
        return lines(0);
    }

    @Override
    public List<String> lines(int indent) {
        if (isFiltermap()) {
            ErlFun fun = (ErlFun) args.get(0);
            List<String> out = new ArrayList<>();
            out.add(IrObject.indent(indent) + "lists:filtermap(");
            List<String> funLines = new ArrayList<>(fun.lines(indent + 1));
            String lastFunLine = funLines.remove(funLines.size() - 1);
            out.addAll(funLines);
            out.add(lastFunLine + ",");
            out.add(IrObject.indent(indent + 1) + args.get(1).asString());
            out.add(IrObject.indent(indent) + ")");
            return out;
        }
        return List.of(IrObject.indent(indent) + inlineAsString());
    }

    private boolean isFiltermap() {
        return "filtermap".equals(function)
                && "lists".equals(module.value())
                && args.size() == 2
                && args.get(0) instanceof ErlFun;
    }

    private String inlineAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(module.asString()).append(':').append(function).append('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args.get(i).asString());
        }
        sb.append(')');
        return sb.toString();
    }
}
