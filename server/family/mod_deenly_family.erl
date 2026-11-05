%% Private Deenly protocol; this is not a general Jingle implementation.
-module(mod_deenly_family).
-behaviour(gen_mod).
-compile({no_auto_import, [put/2]}).
-export([start/2, stop/1, supported_features/0, config_spec/0, process_iq/5,
         hooks/1, filter_packet/3, sweep/1, presence_removed/3]).
-include("jlib.hrl").
-include("mongoose_logger.hrl").
-ifdef(TEST).
-export([execute/4, expire/1]).
-endif.
-include("mongoose_config_spec.hrl").
-define(NS, <<"urn:starception:family:1">>).
-define(TABLE, deenly_family).

supported_features() -> [dynamic_domains].
config_spec() -> #section{items = #{}}.
start(Host, _) ->
    case mnesia:create_table(?TABLE, [{attributes, [key, value]}, {disc_copies, [node()]}]) of
        {atomic, ok} -> ok;
        {aborted, {already_exists, ?TABLE}} -> ok;
        Error -> error({family_storage_unavailable, Error})
    end,
    ok = mnesia:wait_for_tables([?TABLE], 10000),
    ok = mod_deenly_family_presence:init(),
    {ok, Timer} = timer:apply_interval(5000, ?MODULE, sweep, [Host]),
    persistent_term:put({?MODULE, Host}, Timer),
    gen_iq_handler:add_iq_handler_for_domain(Host, ?NS, ejabberd_local,
                                           fun ?MODULE:process_iq/5, #{}, one_queue).
stop(Host) ->
    timer:cancel(persistent_term:get({?MODULE, Host})),
    persistent_term:erase({?MODULE, Host}),
    gen_iq_handler:remove_iq_handler_for_domain(Host, ?NS, ejabberd_local).
hooks(Host) -> [{filter_local_packet, Host, fun ?MODULE:filter_packet/3, #{}, 5},
                {unset_presence, Host, fun ?MODULE:presence_removed/3, #{}, 60},
                {sm_remove_connection, Host, fun ?MODULE:presence_removed/3, #{}, 60}].
presence_removed(Acc, #{jid := #jid{lserver = D} = J}, _) ->
    F = jid:to_binary(J), U = bare(F),
    case mnesia:transaction(fun() ->
        mnesia:read(?TABLE, {lock, D}, write),
        mod_deenly_family_presence:remove(D, U, F)
    end) of {atomic, Events} -> deliver(D, Events); _ -> ok end,
    {ok, Acc}.

%% Clients must use authenticated server IQs. Never route peer-authored call messages.
filter_packet(drop, _, _) -> {ok, drop};
filter_packet({#jid{luser = U}, #jid{luser = V}, _, #xmlel{name = Name}}, _, _)
  when U =/= <<>>, V =/= <<>>, (Name =:= <<"message">> orelse Name =:= <<"iq">> orelse Name =:= <<"presence">>) -> {stop, drop};
filter_packet(Packet, _, _) -> {ok, Packet}.

process_iq(Acc, From, #jid{lserver = Domain}, #iq{type = set, sub_el = El} = IQ, _) ->
    Full = jid:to_binary(From), User = bare(Full),
    Result = try
        Request = jiffy:decode(exml_query:cdata(El), [return_maps]),
        true = maps:get(<<"v">>, Request) =:= 1,
        true = byte_size(jiffy:encode(Request)) =< 40000,
        true = From#jid.lserver =:= Domain,
        case mnesia:transaction(fun() ->
            %% Domain-wide lock serializes eligibility checks, answers and revocations.
            mnesia:read(?TABLE, {lock, Domain}, write),
            expire(Domain),
            execute(Domain, User, Full, Request)
        end) of
            {atomic, {Reply, Events}} ->
                deliver(Domain, Events),
                maps:merge(#{<<"ok">> => true, <<"v">> => 1}, Reply);
            {aborted, {denied, Code}} -> #{<<"ok">> => false, <<"error">> => Code};
            _ -> #{<<"ok">> => false, <<"error">> => <<"unavailable">>}
        end
    catch _:_ -> #{<<"ok">> => false, <<"error">> => <<"invalid_request">>} end,
    {Acc, IQ#iq{type = result, sub_el = [element(<<"family">>, ?NS, Result)]}};
process_iq(Acc, _, _, IQ, _) ->
    {Acc, IQ#iq{type = error, sub_el = [mongoose_xmpp_errors:not_allowed()]}}.

execute(D, U, F, #{<<"action">> := <<"register_device">>} = R) ->
    mod_deenly_family_push:register(D, U, F, R);
execute(D, U, _, #{<<"action">> := <<"unregister_device">>, <<"deviceId">> := Device}) ->
    mod_deenly_family_push:unregister(D, U, Device);
execute(D, U, F, #{<<"action">> := <<"presence">>, <<"active">> := Active} = Req) when is_boolean(Active) ->
    {#{<<"presenceEpoch">> => mod_deenly_family_presence:epoch()},
     mod_deenly_family_presence:update(D, U, F, Active, presence_sequence(Req))};
execute(D, U, F, #{<<"action">> := <<"snapshot">>} = Req) ->
    PresenceEvents = case maps:find(<<"active">>, Req) of
        {ok, Active} when is_boolean(Active) -> mod_deenly_family_presence:update(D, U, F, Active, presence_sequence(Req));
        error -> [];
        _ -> denied(<<"invalid_request">>)
    end,
    lists:foreach(fun(C) ->
        case maps:get(<<"stage">>, C) =:= <<"active">> andalso selected(C, F) of
            true ->
                Lease = case F =:= maps:get(<<"callerFull">>, C) of true -> <<"callerLease">>; false -> <<"calleeLease">> end,
                save_call(D, C#{Lease => now_ms() + 75000});
            false -> ok
        end
    end, calls(D)),
    Calls = [view(C, F) || C <- calls(D), participant(C, U),
                          maps:get(<<"stage">>, C) =/= <<"ended">>],
    {#{<<"members">> => members(D, U), <<"calls">> => Calls,
       <<"presenceEpoch">> => mod_deenly_family_presence:epoch()}, PresenceEvents};
execute(D, U, _, #{<<"action">> := Action, <<"peer">> := Peer})
  when Action =:= <<"invite">>; Action =:= <<"accept_member">>;
       Action =:= <<"remove_member">> ->
    valid_peer(D, U, Peer), K = family_key(D, U, Peer), Old = read(K),
    case Action of
        <<"invite">> ->
            case Old of
                undefined ->
                    true_or_deny(length(members(D, U)) < 100 andalso length(members(D, Peer)) < 100, <<"family_limit">>),
                    put(K, #{a => U, b => Peer, by => U, status => <<"pending">>});
                #{status := <<"accepted">>} -> ok;
                #{by := U} -> ok;
                _ -> denied(<<"accept_invitation_first">>)
            end;
        <<"accept_member">> ->
            case Old of
                #{by := Peer, status := <<"pending">>} -> put(K, Old#{status => <<"accepted">>}),
                    mod_deenly_family_presence:accept(D, U, Peer);
                #{status := <<"accepted">>} -> ok;
                _ -> denied(<<"invitation_not_found">>)
            end;
        <<"remove_member">> ->
            mnesia:delete({?TABLE, K}), mod_deenly_family_presence:revoke(D, U, Peer)
    end,
    EndEvents = case Action of
        <<"remove_member">> -> lists:flatmap(fun(C) ->
            case participant(C, U) andalso participant(C, Peer) andalso live(C) of
                true -> End = ended(C, <<"family_removed">>), save_call(D, End), call_events(End);
                false -> []
            end end, calls(D));
        _ -> []
    end,
    Events = [{U, #{<<"event">> => <<"family">>, <<"presenceEpoch">> => mod_deenly_family_presence:epoch(), <<"members">> => members(D, U)}},
              {Peer, #{<<"event">> => <<"family">>, <<"presenceEpoch">> => mod_deenly_family_presence:epoch(), <<"members">> => members(D, Peer)}}],
    {#{<<"members">> => members(D, U), <<"presenceEpoch">> => mod_deenly_family_presence:epoch()}, Events ++ EndEvents};
execute(D, U, F, #{<<"action">> := <<"invite_call">>, <<"peer">> := Peer,
                   <<"callId">> := Id, <<"kind">> := Kind}) ->
    valid_peer(D, U, Peer), family(D, U, Peer),
    true_or_deny(is_binary(Id) andalso byte_size(Id) =:= 36 andalso
        re:run(Id, <<"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$">>,
               [{capture, none}]) =:= match, <<"invalid_call">>),
    true_or_deny(lists:member(Kind, [<<"voice">>, <<"video">>]), <<"invalid_kind">>),
    C = case read({call, D, Id}) of
        undefined ->
            true_or_deny(not lists:any(fun(X) -> live(X) andalso
                (participant(X, U) orelse participant(X, Peer)) end, calls(D)), <<"busy">>),
            New = #{<<"id">> => Id, <<"caller">> => U, <<"callee">> => Peer,
                    <<"callerFull">> => F, <<"calleeFull">> => <<>>,
                    <<"kind">> => Kind, <<"stage">> => <<"ringing">>,
                    <<"version">> => 1, <<"expires">> => now_ms() + 45000,
                    <<"reason">> => <<>>},
            save_call(D, New), mod_deenly_family_push:enqueue(D, New), New;
        Existing ->
            true_or_deny(maps:get(<<"callerFull">>, Existing) =:= F andalso
                         maps:get(<<"callee">>, Existing) =:= Peer, <<"invalid_call">>), Existing
    end,
    {#{<<"call">> => view(C, F)}, call_events(C)};
execute(D, U, F, #{<<"action">> := Action, <<"callId">> := Id} = Req) ->
    C = read({call, D, Id}),
    true_or_deny(is_map(C) andalso participant(C, U), <<"call_not_found">>),
    case Action of
        <<"accept_call">> ->
            family(D, maps:get(<<"caller">>, C), maps:get(<<"callee">>, C)),
            true_or_deny(U =:= maps:get(<<"callee">>, C), <<"not_invited">>),
            New = case maps:get(<<"stage">>, C) of
                <<"ringing">> -> C#{<<"stage">> => <<"active">>, <<"calleeFull">> => F,
                                    <<"version">> => maps:get(<<"version">>, C) + 1,
                                    <<"callerLease">> => now_ms() + 75000,
                                    <<"calleeLease">> => now_ms() + 75000,
                                    <<"hardExpires">> => now_ms() + 3300000};
                <<"active">> -> true_or_deny(F =:= maps:get(<<"calleeFull">>, C), <<"answered_elsewhere">>), C;
                _ -> denied(<<"call_ended">>)
            end,
            save_call(D, New), {#{<<"call">> => view(New, F)}, call_events(New)};
        <<"end_call">> ->
            true_or_deny(selected(C, F) orelse maps:get(<<"stage">>, C) =:= <<"ringing">>, <<"answered_elsewhere">>),
            New = case live(C) of true -> ended(C, <<"hangup">>); false -> C end,
            save_call(D, New), {#{<<"call">> => view(New, F)}, call_events(New)};
        <<"turn">> ->
            true_or_deny(selected(C, F) andalso maps:get(<<"stage">>, C) =:= <<"active">>, <<"not_active">>),
            family(D, maps:get(<<"caller">>, C), maps:get(<<"callee">>, C)),
            {#{<<"iceServers">> => turn(U, Id)}, []};
        <<"signal">> ->
            true_or_deny(selected(C, F) andalso maps:get(<<"stage">>, C) =:= <<"active">>, <<"not_active">>),
            family(D, maps:get(<<"caller">>, C), maps:get(<<"callee">>, C)),
            Payload = maps:get(<<"payload">>, Req),
            true_or_deny(is_map(Payload) andalso byte_size(jiffy:encode(Payload)) < 35000, <<"invalid_signal">>),
            Other = case F =:= maps:get(<<"callerFull">>, C) of
                true -> maps:get(<<"calleeFull">>, C); false -> maps:get(<<"callerFull">>, C) end,
            {#{}, [{Other, #{<<"event">> => <<"signal">>, <<"callId">> => Id, <<"payload">> => Payload}}]};
        _ -> denied(<<"unknown_action">>)
    end;
execute(_, _, _, _) -> denied(<<"unknown_action">>).

presence_sequence(Req) ->
    Sequence = maps:get(<<"activityVersion">>, Req, 0),
    true_or_deny(is_integer(Sequence) andalso Sequence >= 0 andalso Sequence < 9007199254740991, <<"invalid_request">>),
    Sequence.
family(D, A, B) ->
    case read(family_key(D, A, B)) of #{status := <<"accepted">>} -> ok; _ -> denied(<<"not_family">>) end.
valid_peer(D, U, Peer) ->
    try
        J = jid:from_binary(Peer),
        true_or_deny(J#jid.lserver =:= D andalso J#jid.luser =/= <<>> andalso
                     J#jid.lresource =:= <<>> andalso bare(Peer) =/= U, <<"invalid_member">>),
        true_or_deny(ejabberd_auth:does_user_exist(J), <<"member_not_found">>)
    catch _:_ -> denied(<<"invalid_member">>) end.
family_key(D, A, B) -> {family, D, lists:sort([A, B])}.
read(K) -> case mnesia:read(?TABLE, K, write) of [{?TABLE, K, V}] -> V; [] -> undefined end.
put(K, V) -> mnesia:write({?TABLE, K, V}).
rows() -> mnesia:foldl(fun({?TABLE, K, V}, A) -> [{K, V} | A] end, [], ?TABLE).
members(D, U) ->
    [maps:merge(#{<<"jid">> => case U =:= A of true -> B; false -> A end,
       <<"status">> => case S of <<"accepted">> -> S; _ when By =:= U -> <<"outgoing">>; _ -> <<"incoming">> end},
       case S of <<"accepted">> -> mod_deenly_family_presence:state(D, case U =:= A of true -> B; false -> A end); _ -> #{} end)
     || {{family, Domain, _}, #{a := A, b := B, by := By, status := S}} <- rows(),
        Domain =:= D, (A =:= U orelse B =:= U)].
calls(D) -> [C || {{call, Domain, _}, C} <- rows(), Domain =:= D].
save_call(D, C) -> put({call, D, maps:get(<<"id">>, C)}, C).
participant(C, U) when is_map(C) -> lists:member(U, [maps:get(<<"caller">>, C), maps:get(<<"callee">>, C)]);
participant(_, _) -> false.
selected(C, F) -> lists:member(F, [maps:get(<<"callerFull">>, C), maps:get(<<"calleeFull">>, C)]).
live(C) -> maps:get(<<"stage">>, C) =/= <<"ended">>.
ended(C, Reason) -> C#{<<"stage">> => <<"ended">>, <<"reason">> => Reason,
                       <<"version">> => maps:get(<<"version">>, C) + 1, <<"endedAt">> => now_ms()}.
view(C, F) -> (maps:without([<<"callerFull">>, <<"calleeFull">>, <<"callerLease">>, <<"calleeLease">>, <<"hardExpires">>], C))#{<<"selected">> => selected(C, F)}.
call_events(C) ->
    Targets = lists:usort([maps:get(<<"callerFull">>, C), case maps:get(<<"calleeFull">>, C) of
        <<>> -> maps:get(<<"callee">>, C); Full -> Full end]),
    [{F, #{<<"event">> => <<"call">>, <<"call">> => view(C, F)}} || F <- Targets].
should_expire(C, Now) ->
    case maps:get(<<"stage">>, C) of
        <<"ringing">> -> maps:get(<<"expires">>, C) =< Now;
        <<"active">> -> lists:any(fun(T) -> T =< Now end,
            [maps:get(<<"callerLease">>, C, 0), maps:get(<<"calleeLease">>, C, 0), maps:get(<<"hardExpires">>, C, 0)]);
        _ -> false
    end.
expire(D) ->
    Now = now_ms(),
    lists:foreach(fun(C) ->
        case should_expire(C, Now) of true -> save_call(D, ended(C, <<"expired">>)); false -> ok end
    end, calls(D)).
sweep(_Host) ->
    Now = now_ms(),
    case mnesia:transaction(fun() ->
        lists:flatmap(fun
            ({{call, D, Id}, C}) ->
                mnesia:read(?TABLE, {lock, D}, write),
                case should_expire(C, Now) of
                    true -> End = ended(C, <<"expired">>), save_call(D, End), [{D, call_events(End)}];
                    false ->
                        case maps:get(<<"stage">>, C) =:= <<"ended">> andalso maps:get(<<"endedAt">>, C, Now) < Now - 86400000 of
                            true -> mnesia:delete({?TABLE, {call, D, Id}});
                            false -> ok
                        end, []
                end;
            (_) -> []
        end, rows())
    end) of {atomic, Events} -> [deliver(D, E) || {D, E} <- Events]; _ -> ok end,
    [deliver(D, E) || {D, E} <- mod_deenly_family_presence:sweep()],
    mod_deenly_family_push:drain().
turn(U, Id) ->
    Path = case os:getenv("FAMILY_TURN_SECRET_FILE") of false -> "/etc/mongooseim/turn-secret"; P -> P end,
    {ok, Data} = file:read_file(Path), Secret = string:trim(Data),
    Name = <<(integer_to_binary(erlang:system_time(second) + 3600))/binary, ":",
             (binary:encode_hex(crypto:hash(sha256, <<U/binary, Id/binary>>)))/binary>>,
    Credential = base64:encode(crypto:mac(hmac, sha, Secret, Name)),
    [#{<<"urls">> => [<<"turn:turn.starception.com:3478?transport=udp">>,
                       <<"turn:turn.starception.com:3478?transport=tcp">>,
                       <<"turns:turn.starception.com:5349?transport=tcp">>],
       <<"username">> => Name, <<"credential">> => Credential}].
deliver(D, Events) ->
    From = jid:from_binary(D),
    lists:foreach(fun({Target, Body}) ->
        IsPresence = maps:get(<<"event">>, Body, <<>>) =:= <<"presence">>,
        Type = case IsPresence of true -> <<"headline">>; false -> <<"normal">> end,
        Hints = case IsPresence of
            true -> [#xmlel{name = <<"no-store">>, attrs = #{<<"xmlns">> => <<"urn:xmpp:hints">>}},
                     #xmlel{name = <<"no-copy">>, attrs = #{<<"xmlns">> => <<"urn:xmpp:hints">>}}];
            false -> []
        end,
        Packet = #xmlel{name = <<"message">>, attrs = #{<<"type">> => Type, <<"from">> => D, <<"to">> => Target},
                        children = [element(<<"body">>, <<"jabber:client">>, Body#{<<"v">> => 1}) | Hints]},
        mongoose_router:route(mongoose_acc:new(From, jid:from_binary(Target), Packet, ?LOCATION))
    end, Events).
element(Name, NS, Body) -> #xmlel{name = Name, attrs = #{<<"xmlns">> => NS},
                               children = [#xmlcdata{content = jiffy:encode(Body)}]}.
bare(F) -> hd(binary:split(F, <<"/">>)).
now_ms() -> erlang:system_time(millisecond).
true_or_deny(true, _) -> ok;
true_or_deny(false, Code) -> denied(Code).
denied(Code) -> mnesia:abort({denied, Code}).
