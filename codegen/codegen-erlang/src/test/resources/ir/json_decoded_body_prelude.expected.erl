Decoded =
    case Body of
        <<>> ->
            #{};
        _ ->
            case jsone:try_decode(Body) of
                {ok, Val, _} -> Val;
                {error, _} -> #{}
            end
    end
