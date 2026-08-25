package io.beam.dsl.erlang;

import java.util.List;

final class DefaultErlangRenderer implements Renderer {

  private static final String INDENT = "    ";
  private static final int PRINT_WIDTH = 100;

  /** Render an expression/pattern into a scratch buffer and return its length. */
  private int compactLength(java.util.function.Consumer<StringBuilder> renderFn) {
    StringBuilder scratch = new StringBuilder();
    renderFn.accept(scratch);
    return scratch.length();
  }

  /** True when a single-line form would meet or exceed erlfmt's print width. */
  private boolean exceedsPrintWidth(java.util.function.Consumer<StringBuilder> renderFn) {
    return compactLength(renderFn) >= PRINT_WIDTH;
  }

  private boolean exceedsPrintWidthWithLinePrefix(
      String linePrefix, java.util.function.Consumer<StringBuilder> renderFn) {
    return linePrefix.length() + compactLength(renderFn) >= PRINT_WIDTH;
  }

  String renderCallbackForTest(Callback callback) {
    StringBuilder out = new StringBuilder();
    render(callback, out);
    return out.toString();
  }

  String renderExpressionForTest(Expression expression, String indent) {
    StringBuilder out = new StringBuilder();
    if (!indent.isEmpty()) {
      out.append(indent);
    }
    render(expression, out, indent);
    return out.toString();
  }

  String renderTypeAliasForTest(TypeAlias typeAlias) {
    StringBuilder out = new StringBuilder();
    render(typeAlias, out);
    return out.toString();
  }

  @Override
  public String render(Module module) {
    StringBuilder out = new StringBuilder();

    if (module.headerComments() != null) {
      for (String comment : module.headerComments()) {
        renderCommentLine(comment, out);
      }
    }
    if (module.moduledoc() != null) {
      out.append("-moduledoc \"").append(escapeString(module.moduledoc().text())).append("\".\n");
    }

    out.append("-module(").append(module.name()).append(").\n");

    if (module.moduleAttributes() != null) {
      for (ModuleAttribute attribute : module.moduleAttributes()) {
        render(attribute, out);
      }
    }

    if (module.includeHeaders() != null) {
      for (String includeHeader : module.includeHeaders()) {
        out.append("-include(\"").append(includeHeader).append("\").\n");
      }
    }

    if (!module.suppressExport()) {
      out.append("-export([\n");
      if (module.exports() != null && !module.exports().isEmpty()) {
        renderNamedExports(module.exports(), out);
      } else {
        renderExports(module.functions(), out);
      }
      out.append("]).\n");
    } else if (module.includeHeaders() == null) {
      out.append('\n');
    }

    if (module.callbacks() != null) {
      for (Callback callback : module.callbacks()) {
        render(callback, out);
      }
    }

    if (module.trailingAttributes() != null) {
      for (ModuleAttribute attribute : module.trailingAttributes()) {
        render(attribute, out);
      }
    }

    if (module.typeAliases() != null && !module.typeAliases().isEmpty()) {
      out.append('\n');
      renderTypeAliases(module.typeAliases(), out);
      out.append('\n');
    } else if (!module.suppressExport() && module.includeHeaders() != null) {
      out.append('\n');
    }

    for (int i = 0; i < module.functions().size(); i++) {
      renderFunctionDefinition(module.functions().get(i), out);
      if (i < module.functions().size() - 1) {
        out.append('\n');
      }
    }

    if (module.epilogueComments() != null && !module.epilogueComments().isEmpty()) {
      out.append('\n');
      for (String comment : module.epilogueComments()) {
        renderCommentLine(comment, out);
      }
    }

    return out.toString().stripTrailing() + "\n";
  }

  @Override
  public String render(Header header) {
    StringBuilder out = new StringBuilder();
    if (header.entriesOrNull() != null) {
      renderHeaderEntries(header.entriesOrNull(), header.separateEntries(), out);
      return out.toString().stripTrailing() + "\n";
    }

    if (header.comments() != null) {
      for (String comment : header.comments()) {
        renderCommentLine(comment, out);
      }
    }

    for (RecordDef record : header.records()) {
      render(record, out);
    }

    if (header.typeAliases() != null) {
      renderTypeAliases(header.typeAliases(), out);
    }

    return out.toString().stripTrailing() + "\n";
  }

  private void renderHeaderEntries(
      List<HeaderEntry> entries, boolean separateEntries, StringBuilder out) {
    for (int i = 0; i < entries.size(); i++) {
      if (separateEntries
          && i > 0
          && shouldSeparateHeaderEntries(entries.get(i - 1), entries.get(i))) {
        out.append('\n');
      }
      render(entries.get(i), out);
    }
  }

  private boolean shouldSeparateHeaderEntries(HeaderEntry previous, HeaderEntry current) {
    if (previous instanceof HeaderRecordEntry recordEntry
        && current instanceof HeaderTypeAliasEntry typeEntry) {
      RecordDef record = recordEntry.record();
      TypeAlias type = typeEntry.typeAlias();
      String recordName = record.name();
      String typeName =
          type.name().endsWith("()")
              ? type.name().substring(0, type.name().length() - 2)
              : type.name();
      return !recordName.equals(typeName);
    }
    return true;
  }

  private void render(HeaderEntry entry, StringBuilder out) {
    if (entry instanceof HeaderComment comment) {
      renderCommentLine(comment.text(), out);
    } else if (entry instanceof HeaderBlankLine) {
      out.append('\n');
    } else if (entry instanceof HeaderIfndef ifndef) {
      out.append("-ifndef(").append(ifndef.name()).append(").\n");
    } else if (entry instanceof HeaderDefine define) {
      out.append("-define(")
          .append(define.name())
          .append(", ")
          .append(define.value())
          .append(").\n");
    } else if (entry instanceof HeaderEndif) {
      out.append("-endif.\n");
    } else if (entry instanceof HeaderRecordEntry recordEntry) {
      render(recordEntry.record(), out);
    } else if (entry instanceof HeaderTypeAliasEntry typeEntry) {
      render(typeEntry.typeAlias(), out);
    }
  }

  private void renderTypeAliases(List<TypeAlias> typeAliases, StringBuilder out) {
    for (TypeAlias typeAlias : typeAliases) {
      render(typeAlias, out);
    }
  }

  private void render(TypeAlias typeAlias, StringBuilder out) {
    if (typeAlias.preambleCommentsOrNull() != null) {
      for (String comment : typeAlias.preambleCommentsOrNull()) {
        renderCommentLine(comment, out);
      }
    }
    String typeName = typeAlias.name().endsWith("()") ? typeAlias.name() : typeAlias.name() + "()";
    List<String> variants = typeAlias.variantsOrNull();
    if (variants != null && variants.size() > 2) {
      out.append("-type ").append(typeName).append(" ::\n");
      for (int i = 0; i < variants.size(); i++) {
        if (i == 0) {
          out.append(INDENT).append(variants.get(i));
        } else {
          out.append(INDENT).append("| ").append(variants.get(i));
        }
        if (i == variants.size() - 1) {
          out.append('.');
        }
        out.append('\n');
      }
      return;
    }
    String line = "-type " + typeName + " :: " + typeAlias.definition();
    if (line.length() <= PRINT_WIDTH) {
      out.append(line).append(".\n");
      return;
    }
    int split = typeAlias.definition().indexOf(" | ");
    if (split < 0) {
      out.append(line).append(".\n");
      return;
    }
    out.append("-type ").append(typeName).append(" ::\n");
    out.append(INDENT).append(typeAlias.definition(), 0, split).append('\n');
    out.append(INDENT)
        .append("| ")
        .append(typeAlias.definition().substring(split + 3))
        .append(".\n");
  }

