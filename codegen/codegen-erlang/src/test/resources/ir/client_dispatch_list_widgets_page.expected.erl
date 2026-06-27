Req = paginated_service_rest_json_1:encode_list_widgets_request(Input),
case runtime_http:dispatch(Config, Req) of
    {ok, Resp} ->
        case paginated_service_rest_json_1:decode_list_widgets_response(Resp) of
            {ok, Output} ->
                NewAcc = Acc ++ Output#list_widgets_output.widgets,
                case Output#list_widgets_output.next_token of
                    undefined ->
                        {ok, NewAcc};
                    NextToken ->
                        NextInput = Input#list_widgets_input{ next_token = NextToken },
                        list_widgets(Config, NextInput, NewAcc)
                end;
            {error, Reason} ->
                {error, Reason}
        end;
    {error, Reason} ->
        {error, Reason}
end.
