unwrap_query_result(Body, ResultName) ->
    try
        {Xml, _} = xmerl_scan:string(binary_to_list(Body)),
        Root = normalize_xml_element(Xml),
        case query_result_element(Root, ResultName) of
            undefined -> {error, {missing_result, ResultName}};
            Result -> {ok, Result}
        end
    catch
        _:Reason -> {error, {xml_parse_error, Reason}}
    end.

normalize_xml_element([H | _]) ->
    normalize_xml_element(H);
normalize_xml_element(Element) ->
    Element.

query_result_element(Element, ResultName) ->
    case is_element(Element) andalso element_name(Element) =:= ResultName of
        true -> Element;
        false -> find_element(ResultName, element_content(Element))
    end.

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

decode_query_error(Status, Body) ->
    try
        {Xml, _} = xmerl_scan:string(binary_to_list(Body)),
        Root = normalize_xml_element(Xml),
        case query_result_element(Root, <<"ErrorResponse">>) of
            undefined ->
                {error, {unknown_error, Status, Body}};
            ErrorResponse ->
                case find_element(<<"Error">>, element_content(ErrorResponse)) of
                    undefined ->
                        {error, {unknown_error, Status, Body}};
                    Error ->
                        {error, {
                            xml_child_text(Error, <<"Code">>), xml_child_text(Error, <<"Message">>)
                        }}
                end
        end
    catch
        _:_ -> {error, {unknown_error, Status, Body}}
    end.
