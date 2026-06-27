-spec parse_labels(binary(), binary()) -> {ok, map()} | {error, path_mismatch}.
parse_labels(Path, Template) ->
    case match_segments(segments(Path), segments(Template), #{}) of
        {ok, Labels} -> {ok, Labels};
        error -> {error, path_mismatch}
    end.

segments(Path) ->
    Parts = binary:split(Path, <<"/">>, [global]),
    [S || S <- Parts, S =/= <<>>].

match_segments([], [], Acc) ->
    {ok, Acc};
match_segments([Seg | RestPath], [TplSeg | RestTpl], Acc) ->
    case label_name(TplSeg) of
        {ok, Key} ->
            Val = uri_string:unquote(Seg),
            match_segments(RestPath, RestTpl, Acc#{Key => Val});
        error ->
            case Seg =:= TplSeg of
                true -> match_segments(RestPath, RestTpl, Acc);
                false -> error
            end
    end;
match_segments(_, _, _) ->
    error.

label_name(<<"{", Rest/binary>>) ->
    case binary:split(Rest, <<"}">>) of
        [Label | [<<>>]] -> {ok, Label};
        _ -> error
    end;
label_name(_) ->
    error.
