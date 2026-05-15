package dev.sharedrun.network;

import dev.sharedrun.SharedRun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record LeaderboardRequestPayload() implements CustomPayload {
    public static final Id<LeaderboardRequestPayload> TYPE = new Id<>(SharedRun.id("leaderboard_request"));
    public static final PacketCodec<RegistryByteBuf, LeaderboardRequestPayload> CODEC =
            PacketCodec.unit(new LeaderboardRequestPayload());

    @Override
    public Id<? extends CustomPayload> getId() { return TYPE; }
}
