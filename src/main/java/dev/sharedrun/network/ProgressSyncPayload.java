package dev.sharedrun.network;

import dev.sharedrun.SharedRun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record ProgressSyncPayload(int flags, int foodCount, int ironIngots, int diamonds,
                                  int blazeRods, int enderPearls, int eyesOfEnder)
        implements CustomPayload {

    public static final Id<ProgressSyncPayload> TYPE = new Id<>(SharedRun.id("progress_sync"));

    public static final PacketCodec<RegistryByteBuf, ProgressSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ProgressSyncPayload::flags,
            PacketCodecs.VAR_INT, ProgressSyncPayload::foodCount,
            PacketCodecs.VAR_INT, ProgressSyncPayload::ironIngots,
            PacketCodecs.VAR_INT, ProgressSyncPayload::diamonds,
            PacketCodecs.VAR_INT, ProgressSyncPayload::blazeRods,
            PacketCodecs.VAR_INT, ProgressSyncPayload::enderPearls,
            PacketCodecs.VAR_INT, ProgressSyncPayload::eyesOfEnder,
            ProgressSyncPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
