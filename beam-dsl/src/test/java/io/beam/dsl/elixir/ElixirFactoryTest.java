package io.beam.dsl.elixir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ElixirFactoryTest {

  @Test
  void caseExprOfMatchesConstructor() {
    List<Clause> clauses = List.of(Clause.of(VariablePattern.of("x"), Variable.of("x")));
    assertEquals(
        new CaseExpr(Variable.of("value"), clauses), CaseExpr.of(Variable.of("value"), clauses));
    assertEquals(new CaseExpr(null, clauses), CaseExpr.piped(clauses));
  }

  @Test
  void blockExprOfMatchesConstructor() {
    List<Expression> statements = List.of(AtomExpr.of("ok"));
    assertEquals(new BlockExpr(statements), BlockExpr.of(statements));
  }

  @Test
  void dotCallExprOfMatchesConstructor() {
    assertEquals(
        new DotCallExpr(Variable.of("map"), "get", List.of(AtomExpr.of("key"))),
        DotCallExpr.of(Variable.of("map"), "get", List.of(AtomExpr.of("key"))));
  }

  @Test
  void pipeExprOfMatchesConstructor() {
    List<PipeStep> steps = List.of(PipeStep.of(LocalCallExpr.of("decode", List.of()), List.of()));
    assertEquals(new PipeExpr(Variable.of("body"), steps), PipeExpr.of(Variable.of("body"), steps));
  }

  @Test
  void pipeStepOfMatchesConstructor() {
    assertEquals(
        new PipeStep(LocalCallExpr.of("decode", List.of()), List.of(StringExpr.of("utf8"))),
        PipeStep.of(LocalCallExpr.of("decode", List.of()), List.of(StringExpr.of("utf8"))));
  }

  @Test
  void comparisonGuardOfMatchesConstructor() {
    assertEquals(
        new ComparisonGuard(Variable.of("x"), "==", IntegerExpr.of(1)),
        ComparisonGuard.of(Variable.of("x"), "==", IntegerExpr.of(1)));
  }

  @Test
  void ifExprOfMatchesConstructor() {
    Expression condition = new InfixExpr(Variable.of("x"), ">", IntegerExpr.of(0));
    Expression thenBranch = AtomExpr.of("ok");
    Expression elseBranch = AtomExpr.of("error");

    assertEquals(
        new IfExpr(condition, thenBranch, elseBranch, false),
        IfExpr.of(condition, thenBranch, elseBranch));
    assertEquals(
        new IfExpr(condition, thenBranch, elseBranch, true),
        IfExpr.of(condition, thenBranch, elseBranch, true));
    assertEquals(
        new IfExpr(condition, thenBranch, elseBranch, true),
        IfExpr.inline(condition, thenBranch, elseBranch));
  }

  @Test
  void interpolatedSegmentsOfMatchConstructors() {
    assertEquals(new InterpolatedLiteral("hello"), InterpolatedLiteral.of("hello"));
    assertEquals(
        new InterpolatedExpr(Variable.of("name")), InterpolatedExpr.of(Variable.of("name")));
    assertEquals(
        new InterpolatedStringExpr(
            List.of(InterpolatedLiteral.of("hello "), InterpolatedExpr.of(Variable.of("name")))),
        InterpolatedStringExpr.of(
            List.of(InterpolatedLiteral.of("hello "), InterpolatedExpr.of(Variable.of("name")))));
  }

  @Test
  void binaryExprOfMatchesConstructor() {
    List<BinarySegmentExpr> segments =
        List.of(BinarySegmentExpr.of(StringExpr.of("hello"), "utf8"));
    assertEquals(new BinaryExpr(segments), BinaryExpr.of(segments));
    assertEquals(
        new BinarySegmentExpr(StringExpr.of("hello"), null),
        BinarySegmentExpr.of(StringExpr.of("hello")));
    assertEquals(
        new BinarySegmentExpr(StringExpr.of("hello"), "utf8"),
        BinarySegmentExpr.of(StringExpr.of("hello"), "utf8"));
  }

  @Test
  void tryExprOfMatchesConstructor() {
    List<CatchClause> catchClauses =
        List.of(
            CatchClause.of(
                VariablePattern.of("kind"), VariablePattern.of("reason"), Variable.of("reason")));
    assertEquals(
        new TryExpr(AtomExpr.of("ok"), catchClauses), TryExpr.of(AtomExpr.of("ok"), catchClauses));
  }

  @Test
  void anonFunOfMatchesConstructor() {
    List<AnonFunClause> clauses =
        List.of(AnonFunClause.of(List.of(VariablePattern.of("x")), Variable.of("x")));
    assertEquals(new AnonFun(clauses), AnonFun.of(clauses));
  }

  @Test
  void functionOfMatchesConstructor() {
    List<FunctionHead> heads = List.of(FunctionHead.of(List.of(VariablePattern.of("x"))));
    Expression body = Variable.of("x");

    assertEquals(
        new Function("identity", false, heads, body, null, null, true),
        Function.of("identity", false, heads, body, true));
    assertEquals(
        new Function(
            "identity",
            false,
            heads,
            body,
            Spec.of("identity(term()) :: term()"),
            FunctionDoc.of("Returns the argument"),
            false),
        Function.of(
            "identity",
            false,
            heads,
            body,
            Spec.of("identity(term()) :: term()"),
            FunctionDoc.of("Returns the argument"),
            false));
  }

  @Test
  void moduleOfMatchesConstructor() {
    List<Function> functions =
        List.of(
            Function.of(
                "identity",
                false,
                List.of(FunctionHead.of(List.of(VariablePattern.of("x")))),
                Variable.of("x"),
                true));
    assertEquals(
        new Module(
            "Example",
            Moduledoc.of("Example module"),
            List.of(UseDirective.of("Jason", List.of())),
            List.of(Alias.of("Types")),
            List.of("@moduledoc false"),
            List.of(),
            List.of(),
            List.of(),
            functions),
        Module.of(
            "Example",
            Moduledoc.of("Example module"),
            List.of(UseDirective.of("Jason", List.of())),
            List.of(Alias.of("Types")),
            List.of("@moduledoc false"),
            List.of(),
            List.of(),
            List.of(),
            functions));
  }

  @Test
  void typesModuleOfMatchesConstructor() {
    TypeDef typeDef = TypeDef.of("user", "map()");
    List<DefstructField> fields = List.of();
    assertEquals(
        new TypesModule("UserTypes", Moduledoc.of("Types"), typeDef, fields),
        TypesModule.of("UserTypes", Moduledoc.of("Types"), typeDef, fields));
    assertEquals(new TypeDef("user", "map()"), TypeDef.of("user", "map()"));
  }

  @Test
  void useDirectiveOfMatchesConstructor() {
    List<UseOption> options =
        List.of(UseOption.of("only", ListExpr.of(List.of(AtomExpr.of("decode")))));
    assertEquals(new UseDirective("Jason", options), UseDirective.of("Jason", options));
    assertEquals(
        new UseOption("only", ListExpr.of(List.of(AtomExpr.of("decode")))),
        UseOption.of("only", ListExpr.of(List.of(AtomExpr.of("decode")))));
  }

  @Test
  void raiseExprOfMatchesConstructor() {
    Expression exception = AtomExpr.of("error");
    Expression message = StringExpr.of("failed");

    assertEquals(new RaiseExpr(exception, message, false), RaiseExpr.of(exception, message));
    assertEquals(
        new RaiseExpr(exception, message, true), RaiseExpr.parenthesized(exception, message));
  }
}
