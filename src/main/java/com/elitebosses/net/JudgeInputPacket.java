package com.elitebosses.net;

import com.elitebosses.entity.JudgeEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: tecla del evento rapido (R/G/C/P) o ESPACIO estando empalado. */
public class JudgeInputPacket {
    public static final int QTE_KEY = 0;
    public static final int SPACE = 1;

    private final int type;
    private final int value;

    public JudgeInputPacket(int type, int value) {
        this.type = type;
        this.value = value;
    }

    public static void encode(JudgeInputPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.type);
        buf.writeVarInt(msg.value);
    }

    public static JudgeInputPacket decode(FriendlyByteBuf buf) {
        return new JudgeInputPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(JudgeInputPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (msg.type == QTE_KEY) {
                JudgeEntity.onQteKey(player, msg.value);
            } else if (msg.type == SPACE) {
                JudgeEntity.onSpacePress(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
