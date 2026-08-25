package io.beam.dsl.elixir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ElixirRendererTest {

  @Test
  void printWidthMatchesMixFormat() {
    assertEquals(98, DefaultElixirRenderer.printWidthForTests());
  }

  @Test
  void rendersAtomExpr() {
    assertEquals(":foo", ElixirRenderer.renderExpression(AtomExpr.of("foo")));
  }

  @Test
  void rendersNilAndBoolean() {
    assertEquals("nil", ElixirRenderer.renderExpression(NilExpr.of()));
    assertEquals("true", ElixirRenderer.renderExpression(BooleanExpr.of(true)));
  }

  @Test
  void rendersStringExprWithEscaping() {
    assertEquals("\"x\\#{y}\"", ElixirRenderer.renderExpression(StringExpr.of("x#{y}")));
  }

  @Test
  void rendersStringExpr() {
    assertEquals("\"hello\"", ElixirRenderer.renderExpression(StringExpr.of("hello")));
  }

  @Test
  void rendersTupleExpr() {
    assertEquals(
        "{:ok, resp}",
        ElixirRenderer.renderExpression(
            TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("resp")))));
  }

  @Test
  void rendersListExpr() {
    assertEquals(
        "[1, 2, 3]",
        ElixirRenderer.renderExpression(
            ListExpr.of(List.of(IntegerExpr.of(1), IntegerExpr.of(2), IntegerExpr.of(3)))));
  }

  @Test
  void rendersListExprWithTail() {
    assertEquals(
        "[1, 2] ++ tail",
        ElixirRenderer.renderExpression(
            ListExpr.of(List.of(IntegerExpr.of(1), IntegerExpr.of(2)), Variable.of("tail"))));
    assertEquals(
        "[{\"Action\", action}, {\"Version\", version}] ++ flatten_query_input(input)",
        ElixirRenderer.renderExpression(
            ListExpr.of(
                List.of(
                    TupleExpr.of(List.of(StringExpr.of("Action"), Variable.of("action"))),
                    TupleExpr.of(List.of(StringExpr.of("Version"), Variable.of("version")))),
                LocalCallExpr.of("flatten_query_input", List.of(Variable.of("input"))))));
  }

  @Test
  void rendersListExprWithTailMultiline() {
    assertEquals(
        """
        [
          first_element(input),
          second_element(input)
        ] ++ flatten_query_input(input)""",
        ElixirRenderer.renderExpression(
            ListExpr.of(
                List.of(
                    LocalCallExpr.of("first_element", List.of(Variable.of("input"))),
                    LocalCallExpr.of("second_element", List.of(Variable.of("input")))),
                LocalCallExpr.of("flatten_query_input", List.of(Variable.of("input"))))));
  }

  @Test
  void rendersMapExpr() {
    assertEquals("%{}", ElixirRenderer.renderExpression(MapExpr.of(List.of())));
    assertEquals(
        "%{key: val}",
        ElixirRenderer.renderExpression(
            MapExpr.of(List.of(MapEntry.atomKey("key", Variable.of("val"))))));
    assertEquals(
        "%{\"k\" => v}",
        ElixirRenderer.renderExpression(
            MapExpr.of(List.of(MapEntry.stringKey("k", Variable.of("v"))))));
    assertEquals(
        "%{acc | key: val}",
        ElixirRenderer.renderExpression(
            MapExpr.of(Variable.of("acc"), List.of(MapEntry.atomKey("key", Variable.of("val"))))));
  }

  @Test
  void rendersStructExpr() {
    assertEquals(
        "%RuntimeTypes.HttpRequest{}",
        ElixirRenderer.renderExpression(StructExpr.of("RuntimeTypes.HttpRequest", List.of())));
    assertEquals(
        "%Types.BasicItem{name: item}",
        ElixirRenderer.renderExpression(
            StructExpr.of(
                "Types.BasicItem", List.of(StructField.of("name", Variable.of("item"))))));
    assertEquals(
        "%HttpRequest{request | headers: headers}",
        ElixirRenderer.renderExpression(
            StructExpr.update(
                Variable.of("request"),
                "HttpRequest",
                List.of(StructField.of("headers", Variable.of("headers"))))));
  }

  @Test
  void rendersStructPattern() {
    assertEquals(
        "%RuntimeTypes.HttpResponse{status: status}",
        ElixirRenderer.renderPattern(
            new StructPattern(
                "RuntimeTypes.HttpResponse",
                List.of(new StructPatternField("status", VariablePattern.of("status"))))));
    assertEquals(
        "%{:headers => headers}",
        ElixirRenderer.renderPattern(
            new StructPattern(
                null, List.of(new StructPatternField("headers", VariablePattern.of("headers"))))));
  }

  @Test
  void rendersRemoteCallExpr() {
    assertEquals(
        "Map.fetch!(config, :credentials)",
        ElixirRenderer.renderExpression(
            RemoteCallExpr.of(
                "Map", "fetch!", List.of(Variable.of("config"), AtomExpr.of("credentials")))));
  }

  @Test
  void rendersLocalCallExpr() {
    assertEquals(
        "inspect(value)",
        ElixirRenderer.renderExpression(
            LocalCallExpr.of("inspect", List.of(Variable.of("value")))));
  }

  @Test
  void rendersCaptureExpr() {
    assertEquals(
        "&encode_event_stream_event/1",
        ElixirRenderer.renderExpression(CaptureExpr.of("encode_event_stream_event", 1)));
  }

  @Test
  void rendersInfixExpr() {
    assertEquals(
        "\"/names/\" <> uri_encode(name)",
        ElixirRenderer.renderExpression(
            new InfixExpr(
                StringExpr.of("/names/"),
                "<>",
                LocalCallExpr.of("uri_encode", List.of(Variable.of("name"))))));
  }

  @Test
  void rendersPipeExpr() {
    assertEquals(
        "body |> AwsEventStream.decode_frames()",
        ElixirRenderer.renderExpression(
            new PipeExpr(
                Variable.of("body"),
                List.of(
                    new PipeStep(
                        RemoteCallExpr.of("AwsEventStream", "decode_frames", List.of()),
                        List.of())))));
  }

  @Test
  void rendersMatchExpr() {
    assertEquals(
        "path = \"/names/\"",
        ElixirRenderer.renderExpression(MatchExpr.bind("path", StringExpr.of("/names/"))));
  }

  @Test
  void rendersInterpolatedStringExpr() {
    assertEquals(
        "\"#{part_a}-#{part_b}\"",
        ElixirRenderer.renderExpression(
            new InterpolatedStringExpr(
                List.of(
                    new InterpolatedExpr(Variable.of("part_a")),
                    new InterpolatedLiteral("-"),
                    new InterpolatedExpr(Variable.of("part_b"))))));
  }

  @Test
  void rendersCaseExpr() {
    String rendered =
        ElixirRenderer.renderExpression(
            new CaseExpr(
                RemoteCallExpr.of(
                    "RuntimeHttp", "dispatch", List.of(Variable.of("config"), Variable.of("req"))),
                List.of(
                    Clause.of(
                        TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("resp"))),
                        RemoteCallExpr.of(
                            "HttpServiceRestJson1",
                            "decode_get_name_response",
                            List.of(Variable.of("resp")))),
                    Clause.of(
                        TuplePattern.of(
                            List.of(AtomPattern.of("error"), VariablePattern.of("reason"))),
                        TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("reason")))))));
    assertEquals(
        """
        case RuntimeHttp.dispatch(config, req) do
          {:ok, resp} -> HttpServiceRestJson1.decode_get_name_response(resp)
          {:error, reason} -> {:error, reason}
        end""",
        rendered);
  }

  @Test
  void rendersInlineIfExpr() {
    assertEquals(
        "if(body == \"\", do: %{}, else: Jason.decode!(body))",
        ElixirRenderer.renderExpression(
            new IfExpr(
                new InfixExpr(Variable.of("body"), "==", StringExpr.of("")),
                MapExpr.of(List.of()),
                RemoteCallExpr.of("Jason", "decode!", List.of(Variable.of("body"))),
                true)));
  }

  @Test
  void rendersAnonFun() {
    assertEquals(
        "fn x -> x end",
        ElixirRenderer.renderExpression(
            new AnonFun(
                List.of(AnonFunClause.of(List.of(VariablePattern.of("x")), Variable.of("x"))))));
  }

  @Test
  void rendersRaiseExpr() {
    assertEquals(
        "raise(ArgumentError, \"unknown event\")",
        ElixirRenderer.renderExpression(
            RaiseExpr.parenthesized(Variable.of("ArgumentError"), StringExpr.of("unknown event"))));
  }

  @Test
  void rendersBinaryExpr() {
    assertEquals(
        "<<a::32, b::16, _::4>>",
        ElixirRenderer.renderExpression(
            new BinaryExpr(
                List.of(
                    new BinarySegmentExpr(Variable.of("a"), "32"),
                    new BinarySegmentExpr(Variable.of("b"), "16"),
                    new BinarySegmentExpr(Variable.of("_"), "4")))));
  }

  @Test
  void rendersStringPattern() {
    assertEquals("\"FOO\"", ElixirRenderer.renderPattern(StringPattern.of("FOO")));
  }

  @Test
  void rendersPinPattern() {
    assertEquals("^config", ElixirRenderer.renderPattern(PinPattern.of("config")));
  }

  @Test
  void rendersIntegerPattern() {
    assertEquals("42", ElixirRenderer.renderPattern(IntegerPattern.of(42)));
  }

  @Test
  void rendersBinaryPattern() {
    assertEquals("<<>>", ElixirRenderer.renderPattern(BinaryPattern.of(List.of())));
    assertEquals(
        "<<\"true\">>",
        ElixirRenderer.renderPattern(
            BinaryPattern.of(List.of(BinarySegmentPattern.literal("true")))));
  }

  @Test
  void rendersBinaryPatternWithSegments() {
    BinaryPattern pattern =
        BinaryPattern.of(
            List.of(
                BinarySegmentPattern.literal("{"),
                BinarySegmentPattern.of(VariablePattern.of("rest"), "binary")));
    assertEquals("<<\"{\", rest::binary>>", ElixirRenderer.renderPattern(pattern));
  }

  @Test
  void rendersConcatPattern() {
    assertEquals(
        "\"[\" <> _",
        ElixirRenderer.renderPattern(
            ConcatPattern.of(StringPattern.of("["), WildcardPattern.of())));
  }

  @Test
  void rendersGuards() {
    DefaultElixirRenderer renderer = new DefaultElixirRenderer();
    assertEquals("is_map(map)", renderer.renderGuardForTest(IsTypeGuard.of("is_map", "map")));
    assertEquals(
        "is_function(handler, 3)",
        renderer.renderGuardForTest(FunctionArityGuard.of("handler", 3)));
    assertEquals(
        "is_binary(id) and is_binary(secret)",
        renderer.renderGuardForTest(
            AndGuard.of(
                List.of(
                    IsTypeGuard.of("is_binary", "id"), IsTypeGuard.of("is_binary", "secret")))));
    assertEquals(
        "map == %{}",
        renderer.renderGuardForTest(
            new ComparisonGuard(Variable.of("map"), "==", MapExpr.of(List.of()))));
  }

  @Test
  void rendersNotExprInGuard() {
    DefaultElixirRenderer renderer = new DefaultElixirRenderer();
    assertEquals(
        "not is_nil(value)",
        renderer.renderGuardForTest(
            ExpressionGuard.of(
                NotExpr.of(LocalCallExpr.of("is_nil", List.of(Variable.of("value")))))));
  }

  @Test
  void rendersCondExpr() {
    assertEquals(
        """
        cond do
          is_binary(endpoint) -> endpoint
          true -> "default"
        end""",
        ElixirRenderer.renderExpression(
            CondExpr.of(
                List.of(
                    CondClause.of(
                        LocalCallExpr.of("is_binary", List.of(Variable.of("endpoint"))),
                        Variable.of("endpoint")),
                    CondClause.of(BooleanExpr.of(true), StringExpr.of("default"))))));
  }

  @Test
  void rendersWithExprWithElse() {
    assertEquals(
        """
        with {:ok, value} <- fetch(input) do
          value
        else
          {:error, reason} -> {:error, reason}
        end""",
        ElixirRenderer.renderExpression(
            WithExpr.of(
                List.of(
                    WithBinding.of(
                        TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("value"))),
                        LocalCallExpr.of("fetch", List.of(Variable.of("input"))))),
                Variable.of("value"),
                List.of(
                    WithElseClause.of(
                        TuplePattern.of(
                            List.of(AtomPattern.of("error"), VariablePattern.of("reason"))),
                        TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("reason"))))))));
  }

  @Test
  void rendersCharlistExpr() {
    assertEquals(
        "~c\"content-type\"", ElixirRenderer.renderExpression(CharlistExpr.of("content-type")));
  }

  @Test
  void rendersCaseExprWithGuard() {
    assertEquals(
        """
        case Map.get(handlers, fun) do
          handler when is_function(handler, 3) -> handler.(ctx, input, meta)
          _ -> {:error, :not_implemented}
        end""",
        ElixirRenderer.renderExpression(
            new CaseExpr(
                RemoteCallExpr.of(
                    "Map", "get", List.of(Variable.of("handlers"), Variable.of("fun"))),
                List.of(
                    Clause.of(
                        VariablePattern.of("handler"),
                        FunctionArityGuard.of("handler", 3),
                        dotCall(
                            Variable.of("handler"),
                            List.of(
                                Variable.of("ctx"), Variable.of("input"), Variable.of("meta")))),
                    Clause.of(
                        WildcardPattern.of(),
                        TupleExpr.of(
                            List.of(AtomExpr.of("error"), AtomExpr.of("not_implemented"))))))));
  }

  @Test
  void rendersOneLinerFunction() {
    assertEquals(
        "defp decode_basic_string(\"FOO\"), do: :foo\n",
        ElixirRenderer.renderFunction(
            new Function(
                "decode_basic_string",
                true,
                List.of(FunctionHead.of(List.of(StringPattern.of("FOO")))),
                AtomExpr.of("foo"),
                null,
                null,
                true)));
  }

  @Test
  void rendersFunctionWithDocAndSpec() {
    assertEquals(
        """
        @doc "Encodes events"
        @spec encode(list()) :: binary()
        def encode(events) when is_list(events), do: :ok
        """,
        ElixirRenderer.renderFunction(
            new Function(
                "encode",
                false,
                List.of(
                    FunctionHead.of(
                        List.of(VariablePattern.of("events")),
                        IsTypeGuard.of("is_list", "events"))),
                AtomExpr.of("ok"),
                Spec.of("encode(list()) :: binary()"),
                FunctionDoc.of("Encodes events"),
                true)));
  }

  @Test
  void rendersBlockFunction() {
    assertEquals(
        """
        defp decode_basic_string(v) when is_binary(v) do
          {:unknown, v}
        end
        """,
        ElixirRenderer.renderFunction(
            new Function(
                "decode_basic_string",
                true,
                List.of(
                    FunctionHead.of(
                        List.of(VariablePattern.of("v")), IsTypeGuard.of("is_binary", "v"))),
                TupleExpr.of(List.of(AtomExpr.of("unknown"), Variable.of("v"))),
                null,
                null,
                false)));
  }

  @Test
  void rendersModule() {
    Function encode =
        new Function(
            "encode_event_stream",
            false,
            List.of(
                FunctionHead.of(
                    List.of(VariablePattern.of("events")), IsTypeGuard.of("is_list", "events"))),
            LocalCallExpr.of(
                "Enum.map",
                List.of(Variable.of("events"), CaptureExpr.of("encode_event_stream_event", 1))),
            null,
            FunctionDoc.of("Encodes a list of event stream events into framed binaries."),
            false);
    assertEquals(
        """
        defmodule EventStreamRestJsonServiceEventStream do
          @moduledoc "Generated helpers"

          alias EventStreamRestJsonServiceTypes

          @doc "Encodes a list of event stream events into framed binaries."
          def encode_event_stream(events) when is_list(events) do
            Enum.map(events, &encode_event_stream_event/1)
          end
        end
        """,
        ElixirRenderer.render(
            new Module(
                "EventStreamRestJsonServiceEventStream",
                Moduledoc.of("Generated helpers"),
                List.of(),
                List.of(Alias.of("EventStreamRestJsonServiceTypes")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(encode))));
  }

  @Test
  void rendersTypesModule() {
    assertEquals(
        """
        defmodule GetNameOutput do
          @moduledoc "structure GetNameOutput"

          @type t :: %__MODULE__{
                  name: HttpServiceClient.name() | nil
                }

          defstruct [:name]
        end
        """,
        ElixirRenderer.render(
            new TypesModule(
                "GetNameOutput",
                Moduledoc.of("structure GetNameOutput"),
                new TypeDef(
                    "t", "%__MODULE__{\n          name: HttpServiceClient.name() | nil\n        }"),
                List.of(DefstructField.field("name")))));
  }

  @Test
  void rendersDotCallExpr() {
    assertEquals("fun.()", ElixirRenderer.renderExpression(dotCall(Variable.of("fun"), List.of())));
    assertEquals(
        "handler.(ctx, input, meta)",
        ElixirRenderer.renderExpression(
            dotCall(
                Variable.of("handler"),
                List.of(Variable.of("ctx"), Variable.of("input"), Variable.of("meta")))));
    assertEquals(
        "v.(:syntax)",
        ElixirRenderer.renderExpression(dotCall(Variable.of("v"), List.of(AtomExpr.of("syntax")))));
    assertEquals(
        "handler.handle_create_user(%{}, input, %{})",
        ElixirRenderer.renderExpression(
            new DotCallExpr(
                Variable.of("handler"),
                "handle_create_user",
                List.of(MapExpr.of(List.of()), Variable.of("input"), MapExpr.of(List.of())))));
  }

  @Test
  void rendersBlockExpr() {
    assertEquals(
        """
        result =
          case fetch("x") do
            {:ok, value} -> value
            :error -> :default
          end

        filtered = Enum.filter_map([result], fn v -> v end)

        if length(filtered) > 0 do
          hd(filtered)
        else
          nil
        end""",
        ElixirRenderer.renderExpression(
            new BlockExpr(
                List.of(
                    MatchExpr.bind(
                        "result",
                        new CaseExpr(
                            LocalCallExpr.of("fetch", List.of(StringExpr.of("x"))),
                            List.of(
                                Clause.of(
                                    TuplePattern.of(
                                        List.of(AtomPattern.of("ok"), VariablePattern.of("value"))),
                                    Variable.of("value")),
                                Clause.of(AtomPattern.of("error"), AtomExpr.of("default"))))),
                    MatchExpr.bind(
                        "filtered",
                        RemoteCallExpr.of(
                            "Enum",
                            "filter_map",
                            List.of(
                                ListExpr.of(List.of(Variable.of("result"))),
                                new AnonFun(
                                    List.of(
                                        AnonFunClause.of(
                                            List.of(VariablePattern.of("v")),
                                            Variable.of("v"))))))),
                    new IfExpr(
                        new InfixExpr(
                            LocalCallExpr.of("length", List.of(Variable.of("filtered"))),
                            ">",
                            IntegerExpr.of(0)),
                        LocalCallExpr.of("hd", List.of(Variable.of("filtered"))),
                        NilExpr.of(),
                        false)))));
  }

  @Test
  void rendersTryExpr() {
    assertEquals(
        """
        try do
          fun.()
        catch
          _, reason -> {:error, reason}
        end""",
        ElixirRenderer.renderExpression(
            new TryExpr(
                dotCall(Variable.of("fun"), List.of()),
                List.of(
                    new CatchClause(
                        WildcardPattern.of(),
                        VariablePattern.of("reason"),
                        TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("reason"))))))));
  }

  @Test
  void rendersMultilinePipeExpr() {
    assertEquals(
        """
        %{"name" => record.name, "count" => record.count}
        |> Enum.reject(fn {_k, v} -> is_nil(v) end)
        |> Map.new()""",
        ElixirRenderer.renderExpression(
            new PipeExpr(
                MapExpr.of(
                    List.of(
                        MapEntry.stringKey("name", dot(Variable.of("record"), "name")),
                        MapEntry.stringKey("count", dot(Variable.of("record"), "count")))),
                List.of(
                    new PipeStep(
                        RemoteCallExpr.of(
                            "Enum",
                            "reject",
                            List.of(
                                new AnonFun(
                                    List.of(
                                        AnonFunClause.of(
                                            List.of(
                                                TuplePattern.of(
                                                    List.of(
                                                        VariablePattern.of("_k"),
                                                        VariablePattern.of("v")))),
                                            LocalCallExpr.of(
                                                "is_nil", List.of(Variable.of("v")))))))),
                        List.of()),
                    new PipeStep(RemoteCallExpr.of("Map", "new", List.of()), List.of())))));
  }

  @Test
  void rendersNestedCaseWithInlineClauseBodies() {
    assertEquals(
        """
        decoded =
          case body do
            "" ->
              %{}

            _ ->
              case Jason.decode(body) do
                {:ok, val} -> val
                {:error, _} -> %{}
              end
          end""",
        ElixirRenderer.renderExpression(
            MatchExpr.bind(
                "decoded",
                new CaseExpr(
                    Variable.of("body"),
                    List.of(
                        Clause.of(StringPattern.of(""), MapExpr.of(List.of())),
                        Clause.of(
                            WildcardPattern.of(),
                            new CaseExpr(
                                RemoteCallExpr.of("Jason", "decode", List.of(Variable.of("body"))),
                                List.of(
                                    Clause.of(
                                        TuplePattern.of(
                                            List.of(
                                                AtomPattern.of("ok"), VariablePattern.of("val"))),
                                        Variable.of("val")),
                                    Clause.of(
                                        TuplePattern.of(
                                            List.of(AtomPattern.of("error"), WildcardPattern.of())),
                                        MapExpr.of(List.of()))))))))));
  }

  @Test
  void rendersMatchExprWithTuplePattern() {
    assertEquals(
        """
        {scheme, authority} = split_base_url(base_url)
        req_url = scheme <> authority <> path""",
        ElixirRenderer.renderExpression(
            new BlockExpr(
                List.of(
                    MatchExpr.bind(
                        TuplePattern.of(
                            List.of(VariablePattern.of("scheme"), VariablePattern.of("authority"))),
                        LocalCallExpr.of("split_base_url", List.of(Variable.of("base_url")))),
                    MatchExpr.bind(
                        "req_url",
                        new InfixExpr(
                            new InfixExpr(Variable.of("scheme"), "<>", Variable.of("authority")),
                            "<>",
                            Variable.of("path")))))));
  }

  @Test
  void rendersBlockIfExpr() {
    assertEquals(
        """
        if retryable?(err) and attempts > 1 do
          Process.sleep(base)
          with_retry(fun, attempts - 1, base, n + 1)
        else
          err
        end""",
        ElixirRenderer.renderExpression(
            new IfExpr(
                new InfixExpr(
                    LocalCallExpr.of("retryable?", List.of(Variable.of("err"))),
                    "and",
                    new InfixExpr(Variable.of("attempts"), ">", IntegerExpr.of(1))),
                new BlockExpr(
                    List.of(
                        RemoteCallExpr.of("Process", "sleep", List.of(Variable.of("base"))),
                        LocalCallExpr.of(
                            "with_retry",
                            List.of(
                                Variable.of("fun"),
                                new InfixExpr(Variable.of("attempts"), "-", IntegerExpr.of(1)),
                                Variable.of("base"),
                                new InfixExpr(Variable.of("n"), "+", IntegerExpr.of(1)))))),
                Variable.of("err"),
                false)));
  }

  @Test
  void rendersMultilineStructExpr() {
    assertEquals(
        """
        %Item{
          name: Map.get(map, "name"),
          count: Map.get(map, "count")
        }""",
        ElixirRenderer.renderExpression(
            StructExpr.of(
                "Item",
                List.of(
                    StructField.of(
                        "name",
                        RemoteCallExpr.of(
                            "Map", "get", List.of(Variable.of("map"), StringExpr.of("name")))),
                    StructField.of(
                        "count",
                        RemoteCallExpr.of(
                            "Map", "get", List.of(Variable.of("map"), StringExpr.of("count"))))))));
  }

  @Test
  void rendersGroupedFunctionHeads() {
    assertEquals(
        "def encode_union({:left, v}), do: %{\"left\" => v}\n",
        ElixirRenderer.renderFunction(
            new Function(
                "encode_union",
                false,
                List.of(
                    FunctionHead.of(
                        List.of(
                            TuplePattern.of(
                                List.of(AtomPattern.of("left"), VariablePattern.of("v")))))),
                MapExpr.of(List.of(MapEntry.stringKey("left", Variable.of("v")))),
                null,
                null,
                true)));
    assertEquals(
        "def encode_union({:right, v}), do: %{\"right\" => v}\n",
        ElixirRenderer.renderFunction(
            new Function(
                "encode_union",
                false,
                List.of(
                    FunctionHead.of(
                        List.of(
                            TuplePattern.of(
                                List.of(AtomPattern.of("right"), VariablePattern.of("v")))))),
                MapExpr.of(List.of(MapEntry.stringKey("right", Variable.of("v")))),
                null,
                null,
                true)));
  }

  @Test
  void rendersAnonFunWithGuard() {
    assertEquals(
        """
        fn
          v when v != nil -> encode_value(v)
          _ -> :pop
        end""",
        ElixirRenderer.renderExpression(
            new AnonFun(
                List.of(
                    AnonFunClause.of(
                        List.of(VariablePattern.of("v")),
                        new ComparisonGuard(Variable.of("v"), "!=", NilExpr.of()),
                        LocalCallExpr.of("encode_value", List.of(Variable.of("v")))),
                    AnonFunClause.of(List.of(WildcardPattern.of()), AtomExpr.of("pop"))))));
  }

  @Test
  void rendersCallbackWithoutDoc() {
    Callback callback =
        Callback.of(
            "handle_get_type_closure",
            List.of("term()", "BasicServiceTypes.GetTypeClosureInput.t()", "term()"),
            "{:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}");

    assertEquals(
        """
          @callback handle_get_type_closure(
                      term(),
                      BasicServiceTypes.GetTypeClosureInput.t(),
                      term()
          ) ::
            {:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}
        """,
        ElixirRenderer.renderCallback(callback));
  }

  @Test
  void rendersCallbackWithDoc() {
    Callback callback =
        Callback.of(
            "handle_get_type_closure",
            List.of("term()", "BasicServiceTypes.GetTypeClosureInput.t()", "term()"),
            "{:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}",
            FunctionDoc.of("Get type closure."));

    assertEquals(
        """
          @doc "Get type closure."
          @callback handle_get_type_closure(
                      term(),
                      BasicServiceTypes.GetTypeClosureInput.t(),
                      term()
          ) ::
            {:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}
        """,
        ElixirRenderer.renderCallback(callback));
  }

  @Test
  void rendersModuleWithCallbacks() {
    Callback callback =
        Callback.of(
            "handle_get_type_closure",
            List.of("term()", "BasicServiceTypes.GetTypeClosureInput.t()", "term()"),
            "{:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}",
            FunctionDoc.of("Get type closure."));
    Function callbacksFunction =
        new Function(
            "callbacks",
            false,
            List.of(FunctionHead.of(List.of())),
            ListExpr.of(List.of(AtomExpr.of("handle_get_type_closure"))),
            null,
            null,
            true);

    assertEquals(
        """
        defmodule BasicServiceBehaviour do
          @moduledoc "Generated behaviour."

          alias BasicServiceTypes

          @doc "Get type closure."
          @callback handle_get_type_closure(
                      term(),
                      BasicServiceTypes.GetTypeClosureInput.t(),
                      term()
          ) ::
            {:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}

          def callbacks, do: [:handle_get_type_closure]
        end
        """,
        ElixirRenderer.render(
            new Module(
                "BasicServiceBehaviour",
                Moduledoc.of("Generated behaviour."),
                List.of(),
                List.of(Alias.of("BasicServiceTypes")),
                List.of(),
                List.of(),
                List.of(callback),
                List.of(),
                List.of(callbacksFunction))));
  }

  private static DotCallExpr dotCall(Expression receiver, List<Expression> args) {
    return new DotCallExpr(receiver, "()", args);
  }

  private static DotCallExpr dot(Expression receiver, String field) {
    return new DotCallExpr(receiver, field, List.of());
  }
}
