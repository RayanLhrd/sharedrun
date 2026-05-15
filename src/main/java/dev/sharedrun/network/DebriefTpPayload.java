package dev.sharedrun.network;

import dev.sharedrun.SharedRun;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Packet C→S envoyé quand un joueur clique sur un bouton de TP debrief dans le RunSummaryScreen.
 * destination : 1=Overworld, 2=Nether, 3=Stronghold, 4=End
 */
public record DebriefTpPayload(int destination) implements CustomPayload {
    public static final Id<DebriefTpPayload> TYPE = new Id<>(SharedRun.id("debrief_tp"));

    public static final PacketCodec<RegistryByteBuf, DebriefTpPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, DebriefTpPayload::destination,
            DebriefTpPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
