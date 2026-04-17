-module(aws_demo_app).
-export([run/0]).

-define(USER_NAME, <<"iam-demo-user">>).
-define(GROUP_NAME, <<"iam-demo-group">>).
-define(NEW_USER_NAME, <<"iam-demo-new-user">>).

run() ->
    io:format("~n=== Running IAM Client Application ===~n~n"),

    io:format("Creating IAM client...~n"),
    Config = #{
        endpoint => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region => <<"us-east-1">>,
        service => <<"iam">>,
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    {ok, Client} = aws_iam_client:new(Config),
    io:format("Client created successfully~n~n"),

    %% 1. List users to see what was created by Terraform
    io:format("--- ListUsers ---~n"),
    CountBefore = case aws_iam_client:list_users(Client, #{}, #{enable_retry => false}) of
        {ok, ListUsersOutput} ->
            Users = normalize_list(maps:get(<<"Users">>, ListUsersOutput, [])),
            case Users of
                [] -> erlang:error({assertion_failed, empty_user_list});
                _  -> io:format("SUCCESS: Found ~p user(s)~n", [length(Users)])
            end,
            lists:foreach(
                fun(User) ->
                    UserName = maps:get(<<"UserName">>, User, <<"unknown">>),
                    Path = maps:get(<<"Path">>, User, <<"/">>),
                    io:format("  - ~s (path: ~s)~n", [UserName, Path])
                end,
                Users
            ),
            length(Users);
        {error, ListUsersError} ->
            erlang:error({list_users_failed, ListUsersError})
    end,
    io:format("~n"),

    %% 2. Get the user created by Terraform
    io:format("--- GetUser ---~n"),
    GetUserInput = #{<<"UserName">> => ?USER_NAME},
    case aws_iam_client:get_user(Client, GetUserInput, #{enable_retry => false}) of
        {ok, GetUserOutput} ->
            User = maps:get(<<"User">>, GetUserOutput, #{}),
            ReturnedUserName = maps:get(<<"UserName">>, User, <<>>),
            case ReturnedUserName =:= ?USER_NAME of
                true  -> io:format("SUCCESS: GetUser UserName matches '~s'~n", [?USER_NAME]);
                false -> erlang:error({assertion_failed, {expected_username, ?USER_NAME}, {got, ReturnedUserName}})
            end,
            UserArn = maps:get(<<"Arn">>, User, <<"unknown">>),
            CreateDate = maps:get(<<"CreateDate">>, User, <<"unknown">>),
            io:format("  ARN: ~s~n", [UserArn]),
            io:format("  Created: ~s~n", [CreateDate]);
        {error, GetUserError} ->
            erlang:error({get_user_failed, GetUserError})
    end,
    io:format("~n"),

    %% 3. List groups
    io:format("--- ListGroups ---~n"),
    case aws_iam_client:list_groups(Client, #{}, #{enable_retry => false}) of
        {ok, ListGroupsOutput} ->
            Groups = normalize_list(maps:get(<<"Groups">>, ListGroupsOutput, [])),
            case Groups of
                [] -> erlang:error({assertion_failed, empty_group_list});
                _  -> io:format("SUCCESS: Found ~p group(s)~n", [length(Groups)])
            end,
            lists:foreach(
                fun(Group) ->
                    GroupName = maps:get(<<"GroupName">>, Group, <<"unknown">>),
                    Path = maps:get(<<"Path">>, Group, <<"/">>),
                    io:format("  - ~s (path: ~s)~n", [GroupName, Path])
                end,
                Groups
            );
        {error, ListGroupsError} ->
            erlang:error({list_groups_failed, ListGroupsError})
    end,
    io:format("~n"),

    %% 4. List groups for user
    io:format("--- ListGroupsForUser ---~n"),
    ListGroupsForUserInput = #{<<"UserName">> => ?USER_NAME},
    case aws_iam_client:list_groups_for_user(Client, ListGroupsForUserInput, #{enable_retry => false}) of
        {ok, ListGroupsForUserOutput} ->
            UserGroups = normalize_list(maps:get(<<"Groups">>, ListGroupsForUserOutput, [])),
            io:format("SUCCESS: User '~s' is in ~p group(s)~n", [?USER_NAME, length(UserGroups)]),
            lists:foreach(
                fun(Group) ->
                    GroupName = maps:get(<<"GroupName">>, Group, <<"unknown">>),
                    io:format("  - ~s~n", [GroupName])
                end,
                UserGroups
            );
        {error, ListGroupsForUserError} ->
            erlang:error({list_groups_for_user_failed, ListGroupsForUserError})
    end,
    io:format("~n"),

    %% 5. Create a new user
    io:format("--- CreateUser ---~n"),
    CreateUserInput = #{
        <<"UserName">> => ?NEW_USER_NAME,
        <<"Path">> => <<"/demo/">>,
        <<"Tags">> => [
            #{<<"Key">> => <<"CreatedBy">>, <<"Value">> => <<"smithy-erlang">>}
        ]
    },
    case aws_iam_client:create_user(Client, CreateUserInput, #{enable_retry => false}) of
        {ok, CreateUserOutput} ->
            NewUser = maps:get(<<"User">>, CreateUserOutput, #{}),
            CreatedName = maps:get(<<"UserName">>, NewUser, <<>>),
            case CreatedName =:= ?NEW_USER_NAME of
                true  -> io:format("SUCCESS: Created user '~s'~n", [?NEW_USER_NAME]);
                false -> erlang:error({assertion_failed, {expected_username, ?NEW_USER_NAME}, {got, CreatedName}})
            end,
            NewUserArn = maps:get(<<"Arn">>, NewUser, <<"unknown">>),
            io:format("  ARN: ~s~n", [NewUserArn]);
        {error, CreateUserError} ->
            erlang:error({create_user_failed, CreateUserError})
    end,
    io:format("~n"),

    %% 6. List users again to verify user count grew and new user is present
    io:format("--- ListUsers (verify) ---~n"),
    case aws_iam_client:list_users(Client, #{}, #{enable_retry => false}) of
        {ok, ListUsersOutput2} ->
            Users2 = normalize_list(maps:get(<<"Users">>, ListUsersOutput2, [])),
            CountAfter = length(Users2),
            io:format("SUCCESS: Found ~p user(s)~n", [CountAfter]),
            case CountAfter > CountBefore of
                true  -> io:format("SUCCESS: User count grew from ~p to ~p~n", [CountBefore, CountAfter]);
                false -> erlang:error({assertion_failed, {user_count_unchanged, CountBefore}})
            end,
            case lists:any(fun(U) -> maps:get(<<"UserName">>, U, <<>>) =:= ?NEW_USER_NAME end, Users2) of
                true  -> io:format("SUCCESS: '~s' found in user list~n", [?NEW_USER_NAME]);
                false -> erlang:error({assertion_failed, {user_not_in_list, ?NEW_USER_NAME}})
            end,
            lists:foreach(
                fun(User) ->
                    UserName = maps:get(<<"UserName">>, User, <<"unknown">>),
                    io:format("  - ~s~n", [UserName])
                end,
                Users2
            );
        {error, ListUsersError2} ->
            erlang:error({list_users_failed, ListUsersError2})
    end,
    io:format("~n"),

    %% 7. Add user to group
    io:format("--- AddUserToGroup ---~n"),
    AddUserToGroupInput = #{
        <<"UserName">> => ?NEW_USER_NAME,
        <<"GroupName">> => ?GROUP_NAME
    },
    case aws_iam_client:add_user_to_group(Client, AddUserToGroupInput, #{enable_retry => false}) of
        {ok, _} ->
            io:format("SUCCESS: Added '~s' to group '~s'~n", [?NEW_USER_NAME, ?GROUP_NAME]);
        {error, AddUserError} ->
            erlang:error({add_user_to_group_failed, AddUserError})
    end,
    io:format("~n"),

    %% 8. List groups for new user
    io:format("--- ListGroupsForUser (new user) ---~n"),
    ListGroupsForNewUserInput = #{<<"UserName">> => ?NEW_USER_NAME},
    case aws_iam_client:list_groups_for_user(Client, ListGroupsForNewUserInput, #{enable_retry => false}) of
        {ok, ListGroupsForNewUserOutput} ->
            NewUserGroups = normalize_list(maps:get(<<"Groups">>, ListGroupsForNewUserOutput, [])),
            case NewUserGroups of
                [] -> erlang:error({assertion_failed, {new_user_not_in_any_group, ?NEW_USER_NAME}});
                _  -> io:format("SUCCESS: User '~s' is in ~p group(s)~n", [?NEW_USER_NAME, length(NewUserGroups)])
            end,
            GroupNames = [maps:get(<<"GroupName">>, G, <<>>) || G <- NewUserGroups],
            case lists:member(?GROUP_NAME, GroupNames) of
                true  -> io:format("SUCCESS: Group '~s' found~n", [?GROUP_NAME]);
                false -> erlang:error({assertion_failed, {group_not_found, ?GROUP_NAME}, {in, GroupNames}})
            end,
            lists:foreach(
                fun(Group) ->
                    GroupName = maps:get(<<"GroupName">>, Group, <<"unknown">>),
                    io:format("  - ~s~n", [GroupName])
                end,
                NewUserGroups
            );
        {error, ListGroupsForNewUserError} ->
            erlang:error({list_groups_for_user_failed, ListGroupsForNewUserError})
    end,
    io:format("~n"),

    %% 9. Remove user from group
    io:format("--- RemoveUserFromGroup ---~n"),
    RemoveUserFromGroupInput = #{
        <<"UserName">> => ?NEW_USER_NAME,
        <<"GroupName">> => ?GROUP_NAME
    },
    case aws_iam_client:remove_user_from_group(Client, RemoveUserFromGroupInput, #{enable_retry => false}) of
        {ok, _} ->
            io:format("SUCCESS: Removed '~s' from group '~s'~n", [?NEW_USER_NAME, ?GROUP_NAME]);
        {error, RemoveUserError} ->
            erlang:error({remove_user_from_group_failed, RemoveUserError})
    end,
    io:format("~n"),

    %% 10. Delete the new user
    io:format("--- DeleteUser ---~n"),
    DeleteUserInput = #{<<"UserName">> => ?NEW_USER_NAME},
    case aws_iam_client:delete_user(Client, DeleteUserInput, #{enable_retry => false}) of
        {ok, _} ->
            io:format("SUCCESS: Deleted user '~s'~n", [?NEW_USER_NAME]);
        {error, DeleteUserError} ->
            erlang:error({delete_user_failed, DeleteUserError})
    end,
    io:format("~n"),

    %% 11. Verify deletion
    io:format("--- GetUser (verify deletion) ---~n"),
    GetDeletedUserInput = #{<<"UserName">> => ?NEW_USER_NAME},
    case aws_iam_client:get_user(Client, GetDeletedUserInput, #{enable_retry => false}) of
        {ok, _} ->
            erlang:error({assertion_failed, user_not_deleted, ?NEW_USER_NAME});
        {error, #{error_type := no_such_entity_exception}} ->
            io:format("SUCCESS: User '~s' confirmed deleted~n", [?NEW_USER_NAME]);
        %% IAM wire code is "NoSuchEntity" (no "Exception" suffix) so the generated
        %% string-dispatch clause for "NoSuchEntityException" doesn't match; the
        %% catch-all returns #{error_type => unknown, body => #{<<"Code">> => <<"NoSuchEntity">>}}.
        {error, #{error_type := unknown, body := #{<<"Code">> := <<"NoSuchEntity">>}}} ->
            io:format("SUCCESS: User '~s' confirmed deleted~n", [?NEW_USER_NAME]);
        {error, VerifyError} ->
            erlang:error({get_user_failed, VerifyError})
    end,
    io:format("~n"),

    io:format("=== IAM Client Application Complete ===~n"),
    ok.

%% Helper to normalize AWS Query protocol list responses
%% Single items come as #{<<"member">> => Item}, multiple as [Item1, Item2, ...]
normalize_list(#{<<"member">> := Item}) when is_map(Item) -> [Item];
normalize_list(#{<<"member">> := Items}) when is_list(Items) -> Items;
normalize_list(List) when is_list(List) -> List;
normalize_list(_) -> [].
