package com.elitebosses.net;

import com.elitebosses.client.HoundClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: 0 = sin musica, 1 = HoundLurk (lejos), 2 = HoundChase (cerca), 3/4/5 = Stalker (acecho 1, acecho 2, persecucion), 6 = JudgeChase. */
public class HoundMusicPacket {
    private final int mode;

    public HoundMusicPacket(int mode) {
        this.mode = mode;
    }

    public static void encode(HoundMusicPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.mode);
    }

    public static HoundMusicPacket decode(FriendlyByteBuf buf) {
        return new HoundMusicPacket(buf.readVarInt());
    }

    public static void handle(HoundMusicPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HoundClient.setMusicMode(msg.mode)));
        ctx.get().setPacketHandled(true);
    }
}
