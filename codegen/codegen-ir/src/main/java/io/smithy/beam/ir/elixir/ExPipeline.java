package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExPipeline implements ExExpr {
  private final String bindingVar;
  private final ExExpr initial;
  private final List<ExExpr> steps;

  public ExPipeline(String bindingVar, ExExpr initial, List<ExExpr> steps) {
    this.bindingVar = bindingVar;
    this.initial = initial;
    this.steps = List.copyOf(steps);
  }

  public static ExPipeline pipeline(String bindingVar, ExExpr initial, List<ExExpr> steps) {
    return new ExPipeline(bindingVar, initial, steps);
  }

  public static ExPipeline pipeline(String bindingVar, ExExpr initial, ExExpr... steps) {
    return new ExPipeline(bindingVar, initial, List.of(steps));
  }

  /** Pipe chain without assignment (function return value). */
  public static ExPipeline pipeChain(ExExpr initial, ExExpr... steps) {
    return new ExPipeline("", initial, List.of(steps));
  }

  public String bindingVar() {
    return bindingVar;
  }

  public ExExpr initial() {
    return initial;
  }

  public List<ExExpr> steps() {
    return steps;
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    if (bindingVar.isEmpty()) {
      if (initial.lines().size() == 1) {
        out.add(IrObject.indent(indent) + initial.asString());
      } else {
        out.addAll(initial.lines(indent));
      }
      for (ExExpr step : steps) {
        List<String> stepLines = step.lines(indent);
        if (stepLines.isEmpty()) {
          continue;
        }
        String indentPrefix = IrObject.indent(indent);
        String first = stepLines.get(0);
        if (first.startsWith(indentPrefix)) {
          first = first.substring(indentPrefix.length());
        }
        out.add(indentPrefix + "|> " + first);
        for (int i = 1; i < stepLines.size(); i++) {
          out.add(stepLines.get(i));
        }
      }
      return out;
    }
    out.add(IrObject.indent(indent) + bindingVar + " =");
    out.addAll(initial.lines(indent + 1));
    for (ExExpr step : steps) {
      out.add(IrObject.indent(indent + 1) + "|> " + step.asString());
    }
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
