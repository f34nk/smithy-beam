flatten_query_input(#delete_user_input{user_name = UserName}) ->
    lists:append([flatten_member(<<"UserName">>, UserName)]);
flatten_query_input(#list_users_input{path_prefix = PathPrefix}) ->
    lists:append([flatten_member(<<"PathPrefix">>, PathPrefix)]).
