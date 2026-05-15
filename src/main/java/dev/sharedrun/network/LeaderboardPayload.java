package dev.sharedrun.network;

import dev.sharedrun.SharedRun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record LeaderboardPayload(String json) implements CustomPayload {
    public static final Id<LeaderboardPayload> TYPE = new Id<>(SharedRun.id("leaderboard"));
    public static final PacketCodec<RegistryByteBuf, LeaderboardPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, LeaderboardPayload::json, LeaderboardPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return TYPE; }
}
