package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExUseAttr implements ExModuleAttribute {
  private final String module;
  private final String optionsOrNull;

  private ExUseAttr(String module, String optionsOrNull) {
    this.module = module;
    this.optionsOrNull = optionsOrNull;
  }

  public static ExUseAttr use(String module) {
    return new ExUseAttr(module, null);
  }

  public static ExUseAttr use(String module, String options) {
    return new ExUseAttr(module, options);
  }

  public String module() {
    return module;
  }

  public String optionsOrNull() {
    return optionsOrNull;
  }

  @Override
  public List<String> lines(int indent) {
    String line = optionsOrNull == null ? "use " + module : "use " + module + ", " + optionsOrNull;
    return List.of(IrObject.indent(indent) + line);
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
