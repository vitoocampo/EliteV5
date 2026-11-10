package com.elitebosses.net;

import com.elitebosses.client.JudgeClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> cliente: todo lo que el jugador ve/oye del Juicio (contador, avisos, eventos rapidos...).
 * Un solo paquete con (tipo, a, b) para no registrar diez.
 */
public class JudgeHudPacket {
    /** a = muertes, b = ticks visibles (0 = solo actualiza, -1 = ocultar). */
    public static final int COUNTER = 0;
    /** a = muertes, b = ticks: contador fijo al centro + "Tus dias estan contados". */
    public static final int DOOM = 1;
    /** a = bloques hasta el Juicio (negativo = ocultar). */
    public static final int DISTANCE = 2;
    /** a = tecla (0 R, 1 G, 2 C, 3 P), b = ticks de ventana. */
    public static final int QTE_START = 3;
    /** a = 1 acierto / 0 fallo. */
    public static final int QTE_END = 4;
    /** a = pulsaciones de ESPACIO que faltan (negativo = liberado). */
    public static final int IMPALE = 5;
    /** a = ticks que no puedes levantarte de la cama. */
    public static final int BED_LOCK = 6;
    /** a = ticks del letrero "COBARDE". */
    public static final int COWARD = 7;
    /** a = sonido (1 JudgeWarn1, 2 JudgeWarn2, 3 JudgeStalk, 4 JudgeSpawn, 0 = detener). */
    public static final int SOUND = 8;
    /** Limpia todo el HUD del Juicio. */
    public static final int CLEAR = 9;

    private final int type;
    private final int a;
    private final int b;

    public JudgeHudPacket(int type, int a, int b) {
        this.type = type;
        this.a = a;
        this.b = b;
    }

    public static void encode(JudgeHudPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.type);
        buf.writeInt(msg.a);
        buf.writeInt(msg.b);
    }

    public static JudgeHudPacket decode(FriendlyByteBuf buf) {
        return new JudgeHudPacket(buf.readVarInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(JudgeHudPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> JudgeClient.handle(msg.type, msg.a, msg.b)));
        ctx.get().setPacketHandled(true);
    }
}