  @Override
  public String renderFunction(Function function) {
    StringBuilder out = new StringBuilder();
    renderFunctionDefinition(function, out);
    return out.toString().stripTrailing() + "\n";
  }

  @Override
  public String renderExpression(Expression expression) {
    StringBuilder out = new StringBuilder();
    render(expression, out, "");
    return out.toString();
  }

  @Override
  public String renderPattern(Pattern pattern) {
    StringBuilder out = new StringBuilder();
    render(pattern, out);
    return out.toString();
  }

  private void renderExports(List<Function> functions, StringBuilder out) {
    for (int i = 0; i < functions.size(); i++) {
      Function function = functions.get(i);
      out.append("    ").append(function.name()).append('/').append(function.arity());
      if (i < functions.size() - 1) {
        out.append(',');
      }
      out.append('\n');
    }
  }

  private void renderNamedExports(List<String> exports, StringBuilder out) {
    for (int i = 0; i < exports.size(); i++) {
      out.append("    ").append(exports.get(i));
      if (i < exports.size() - 1) {
        out.append(',');
      }
      out.append('\n');
    }
  }

  private void renderFunctionDefinition(Function function, StringBuilder out) {

    render(function.doc(), out);
    render(function.spec(), out);

    List<FunctionClause> clauses = function.clauses();
    boolean multilineClauses =
        clauses.stream()
            .anyMatch(clause -> usesMultilineFunctionClauseLayout(function.name(), clause));
    for (int i = 0; i < clauses.size(); i++) {
      FunctionClause clause = clauses.get(i);
      out.append(function.name()).append('(');
      renderPatterns(clause.patterns(), out);
      out.append(')');
      if (clause.guard() != null) {
        out.append(" when ");
        render(clause.guard(), out);
      }
      out.append(" ->");
      if (!multilineClauses && isSingleLineBody(clause.body())) {
        out.append(' ');
        render(clause.body(), out, "");
      } else {
        out.append('\n');
        out.append(INDENT);
        render(clause.body(), out, INDENT);
      }

      if (i < clauses.size() - 1) {
        out.append(";\n");
      } else {
        out.append(".\n");
      }
    }
  }

  private boolean usesMultilineFunctionClauseLayout(String name, FunctionClause clause) {
    if (!isSingleLineBody(clause.body())) {
      return true;
    }
    if (hasRecordPattern(clause.patterns())) {
      return true;
    }
    return exceedsPrintWidth(
            scratch -> {
              scratch.append(name).append('(');
              renderPatterns(clause.patterns(), scratch);
              scratch.append(')');
              if (clause.guard() != null) {
                scratch.append(" when ");
                render(clause.guard(), scratch);
              }
              scratch.append(" -> ");
              render(clause.body(), scratch, "");
            })
        || isWideCall(clause.body());
  }

  private boolean isWideCall(Expression body) {
    if (body instanceof LocalCallExpr call) {
      return call.arguments().size() >= 4;
    }
    return false;
  }

  private boolean hasRecordPattern(List<Pattern> patterns) {
    for (Pattern pattern : patterns) {
      if (pattern instanceof RecordPattern) {
        return true;
      }
    }
    return false;
  }

  private boolean isSingleLineBody(Expression body) {
    if (body instanceof ListExpr list) {
      return list.elements().size() <= 1;
    }
    if (body instanceof ListComprehensionExpr comprehension) {
      return !usesMultilineListComprehensionLayout(comprehension);
    }
    return !(body instanceof RecordExpr)
        && !(body instanceof CaseExpr)
        && !(body instanceof IfExpr)
        && !(body instanceof Fun)
        && !(body instanceof MatchExpr)
        && !(body instanceof TryExpr);
  }

  private void renderPatterns(List<Pattern> patterns, StringBuilder out) {
    for (int i = 0; i < patterns.size(); i++) {
      if (i > 0) {
        out.append(", ");
      }
      render(patterns.get(i), out);
    }
  }

  private void render(ModuleAttribute attribute, StringBuilder out) {
    out.append('-').append(attribute.name()).append('(').append(attribute.value()).append(").\n");
  }

  private void render(Callback callback, StringBuilder out) {
    render(callback.docOrNull(), out);
    String body = callback.name() + "(" + callback.inputTypes() + ") -> " + callback.outputTypes();
    String line = "-callback " + body;
    if (line.length() <= PRINT_WIDTH || body.indexOf(" -> ") < 0) {
      out.append(line).append(".\n");
      return;
    }
    int split = body.indexOf(" -> ");
    out.append("-callback ").append(body, 0, split).append(" ->\n");
    out.append(INDENT).append(body.substring(split + 4)).append(".\n");
  }

  private void render(FunctionDoc functionDoc, StringBuilder out) {
    if (functionDoc instanceof Edoc edoc) {
      render(edoc, out);
    } else if (functionDoc instanceof Doc doc) {
      render(doc, out);
    }
  }

  private void render(Doc doc, StringBuilder out) {
    if (doc.text() == null || doc.text().isEmpty()) {
      return;
    }
    // Split doc text into lines, prefixing each with %%
    String[] lines = doc.text().split("\n");
    for (String line : lines) {
      out.append("%% ");
      out.append(line);
      out.append('\n');
    }
  }

  private void render(Edoc edoc, StringBuilder out) {
    if (edoc.text() == null || edoc.text().isEmpty()) {
      return;
    }
    // Split edoc text into lines, prefixing the first with %% @doc and the rest with %%
    String[] lines = edoc.text().split("\n");
    if (lines.length > 0) {
      out.append("%% @doc ");
      out.append(lines[0]);
      out.append('\n');
      for (int i = 1; i < lines.length; i++) {
        out.append("%% ");
        out.append(lines[i]);
        out.append('\n');
      }
    }
  }

  private void render(Guard guard, StringBuilder out) {
    if (guard instanceof IsTypeGuard isTypeGuard) {
      out.append("is_").append(isTypeGuard.type()).append('(');
      render(isTypeGuard.expression(), out, "");
      out.append(')');
    } else if (guard instanceof NotEqualGuard notEqualGuard) {
      render(notEqualGuard.left(), out, "");
      out.append(" =/= ");
      render(notEqualGuard.right(), out, "");
    } else if (guard instanceof EqualGuard equalGuard) {
      render(equalGuard.left(), out, "");
      out.append(" =:= ");
      render(equalGuard.right(), out, "");
    } else if (guard instanceof AndGuard andGuard) {
      List<Guard> guards = andGuard.guards();
      for (int i = 0; i < guards.size(); i++) {
        if (i > 0) {
          out.append(", ");
        }
        render(guards.get(i), out);
      }
    } else if (guard instanceof ExpressionGuard expressionGuard) {
      renderGuardExpression(expressionGuard.expression(), out, "");
    } else {
      throw new IllegalArgumentException("Unsupported guard: " + guard);
    }
  }

