parse_list_users_input_input(Params) ->
    #list_users_input{
        path_prefix = form_value(Params, <<"PathPrefix">>)
    }.
