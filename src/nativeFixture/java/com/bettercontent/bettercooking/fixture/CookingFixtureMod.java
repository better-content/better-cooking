package com.bettercontent.bettercooking.fixture;

import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Path;

/** Nonshipping fixture only. A real connected client is required for the nutrient-bearing snapshot. */
@Mod("better_cooking_fixture")
public final class CookingFixtureMod {
    public CookingFixtureMod(){MinecraftForge.EVENT_BUS.addListener(CookingFixtureMod::commands);}
    private static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("cooking-fixture").requires(source->source.hasPermission(4))
            .then(Commands.literal("catalogue").executes(context->{
                ServerPlayer player=context.getSource().getEntity() instanceof ServerPlayer actor?actor:
                    context.getSource().getServer().getPlayerList().getPlayers().stream().filter(p->!(p instanceof FakePlayer)&&p.connection!=null).findFirst().orElse(null);
                if(player==null||player instanceof FakePlayer||player.connection==null)throw new SimpleCommandExceptionType(Component.literal("Catalogue needs an actual connected client, not FakePlayer")).create();
                String target=System.getProperty("better_cooking.nativeCatalogueOutput","");
                if(target.isEmpty())throw new SimpleCommandExceptionType(Component.literal("Missing explicit native catalogue output path")).create();
                try{NativeCatalogueCollector.collect(context.getSource().getServer(),player,Path.of(target));}
                catch(Exception error){
                    LogUtils.getLogger().error("Native catalogue discovery failed (actor={}, output={})",player.getUUID(),target,error);
                    throw new SimpleCommandExceptionType(Component.literal(error.toString())).create();
                }
                context.getSource().sendSuccess(()->Component.literal("Discovery snapshot written; this is NOT culinary qualification"),true);return 1;
            })));
    }
}
