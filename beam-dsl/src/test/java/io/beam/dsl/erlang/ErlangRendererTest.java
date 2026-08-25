package io.beam.dsl.erlang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErlangRendererTest {

  @Test
  void rendersStringExprWithEscaping() {
    assertEquals("\"a\\\"b\\\\c\"", ErlangRenderer.renderExpression(StringExpr.of("a\"b\\c")));
  }

  @Test
  void rendersAtomExpr() {
    AtomExpr atom = AtomExpr.of("foo");
    assertEquals("foo", ErlangRenderer.renderExpression(atom));
  }

  @Test
  void rendersNotExprInComprehensionFilter() {
    Expression filter =
        NotExpr.of(LocalCallExpr.of("is_element_string", List.of(Variable.of("T"))));
    assertEquals(
        "[T || T <- Content, not is_element_string(T)]",
        ErlangRenderer.renderExpression(
            ListComprehensionExpr.of(
                Variable.of("T"),
                List.of(
                    ListComprehensionGenerator.of(VariablePattern.of("T"), Variable.of("Content")),
                    ListComprehensionFilter.of(filter)))));
  }

  @Test
  void rendersFunRefExpr() {
    assertEquals(
        "fun should_retry/1", ErlangRenderer.renderExpression(FunRefExpr.of("should_retry", 1)));
  }

  @Test
  void rendersQuotedAtomExpr() {
    assertEquals("'xmlns:'", ErlangRenderer.renderExpression(QuotedAtomExpr.of("xmlns:")));
  }

  @Test
  void rendersCharPatternInListCons() {
    Pattern pattern =
        MatchPattern.of(
            ListPattern.cons(CharPattern.of('['), WildcardPattern.of()),
            VariablePattern.of("Line"));
    assertEquals("[$[ | _] = Line", ErlangRenderer.renderPattern(pattern));
  }

  @Test
  void rendersAtomPattern() {
    AtomPattern pattern = AtomPattern.of("foo");
    DefaultErlangRenderer renderer = new DefaultErlangRenderer();
    String funWrap =
        renderer.renderExpression(Fun.of(List.of(FunClause.of(pattern, null, AtomExpr.of("bar")))));
    assertEquals("fun(foo) -> bar end", funWrap);
  }

  @Test
  void rendersZeroArityFunWithBlockBody() {
    Fun fun =
        Fun.of(
            List.of(
                FunClause.of(
                    List.of(),
                    null,
                    MatchExpr.bind(
                        "Req",
                        AtomExpr.of("ok"),
                        CaseExpr.of(
                            Variable.of("Req"),
                            List.of(
                                Clause.of(
                                    TuplePattern.of(
                                        List.of(AtomPattern.of("ok"), WildcardPattern.of())),
                                    AtomExpr.of("done"))))))));
    String expected =
        """
        fun() ->
            Req = ok,
            case Req of
                {ok, _} -> done
            end
        end""";
    assertEquals(expected, ErlangRenderer.renderExpression(fun));
  }

  @Test
  void rendersBinaryExpr() {
    // BinaryExpr could mean something like bitstring binary construction in Erlang: <<1,2>>
    BinaryExpr binaryExpr = BinaryExpr.of("foo");
    DefaultErlangRenderer renderer = new DefaultErlangRenderer();
    String result = renderer.renderExpression(binaryExpr);
    String expected = "<<\"foo\">>";
    assertEquals(expected, result);
  }

  @Test
  void rendersBinaryExprWithSegments() {
    BinaryExpr binaryExpr =
        BinaryExpr.of(
            List.of(
                BinarySegmentExpr.literal("https://"),
                BinarySegmentExpr.of(Variable.of("Prefix"), "binary"),
                BinarySegmentExpr.literal("."),
                BinarySegmentExpr.of(Variable.of("Region"), "binary"),
                BinarySegmentExpr.literal(".amazonaws.com")));
    String expected = "<<\"https://\", Prefix/binary, \".\", Region/binary, \".amazonaws.com\">>";
    assertEquals(expected, ErlangRenderer.renderExpression(binaryExpr));
  }

  @Test
  void rendersBinaryExprWrappedInTupleWithoutDuplicatingPrefix() {
    BinaryExpr schemePrefix =
        BinaryExpr.of(
            List.of(
                BinarySegmentExpr.of(
                    LocalCallExpr.of("list_to_binary", List.of(Variable.of("Scheme"))), "binary"),
                BinarySegmentExpr.literal("://")));
    BinaryExpr authority =
        BinaryExpr.of(
            List.of(
                BinarySegmentExpr.of(
                    LocalCallExpr.of("list_to_binary", List.of(Variable.of("Host"))), "binary"),
                BinarySegmentExpr.of(Variable.of("PortSuffix"), "binary")));
    TupleExpr tuple = TupleExpr.of(List.of(schemePrefix, authority));

    String rendered = new DefaultErlangRenderer().renderExpressionForTest(tuple, "            ");

    assertTrue(rendered.contains("(list_to_binary(Host))"));
    assertTrue(rendered.contains("PortSuffix/binary"));
    assertFalse(
        rendered.contains(
            "{<<(list_to_binary(Scheme))/binary, \"://\">>,     PortSuffix/binary>>"));
    assertEquals(1, rendered.split("\\(list_to_binary\\(Scheme\\)\\)", -1).length - 1);
  }

  @Test
  void rendersBinaryExprWithParenthesizedSegment() {
    BinaryExpr binaryExpr =
        BinaryExpr.of(
            List.of(
                BinarySegmentExpr.literal(":"),
                BinarySegmentExpr.of(
                    LocalCallExpr.of("integer_to_binary", List.of(Variable.of("Port"))),
                    "binary")));
    String expected = "<<\":\", (integer_to_binary(Port))/binary>>";
    assertEquals(expected, ErlangRenderer.renderExpression(binaryExpr));
  }

  @Test
  void rendersBinaryPattern() {
    assertEquals("<<>>", ErlangRenderer.renderPattern(BinaryPattern.of("")));
    assertEquals("<<\"true\">>", ErlangRenderer.renderPattern(BinaryPattern.of("true")));
  }

  @Test
  void rendersBinaryPatternWithSegments() {
    BinaryPattern pattern =
        BinaryPattern.of(
            List.of(
                BinarySegmentPattern.of(VariablePattern.of("Y"), 4, "binary"),
                BinarySegmentPattern.literal("-"),
                BinarySegmentPattern.of(VariablePattern.of("Mo"), 2, "binary"),
                BinarySegmentPattern.literal("-"),
                BinarySegmentPattern.of(VariablePattern.of("D"), 2, "binary"),
                BinarySegmentPattern.literal("T"),
                BinarySegmentPattern.of(VariablePattern.of("H"), 2, "binary"),
                BinarySegmentPattern.literal(":"),
                BinarySegmentPattern.of(VariablePattern.of("Mi"), 2, "binary"),
                BinarySegmentPattern.literal(":"),
                BinarySegmentPattern.of(VariablePattern.of("S"), 2, "binary"),
                BinarySegmentPattern.of(WildcardPattern.of(), "binary")));
    String expected =
        "<<Y:4/binary, \"-\", Mo:2/binary, \"-\", D:2/binary, \"T\", H:2/binary, \":\","
            + " Mi:2/binary, \":\",\n"
            + "    S:2/binary, _/binary>>";
    assertEquals(expected, ErlangRenderer.renderPattern(pattern));
  }

  @Test
  void rendersBinaryPatternWithConsHead() {
    BinaryPattern pattern =
        BinaryPattern.of(
            List.of(
                BinarySegmentPattern.literal("{"),
                BinarySegmentPattern.of(VariablePattern.of("Rest"), "binary")));
    String expected = "<<\"{\", Rest/binary>>";
    assertEquals(expected, ErlangRenderer.renderPattern(pattern));
  }

  @Test
  void rendersCaseExpr() {
    // case X of 1 -> foo; 2 -> bar end
    Variable variable = Variable.of("X");
    Clause clause1 = Clause.of(IntegerPattern.of(1), AtomExpr.of("foo"));
    Clause clause2 = Clause.of(IntegerPattern.of(2), AtomExpr.of("bar"));
    List<Clause> clauses = List.of(clause1, clause2);

    CaseExpr caseExpr = CaseExpr.of(variable, clauses);

    DefaultErlangRenderer renderer = new DefaultErlangRenderer();
    String result = renderer.renderExpression(caseExpr);

    String expected =
        """
        case X of
            1 -> foo;
            2 -> bar
        end""";

    assertEquals(expected, result);
  }

  @Test
  void rendersCaseWithBrokenScrutinee() {
    Expression expression = GoldenRendererTest.syntaxCaseExpression();
    String expected =
        """
        case validate_checksum(Body, Headers, [
            <<"x-checksum-a">>,
            <<"x-checksum-b">>
        ]) of
            ok -> {ok, Output};
            {error, Reason} -> {error, {checksum_failed, Reason}}
        end""";
    assertEquals(expected, ErlangRenderer.renderExpression(expression));
  }

  @Test
  void rendersFunction() {
    // Define a function named "inc" with one clause: inc(X) -> X + 1.
    Function function =
        Function.of(
            "inc",
            List.of(
                FunctionClause.of(
                    List.of(VariablePattern.of("X")),
                    InfixExpr.of(Variable.of("X"), "+", IntegerExpr.of(1)))));
    DefaultErlangRenderer renderer = new DefaultErlangRenderer();
    String result = renderer.renderFunction(function);
    String expected = "inc(X) -> (X + 1).\n";
    assertEquals(expected, result);
  }

  @Test
  void rendersFunctionWithSpec() {
    // Define a function named "inc" with one clause, and add a -spec annotation.
    Spec spec = Spec.of("inc(integer()) -> integer()");
    Function function =
        Function.of(
            "inc",
            List.of(
                FunctionClause.of(
                    List.of(VariablePattern.of("X")),
                    InfixExpr.of(Variable.of("X"), "+", IntegerExpr.of(1)))),
            spec);
    DefaultErlangRenderer renderer = new DefaultErlangRenderer();
    String result = renderer.renderFunction(function);
    String expected =
        """
        -spec inc(integer()) -> integer().
        inc(X) -> (X + 1).
        """;
    assertEquals(expected, result);
  }

  @Test
  void rendersFunctionWithSpecAndDoc() {
    // Define a function named "inc" with one clause, with both -spec and doc annotation.
    Spec spec = Spec.of("inc(integer()) -> integer()");
    Doc doc = Doc.of("Increment X by one.\nWorks with integers.");
    Function function =
        Function.of(
            "inc",
            List.of(
                FunctionClause.of(
                    List.of(VariablePattern.of("X")),
                    InfixExpr.of(Variable.of("X"), "+", IntegerExpr.of(1)))),
            spec,
            doc);
    DefaultErlangRenderer renderer = new DefaultErlangRenderer();
    String result = renderer.renderFunction(function);
    String expected =
        """
        %% Increment X by one.
        %% Works with integers.
        -spec inc(integer()) -> integer().
        inc(X) -> (X + 1).
        """;
    assertEquals(expected, result);
  }

  @Test
  void rendersFunctionWithSpecAndEdoc() {
    // Define a function named "inc" with one clause, with both -spec and @doc Edoc annotation.
    Spec spec = Spec.of("inc(integer()) -> integer()");
    Edoc edoc = Edoc.of("Increment X by one.\nWorks with integers.");
    Function function =
        Function.of(
            "inc",
            List.of(
                FunctionClause.of(
                    List.of(VariablePattern.of("X")),
                    InfixExpr.of(Variable.of("X"), "+", IntegerExpr.of(1)))),
            spec,
            edoc);
    DefaultErlangRenderer renderer = new DefaultErlangRenderer();
    String result = renderer.renderFunction(function);
    String expected =
        """
        %% @doc Increment X by one.
        %% Works with integers.
        -spec inc(integer()) -> integer().
        inc(X) -> (X + 1).
        """;
    assertEquals(expected, result);
  }

  @Test
  void rendersFunctionWithMixedClauseWidths() {
    Function function =
        Function.of(
            "decode_color_labels",
            List.of(
                FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
                FunctionClause.of(List.of(AtomPattern.of("null")), AtomExpr.of("undefined")),
                FunctionClause.of(
                    List.of(VariablePattern.of("Map")),
                    IsTypeGuard.of("map", Variable.of("Map")),
                    RemoteCallExpr.of(
                        "maps",
                        "from_list",
                        List.of(
                            ListComprehensionExpr.of(
                                TupleExpr.of(
                                    List.of(
                                        LocalCallExpr.of("decode_color", List.of(Variable.of("K"))),
                                        Variable.of("V"))),
                                TuplePattern.of(
                                    List.of(VariablePattern.of("K"), VariablePattern.of("V"))),
                                RemoteCallExpr.of(
                                    "maps", "to_list", List.of(Variable.of("Map")))))))));
    String result = ErlangRenderer.renderFunction(function);
    String expected =
        """
        decode_color_labels(undefined) ->
            undefined;
        decode_color_labels(null) ->
            undefined;
        decode_color_labels(Map) when is_map(Map) ->
            maps:from_list([{decode_color(K), V} || {K, V} <- maps:to_list(Map)]).
        """;
    assertEquals(expected, result);
  }

  @Test
  void rendersRecordPatternInFunctionHead() {
    Function function =
        Function.of(
            "decode_get_user_request",
            List.of(
                FunctionClause.of(
                    List.of(
                        RecordPattern.of(
                            "http_request",
                            List.of(RecordPatternField.of("body", VariablePattern.of("Body"))))),
                    Variable.of("Body"))));
    DefaultErlangRenderer renderer = new DefaultErlangRenderer();
    String result = renderer.renderFunction(function);
    String expected =
        """
        decode_get_user_request(#http_request{body = Body}) ->
            Body.
        """;
    assertEquals(expected, result);
  }

  @Test
  void rendersRecordFieldAccessExpr() {
    assertEquals(
        "Request#http_request.path",
        ErlangRenderer.renderExpression(
            RecordFieldAccessExpr.of(Variable.of("Request"), "http_request", "path")));
    assertEquals(
        "Record#basic_item.name",
        ErlangRenderer.renderExpression(
            RecordFieldAccessExpr.of(Variable.of("Record"), "basic_item", "name")));
  }

  @Test
  void rendersRecordFieldAccessExprInCallArguments() {
    LocalCallExpr call =
        LocalCallExpr.of(
            "build_url",
            List.of(
                Variable.of("Host"),
                RecordFieldAccessExpr.of(Variable.of("Request"), "http_request", "path"),
                RecordFieldAccessExpr.of(Variable.of("Request"), "http_request", "query")));
    assertEquals(
        "build_url(Host, Request#http_request.path, Request#http_request.query)",
        ErlangRenderer.renderExpression(call));
  }

  @Test
  void rendersInfixExpr() {
    InfixExpr infixExpr = InfixExpr.of(IntegerExpr.of(1), "+", IntegerExpr.of(2));
    DefaultErlangRenderer renderer = new DefaultErlangRenderer();
    String result = renderer.renderExpression(infixExpr);
    String expected = "(1 + 2)";
    assertEquals(expected, result);
  }

  @Test
  void rendersTuplePattern() {
    TuplePattern pattern =
        TuplePattern.of(
            List.of(
                VariablePattern.of("Mega"),
                VariablePattern.of("Secs"),
                WildcardPattern.of("Micro")));
    String expected = "{Mega, Secs, _Micro}";
    assertEquals(expected, ErlangRenderer.renderPattern(pattern));
  }

  @Test
  void rendersMapExpr() {
    assertEquals("#{}", ErlangRenderer.renderExpression(MapExpr.of(List.of())));
    assertEquals(
        "#{<<\"message\">> => V}",
        ErlangRenderer.renderExpression(
            MapExpr.of(List.of(MapEntry.of(BinaryExpr.of("message"), Variable.of("V"))))));
    assertEquals(
        "Acc#{Key => Val}",
        ErlangRenderer.renderExpression(
            MapExpr.of(
                Variable.of("Acc"), List.of(MapEntry.of(Variable.of("Key"), Variable.of("Val"))))));
  }

  @Test
  void rendersMatchExprWithCase() {
    CaseExpr innerCase =
        CaseExpr.of(
            Variable.of("Body"),
            List.of(
                Clause.of(BinaryPattern.of(""), MapExpr.of(List.of())),
                Clause.of(
                    WildcardPattern.of(),
                    CaseExpr.of(
                        RemoteCallExpr.of("jsone", "try_decode", List.of(Variable.of("Body"))),
                        List.of(
                            Clause.of(
                                TuplePattern.of(
                                    List.of(
                                        AtomPattern.of("ok"),
                                        VariablePattern.of("Val"),
                                        WildcardPattern.of())),
                                Variable.of("Val")),
                            Clause.of(
                                TuplePattern.of(
                                    List.of(AtomPattern.of("error"), WildcardPattern.of())),
                                MapExpr.of(List.of())))))));

    MatchExpr match =
        MatchExpr.bind(
            "Decoded",
            innerCase,
            RecordExpr.of(
                "get_user_input",
                List.of(
                    RecordField.of(
                        "user_name",
                        RemoteCallExpr.of(
                            "maps",
                            "get",
                            List.of(
                                BinaryExpr.of("userName"),
                                Variable.of("Decoded"),
                                AtomExpr.of("undefined")))))));

    Function function =
        Function.of(
            "decode_get_user_request",
            List.of(
                FunctionClause.of(
                    List.of(
                        RecordPattern.of(
                            "http_request",
                            List.of(RecordPatternField.of("body", VariablePattern.of("Body"))))),
                    match)));

    String expected =
        """
        decode_get_user_request(#http_request{body = Body}) ->
            Decoded =
                case Body of
                    <<>> ->
                        #{};
                    _ ->
                        case jsone:try_decode(Body) of
                            {ok, Val, _} -> Val;
                            {error, _} -> #{}
                        end
                end,
            #get_user_input{
                user_name = maps:get(<<"userName">>, Decoded, undefined)
            }.
        """;
    assertEquals(expected, ErlangRenderer.renderFunction(function));
  }

  @Test
  void rendersMapPattern() {
    assertEquals("Map = #{}", ErlangRenderer.renderPattern(MapPattern.bind("Map")));
  }

  @Test
  void rendersListPattern() {
    assertEquals(
        "[{<<\"message\">>, V}]",
        ErlangRenderer.renderPattern(
            ListPattern.of(
                List.of(
                    TuplePattern.of(
                        List.of(BinaryPattern.of("message"), VariablePattern.of("V")))))));
  }

  @Test
  void rendersConsListPattern() {
    assertEquals(
        "[Base | _]",
        ErlangRenderer.renderPattern(
            ListPattern.cons(VariablePattern.of("Base"), WildcardPattern.of())));
    assertEquals(
        "[H | Rest]",
        ErlangRenderer.renderPattern(
            ListPattern.cons(VariablePattern.of("H"), VariablePattern.of("Rest"))));
  }

  @Test
  void rendersListComprehensionExprSingleLine() {
    ListComprehensionExpr comprehension =
        ListComprehensionExpr.of(
            Variable.of("V"),
            VariablePattern.of("V"),
            Variable.of("List"),
            InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("null")));

    String expected = "[V || V <- List, V =/= null]";
    assertEquals(expected, ErlangRenderer.renderExpression(comprehension));
  }

  @Test
  void rendersListComprehensionExprWithCallAndFilter() {
    ListComprehensionExpr comprehension =
        ListComprehensionExpr.of(
            LocalCallExpr.of("decode_basic_item", List.of(Variable.of("V"))),
            VariablePattern.of("V"),
            Variable.of("List"),
            InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("null")));

    String expected = "[decode_basic_item(V) || V <- List, V =/= null]";
    assertEquals(expected, ErlangRenderer.renderExpression(comprehension));
  }

  @Test
  void rendersListComprehensionExprMultilineWithMultipleFilters() {
    ListComprehensionExpr comprehension =
        ListComprehensionExpr.of(
            Variable.of("C"),
            List.of(
                ListComprehensionGenerator.of(VariablePattern.of("C"), Variable.of("Content")),
                ListComprehensionFilter.of(
                    LocalCallExpr.of("is_element", List.of(Variable.of("C")))),
                ListComprehensionFilter.of(
                    InfixExpr.of(
                        LocalCallExpr.of("element_name", List.of(Variable.of("C"))),
                        "=:=",
                        Variable.of("Name")))));

    String expected =
        """
        [
            C
         || C <- Content,
            is_element(C),
            element_name(C) =:= Name
        ]""";
    assertEquals(expected, ErlangRenderer.renderExpression(comprehension));
  }

  @Test
  void rendersListComprehensionExprMultilineWithTupleExpressionAndFilter() {
    ListComprehensionExpr comprehension =
        ListComprehensionExpr.of(
            TupleExpr.of(
                List.of(
                    Variable.of("Fun"),
                    LocalCallExpr.of(
                        "make_handler", List.of(Variable.of("Impl"), Variable.of("Fun"))))),
            List.of(
                ListComprehensionGenerator.of(
                    TuplePattern.of(List.of(VariablePattern.of("Fun"), IntegerPattern.of(3))),
                    Variable.of("Callbacks")),
                ListComprehensionFilter.of(
                    RemoteCallExpr.of(
                        "erlang",
                        "function_exported",
                        List.of(Variable.of("Impl"), Variable.of("Fun"), IntegerExpr.of(3))))));

    String expected =
        """
        [
            {Fun, make_handler(Impl, Fun)}
         || {Fun, 3} <- Callbacks,
            erlang:function_exported(Impl, Fun, 3)
        ]""";
    assertEquals(expected, ErlangRenderer.renderExpression(comprehension));
  }

  @Test
  void rendersCatchPattern() {
    assertEquals("_:_", ErlangRenderer.renderPattern(CatchPattern.anyAny()));
    assertEquals("_:Reason", ErlangRenderer.renderPattern(CatchPattern.anyReason("Reason")));
  }

  @Test
  void rendersTryExprWithAnyAnyCatch() {
    TryExpr tryExpr =
        TryExpr.of(
            RemoteCallExpr.of(
                "calendar", "datetime_to_gregorian_seconds", List.of(Variable.of("Dt"))),
            List.of(Clause.of(CatchPattern.anyAny(), AtomExpr.of("undefined"))));

    String expected =
        """
        try
            calendar:datetime_to_gregorian_seconds(Dt)
        catch
            _:_ -> undefined
        end""";
    assertEquals(expected, ErlangRenderer.renderExpression(tryExpr));
  }

  @Test
  void rendersTryExprWithAnyReasonCatch() {
    TryExpr tryExpr =
        TryExpr.of(
            MatchExpr.bind(
                "Xml",
                RemoteCallExpr.of(
                    "xmerl_scan",
                    "string",
                    List.of(LocalCallExpr.of("binary_to_list", List.of(Variable.of("Body"))))),
                AtomExpr.of("ok")),
            List.of(
                Clause.of(
                    CatchPattern.anyReason("Reason"),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("error"),
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("xml_parse_error"), Variable.of("Reason"))))))));

    String expected =
        """
        try
            Xml = xmerl_scan:string(binary_to_list(Body)),
            ok
        catch
            _:Reason -> {error, {xml_parse_error, Reason}}
        end""";
    assertEquals(expected, ErlangRenderer.renderExpression(tryExpr));
  }

  @Test
  void rendersTryExprWithMatchBodyChainAndCase() {
    BinaryPattern isoTimestampPattern =
        BinaryPattern.of(
            List.of(
                BinarySegmentPattern.of(VariablePattern.of("Y"), 4, "binary"),
                BinarySegmentPattern.literal("-"),
                BinarySegmentPattern.of(VariablePattern.of("Mo"), 2, "binary"),
                BinarySegmentPattern.literal("-"),
                BinarySegmentPattern.of(VariablePattern.of("D"), 2, "binary"),
                BinarySegmentPattern.literal("T"),
                BinarySegmentPattern.of(VariablePattern.of("H"), 2, "binary"),
                BinarySegmentPattern.literal(":"),
                BinarySegmentPattern.of(VariablePattern.of("Mi"), 2, "binary"),
                BinarySegmentPattern.literal(":"),
                BinarySegmentPattern.of(VariablePattern.of("S"), 2, "binary"),
                BinarySegmentPattern.of(WildcardPattern.of(), "binary")));
    Expression gregorianSecs =
        RemoteCallExpr.of("calendar", "datetime_to_gregorian_seconds", List.of(Variable.of("Dt")));
    Expression epochSecs =
        InfixExpr.of(Variable.of("GregorianSecs"), "-", IntegerExpr.of(62167219200L));
    Expression mega = InfixExpr.of(Variable.of("EpochSecs"), "div", IntegerExpr.of(1000000));
    Expression result =
        TupleExpr.of(
            List.of(
                Variable.of("Mega"),
                InfixExpr.of(Variable.of("EpochSecs"), "rem", IntegerExpr.of(1000000)),
                IntegerExpr.of(0)));

    TryExpr tryExpr =
        TryExpr.of(
            MatchExpr.of(
                isoTimestampPattern,
                Variable.of("V"),
                MatchExpr.bind(
                    "Dt",
                    TupleExpr.of(
                        List.of(
                            TupleExpr.of(
                                List.of(
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("Y"))),
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("Mo"))),
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("D"))))),
                            TupleExpr.of(
                                List.of(
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("H"))),
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("Mi"))),
                                    LocalCallExpr.of(
                                        "binary_to_integer", List.of(Variable.of("S"))))))),
                    MatchExpr.bind(
                        "GregorianSecs",
                        gregorianSecs,
                        MatchExpr.bind(
                            "EpochSecs", epochSecs, MatchExpr.bind("Mega", mega, result))))),
            List.of(Clause.of(CatchPattern.anyAny(), AtomExpr.of("undefined"))));

    String expected =
        """
        try
            <<Y:4/binary, "-", Mo:2/binary, "-", D:2/binary, "T", H:2/binary, ":", Mi:2/binary, ":",
                S:2/binary, _/binary>> = V,
            Dt = {{binary_to_integer(Y), binary_to_integer(Mo), binary_to_integer(D)}, {
                binary_to_integer(H), binary_to_integer(Mi), binary_to_integer(S)
            }},
            GregorianSecs = calendar:datetime_to_gregorian_seconds(Dt),
            EpochSecs = (GregorianSecs - 62167219200),
            Mega = (EpochSecs div 1000000),
            {Mega, (EpochSecs rem 1000000), 0}
        catch
            _:_ -> undefined
        end""";
    assertEquals(expected, ErlangRenderer.renderExpression(tryExpr));
  }

  @Test
  void rendersModuleTypeAliases() {
    Module module =
        Module.of(
            "sigv4test_service_sigv4",
            List.of(
                Function.of(
                    "sign",
                    List.of(
                        FunctionClause.of(
                            List.of(
                                VariablePattern.of("Config"),
                                VariablePattern.of("Operation"),
                                VariablePattern.of("Request")),
                            AtomExpr.of("ok"))),
                    Spec.of(
                        "sign(client_config(), Operation :: atom(), http_request()) -> http_request()"))),
            null,
            null,
            "runtime_types.hrl",
            List.of(TypeAlias.of("client_config()", "#{binary() => term()}")));

    String expected =
        """
        -module(sigv4test_service_sigv4).
        -include("runtime_types.hrl").
        -export([
            sign/3
        ]).

        -type client_config() :: #{binary() => term()}.

        -spec sign(client_config(), Operation :: atom(), http_request()) -> http_request().
        sign(Config, Operation, Request) -> ok.
        """;

    assertEquals(expected, ErlangRenderer.render(module));
  }

  @Test
  void rendersRemoteCallExprWithDynamicModuleAndFunction() {
    RemoteCallExpr call =
        RemoteCallExpr.of(
            Variable.of("Impl"),
            Variable.of("Fun"),
            List.of(Variable.of("Ctx"), Variable.of("Input"), Variable.of("Meta")));
    assertEquals("Impl:Fun(Ctx, Input, Meta)", ErlangRenderer.renderExpression(call));
  }

  @Test
  void rendersRemoteCallExprWithDynamicModule() {
    RemoteCallExpr call =
        RemoteCallExpr.of(
            Variable.of("HttpClient"),
            AtomExpr.of("request"),
            List.of(
                LocalCallExpr.of(
                    "binary_to_atom",
                    List.of(
                        RemoteCallExpr.of("string", "lowercase", List.of(Variable.of("Method"))),
                        AtomExpr.of("utf8"))),
                Variable.of("Req"),
                ListExpr.of(List.of()),
                ListExpr.of(List.of(AtomExpr.of("with_body")))));
    assertEquals(
        "HttpClient:request(binary_to_atom(string:lowercase(Method), utf8), Req, [], [with_body])",
        ErlangRenderer.renderExpression(call));
  }

  @Test
  void rendersRemoteCallExprWithComplexModuleTarget() {
    RemoteCallExpr call =
        RemoteCallExpr.of(
            InfixExpr.of(Variable.of("A"), "+", Variable.of("B")), AtomExpr.of("run"), List.of());
    assertEquals("(A + B):run()", ErlangRenderer.renderExpression(call));
  }

  @Test
  void rendersNestedRemoteCallWithVerticalLayout() {
    LocalCallExpr call =
        LocalCallExpr.of(
            "iolist_to_binary",
            List.of(
                RemoteCallExpr.of(
                    "io_lib",
                    "format",
                    List.of(
                        StringExpr.of("~4..0B-~2..0B-~2..0BT~2..0B:~2..0B:~2..0BZ"),
                        ListExpr.of(
                            List.of(
                                Variable.of("Y"),
                                Variable.of("Mo"),
                                Variable.of("D"),
                                Variable.of("H"),
                                Variable.of("Mi"),
                                Variable.of("S")))))));
    String expected =
        """
        iolist_to_binary(
            io_lib:format("~4..0B-~2..0B-~2..0BT~2..0B:~2..0B:~2..0BZ", [
                Y,
                Mo,
                D,
                H,
                Mi,
                S
            ])
        )""";
    assertEquals(expected, ErlangRenderer.renderExpression(call));
  }

  @Test
  void rendersMapsMergeWithVerticalLayout() {
    Expression optionalRegion =
        LocalCallExpr.of(
            "optional_param",
            List.of(Variable.of("Config"), AtomExpr.of("region"), BinaryExpr.of("Region")));
    Expression optionalBucket =
        LocalCallExpr.of(
            "optional_param",
            List.of(Variable.of("Config"), AtomExpr.of("bucket"), BinaryExpr.of("Bucket")));
    RemoteCallExpr call =
        RemoteCallExpr.of("maps", "merge", List.of(optionalRegion, optionalBucket));
    String expected =
        """
        maps:merge(
            optional_param(Config, region, <<\"Region\">>),
            optional_param(Config, bucket, <<\"Bucket\">>)
        )""";
    assertEquals(expected, ErlangRenderer.renderExpression(call));
  }

  @Test
  void rendersBlockExprWithNewlines() {
    Expression block =
        BlockExpr.newlineSeparated(
            List.of(
                MatchExpr.bindValue("Headers", ListExpr.of(List.of())),
                MatchExpr.bindValue("Body", AtomExpr.of("ok")),
                RecordExpr.of(
                    "http_response",
                    List.of(
                        RecordField.of("status", IntegerExpr.of(200)),
                        RecordField.of("body", Variable.of("Body"))))));
    String expected =
        """
        Headers = []
        Body = ok
        #http_response{
            status = 200,
            body = Body
        }""";
    assertEquals(expected, ErlangRenderer.renderExpression(block));
  }

  @Test
  void rendersMapEntriesExpr() {
    MapEntriesExpr entries =
        MapEntriesExpr.of(
            List.of(
                MapEntry.of(BinaryExpr.of("name"), Variable.of("Name")),
                MapEntry.of(BinaryExpr.of("count"), Variable.of("Count"))));
    assertEquals(
        """
        <<\"name\">> => Name,
        <<\"count\">> => Count""",
        ErlangRenderer.renderExpression(entries));
  }

  @Test
  void rendersStatementAddsTrailingPeriod() {
    Expression expression =
        MatchExpr.bind(
            "Req",
            RemoteCallExpr.of("mod", "encode", List.of(Variable.of("Input"))),
            AtomExpr.of("done"));
    assertEquals("Req = mod:encode(Input),\ndone.", ErlangRenderer.renderStatement(expression));
  }

  @Test
  void rendersEqualGuard() {
    Function function =
        Function.of(
            "query_suffix",
            List.of(
                FunctionClause.of(
                    List.of(VariablePattern.of("Query")),
                    EqualGuard.of(
                        LocalCallExpr.of("map_size", List.of(Variable.of("Query"))),
                        IntegerExpr.of(0)),
                    BinaryExpr.of("")),
                FunctionClause.of(
                    List.of(VariablePattern.of("Query")), null, AtomExpr.of("other"))));
    String expected =
        """
        query_suffix(Query) when map_size(Query) =:= 0 -> <<>>;
        query_suffix(Query) -> other.
        """;
    assertEquals(expected, ErlangRenderer.renderFunction(function));
  }

  @Test
  void rendersAndGuard() {
    Function function =
        Function.of(
            "resolve_from_env",
            List.of(
                FunctionClause.of(
                    List.of(
                        TuplePattern.of(
                            List.of(VariablePattern.of("Id"), VariablePattern.of("Secret")))),
                    AndGuard.of(
                        NotEqualGuard.of(Variable.of("Id"), AtomExpr.of("false")),
                        NotEqualGuard.of(Variable.of("Secret"), AtomExpr.of("false"))),
                    AtomExpr.of("ok"))));
    String expected =
        """
        resolve_from_env({Id, Secret}) when Id =/= false, Secret =/= false -> ok.
        """;
    assertEquals(expected, ErlangRenderer.renderFunction(function));
  }

  @Test
  void rendersExpressionGuard() {
    Function function =
        Function.of(
            "retry_clause",
            List.of(
                FunctionClause.of(
                    List.of(AtomPattern.of("true")),
                    ExpressionGuard.of(
                        InfixExpr.of(Variable.of("Attempts"), ">", IntegerExpr.of(1))),
                    AtomExpr.of("backoff"))));
    String expected = """
        retry_clause(true) when Attempts > 1 -> backoff.
        """;
    assertEquals(expected, ErlangRenderer.renderFunction(function));
  }

  @Test
  void rendersSingleLineCallback() {
    Callback callback =
        Callback.of(
            "handle_basic",
            "term(), basic_input(), term()",
            "{ok, basic_output()} | {error, term()}");
    assertEquals(
        "-callback handle_basic(term(), basic_input(), term()) -> {ok, basic_output()} | {error, term()}.\n",
        new DefaultErlangRenderer().renderCallbackForTest(callback));
  }

  @Test
  void rendersWrappedCallback() {
    String longInput =
        "a() | b() | c() | d() | e() | f() | g() | h() | i() | j() | k() | l() | m() | n()";
    Callback callback = Callback.of("very_long_callback_name", longInput, "ok()");
    assertEquals(
        "-callback very_long_callback_name(" + longInput + ") ->\n    ok().\n",
        new DefaultErlangRenderer().renderCallbackForTest(callback));
  }

  @Test
  void rendersCallbackWithEdoc() {
    Callback callback =
        Callback.of(
            "handle_get_type_closure",
            "Ctx :: term(), Input :: get_type_closure_input(), Meta :: term()",
            "{ok, get_type_closure_output()} | {error, term()}",
            Edoc.of("Handle GetTypeClosure operation."));
    String expected =
        """
        %% @doc Handle GetTypeClosure operation.
        -callback handle_get_type_closure(Ctx :: term(), Input :: get_type_closure_input(), Meta :: term()) ->
            {ok, get_type_closure_output()} | {error, term()}.
        """;
    assertEquals(expected, new DefaultErlangRenderer().renderCallbackForTest(callback));
  }

  @Test
  void rendersEmptyRecord() {
    Header header =
        Header.of(List.of(), List.of(RecordDef.of("health_check_input", List.of())), null);
    assertEquals("-record(health_check_input, {}).\n", ErlangRenderer.render(header));
  }

  @Test
  void rendersRecordWithDefaultAndFieldComments() {
    Header header =
        Header.of(
            List.of(),
            List.of(
                RecordDef.of(
                    "service_unavailable",
                    List.of(
                        TypedField.of("message", "binary() | undefined"),
                        TypedField.of(
                            "'__beam_error_kind'",
                            "client | server",
                            "server",
                            List.of("fault: server | retryable: true | throttling: false"))))),
            null);
    String expected =
        """
        -record(service_unavailable, {
            message :: binary() | undefined,
            %% fault: server | retryable: true | throttling: false
            '__beam_error_kind' = server :: client | server
        }).
        """;
    assertEquals(expected, ErlangRenderer.render(header));
  }

  @Test
  void rendersTypeAliasWithPreamble() {
    TypeAlias alias = TypeAlias.of("pa_blob", "binary()", List.of("@doc A blob payload."));
    assertEquals(
        "%% @doc A blob payload.\n-type pa_blob() :: binary().\n",
        new DefaultErlangRenderer().renderTypeAliasForTest(alias));
  }

  @Test
  void rendersUnionTypeMultiline() {
    TypeAlias alias =
        TypeAlias.union(
            "basic_union",
            List.of(
                "{text, basic_string()}",
                "{number, basic_integer()}",
                "{flag, basic_boolean()}",
                "{unknown, binary()}"));
    String expected =
        """
        -type basic_union() ::
            {text, basic_string()}
            | {number, basic_integer()}
            | {flag, basic_boolean()}
            | {unknown, binary()}.
        """;
    assertEquals(expected, new DefaultErlangRenderer().renderTypeAliasForTest(alias));
  }

  @Test
  void rendersTypeAliasAppendsAritySuffix() {
    TypeAlias alias = TypeAlias.of("basic_item", "#basic_item{}");
    assertEquals(
        "-type basic_item() :: #basic_item{}.\n",
        new DefaultErlangRenderer().renderTypeAliasForTest(alias));
  }

  @Test
  void rendersBehaviourModuleWithCallbacks() {
    Module module =
        Module.behaviour(
            "basic_service_behaviour",
            List.of("Generated Erlang server behaviour for smithy.beam.demo.basic#BasicService."),
            "basic_service_types.hrl",
            List.of(
                Callback.of(
                    "handle_get_type_closure",
                    "Ctx :: term(), Input :: get_type_closure_input(), Meta :: term()",
                    "{ok, get_type_closure_output()} | {error, term()}",
                    Edoc.of("Handle GetTypeClosure operation."))));

    String expected =
        """
        %% Generated Erlang server behaviour for smithy.beam.demo.basic#BasicService.
        -module(basic_service_behaviour).
        -include("basic_service_types.hrl").
        %% @doc Handle GetTypeClosure operation.
        -callback handle_get_type_closure(Ctx :: term(), Input :: get_type_closure_input(), Meta :: term()) ->
            {ok, get_type_closure_output()} | {error, term()}.
        """;
    assertEquals(expected, ErlangRenderer.render(module));
  }

  @Test
  void rendersServerModuleAttributesAndEpilogue() {
    Module module =
        Module.server(
            "basic_service_server",
            List.of("Generated Erlang server dispatcher."),
            "basic_service_behaviour",
            List.of("handle_get_type_closure/3", "init_handlers/0"),
            "basic_service_types.hrl",
            "basic_service_impl",
            "{basic_service_server, handlers}",
            List.of(
                Function.of(
                    "init_handlers", List.of(FunctionClause.of(List.of(), AtomExpr.of("ok"))))),
            List.of(
                "Call basic_service_server:init_handlers/0 during application start before dispatch.",
                "Default impl module: basic_service_impl."));

    String rendered = ErlangRenderer.render(module);
    assertTrue(rendered.contains("-behaviour(basic_service_behaviour)."));
    assertTrue(rendered.contains("-define(DEFAULT_IMPL, basic_service_impl)."));
    assertTrue(rendered.contains("-define(HANDLERS_KEY, {basic_service_server, handlers})."));
    assertTrue(rendered.contains("Call basic_service_server:init_handlers/0"));
    assertFalse(rendered.contains("-export([])"));
  }

  @Test
  void rendersRemoteCallWithVerticalLayout() {
    Function function =
        Function.of(
            "generate_uuid",
            List.of(
                FunctionClause.of(
                    List.of(),
                    LocalCallExpr.of(
                        "list_to_binary",
                        List.of(
                            RemoteCallExpr.of(
                                "uuid",
                                "to_string",
                                List.of(RemoteCallExpr.of("uuid", "v4", List.of()))))))));

    String expected =
        """
        generate_uuid() -> list_to_binary(uuid:to_string(uuid:v4())).
        """;
    assertEquals(expected, ErlangRenderer.renderFunction(function));
  }
}
