package io.beam.lang.gleam;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class DefaultGleamRenderer implements Renderer {

  private static final String INDENT = "  ";

  @Override
  public String render(Module module) {
    List<String> parts = new ArrayList<>();

    if (module.moduleDocOrNull() != null) {
      parts.add(renderDoc(module.moduleDocOrNull(), "////"));
    }

    if (!module.imports().isEmpty()) {
      parts.add(
          module.imports().stream().map(this::renderImport).collect(Collectors.joining("\n")));
    }

    for (TypeAlias alias : module.typeAliases()) {
      parts.add(render(alias).stripTrailing());
    }

    for (CustomType customType : module.customTypes()) {
      parts.add(render(customType).stripTrailing());
    }

    for (Function function : module.functions()) {
      parts.add(render(function).stripTrailing());
    }

    if (parts.isEmpty()) {
      return "\n";
    }
    return String.join("\n\n", parts) + "\n";
  }

  @Override
  public String render(TypeAlias typeAlias) {
    StringBuilder out = new StringBuilder();
    if (typeAlias.docOrNull() != null) {
      out.append(renderDoc(typeAlias.docOrNull(), "///")).append('\n');
    }
    if (typeAlias.pub()) {
      out.append("pub ");
    }
    out.append("type ")
        .append(typeAlias.name())
        .append(" = ")
        .append(typeAlias.definition())
        .append('\n');
    return out.toString();
  }

  @Override
  public String render(CustomType customType) {
    StringBuilder out = new StringBuilder();
    if (customType.docOrNull() != null) {
      out.append(renderDoc(customType.docOrNull(), "///")).append('\n');
    }
    if (customType.pub()) {
      out.append("pub ");
    }
    out.append("type ").append(customType.name()).append(" {\n");
    for (Variant variant : customType.variants()) {
      out.append(INDENT).append(renderVariant(variant)).append('\n');
    }
    out.append("}\n");
    return out.toString();
  }

  @Override
  public String render(Function function) {
    StringBuilder out = new StringBuilder();
    if (function.docOrNull() != null) {
      out.append(renderDoc(function.docOrNull(), "///")).append('\n');
    }
    if (function.pub()) {
      out.append("pub ");
    }
    out.append("fn ").append(function.name()).append("(");
    List<Param> params = function.params();
    if (!params.isEmpty()) {
      out.append('\n');
      for (int i = 0; i < params.size(); i++) {
        Param param = params.get(i);
        out.append(INDENT).append(param.name()).append(": ").append(param.typeExpression());
        if (i < params.size() - 1) {
          out.append(',');
        }
        out.append('\n');
      }
    }
    out.append(')');
    if (function.returnTypeOrNull() != null && !function.returnTypeOrNull().isEmpty()) {
      out.append(" -> ").append(function.returnTypeOrNull());
    }
    out.append(" {\n");
    renderIndentedBody(function.body(), out, INDENT);
    out.append("}\n");
    return out.toString();
  }

  @Override
  public String render(Expression expression) {
    StringBuilder out = new StringBuilder();
    render(expression, out, "");
    return out.toString();
  }

  private void renderIndentedBody(Expression body, StringBuilder out, String indent) {
    if (body instanceof BlockExpr block) {
      for (Expression statement : block.statements()) {
        out.append(indent);
        render(statement, out, indent);
        out.append('\n');
      }
      return;
    }
    if (body instanceof RawExpr raw && raw.source().contains("\n")) {
      for (String line : raw.source().split("\n", -1)) {
        if (line.isEmpty()) {
          out.append('\n');
        } else {
          out.append(indent).append(line).append('\n');
        }
      }
      return;
    }
    out.append(indent);
    render(body, out, indent);
    out.append('\n');
  }

  private void render(Expression expr, StringBuilder out, String indent) {
    switch (expr) {
      case RawExpr raw -> out.append(raw.source());
      case Variable variable -> out.append(variable.name());
      case StringExpr string -> out.append('"').append(escapeString(string.value())).append('"');
      case IntegerExpr integer -> out.append(Long.toString(integer.value()));
      case BoolExpr bool -> out.append(bool.value() ? "True" : "False");
      case TupleExpr tuple -> renderTuple(tuple, out, indent);
      case ListExpr list -> renderList(list, out, indent);
      case LocalCallExpr call -> renderCall(call.function(), call.args(), out, indent);
      case RemoteCallExpr call ->
          renderCall(call.module() + "." + call.function(), call.args(), out, indent);
      case ConstructorExpr ctor -> renderConstructor(ctor, out, indent);
      case FieldAccessExpr access -> {
        render(access.target(), out, indent);
        out.append('.').append(access.field());
      }
      case PipeExpr pipe -> renderPipe(pipe, out, indent);
      case LetExpr let -> {
        out.append("let ");
        render(let.pattern(), out);
        out.append(" = ");
        render(let.value(), out, indent);
      }
      case CaseExpr caseExpr -> renderCase(caseExpr, out, indent);
      case UseExpr use -> {
        out.append("use ");
        render(use.pattern(), out);
        out.append(" <- ");
        render(use.callback(), out, indent);
      }
      case BlockExpr block -> {
        // Nested blocks render as parenthesized sequences only when needed; top-level bodies
        // use renderIndentedBody. Inline nested blocks join with newlines under indent.
        for (int i = 0; i < block.statements().size(); i++) {
          if (i > 0) {
            out.append('\n').append(indent);
          }
          render(block.statements().get(i), out, indent);
        }
      }
    }
  }

  private void renderTuple(TupleExpr tuple, StringBuilder out, String indent) {
    out.append("#(");
    renderCommaSeparated(tuple.elements(), out, indent);
    out.append(')');
  }

  private void renderList(ListExpr list, StringBuilder out, String indent) {
    out.append('[');
    renderCommaSeparated(list.elements(), out, indent);
    out.append(']');
  }

  private void renderCall(
      String callee, List<Expression> args, StringBuilder out, String indent) {
    out.append(callee).append('(');
    renderCommaSeparated(args, out, indent);
    out.append(')');
  }

  private void renderConstructor(ConstructorExpr ctor, StringBuilder out, String indent) {
    out.append(ctor.name());
    if (ctor.args().isEmpty()) {
      return;
    }
    out.append('(');
    for (int i = 0; i < ctor.args().size(); i++) {
      if (i > 0) {
        out.append(", ");
      }
      LabelledArg arg = ctor.args().get(i);
      if (arg.labelOrNull() != null && !arg.labelOrNull().isEmpty()) {
        out.append(arg.labelOrNull()).append(": ");
      }
      render(arg.value(), out, indent);
    }
    out.append(')');
  }

  private void renderPipe(PipeExpr pipe, StringBuilder out, String indent) {
    render(pipe.initial(), out, indent);
    for (PipeStep step : pipe.steps()) {
      out.append("\n").append(indent).append("|> ");
      renderPipeStep(step, out, indent);
    }
  }

  private void renderPipeStep(PipeStep step, StringBuilder out, String indent) {
    Expression callable = step.callable();
    if (callable instanceof LocalCallExpr call) {
      out.append(call.function());
      List<Expression> args = mergePipeArgs(call.args(), step.extraArgs());
      if (!args.isEmpty()) {
        out.append('(');
        renderCommaSeparated(args, out, indent);
        out.append(')');
      }
      return;
    }
    if (callable instanceof RemoteCallExpr call) {
      out.append(call.module()).append('.').append(call.function());
      List<Expression> args = mergePipeArgs(call.args(), step.extraArgs());
      if (!args.isEmpty()) {
        out.append('(');
        renderCommaSeparated(args, out, indent);
        out.append(')');
      }
      return;
    }
    render(callable, out, indent);
    if (!step.extraArgs().isEmpty()) {
      out.append('(');
      renderCommaSeparated(step.extraArgs(), out, indent);
      out.append(')');
    }
  }

  private static List<Expression> mergePipeArgs(
      List<Expression> callArgs, List<Expression> extraArgs) {
    if (extraArgs.isEmpty()) {
      return callArgs;
    }
    List<Expression> merged = new ArrayList<>(callArgs.size() + extraArgs.size());
    merged.addAll(callArgs);
    merged.addAll(extraArgs);
    return merged;
  }

  private void renderCase(CaseExpr caseExpr, StringBuilder out, String indent) {
    out.append("case ");
    render(caseExpr.subject(), out, indent);
    out.append(" {\n");
    String bodyIndent = indent + INDENT;
    for (CaseClause clause : caseExpr.clauses()) {
      out.append(bodyIndent);
      render(clause.pattern(), out);
      out.append(" -> ");
      if (clause.body() instanceof BlockExpr || clause.body() instanceof CaseExpr) {
        out.append('\n').append(bodyIndent).append(INDENT);
        render(clause.body(), out, bodyIndent + INDENT);
        out.append('\n');
      } else {
        render(clause.body(), out, bodyIndent);
        out.append('\n');
      }
    }
    out.append(indent).append('}');
  }

  private void render(Pattern pattern, StringBuilder out) {
    switch (pattern) {
      case VariablePattern variable -> out.append(variable.name());
      case WildcardPattern ignored -> out.append('_');
      case StringPattern string -> out.append('"').append(escapeString(string.value())).append('"');
      case IntegerPattern integer -> out.append(Long.toString(integer.value()));
      case TuplePattern tuple -> {
        out.append("#(");
        for (int i = 0; i < tuple.elements().size(); i++) {
          if (i > 0) {
            out.append(", ");
          }
          render(tuple.elements().get(i), out);
        }
        out.append(')');
      }
      case ListPattern list -> {
        out.append('[');
        for (int i = 0; i < list.elements().size(); i++) {
          if (i > 0) {
            out.append(", ");
          }
          render(list.elements().get(i), out);
        }
        out.append(']');
      }
      case ConstructorPattern ctor -> {
        out.append(ctor.name());
        if (ctor.fields().isEmpty()) {
          return;
        }
        out.append('(');
        for (int i = 0; i < ctor.fields().size(); i++) {
          if (i > 0) {
            out.append(", ");
          }
          PatternField field = ctor.fields().get(i);
          if (field.labelOrNull() != null && !field.labelOrNull().isEmpty()) {
            out.append(field.labelOrNull()).append(':');
            if (field.patternOrNull() != null) {
              out.append(' ');
              render(field.patternOrNull(), out);
            }
          } else if (field.patternOrNull() != null) {
            render(field.patternOrNull(), out);
          }
        }
        out.append(')');
      }
    }
  }

  private void renderCommaSeparated(
      List<Expression> elements, StringBuilder out, String indent) {
    for (int i = 0; i < elements.size(); i++) {
      if (i > 0) {
        out.append(", ");
      }
      render(elements.get(i), out, indent);
    }
  }

  private String renderImport(Import importStmt) {
    if (importStmt.modulePath().contains(" as ")) {
      return "import " + importStmt.modulePath();
    }
    if (importStmt.typeNames().isEmpty()) {
      return "import " + importStmt.modulePath();
    }
    String types =
        importStmt.typeNames().stream()
            .map(name -> "type " + name)
            .collect(Collectors.joining(", "));
    return "import " + importStmt.modulePath() + ".{" + types + "}";
  }

  private String renderVariant(Variant variant) {
    if (variant.fields().isEmpty()) {
      return variant.name();
    }
    String fields =
        variant.fields().stream().map(this::renderField).collect(Collectors.joining(", "));
    return variant.name() + "(" + fields + ")";
  }

  private String renderField(Field field) {
    if (field.labelOrNull() == null || field.labelOrNull().isEmpty()) {
      return field.typeExpression();
    }
    return field.labelOrNull() + ": " + field.typeExpression();
  }

  private static String escapeString(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private static String renderDoc(DocComment doc, String prefix) {
    String text = doc.text() == null ? "" : doc.text();
    if (text.isEmpty()) {
      return prefix;
    }
    String[] lines = text.split("\n", -1);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        out.append('\n');
      }
      String line = lines[i];
      if (line.isEmpty()) {
        out.append(prefix);
      } else {
        out.append(prefix).append(' ').append(line);
      }
    }
    return out.toString();
  }
}