  private void render(Spec spec, StringBuilder out) {
    if (spec == null || spec.text() == null || spec.text().isEmpty()) {
      return;
    }
    out.append("-spec ").append(spec.text()).append(".\n");
  }

  private void render(RecordDef record, StringBuilder out) {
    List<TypedField> fields = record.fields();
    if (fields.isEmpty()) {
      out.append("-record(").append(record.name()).append(", {}).\n");
      return;
    }
    out.append("-record(").append(record.name()).append(", {\n");
    for (int i = 0; i < fields.size(); i++) {
      TypedField field = fields.get(i);
      if (field.fieldCommentsOrNull() != null) {
        for (String comment : field.fieldCommentsOrNull()) {
          out.append(INDENT).append("%% ").append(comment).append('\n');
        }
      }
      out.append(INDENT);
      if (field.defaultValueOrNull() != null) {
        out.append(field.name()).append(" = ").append(field.defaultValueOrNull());
      } else {
        out.append(field.name());
      }
      out.append(" :: ").append(field.type());
      if (i < fields.size() - 1) {
        out.append(',');
      }
      out.append('\n');
    }
    out.append("}).\n");
  }

  private void render(Expression expression, StringBuilder out, String indent) {
    if (expression instanceof AtomExpr atom) {
      out.append(atom.value());
    } else if (expression instanceof QuotedAtomExpr quoted) {
      out.append('\'').append(escapeQuotedAtom(quoted.value())).append('\'');
    } else if (expression instanceof IntegerExpr integer) {
      out.append(integer.value());
    } else if (expression instanceof Variable variable) {
      out.append(variable.name());
    } else if (expression instanceof BinaryExpr binary) {
      render(binary, out, indent);
    } else if (expression instanceof InfixExpr infix) {
      out.append('(');
      render(infix.left(), out, indent);
      out.append(' ').append(infix.operator()).append(' ');
      render(infix.right(), out, indent);
      out.append(')');
    } else if (expression instanceof CaseExpr caseExpr) {
      render(caseExpr, out, indent);
    } else if (expression instanceof IfExpr ifExpr) {
      render(ifExpr, out, indent);
    } else if (expression instanceof RemoteCallExpr call) {
      render(call, out, indent);
    } else if (expression instanceof LocalCallExpr call) {
      render(call, out, indent);
    } else if (expression instanceof ApplyExpr apply) {
      render(apply, out, indent);
    } else if (expression instanceof MacroExpr macro) {
      out.append('?').append(macro.name());
    } else if (expression instanceof RecordExpr record) {
      render(record, out, indent);
    } else if (expression instanceof RecordFieldAccessExpr fieldAccess) {
      render(fieldAccess, out, indent);
    } else if (expression instanceof TupleExpr tuple) {
      render(tuple, out, indent);
    } else if (expression instanceof ListExpr list) {
      render(list, out, indent);
    } else if (expression instanceof ListComprehensionExpr comprehension) {
      render(comprehension, out, indent);
    } else if (expression instanceof FunRefExpr funRef) {
      out.append("fun ").append(funRef.name()).append('/').append(funRef.arity());
    } else if (expression instanceof Fun fun) {
      render(fun, out, indent);
    } else if (expression instanceof StringExpr string) {
      out.append('"').append(escapeString(string.value())).append('"');
    } else if (expression instanceof MapExpr map) {
      render(map, out, indent);
    } else if (expression instanceof MatchExpr match) {
      render(match, out, indent);
    } else if (expression instanceof TryExpr tryExpr) {
      render(tryExpr, out, indent);
    } else if (expression instanceof BlockExpr block) {
      render(block, out, indent);
    } else if (expression instanceof MapEntriesExpr mapEntries) {
      render(mapEntries, out, indent);
    } else if (expression instanceof NotExpr notExpr) {
      out.append("not ");
      render(notExpr.expression(), out, indent);
    } else {
      throw new IllegalArgumentException("Unsupported expression: " + expression);
    }
  }

  private void render(BlockExpr block, StringBuilder out, String indent) {
    List<Expression> expressions = block.expressions();
    BlockExpr.BlockSeparator separator = block.separator();
    for (int i = 0; i < expressions.size(); i++) {
      if (i > 0) {
        if (separator == BlockExpr.BlockSeparator.COMMA) {
          out.append(",\n");
          if (!indent.isEmpty()) {
            out.append(indent);
          }
        } else {
          out.append('\n');
        }
      }
      render(expressions.get(i), out, indent);
    }
    if (block.terminateWithPeriod()) {
      out.append('.');
    }
  }

