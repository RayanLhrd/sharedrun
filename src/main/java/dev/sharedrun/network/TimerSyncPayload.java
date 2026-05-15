package dev.sharedrun.network;

import dev.sharedrun.SharedRun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record TimerSyncPayload(int remainingSeconds, int totalSeconds, boolean running) implements CustomPayload {
    public static final Id<TimerSyncPayload> TYPE = new Id<>(SharedRun.id("timer_sync"));

    public static final PacketCodec<RegistryByteBuf, TimerSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, TimerSyncPayload::remainingSeconds,
            PacketCodecs.VAR_INT, TimerSyncPayload::totalSeconds,
            PacketCodecs.BOOLEAN, TimerSyncPayload::running,
            TimerSyncPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
