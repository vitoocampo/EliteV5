package com.elitebosses.net;

import com.elitebosses.entity.HoundmasterEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: el jugador agarrado pulso ESPACIO. */
public class HoundEscapePacket {
    public HoundEscapePacket() {
    }

    public static void encode(HoundEscapePacket msg, FriendlyByteBuf buf) {
    }

    public static HoundEscapePacket decode(FriendlyByteBuf buf) {
        return new HoundEscapePacket();
    }

    public static void handle(HoundEscapePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                HoundmasterEntity.onEscapePress(player);
                com.elitebosses.entity.StalkerEntity.onEscapePress(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
