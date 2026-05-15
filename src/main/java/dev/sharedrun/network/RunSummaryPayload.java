package dev.sharedrun.network;

import dev.sharedrun.SharedRun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Packet S→C envoyé à la fin d'une run : contient toute la data nécessaire au RunSummaryScreen.
 * Le payload est sérialisé en JSON pour rester souple sur l'ajout de futurs champs.
 */
public record RunSummaryPayload(String json) implements CustomPayload {
    public static final Id<RunSummaryPayload> TYPE = new Id<>(SharedRun.id("run_summary"));

    public static final PacketCodec<RegistryByteBuf, RunSummaryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, RunSummaryPayload::json,
            RunSummaryPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
