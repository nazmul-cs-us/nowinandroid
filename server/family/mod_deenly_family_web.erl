%% Short-lived browser pairing, scoped to the authenticated native installation.
-module(mod_deenly_family_web).
-export([pair/3, revoke/3, enqueue/2, current/3]).
-define(T, deenly_family).
config() ->
    Path = case os:getenv("FAMILY_WEB_CONFIG_FILE") of false -> "/etc/mongooseim/family-web.json"; P -> P end,
    try case file:read_file(Path) of {ok, Data} -> case jiffy:decode(Data, [return_maps]) of M when is_map(M) -> M; _ -> #{} end; _ -> #{} end
    catch _:_ -> #{} end.
pair(D, U, R) ->
    Device = maps:get(<<"deviceId">>, R),
    valid(is_binary(Device) andalso byte_size(Device) >= 16 andalso byte_size(Device) =< 80, <<"invalid_device">>),
    Config = config(), valid(maps:is_key(<<"url">>, Config), <<"web_helper_unavailable">>),
    K = {web_generation, D, U, Device},
    Count = length([ok || {{web_generation, Domain, User, _}, _} <- rows(), Domain =:= D, User =:= U]),
    valid(read(K) =/= undefined orelse Count < 8, <<"family_limit">>),
    Generation = case read(K) of undefined -> write(K, 0), 0; N -> N end,
    Expires = erlang:system_time(second) + 300,
    Payload = iolist_to_binary(jiffy:encode(#{<<"purpose">> => <<"family-web-pair-v1">>, <<"account">> => U,
        <<"owner">> => Device, <<"generation">> => Generation, <<"expires">> => Expires,
        <<"nonce">> => b64(crypto:strong_rand_bytes(24))})),
    SecretPath = case os:getenv("FAMILY_PUSH_SECRET_FILE") of false -> "/etc/mongooseim/push-bridge-secret"; S -> S end,
    {ok, Secret0} = file:read_file(SecretPath), Secret = string:trim(Secret0),
    valid(byte_size(Secret) >= 32, <<"web_helper_unavailable">>),
    Encoded = b64(Payload),
    Signature = b64(crypto:mac(hmac, sha256, Secret, <<"family-web-pair-v1:", Encoded/binary>>)),
    {#{<<"webPairing">> => #{<<"code">> => <<Encoded/binary, ".", Signature/binary>>,
        <<"url">> => maps:get(<<"url">>, Config), <<"expires">> => Expires * 1000}}, []}.
revoke(D, U, Device) ->
    K = {web_generation, D, U, Device},
    case read(K) of undefined -> ok; N -> write(K, N + 1) end,
    %% A sign-out must revoke even an already queued browser alert.
    [mnesia:delete({?T, K2}) || {K2 = {push, Domain, _, <<"web-helper">>}, #{user := User}} <- rows(), Domain =:= D, User =:= U], ok.
generations(D, U) -> maps:from_list([{Id, N} || {{web_generation, Domain, User, Id}, N} <- rows(), Domain =:= D, User =:= U]).
enqueue(D, C) ->
    U = maps:get(<<"callee">>, C), Generations = generations(D, U),
    case maps:is_key(<<"url">>, config()) andalso map_size(Generations) > 0 of
        false -> ok;
        true ->
            Id = maps:get(<<"id">>, C), K = {push, D, Id, <<"web-helper">>},
            case read(K) of
                undefined -> write(K, #{user => U, expires => maps:get(<<"expires">>, C),
                    next => erlang:system_time(millisecond), attempts => 0,
                    registration => #{token => U, platform => <<"web">>, environment => <<"production">>, generations => Generations},
                    payload => #{<<"v">> => 1, <<"callId">> => Id, <<"caller">> => maps:get(<<"caller">>, C),
                        <<"callee">> => U, <<"kind">> => maps:get(<<"kind">>, C), <<"expires">> => maps:get(<<"expires">>, C)}});
                _ -> ok
            end
    end.
current(D, U, #{platform := <<"web">>, generations := Generations}) ->
    maps:is_key(<<"url">>, config()) andalso Generations =:= generations(D, U);
current(_, _, _) -> false.
b64(B) -> binary:replace(binary:replace(binary:replace(base64:encode(B), <<"+">>, <<"-">>, [global]), <<"/">>, <<"_">>, [global]), <<"=">>, <<>>, [global]).
valid(true, _) -> ok;
valid(false, Reason) -> mnesia:abort({denied, Reason}).
read(K) -> case mnesia:read(?T, K, write) of [{?T, K, V}] -> V; [] -> undefined end.
write(K, V) -> mnesia:write({?T, K, V}).
rows() -> mnesia:foldl(fun({?T, K, V}, A) -> [{K, V} | A] end, [], ?T).
