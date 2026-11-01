package com.bettercontent.bettercooking.kitchen;

import com.bettercontent.bettercooking.CookingMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;

public final class KitchenNetwork {
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(CookingMod.MOD_ID, "kitchen"), () -> VERSION, VERSION::equals, VERSION::equals);
    private record Pulse(int window, ResourceLocation recipe) {
        static void encode(Pulse packet, FriendlyByteBuf buf) { buf.writeVarInt(packet.window); buf.writeResourceLocation(packet.recipe); }
        static Pulse decode(FriendlyByteBuf buf) { return new Pulse(buf.readVarInt(), buf.readResourceLocation()); }
        static void handle(Pulse packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            ctx.enqueueWork(() -> {
                var player = ctx.getSender();
                if (player != null && player.containerMenu instanceof KitchenMenu menu && menu.kitchen() != null) menu.kitchen().pulse(player, packet.window, packet.recipe);
            });
            ctx.setPacketHandled(true);
        }
    }
    private record Fill(int window, ResourceLocation recipe) {
        static void encode(Fill packet, FriendlyByteBuf buf) { buf.writeVarInt(packet.window); buf.writeResourceLocation(packet.recipe); }
        static Fill decode(FriendlyByteBuf buf) { return new Fill(buf.readVarInt(), buf.readResourceLocation()); }
        static void handle(Fill packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            ctx.enqueueWork(() -> {
                var player = ctx.getSender();
                if (player != null && player.containerMenu instanceof KitchenMenu menu) menu.transfer(player, packet.recipe, packet.window);
            });
            ctx.setPacketHandled(true);
        }
    }
    private record ChooseCut(int window, ResourceLocation recipe) {
        static void encode(ChooseCut packet, FriendlyByteBuf buf) { buf.writeVarInt(packet.window); buf.writeResourceLocation(packet.recipe); }
        static ChooseCut decode(FriendlyByteBuf buf) { return new ChooseCut(buf.readVarInt(), buf.readResourceLocation()); }
        static void handle(ChooseCut packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            ctx.enqueueWork(() -> {
                var player = ctx.getSender();
                if (player != null && player.containerMenu instanceof KitchenMenu menu) menu.chooseNativeCut(player, packet.recipe, packet.window);
            });
            ctx.setPacketHandled(true);
        }
    }
    private KitchenNetwork() {}
    static void register() {
        CHANNEL.messageBuilder(Pulse.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder(Pulse::encode).decoder(Pulse::decode).consumerNetworkThread(Pulse::handle).add();
        CHANNEL.messageBuilder(Fill.class, 1, NetworkDirection.PLAY_TO_SERVER).encoder(Fill::encode).decoder(Fill::decode).consumerNetworkThread(Fill::handle).add();
        CHANNEL.messageBuilder(ChooseCut.class, 2, NetworkDirection.PLAY_TO_SERVER).encoder(ChooseCut::encode).decoder(ChooseCut::decode).consumerNetworkThread(ChooseCut::handle).add();
    }
    public static void pulse(int window, ResourceLocation recipe) { CHANNEL.sendToServer(new Pulse(window, recipe)); }
    public static void fill(int window, ResourceLocation recipe) { CHANNEL.sendToServer(new Fill(window, recipe)); }
    public static void chooseCut(int window, ResourceLocation recipe) { CHANNEL.sendToServer(new ChooseCut(window, recipe)); }
}