  private void render(MapEntriesExpr mapEntries, StringBuilder out, String indent) {
    List<MapEntry> entries = mapEntries.entries();
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) {
        out.append(",\n");
      }
      MapEntry entry = entries.get(i);
      render(entry.key(), out, indent);
      out.append(entry.updateOnly() ? " := " : " => ");
      render(entry.value(), out, indent);
    }
  }

  private void render(RemoteCallExpr call, StringBuilder out, String indent) {
    String linePrefix = currentLinePrefix(out);
    boolean vertical =
        useVerticalCallLayout(
            call.arguments(),
            linePrefix,
            scratch -> {
              renderRemoteTarget(call.module(), scratch, indent);
              scratch.append(':');
              renderRemoteTarget(call.function(), scratch, indent);
              scratch.append('(');
              renderArguments(call.arguments(), scratch, indent);
              scratch.append(')');
            });
    renderRemoteTarget(call.module(), out, indent);
    out.append(':');
    renderRemoteTarget(call.function(), out, indent);
    out.append('(');
    renderCallArguments(call.arguments(), out, indent, vertical);
  }

  private void renderRemoteTarget(Expression target, StringBuilder out, String indent) {
    if (needsRemoteTargetParens(target)) {
      out.append('(');
      render(target, out, indent);
      out.append(')');
    } else {
      render(target, out, indent);
    }
  }

  private void render(LocalCallExpr call, StringBuilder out, String indent) {
    String linePrefix = currentLinePrefix(out);
    boolean vertical =
        useVerticalCallLayout(
            call.arguments(),
            linePrefix,
            scratch -> {
              scratch.append(call.function()).append('(');
              renderArguments(call.arguments(), scratch, indent);
              scratch.append(')');
            });
    out.append(call.function()).append('(');
    renderCallArguments(call.arguments(), out, indent, vertical);
  }

  private void render(ApplyExpr apply, StringBuilder out, String indent) {
    String linePrefix = currentLinePrefix(out);
    boolean vertical =
        useVerticalCallLayout(
            apply.arguments(),
            linePrefix,
            scratch -> {
              render(apply.callee(), scratch, indent);
              scratch.append('(');
              renderArguments(apply.arguments(), scratch, indent);
              scratch.append(')');
            });
    render(apply.callee(), out, indent);
    out.append('(');
    renderCallArguments(apply.arguments(), out, indent, vertical);
  }

  private void renderArguments(List<Expression> arguments, StringBuilder out, String indent) {
    for (int i = 0; i < arguments.size(); i++) {
      if (i > 0) {
        out.append(", ");
      }
      render(arguments.get(i), out, indent);
    }
  }

  private boolean useVerticalCallLayout(
      List<Expression> arguments,
      String linePrefix,
      java.util.function.Consumer<StringBuilder> compactRender) {
    for (Expression argument : arguments) {
      if (argument instanceof Fun fun && !usesCompactFunLayout(fun.clauses())) {
        return true;
      }
    }
    return exceedsPrintWidthWithLinePrefix(linePrefix, compactRender);
  }

  private void renderCallArguments(
      List<Expression> arguments, StringBuilder out, String indent, boolean vertical) {
    if (!vertical) {
      renderArguments(arguments, out, indent);
      out.append(')');
      return;
    }

    if (arguments.isEmpty()) {
      out.append(')');
      return;
    }

    int inlineCount = countInlineCallArguments(arguments, out, indent);
    if (inlineCount == 0 && arguments.size() == 1 && canHangOpeningBracket(arguments.get(0))) {
      Expression argument = arguments.get(0);
      if (callArgumentFitsOpeningBracket(argument, out)) {
        renderCallArgumentWithOpeningBracket(argument, out, indent);
        out.append(')');
        return;
      }
    }
    if (inlineCount == 0) {
      if (arguments.size() == 1
          && singleCallArgumentFitsOnIndentedLine(arguments.get(0), out, indent)) {
        out.append('\n').append(indent).append(INDENT);
        render(arguments.get(0), out, indent + INDENT);
        out.append(')');
        return;
      }
      for (int i = 0; i < arguments.size(); i++) {
        if (i > 0) {
          out.append(',');
        }
        out.append('\n').append(indent).append(INDENT);
        render(arguments.get(i), out, indent + INDENT);
      }
      out.append('\n').append(indent).append(')');
      return;
    }

    for (int i = 0; i < inlineCount; i++) {
      if (i > 0) {
        out.append(", ");
      }
      render(arguments.get(i), out, indent);
    }
    boolean closedHangingList = false;
    for (int i = inlineCount; i < arguments.size(); i++) {
      out.append(',');
      if (callArgumentFitsOnCurrentLine(arguments.get(i), out, indent)) {
        if (arguments.get(i) instanceof ListExpr list
            && list.tail() == null
            && list.elements().size() > 1) {
          renderListHang(list, out, indent, " [");
          closedHangingList = i == arguments.size() - 1;
        } else {
          out.append(' ');
          render(arguments.get(i), out, indent);
        }
      } else {
        out.append('\n').append(indent).append(INDENT);
        render(arguments.get(i), out, indent + INDENT);
      }
    }
    if (closedHangingList) {
      out.append(')');
    } else {
      out.append('\n').append(indent).append(')');
    }
  }

  private boolean canHangOpeningBracket(Expression argument) {
    if (argument instanceof ListComprehensionExpr) {
      return true;
    }
    return argument instanceof ListExpr list && list.tail() == null && list.elements().size() > 1;
  }

  private boolean callArgumentFitsOpeningBracket(Expression argument, StringBuilder prefix) {
    String linePrefix = currentLinePrefix(prefix);
    return !exceedsPrintWidthWithLinePrefix(
        linePrefix,
        scratch -> {
          scratch.append(" [");
        });
  }

  private void renderCallArgumentWithOpeningBracket(
      Expression argument, StringBuilder out, String indent) {
    if (argument instanceof ListComprehensionExpr comprehension) {
      if (listComprehensionFitsInlineInCall(comprehension, out)) {
        out.append('[');
        render(comprehension.expression(), out, indent);
        renderListComprehensionQualifiers(comprehension.qualifiers(), out, indent, false);
        out.append(']');
        return;
      }
      out.append('[').append('\n');
      out.append(indent).append(INDENT);
      render(comprehension.expression(), out, indent + INDENT);
      out.append('\n');
      out.append(indent).append(" ||");
      renderListComprehensionQualifiers(comprehension.qualifiers(), out, indent, true);
      out.append('\n');
      out.append(indent).append(']');
      return;
    }
    renderListHang((ListExpr) argument, out, indent, "[");
  }

  private boolean listComprehensionFitsInlineInCall(
      ListComprehensionExpr comprehension, StringBuilder out) {
    if (usesMultilineListComprehensionLayout(comprehension)) {
      return false;
    }
    String linePrefix = currentLinePrefix(out);
    return !exceedsPrintWidthWithLinePrefix(
        linePrefix,
        scratch -> {
          scratch.append('[');
          render(comprehension.expression(), scratch, "");
          renderListComprehensionQualifiers(comprehension.qualifiers(), scratch, "", false);
          scratch.append(']');
        });
  }

  private void renderListHang(ListExpr list, StringBuilder out, String indent, String open) {
    out.append(open).append('\n');
    List<Expression> elements = list.elements();
    for (int i = 0; i < elements.size(); i++) {
      out.append(indent).append(INDENT);
      render(elements.get(i), out, indent + INDENT);
      if (i < elements.size() - 1) {
        out.append(',');
      }
      out.append('\n');
    }
    out.append(indent).append(']');
  }

  private boolean callArgumentFitsOnCurrentLine(
      Expression argument, StringBuilder prefix, String indent) {
    String linePrefix = currentLinePrefix(prefix);
    if (argument instanceof ListExpr list && list.tail() == null && list.elements().size() > 1) {
      return !exceedsPrintWidthWithLinePrefix(
          linePrefix,
          scratch -> {
            scratch.append(" [");
          });
    }
    return !exceedsPrintWidthWithLinePrefix(
        linePrefix,
        scratch -> {
          scratch.append(' ');
          render(argument, scratch, indent);
        });
  }

  private int countInlineCallArguments(
      List<Expression> arguments, StringBuilder prefix, String indent) {
    String linePrefix = currentLinePrefix(prefix);
    for (int count = arguments.size(); count >= 1; count--) {
      int inlineCount = count;
      boolean hasMoreArguments = inlineCount < arguments.size();
      if (!canUseInlineCallHead(arguments, inlineCount)) {
        continue;
      }
      if (!exceedsPrintWidthWithLinePrefix(
          linePrefix,
          scratch -> {
            for (int i = 0; i < inlineCount; i++) {
              if (i > 0) {
                scratch.append(", ");
              }
              render(arguments.get(i), scratch, indent);
            }
            if (hasMoreArguments) {
              scratch.append(',');
            } else {
              scratch.append(')');
            }
          })) {
        return inlineCount;
      }
    }
    return 0;
  }

  private boolean canUseInlineCallHead(List<Expression> arguments, int inlineCount) {
    if (inlineCount <= 0 || inlineCount > arguments.size()) {
      return false;
    }
    for (int i = 0; i < inlineCount; i++) {
      Expression argument = arguments.get(i);
      if (argument instanceof Fun
          || argument instanceof LocalCallExpr
          || argument instanceof RemoteCallExpr
          || argument instanceof ApplyExpr) {
        return false;
      }
    }
    return true;
  }

  private boolean singleCallArgumentFitsOnIndentedLine(
      Expression argument, StringBuilder out, String indent) {
    String linePrefix = currentLinePrefix(out) + '\n' + indent + INDENT;
    return !exceedsPrintWidthWithLinePrefix(
        linePrefix, scratch -> render(argument, scratch, indent + INDENT));
  }

  private void render(RecordFieldAccessExpr fieldAccess, StringBuilder out, String indent) {
    render(fieldAccess.receiver(), out, indent);
    out.append('#').append(fieldAccess.recordName()).append('.').append(fieldAccess.fieldName());
  }

  private void render(RecordExpr record, StringBuilder out, String indent) {
    if (record.base() != null
        && record.fields().size() == 1
        && isSingleLineBody(record.fields().get(0).value())) {
      render(record.base(), out, indent);
      out.append('#').append(record.name()).append('{');
      RecordField field = record.fields().get(0);
      out.append(field.name()).append(" = ");
      render(field.value(), out, indent);
      out.append('}');
      return;
    }
    if (record.base() != null) {
      render(record.base(), out, indent);
      out.append('#');
    } else {
      out.append('#');
    }
    out.append(record.name()).append("{\n");
    List<RecordField> fields = record.fields();
    for (int i = 0; i < fields.size(); i++) {
      out.append(indent).append(INDENT).append(fields.get(i).name()).append(" = ");
      render(fields.get(i).value(), out, indent + INDENT);
      if (i < fields.size() - 1) {
        out.append(',');
      }
      out.append('\n');
    }
    out.append(indent).append('}');
  }

  private void render(TupleExpr tuple, StringBuilder out, String indent) {
    if (!tupleExceedsPrintWidth(tuple)) {
      out.append('{');
      renderArguments(tuple.elements(), out, indent);
      out.append('}');
      return;
    }
    renderTupleWrapped(tuple.elements(), out, indent);
  }

  private boolean tupleExceedsPrintWidth(TupleExpr tuple) {
    return exceedsPrintWidth(
        scratch -> {
          scratch.append('{');
          renderFlatArguments(tuple.elements(), scratch);
          scratch.append('}');
        });
  }

  /** Render an expression as a single flat line for print-width measurement. */
  private void renderFlat(Expression expression, StringBuilder out) {
    if (expression instanceof AtomExpr atom) {
      out.append(atom.value());
    } else if (expression instanceof IntegerExpr integer) {
      out.append(integer.value());
    } else if (expression instanceof Variable variable) {
      out.append(variable.name());
    } else if (expression instanceof StringExpr string) {
      out.append('"').append(escapeString(string.value())).append('"');
    } else if (expression instanceof MacroExpr macro) {
      out.append('?').append(macro.name());
    } else if (expression instanceof InfixExpr infix) {
      out.append('(');
      renderFlat(infix.left(), out);
      out.append(' ').append(infix.operator()).append(' ');
      renderFlat(infix.right(), out);
      out.append(')');
    } else if (expression instanceof RecordFieldAccessExpr fieldAccess) {
      renderFlat(fieldAccess.receiver(), out);
      out.append('#').append(fieldAccess.recordName()).append('.').append(fieldAccess.fieldName());
    } else if (expression instanceof RecordExpr record) {
      if (record.base() != null) {
        renderFlat(record.base(), out);
      }
      out.append('#').append(record.name()).append('{');
      List<RecordField> fields = record.fields();
      for (int i = 0; i < fields.size(); i++) {
        if (i > 0) {
          out.append(", ");
        }
        RecordField field = fields.get(i);
        out.append(field.name()).append(" = ");
        renderFlat(field.value(), out);
      }
      out.append('}');
    } else if (expression instanceof TupleExpr tuple) {
      out.append('{');
      renderFlatArguments(tuple.elements(), out);
      out.append('}');
    } else if (expression instanceof LocalCallExpr call) {
      out.append(call.function()).append('(');
      renderFlatArguments(call.arguments(), out);
      out.append(')');
    } else if (expression instanceof RemoteCallExpr call) {
      renderFlat(call.module(), out);
      out.append(':');
      renderFlat(call.function(), out);
      out.append('(');
      renderFlatArguments(call.arguments(), out);
      out.append(')');
    } else if (expression instanceof ApplyExpr apply) {
      renderFlat(apply.callee(), out);
      out.append('(');
      renderFlatArguments(apply.arguments(), out);
      out.append(')');
    } else if (expression instanceof BinaryExpr binary) {
      out.append("<<");
      List<BinarySegmentExpr> segments = binary.segments();
      for (int i = 0; i < segments.size(); i++) {
        if (i > 0) {
          out.append(", ");
        }
        renderFlat(segments.get(i), out);
      }
      out.append(">>");
    } else if (expression instanceof Fun) {
      out.append("fun end");
    } else {
      render(expression, out, "");
    }
  }

  private void renderFlat(BinarySegmentExpr segment, StringBuilder out) {
    if (segment.literal() != null) {
      out.append('"').append(escapeString(segment.literal())).append('"');
    } else {
      renderFlat(segment.expression(), out);
    }
    renderBinarySegmentSpecifiers(segment.size(), segment.type(), segment.unit(), out);
  }

  private void renderFlatArguments(List<Expression> arguments, StringBuilder out) {
    for (int i = 0; i < arguments.size(); i++) {
      if (i > 0) {
        out.append(", ");
      }
      renderFlat(arguments.get(i), out);
    }
  }

  private void renderTupleWrapped(List<Expression> elements, StringBuilder out, String indent) {
    out.append('{');
    for (int i = 0; i < elements.size(); i++) {
      if (i > 0) {
        out.append(", ");
      }
      Expression element = elements.get(i);
      if (i > 0 && tupleElementNeedsLineBreak(elements, i)) {
        if (element instanceof TupleExpr inner) {
          out.append("{\n");
          out.append(indent).append(INDENT);
          renderArguments(inner.elements(), out, indent + INDENT);
          out.append('\n');
          out.append(indent).append('}');
        } else {
          out.append('\n');
          out.append(indent).append(INDENT);
          render(element, out, indent + INDENT);
        }
      } else {
        render(element, out, indent);
      }
    }
    out.append('}');
  }

  private boolean tupleElementNeedsLineBreak(List<Expression> elements, int elementIndex) {
    return exceedsPrintWidth(
        scratch -> {
          scratch.append('{');
          for (int i = 0; i < elementIndex; i++) {
            if (i > 0) {
              scratch.append(", ");
            }
            renderFlat(elements.get(i), scratch);
          }
          scratch.append(", ");
          renderFlat(elements.get(elementIndex), scratch);
          scratch.append('}');
        });
  }

  private String currentLinePrefix(StringBuilder out) {
    int i = out.length();
    while (i > 0 && out.charAt(i - 1) != '\n') {
      i--;
    }
    return out.substring(i);
  }

  private static String escapeString(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String escapeQuotedAtom(String value) {
    return value.replace("\\", "\\\\").replace("'", "\\'");
  }

  private static String leadingWhitespace(String text) {
    int end = 0;
    while (end < text.length() && text.charAt(end) == ' ') {
      end++;
    }
    return text.substring(0, end);
  }

  private void render(MapExpr map, StringBuilder out, String indent) {
    if (map.base() != null) {
      render(map.base(), out, indent);
    }
    out.append("#{");
    renderMapEntries(map.entries(), out, indent);
    out.append('}');
  }

  private void renderMapEntries(List<MapEntry> entries, StringBuilder out, String indent) {
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) {
        out.append(", ");
      }
      MapEntry entry = entries.get(i);
      render(entry.key(), out, indent);
      out.append(entry.updateOnly() ? " := " : " => ");
      render(entry.value(), out, indent);
    }
  }

  private void render(MatchExpr match, StringBuilder out, String indent) {
    render(match.pattern(), out);
    if (usesMultilineMatchValue(match.value())) {
      out.append(" =\n");
      out.append(indent).append(INDENT);
      render(match.value(), out, indent + INDENT);
    } else {
      out.append(" = ");
      render(match.value(), out, indent);
    }
    if (match.body() != null) {
      out.append(",\n");
      out.append(indent);
      render(match.body(), out, indent);
    }
  }

  private boolean usesMultilineMatchValue(Expression value) {
    return value instanceof CaseExpr || value instanceof MatchExpr || value instanceof TryExpr;
  }

  private void render(ListExpr list, StringBuilder out, String indent) {
    List<Expression> elements = list.elements();
    if (elements.size() <= 1 && list.tail() == null) {
      out.append('[');
      renderArguments(elements, out, indent);
      out.append(']');
      return;
    }

    if (elements.size() <= 1 && list.tail() != null) {
      out.append('[');
      renderArguments(elements, out, indent);
      out.append(" | ");
      render(list.tail(), out, indent);
      out.append(']');
      return;
    }

    out.append('[').append('\n');
    for (int i = 0; i < elements.size(); i++) {
      out.append(indent).append(INDENT);
      render(elements.get(i), out, indent + INDENT);
      if (i < elements.size() - 1) {
        out.append(',');
      }
      out.append('\n');
    }
    if (list.tail() != null) {
      out.append(indent).append(INDENT).append('|');
      out.append(' ');
      render(list.tail(), out, indent + INDENT);
      out.append('\n');
    }
    out.append(indent).append(']');
  }

  private void render(ListComprehensionExpr comprehension, StringBuilder out, String indent) {
    if (usesMultilineListComprehensionLayout(comprehension)) {
      out.append('[').append('\n');
      out.append(indent).append(INDENT);
      render(comprehension.expression(), out, indent + INDENT);
      out.append('\n');
      out.append(indent).append(" ||");
      renderListComprehensionQualifiers(comprehension.qualifiers(), out, indent, true);
      out.append('\n');
      out.append(indent).append(']');
      return;
    }

    out.append('[');
    render(comprehension.expression(), out, indent);
    renderListComprehensionQualifiers(comprehension.qualifiers(), out, indent, false);
    out.append(']');
  }

  private void renderListComprehensionQualifiers(
      List<ListComprehensionQualifier> qualifiers,
      StringBuilder out,
      String indent,
      boolean multiline) {
    if (qualifiers.isEmpty()) {
      return;
    }

    if (!multiline) {
      out.append(" || ");
    } else if (!qualifiers.isEmpty()) {
      out.append(' ');
    }

    for (int i = 0; i < qualifiers.size(); i++) {
      if (i > 0) {
        out.append(',');
        if (multiline) {
          out.append('\n');
          out.append(indent).append(INDENT);
        } else {
          out.append(' ');
        }
      }
      renderListComprehensionQualifier(qualifiers.get(i), out, indent + INDENT);
    }
  }

  private void renderListComprehensionQualifier(
      ListComprehensionQualifier qualifier, StringBuilder out, String indent) {
    if (qualifier instanceof ListComprehensionGenerator generator) {
      render(generator.pattern(), out);
      out.append(" <- ");
      render(generator.source(), out, indent);
    } else if (qualifier instanceof ListComprehensionFilter filter) {
      renderGuardExpression(filter.expression(), out, indent);
    }
  }

  private void renderGuardExpression(Expression expression, StringBuilder out, String indent) {
    if (expression instanceof NotExpr notExpr) {
      out.append("not ");
      renderGuardExpression(notExpr.expression(), out, indent);
      return;
    }
    if (expression instanceof InfixExpr infix) {
      render(infix.left(), out, indent);
      out.append(' ').append(infix.operator()).append(' ');
      render(infix.right(), out, indent);
    } else {
      render(expression, out, indent);
    }
  }

  private boolean usesMultilineListComprehensionLayout(ListComprehensionExpr comprehension) {
    Expression expression = comprehension.expression();
    if (expression instanceof CaseExpr
        || expression instanceof MatchExpr
        || expression instanceof Fun
        || expression instanceof RecordExpr) {
      return true;
    }

    int filterCount = 0;
    int generatorCount = 0;
    for (ListComprehensionQualifier qualifier : comprehension.qualifiers()) {
      if (qualifier instanceof ListComprehensionFilter) {
        filterCount++;
      } else if (qualifier instanceof ListComprehensionGenerator) {
        generatorCount++;
      }
    }

    if (filterCount > 1 || generatorCount > 1) {
      return true;
    }
    return filterCount >= 1 && expression instanceof TupleExpr;
  }

  private void render(Fun fun, StringBuilder out, String indent) {
    List<FunClause> clauses = fun.clauses();
    if (usesCompactFunLayout(clauses)) {
      FunClause clause = clauses.get(0);
      out.append("fun");
      renderFunHead(clause, out);
      out.append(" -> ");
      render(clause.body(), out, indent);
      out.append(" end");
      return;
    }

    out.append("fun");
    if (clauses.size() == 1 && clauses.get(0).patterns().isEmpty()) {
      out.append("() ->\n");
      out.append(indent).append(INDENT);
      render(clauses.get(0).body(), out, indent + INDENT);
      out.append('\n');
      out.append(indent).append("end");
      return;
    }

    out.append('\n');
    for (int i = 0; i < clauses.size(); i++) {
      out.append(indent).append(INDENT);
      renderFunClause(clauses.get(i), out);
      if (i < clauses.size() - 1) {
        out.append(';');
      }
      out.append('\n');
    }
    out.append(indent).append("end");
  }

  private void renderFunHead(FunClause clause, StringBuilder out) {
    if (clause.patterns().isEmpty()) {
      out.append("()");
      return;
    }
    out.append('(');
    renderPatterns(clause.patterns(), out);
    out.append(')');
  }

  private boolean usesCompactFunLayout(List<FunClause> clauses) {
    if (clauses.size() != 1) {
      return false;
    }
    FunClause clause = clauses.get(0);
    if (clause.guard() != null) {
      return false;
    }
    if (clause.patterns().isEmpty()) {
      return false;
    }
    return isSingleLineBody(clause.body())
        && !exceedsPrintWidth(
            scratch -> {
              scratch.append("fun");
              renderFunHead(clause, scratch);
              scratch.append(" -> ");
              render(clause.body(), scratch, "");
              scratch.append(" end");
            });
  }

  private void renderFunClause(FunClause clause, StringBuilder out) {
    out.append('(');
    renderPatterns(clause.patterns(), out);
    out.append(')');
    if (clause.guard() != null) {
      out.append(" when ");
      render(clause.guard(), out);
    }
    out.append(" -> ");
    render(clause.body(), out, "");
  }

  private void render(CaseExpr caseExpr, StringBuilder out, String indent) {
    if (useMultilineCaseScrutinee(caseExpr.expression(), out, indent)) {
      out.append("case\n");
      out.append(indent).append(INDENT);
      render(caseExpr.expression(), out, indent + INDENT);
      out.append("\n");
      out.append(indent).append("of\n");
    } else {
      out.append("case ");
      render(caseExpr.expression(), out, indent);
      out.append(" of\n");
    }

    renderClauses(caseExpr.clauses(), out, indent);
    out.append(indent).append("end");
  }

  private void render(IfExpr ifExpr, StringBuilder out, String indent) {
    out.append("if\n");
    List<IfClause> clauses = ifExpr.clauses();
    for (int i = 0; i < clauses.size(); i++) {
      IfClause clause = clauses.get(i);
      out.append(indent).append(INDENT);
      render(clause.guard(), out);
      out.append(" -> ");
      if (usesMultilineCaseBody(clause.body())) {
        out.append('\n');
        out.append(indent).append(INDENT).append(INDENT);
        render(clause.body(), out, indent + INDENT + INDENT);
      } else {
        render(clause.body(), out, indent + INDENT);
      }
      if (i < clauses.size() - 1) {
        out.append(';');
      }
      out.append('\n');
    }
    out.append(indent).append("end");
  }

  private boolean useMultilineCaseScrutinee(
      Expression expression, StringBuilder out, String indent) {
    String linePrefix = currentLinePrefix(out);
    return exceedsPrintWidthWithLinePrefix(
        linePrefix,
        scratch -> {
          scratch.append("case ");
          render(expression, scratch, indent);
          scratch.append(" of");
        });
  }

  private boolean caseUsesMultilineClauseLayout(List<Clause> clauses, String indent) {
    for (Clause clause : clauses) {
      if (usesMultilineCaseBody(clause.body())) {
        return true;
      }
      if (exceedsPrintWidth(
          scratch -> {
            render(clause.pattern(), scratch);
            if (clause.guard() != null) {
              scratch.append(" when ");
              render(clause.guard(), scratch);
            }
            scratch.append(" -> ");
            render(clause.body(), scratch, indent);
          })) {
        return true;
      }
    }
    return false;
  }

  private boolean usesMultilineCaseBody(Expression body) {
    return body instanceof CaseExpr
        || body instanceof RecordExpr
        || body instanceof MatchExpr
        || body instanceof Fun
        || body instanceof TryExpr;
  }

  private void render(TryExpr tryExpr, StringBuilder out, String indent) {
    out.append("try\n");
    out.append(indent).append(INDENT);
    render(tryExpr.body(), out, indent + INDENT);
    out.append("\n");
    out.append(indent).append("catch\n");

    renderClauses(tryExpr.catchClauses(), out, indent);

    out.append(indent).append("end");
  }

  private void renderClauses(List<Clause> clauses, StringBuilder out, String indent) {
    boolean multilineClauses = caseUsesMultilineClauseLayout(clauses, indent);
    for (int i = 0; i < clauses.size(); i++) {
      Clause clause = clauses.get(i);
      out.append(indent).append(INDENT);
      render(clause.pattern(), out);
      if (clause.guard() != null) {
        out.append(" when ");
        render(clause.guard(), out);
      }
      out.append(" ->");
      if (multilineClauses) {
        out.append('\n');
        out.append(indent).append(INDENT).append(INDENT);
        render(clause.body(), out, indent + INDENT + INDENT);
      } else {
        out.append(' ');
        render(clause.body(), out, indent + INDENT);
      }
      if (i < clauses.size() - 1) {
        out.append(';');
      }
      out.append('\n');
    }
  }

  private void renderCommentLine(String comment, StringBuilder out) {
    if (comment.isEmpty()) {
      out.append("%%\n");
    } else {
      out.append("%% ").append(comment).append('\n');
    }
  }

  private void render(Pattern pattern, StringBuilder out) {
    if (pattern instanceof AtomPattern atom) {
      out.append(atom.value());
    } else if (pattern instanceof IntegerPattern integer) {
      out.append(integer.value());
    } else if (pattern instanceof VariablePattern variable) {
      out.append(variable.name());
    } else if (pattern instanceof WildcardPattern wildcard) {
      out.append('_');
      if (wildcard.name() != null) {
        out.append(wildcard.name());
      }
    } else if (pattern instanceof CharPattern charPattern) {
      out.append('$').append(charPattern.value());
    } else if (pattern instanceof RecordPattern record) {
      render(record, out);
    } else if (pattern instanceof BinaryPattern binary) {
      render(binary, out);
    } else if (pattern instanceof TuplePattern tuple) {
      render(tuple, out);
    } else if (pattern instanceof MapPattern map) {
      render(map, out);
    } else if (pattern instanceof ListPattern list) {
      render(list, out);
    } else if (pattern instanceof MatchPattern match) {
      render(match.left(), out);
      out.append(" = ");
      render(match.right(), out);
    } else if (pattern instanceof CatchPattern catchPattern) {
      render(catchPattern.classPattern(), out);
      out.append(':');
      render(catchPattern.reasonPattern(), out);
    } else {
      throw new IllegalArgumentException("Unsupported pattern: " + pattern);
    }
  }

  private void render(BinaryExpr binary, StringBuilder out, String indent) {
    List<BinarySegmentExpr> segments = binary.segments();
    if (segments.isEmpty()) {
      out.append("<<>>");
      return;
    }
    if (segments.size() == 1 && isSimpleLiteralSegment(segments.get(0))) {
      out.append("<<\"").append(escapeString(segments.get(0).literal())).append("\">>");
      return;
    }
    if (shouldWrapBinaryExprSegments(segments, out, indent)) {
      renderBinaryExprSegmentsMultiline(segments, out, indent);
      return;
    }
    out.append("<<");
    for (int i = 0; i < segments.size(); i++) {
      if (i > 0) {
        out.append(", ");
      }
      render(segments.get(i), out, indent);
    }
    out.append(">>");
  }

  private boolean shouldWrapBinaryExprSegments(
      List<BinarySegmentExpr> segments, StringBuilder out, String indent) {
    String linePrefix = currentLinePrefix(out);
    return binarySegmentsExceedPrintWidth(
        segments.size(),
        linePrefix,
        (index, scratch) -> render(segments.get(index), scratch, indent));
  }

  private void renderBinaryExprSegmentsMultiline(
      List<BinarySegmentExpr> segments, StringBuilder out, String indent) {
    String linePrefix = currentLinePrefix(out);
    int breakIndex =
        findBinarySegmentBreakIndex(
            segments.size(),
            linePrefix,
            (index, scratch) -> render(segments.get(index), scratch, indent));
    out.append("<<");
    for (int i = 0; i < breakIndex; i++) {
      if (i > 0) {
        out.append(", ");
      }
      render(segments.get(i), out, indent);
    }
    out.append(",\n");
    out.append(indent).append(INDENT);
    for (int i = breakIndex; i < segments.size(); i++) {
      if (i > breakIndex) {
        out.append(", ");
      }
      render(segments.get(i), out, indent + INDENT);
    }
    out.append(">>");
  }

  private boolean shouldWrapBinaryPatternSegments(
      List<BinarySegmentPattern> segments, String linePrefix) {
    return binarySegmentsExceedPrintWidth(
        segments.size(), linePrefix, (index, scratch) -> render(segments.get(index), scratch));
  }

  private void renderBinaryPatternSegmentsMultiline(
      List<BinarySegmentPattern> segments, StringBuilder out, String linePrefix) {
    int breakIndex =
        findBinarySegmentBreakIndex(
            segments.size(), linePrefix, (index, scratch) -> render(segments.get(index), scratch));
    out.append("<<");
    for (int i = 0; i < breakIndex; i++) {
      if (i > 0) {
        out.append(", ");
      }
      render(segments.get(i), out);
    }
    out.append(",\n");
    out.append(leadingWhitespace(linePrefix)).append(INDENT);
    for (int i = breakIndex; i < segments.size(); i++) {
      if (i > breakIndex) {
        out.append(", ");
      }
      render(segments.get(i), out);
    }
    out.append(">>");
  }

  private boolean binarySegmentsExceedPrintWidth(
      int segmentCount,
      String linePrefix,
      java.util.function.BiConsumer<Integer, StringBuilder> segmentRenderer) {
    return exceedsPrintWidth(
        scratch -> {
          scratch.append(linePrefix);
          scratch.append("<<");
          for (int i = 0; i < segmentCount; i++) {
            if (i > 0) {
              scratch.append(", ");
            }
            segmentRenderer.accept(i, scratch);
          }
          scratch.append(">>");
        });
  }

  private int findBinarySegmentBreakIndex(
      int segmentCount,
      String linePrefix,
      java.util.function.BiConsumer<Integer, StringBuilder> segmentRenderer) {
    for (int breakIndex = segmentCount - 1; breakIndex >= 1; breakIndex--) {
      final int firstLineSegmentCount = breakIndex;
      if (compactLength(
              scratch -> {
                scratch.append(linePrefix);
                scratch.append("<<");
                for (int i = 0; i < firstLineSegmentCount; i++) {
                  if (i > 0) {
                    scratch.append(", ");
                  }
                  segmentRenderer.accept(i, scratch);
                }
                scratch.append(',');
              })
          < PRINT_WIDTH) {
        return breakIndex;
      }
    }
    return 1;
  }

  private void render(BinarySegmentExpr segment, StringBuilder out, String indent) {
    if (segment.literal() != null) {
      out.append('"').append(escapeString(segment.literal())).append('"');
    } else {
      boolean needsParens =
          hasBinarySegmentSpecifiers(segment) && needsParens(segment.expression());
      if (needsParens) {
        out.append('(');
      }
      render(segment.expression(), out, indent);
      if (needsParens) {
        out.append(')');
      }
    }
    renderBinarySegmentSpecifiers(segment.size(), segment.type(), segment.unit(), out);
  }

  private void render(BinaryPattern binary, StringBuilder out) {
    List<BinarySegmentPattern> segments = binary.segments();
    if (segments.isEmpty()) {
      out.append("<<>>");
      return;
    }
    if (segments.size() == 1 && isSimpleLiteralSegment(segments.get(0))) {
      out.append("<<\"").append(escapeString(segments.get(0).literal())).append("\">>");
      return;
    }
    String linePrefix = currentLinePrefix(out);
    if (shouldWrapBinaryPatternSegments(segments, linePrefix)) {
      renderBinaryPatternSegmentsMultiline(segments, out, linePrefix);
      return;
    }
    out.append("<<");
    for (int i = 0; i < segments.size(); i++) {
      if (i > 0) {
        out.append(", ");
      }
      render(segments.get(i), out);
    }
    out.append(">>");
  }

  private void render(BinarySegmentPattern segment, StringBuilder out) {
    if (segment.literal() != null) {
      out.append('"').append(escapeString(segment.literal())).append('"');
    } else {
      render(segment.pattern(), out);
    }
    renderBinarySegmentSpecifiers(segment.size(), segment.type(), segment.unit(), out);
  }

  private void renderBinarySegmentSpecifiers(
      Integer size, String type, Integer unit, StringBuilder out) {
    if (size != null) {
      out.append(':').append(size);
    }
    if (type != null) {
      out.append('/').append(type);
      if (unit != null) {
        out.append("-unit:").append(unit);
      }
    }
  }

  private boolean isSimpleLiteralSegment(BinarySegmentExpr segment) {
    return segment.literal() != null
        && segment.expression() == null
        && segment.size() == null
        && segment.type() == null
        && segment.unit() == null;
  }

  private boolean isSimpleLiteralSegment(BinarySegmentPattern segment) {
    return segment.literal() != null
        && segment.pattern() == null
        && segment.size() == null
        && segment.type() == null
        && segment.unit() == null;
  }

  private boolean hasBinarySegmentSpecifiers(BinarySegmentExpr segment) {
    return segment.size() != null || segment.type() != null || segment.unit() != null;
  }

  private boolean needsParens(Expression expression) {
    return !(expression instanceof Variable
        || expression instanceof AtomExpr
        || expression instanceof IntegerExpr);
  }

  private boolean needsRemoteTargetParens(Expression expression) {
    if (expression instanceof InfixExpr) {
      return false;
    }
    return needsParens(expression);
  }

  private void render(TuplePattern tuple, StringBuilder out) {
    out.append('{');
    renderPatterns(tuple.elements(), out);
    out.append('}');
  }

  private void render(RecordPattern record, StringBuilder out) {
    if (record.alias() != null) {
      out.append(record.alias()).append(" = ");
    }
    out.append('#').append(record.name()).append('{');
    List<RecordPatternField> fields = record.fields();
    for (int i = 0; i < fields.size(); i++) {
      RecordPatternField field = fields.get(i);
      if (field.pattern() != null) {
        out.append(field.name()).append(" = ");
        render(field.pattern(), out);
      } else {
        out.append(field.name());
      }
      if (i < fields.size() - 1) {
        out.append(", ");
      }
    }
    out.append('}');
  }

  private void render(MapPattern map, StringBuilder out) {
    if (map.variable() != null) {
      out.append(map.variable()).append(" = ");
    }
    out.append("#{");
    renderMapPatternEntries(map.entries(), out);
    out.append('}');
  }

  private void renderMapPatternEntries(List<MapPatternEntry> entries, StringBuilder out) {
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) {
        out.append(", ");
      }
      MapPatternEntry entry = entries.get(i);
      render(entry.key(), out, "");
      out.append(entry.updateOnly() ? " := " : " => ");
      render(entry.value(), out);
    }
  }

  private void render(ListPattern list, StringBuilder out) {
    out.append('[');
    renderPatterns(list.elements(), out);
    if (list.tail() != null) {
      out.append(" | ");
      render(list.tail(), out);
    }
    out.append(']');
  }
}
