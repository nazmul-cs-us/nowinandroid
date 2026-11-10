%% Durable, authenticated device registry and bounded call-invitation outbox.
-module(mod_deenly_family_push).
-export([register/4, unregister/3, enqueue/2, drain/0]).
-define(T, deenly_family).

register(D, U, F, R) ->
    Device = maps:get(<<"deviceId">>, R), Token = maps:get(<<"token">>, R),
    Platform = maps:get(<<"platform">>, R), Env = maps:get(<<"environment">>, R, <<"production">>),
    valid(is_binary(Device) andalso byte_size(Device) >= 16 andalso byte_size(Device) =< 80),
    valid(is_binary(Token) andalso byte_size(Token) >= 32 andalso byte_size(Token) =< 4096),
    valid(lists:member(Platform, [<<"android">>, <<"ios">>])),
    valid(lists:member(Env, [<<"production">>, <<"sandbox">>])),
    Existing = rows(),
    %% A token belongs to one signed-in account; account changes revoke old ownership.
    lists:foreach(fun
        ({K = {device, _, _, _}, #{token := T}}) when T =:= Token -> mnesia:delete({?T, K});
        (_) -> ok
    end, Existing),
    Count = length([ok || {{device, Domain, User, Id}, _} <- rows(), Domain =:= D, User =:= U, Id =/= Device]),
    valid(Count < 8),
    write({device, D, U, Device}, #{token => Token, platform => Platform, environment => Env,
          resource => F, updated => now_ms()}),
    {#{}, []}.
unregister(D, U, Device) ->
    mnesia:delete({?T, {device, D, U, Device}}),
    mod_deenly_family_web:revoke(D, U, Device), {#{}, []}.

enqueue(D, C) ->
    Id = maps:get(<<"id">>, C), Callee = maps:get(<<"callee">>, C), Now = now_ms(),
    lists:foreach(fun
        ({{device, Domain, User, Device}, #{updated := Updated} = Registration})
          when Domain =:= D, User =:= Callee, Updated > Now - 2592000000 ->
            K = {push, D, Id, Device},
            case read(K) of
                undefined -> write(K, #{user => Callee, registration => Registration,
                    expires => maps:get(<<"expires">>, C), next => Now, attempts => 0,
                    payload => #{<<"v">> => 1, <<"callId">> => Id,
                        <<"caller">> => maps:get(<<"caller">>, C), <<"callee">> => Callee,
                        <<"kind">> => maps:get(<<"kind">>, C), <<"expires">> => maps:get(<<"expires">>, C)}});
                _ -> ok
            end;
        (_) -> ok
    end, rows()),
    mod_deenly_family_web:enqueue(D, C).

drain() ->
    %% Claim jobs in a transaction, but never perform network IO under Mnesia locks.
    case mnesia:transaction(fun() ->
        Now = now_ms(),
        lists:filtermap(fun
            ({K = {push, D, Id, Device}, #{expires := Exp, next := Next, user := U} = Job}) ->
                mnesia:read(?T, {lock, D}, write),
                Current = read({call, D, Id}), Registered = read({device, D, U, Device}),
                Registration = maps:get(registration, Job),
                IsCurrent = case maps:get(platform, Registration) of
                    <<"web">> -> mod_deenly_family_web:current(D, U, Registration);
                    _ -> Registered =:= Registration
                end,
                Allowed = is_map(Current) andalso maps:get(<<"stage">>, Current) =:= <<"ringing">>
                    andalso IsCurrent,
                case Exp > Now andalso Allowed of
                    false -> mnesia:delete({?T, K}), false;
                    true when Next =< Now -> write(K, Job#{next => Now + 30000}), {true, {K, Job}};
                    _ -> false
                end;
            ({K = {device, _, _, _}, #{updated := Updated}}) when Updated < Now - 2592000000 ->
                mnesia:delete({?T, K}), false;
            (_) -> false
        end, rows())
    end) of
        {atomic, Jobs} -> [spawn(fun() -> send(K, Job) end) || {K, Job} <- Jobs], ok;
        _ -> ok
    end.

send(K, #{registration := Registration, payload := Payload, attempts := Attempts} = Job) ->
    Result = try
        {ok, Secret0} = file:read_file("/etc/mongooseim/push-bridge-secret"),
        Secret = string:trim(Secret0),
        true = byte_size(Secret) >= 32,
        application:ensure_all_started(inets),
        Public0 = maps:from_list([{atom_to_binary(Key), maps:get(Key, Registration)} || Key <- [token, platform, environment]]),
        Public = case maps:get(platform, Registration) of
            <<"web">> -> Public0#{<<"generations">> => maps:get(generations, Registration)};
            _ -> Public0
        end,
        Body = iolist_to_binary(jiffy:encode(#{<<"registration">> => Public,
                              <<"payload">> => Payload})),
        {ok, {{_, Code, _}, _, Response}} = httpc:request(post,
            {"http://127.0.0.1:8789/v1/call", [{"authorization", "Bearer " ++ binary_to_list(Secret)}],
             "application/json", Body}, [{timeout, 8000}, {connect_timeout, 1000}], [{body_format, binary}]),
        case Code of 200 -> delivered; 410 -> invalid_token; _ -> {retry, Response} end
    catch _:_ -> retry end,
    mnesia:transaction(fun() ->
        case Result of
            delivered -> mnesia:delete({?T, K});
            invalid_token ->
                {push, D, _, Device} = K, Key = {device, D, maps:get(user, Job), Device},
                case read(Key) =:= Registration of true -> mnesia:delete({?T, Key}); false -> ok end,
                mnesia:delete({?T, K});
            _ ->
                case read(K) of
                    undefined -> ok;
                    _ -> write(K, Job#{attempts => Attempts + 1, next => now_ms() + min(15000, 2000 bsl min(Attempts, 3))})
                end
        end
    end), ok.

valid(true) -> ok;
valid(false) -> mnesia:abort({denied, <<"invalid_device">>}).
read(K) -> case mnesia:read(?T, K, write) of [{?T, K, V}] -> V; [] -> undefined end.
write(K, V) -> mnesia:write({?T, K, V}).
rows() -> mnesia:foldl(fun({?T, K, V}, Acc) -> [{K, V} | Acc] end, [], ?T).
now_ms() -> erlang:system_time(millisecond).
