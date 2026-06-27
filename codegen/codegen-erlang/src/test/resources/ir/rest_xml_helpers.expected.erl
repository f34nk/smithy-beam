parse_xml_root(Body, RootName) ->
    try
        {Xml, _} = xmerl_scan:string(binary_to_list(Body)),
        case xml_element_named(Xml, RootName) of
            true ->
                {ok, Xml};
            false ->
                case find_element(RootName, element_content(Xml)) of
                    undefined -> {error, {missing_root, RootName}};
                    Root -> {ok, Root}
                end
        end
    catch
        _:Reason -> {error, {xml_parse_error, Reason}}
    end.

xml_element_named(Element, Name) -> is_element(Element) andalso element_name(Element) =:= Name.

element_content({xmlElement, _, _, _, _, _, _, _, Content, _, _, _}) -> Content;
element_content({_, _, Content, _, _, _}) when is_list(Content) -> Content;
element_content([H | _]) -> element_content(H);
element_content(_) -> [].

find_element(Name, Content) ->
    case
        [
            C
         || C <- Content,
            is_element(C),
            element_name(C) =:= Name
        ]
    of
        [Element | _] -> Element;
        [] -> undefined
    end.

is_element({xmlElement, _, _, _, _, _, _, _, _, _, _, _}) -> true;
is_element({_, _, Content, _, _, _}) when is_list(Content) -> true;
is_element(_) -> false.

element_name({xmlElement, Name, _, _, _, _, _, _, _, _, _, _}) when is_atom(Name) ->
    list_to_binary(atom_to_list(Name));
element_name({xmlElement, Name, _, _, _, _, _, _, _, _, _, _}) when is_list(Name) ->
    list_to_binary(Name);
element_name({xmlElement, Name, _, _, _, _, _, _, _, _, _, _}) when is_binary(Name) -> Name;
element_name({Name, _, _, _, _, _}) when is_atom(Name) -> list_to_binary(atom_to_list(Name));
element_name({Name, _, _, _, _, _}) when is_list(Name) -> list_to_binary(Name);
element_name({Name, _, _, _, _, _}) when is_binary(Name) -> Name.

xml_child_text(Parent, Name) ->
    case find_element(Name, element_content(Parent)) of
        undefined ->
            undefined;
        Element ->
            case element_text(Element) of
                [] -> undefined;
                Text -> list_to_binary(Text)
            end
    end.

element_text({xmlElement, _, _, _, _, _, _, _, Content, _, _, _}) ->
    xml_text_values(Content);
element_text({_, _, Content, _, _, _}) when is_list(Content) ->
    [
        T
     || T <- Content,
        is_list(T),
        not is_element_string(T)
    ];
element_text(_) ->
    [].

xml_text_values(Content) ->
    lists:flatten([
        case C of
            {xmlText, _, _, _, V, _} when is_list(V) -> V;
            {xmlText, _, _, _, V, _} when is_binary(V) -> binary_to_list(V);
            _ -> []
        end
     || C <- Content
    ]).

is_element_string(T) when is_list(T) ->
    case T of
        {xmlElement, _, _, _, _, _, _, _, _, _, _, _} -> true;
        {_, _, _, _, _, _} -> true;
        _ -> false
    end;
is_element_string(_) ->
    false.

xml_attribute(Element, AttrName) ->
    case proplists:get_value(AttrName, element(Element, 2), undefined) of
        undefined -> undefined;
        Value -> list_to_binary(Value)
    end.

xml_child_list(Parent, undefined, ItemName) ->
    [
        ItemText
     || Item <- element_content(Parent),
        is_element(Item),
        element_name(Item) =:= ItemName,
        ItemText <- [list_to_binary(element_text(Item))],
        ItemText =/= <<>>
    ];
xml_child_list(Parent, ListName, ItemName) ->
    case find_element(ListName, element_content(Parent)) of
        undefined ->
            undefined;
        ListElement ->
            [
                ItemText
             || Item <- element_content(ListElement),
                is_element(Item),
                element_name(Item) =:= ItemName,
                ItemText <- [list_to_binary(element_text(Item))],
                ItemText =/= <<>>
            ]
    end.

xml_child_struct_list(Parent, undefined, ItemName, DecodeFun) ->
    [
        DecodeFun(Item)
     || Item <- element_content(Parent),
        is_element(Item),
        element_name(Item) =:= ItemName
    ];
xml_child_struct_list(Parent, ListName, ItemName, DecodeFun) ->
    case find_element(ListName, element_content(Parent)) of
        undefined ->
            undefined;
        ListElement ->
            [
                DecodeFun(Item)
             || Item <- element_content(ListElement),
                is_element(Item),
                element_name(Item) =:= ItemName
            ]
    end.

decode_rest_xml_error(Status, Body) ->
    case parse_xml_root(Body, <<"ErrorResponse">>) of
        {ok, ErrorResponse} ->
            case find_element(<<"Error">>, element_content(ErrorResponse)) of
                undefined ->
                    {error, {unknown_error, Status, Body}};
                Error ->
                    {error, {
                        xml_child_text(Error, <<"Code">>), xml_child_text(Error, <<"Message">>)
                    }}
            end;
        {error, _} ->
            {error, {unknown_error, Status, Body}}
    end.

encode_xml(RootMap, XmlNs) ->
    [{RootName, Content}] = maps:to_list(RootMap),
    Element = build_xml_element(RootName, Content, XmlNs),
    iolist_to_binary(xmerl:export_simple([Element], xmerl_xmlns, [], [{prolog, false}])).

build_xml_element(Name, Content, XmlNs) when is_map(Content) ->
    Attrs = xml_namespace_attrs(XmlNs),
    Children = [build_xml_child(K, V) || {K, V} <- maps:to_list(Content), V =/= undefined],
    {Name, Attrs, Children};
build_xml_element(Name, Content, XmlNs) ->
    {Name, xml_namespace_attrs(XmlNs), [{text, to_binary(Content)}]}.

build_xml_child(Name, Value) when is_map(Value) ->
    {Name, [], [build_xml_element(K, V, #{}) || {K, V} <- maps:to_list(Value), V =/= undefined]};
build_xml_child(Name, Values) when is_list(Values) ->
    {Name, [], [build_xml_element(<<"member">>, V, #{}) || V <- Values, V =/= undefined]};
build_xml_child(Name, Value) ->
    {Name, [], [{text, to_binary(Value)}]}.

xml_namespace_attrs(#{uri := Uri}) ->
    [{xmlns, Uri}];
xml_namespace_attrs(#{uri := Uri, prefix := Prefix}) ->
    [{'xmlns:' ++ binary_to_list(Prefix), Uri}];
xml_namespace_attrs(_) ->
    [].

encode_query_value(V) when is_integer(V) -> integer_to_binary(V);
encode_query_value(V) when is_float(V) -> float_to_binary(V, [short]);
encode_query_value(V) when is_boolean(V) -> atom_to_binary(V, utf8);
encode_query_value(V) -> to_binary(V).

to_binary(V) when is_binary(V) -> V;
to_binary(V) when is_list(V) -> list_to_binary(V);
to_binary(V) when is_atom(V) -> atom_to_binary(V, utf8);
to_binary(V) when is_integer(V) -> integer_to_binary(V);
to_binary(V) when is_float(V) -> float_to_binary(V, [short]);
to_binary(V) when is_boolean(V) -> atom_to_binary(V, utf8).
