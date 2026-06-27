ct_base(CT) ->
    case binary:split(CT, <<";">>) of
        [Base | _] -> Base;
        _ -> CT
    end.
