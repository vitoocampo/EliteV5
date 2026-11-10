package com.elitebosses.net;

import com.elitebosses.client.HoundClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: avisa que el jugador esta (o ya no esta) agarrado por el Sabueso. */
public class HoundGrabPacket {
    private final boolean grabbed;

    public HoundGrabPacket(boolean grabbed) {
        this.grabbed = grabbed;
    }

    public static void encode(HoundGrabPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.grabbed);
    }

    public static HoundGrabPacket decode(FriendlyByteBuf buf) {
        return new HoundGrabPacket(buf.readBoolean());
    }

    public static void handle(HoundGrabPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HoundClient.setGrabbed(msg.grabbed)));
        ctx.get().setPacketHandled(true);
    }
}
