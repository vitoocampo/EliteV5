package com.elitebosses.net;

import com.elitebosses.EliteBosses;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EliteBosses.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, HoundMusicPacket.class, HoundMusicPacket::encode, HoundMusicPacket::decode, HoundMusicPacket::handle);
        CHANNEL.registerMessage(id++, HoundGrabPacket.class, HoundGrabPacket::encode, HoundGrabPacket::decode, HoundGrabPacket::handle);
        CHANNEL.registerMessage(id++, HoundEscapePacket.class, HoundEscapePacket::encode, HoundEscapePacket::decode, HoundEscapePacket::handle);
        CHANNEL.registerMessage(id++, JudgeHudPacket.class, JudgeHudPacket::encode, JudgeHudPacket::decode, JudgeHudPacket::handle);
        CHANNEL.registerMessage(id++, JudgeInputPacket.class, JudgeInputPacket::encode, JudgeInputPacket::decode, JudgeInputPacket::handle);
    }
}
