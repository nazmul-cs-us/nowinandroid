%% Routing-boundary test double; production uses the MongooseIM accumulator.
-module(mongoose_acc).
-export([new/4]).
new(From, To, Packet, _) -> #{from => From, to => To, packet => Packet}.
