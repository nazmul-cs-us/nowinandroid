%% Create-only XEP-0077 signup over verified client TLS; no account-management API.
-module(mod_deenly_family_signup).
-export([start/1, stop/1, features/3, packet/3, process_iq/5, validate/2, register/5, allow/3]).
-include("jlib.hrl").
-ifdef(TEST).
-export([handle/3]).
-endif.
-include("mongoose_logger.hrl").
-define(NS, <<"jabber:iq:register">>).
-define(T, deenly_family_signup_rate).

start(Host) ->
    ejabberd_sup:create_ets_table(?T, [named_table, public, set, {write_concurrency, true}]),
    gen_iq_handler:add_iq_handler_for_domain(Host, ?NS, ejabberd_local,
        fun ?MODULE:process_iq/5, #{}, one_queue).
stop(Host) -> gen_iq_handler:remove_iq_handler_for_domain(Host, ?NS, ejabberd_local).
%% MongooseIM 6.10 uses an empty generic schema for custom modules, so this
%% switch belongs to the service environment, not the module's TOML section.
enabled(_Host) ->
    case os:getenv("FAMILY_SIGNUP_ENABLED") of
        false -> true;
        "true" -> true;
        "1" -> true;
        _ -> false
    end.
features(Acc, _, #{host_type := Host}) ->
    case enabled(Host) of
        true -> {ok, [#xmlel{name = <<"register">>,
                     attrs = #{<<"xmlns">> => <<"http://jabber.org/features/iq-register">>}} | Acc]};
        false -> {ok, Acc}
    end.
%% Reject authenticated registration/password changes/removal; this endpoint only creates accounts.
process_iq(Acc, _, _, IQ, _) -> {Acc, error_reply(IQ, <<"not-allowed">>)}.
packet(Acc, #{c2s_data := Data, c2s_state := State}, #{host_type := Host}) ->
    case mongoose_acc:stanza_name(Acc) of
        <<"iq">> ->
            case mongoose_iq:info(Acc) of
                {#iq{xmlns = ?NS} = IQ, Acc1} ->
                    Domain = mongoose_c2s:get_lserver(Data),
                    {IP, _} = mongoose_c2s:get_ip(Data),
                    Secure = mongoose_xmpp_socket:is_ssl(mongoose_c2s:get_socket(Data)),
                    BeforeAuth = case State of {wait_for_feature_before_auth, _, _} -> true; _ -> false end,
                    Reply = case enabled(Host) andalso Secure andalso BeforeAuth of
                        true -> handle(IQ, Domain, IP);
                        false -> error_reply(IQ, <<"not-allowed">>)
                    end,
                    Server = jid:make_noprep(<<>>, Domain, <<>>),
                    Element = jlib:iq_to_xml(Reply),
                    Response = Element#xmlel{attrs = (Element#xmlel.attrs)#{<<"from">> => Domain}},
                    Routed = mongoose_acc:update_stanza(#{from_jid => Server, to_jid => Server, element => Response}, Acc1),
                    {stop, mongoose_c2s_acc:to_acc(Acc1, route, Routed)};
                _ -> {ok, Acc}
            end;
        _ -> {ok, Acc}
    end.
handle(#iq{type = get} = IQ, _, _) ->
    IQ#iq{type = result, sub_el = [#xmlel{name = <<"query">>, attrs = #{<<"xmlns">> => ?NS},
        children = [#xmlel{name = <<"username">>}, #xmlel{name = <<"password">>}]}]};
handle(#iq{type = set, sub_el = #xmlel{name = <<"query">>, children = Children}} = IQ, D, IP) ->
    Users = [exml_query:cdata(E) || #xmlel{name = <<"username">>} = E <- Children],
    Passwords = [exml_query:cdata(E) || #xmlel{name = <<"password">>} = E <- Children],
    Other = [E || #xmlel{name = N} = E <- Children, N =/= <<"username">>, N =/= <<"password">>],
    case {Users, Passwords, Other} of
        {[User], [Password], []} ->
            case register(D, User, Password, IP, true) of
                ok -> IQ#iq{type = result, sub_el = []};
                {error, Condition} -> error_reply(IQ, Condition)
            end;
        _ -> error_reply(IQ, <<"bad-request">>)
    end;
handle(IQ, _, _) -> error_reply(IQ, <<"bad-request">>).

register(_, _, _, _, false) -> {error, <<"not-allowed">>};
register(D, User, Password, IP, true) ->
    case allow(D, IP, erlang:monotonic_time(second) div 60) of
        false -> {error, <<"resource-constraint">>};
        true ->
            case validate(User, Password) of
                {ok, Normalized} ->
                    JID = jid:from_binary(<<Normalized/binary, "@", D/binary>>),
                    try ejabberd_auth:try_register(JID, Password) of
                        ok -> ok;
                        {error, exists} -> {error, <<"conflict">>};
                        {error, limit_per_domain_exceeded} -> {error, <<"resource-constraint">>};
                        _ -> {error, <<"service-unavailable">>}
                    catch _:_ -> {error, <<"service-unavailable">>} end;
                Error -> Error
            end
    end.
validate(User, Password) when is_binary(User), is_binary(Password), byte_size(User) =< 32, byte_size(Password) =< 512 ->
    Name = << <<(case C of X when X >= $A, X =< $Z -> X + 32; X -> X end)>> || <<C>> <= User >>,
    Reserved = lists:member(Name, [<<"admin">>, <<"administrator">>, <<"root">>, <<"support">>,
                                  <<"postmaster">>, <<"android">>, <<"ios">>]),
    case re:run(Name, <<"^[a-z0-9][a-z0-9._-]{2,31}$">>, [{capture, none}]) of
        match when Reserved -> {error, <<"forbidden">>};
        match ->
            case unicode:characters_to_binary(Password, utf8, {utf16, big}) of
                UTF16 when is_binary(UTF16), byte_size(UTF16) >= 24, byte_size(UTF16) =< 256 ->
                    case re:run(Password, <<"[\\x00-\\x1f\\x7f]">>, [{capture, none}]) =:= nomatch
                         andalso string:trim(Password) =/= <<>> of
                        true -> {ok, Name}; false -> {error, <<"not-acceptable">>}
                    end;
                _ -> {error, <<"not-acceptable">>}
            end;
        _ -> {error, <<"bad-request">>}
    end;
validate(_, _) -> {error, <<"bad-request">>}.

%% Atomic fixed windows: 5 attempts/IP/minute and 100/domain/minute. No raw IPs persist on disk.
allow(D, IP, Window) ->
    ets:select_delete(?T, [{{{'_', '_', '$1'}, '_'}, [{'<', '$1', Window}], [true]}]),
    case ets:info(?T, size) >= 4096 of
        true -> false;
        false ->
            Global = ets:update_counter(?T, {D, global, Window}, {2, 1}, {{D, global, Window}, 0}),
            Count = ets:update_counter(?T, {D, IP, Window}, {2, 1}, {{D, IP, Window}, 0}),
            Global =< 100 andalso Count =< 5
    end.
error_reply(IQ, Condition) ->
    %% Never echo the submitted query/password in success or error responses.
    Type = case Condition of <<"resource-constraint">> -> <<"wait">>; _ -> <<"cancel">> end,
    IQ#iq{type = error, sub_el = [#xmlel{name = <<"query">>, attrs = #{<<"xmlns">> => ?NS}},
        #xmlel{name = <<"error">>, attrs = #{<<"type">> => Type}, children = [
            #xmlel{name = Condition, attrs = #{<<"xmlns">> => <<"urn:ietf:params:xml:ns:xmpp-stanzas">>}}]}]}.
