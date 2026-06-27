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
