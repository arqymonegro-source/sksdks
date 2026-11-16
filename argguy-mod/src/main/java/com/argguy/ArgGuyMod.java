package com.argguy;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class ArgGuyMod implements ModInitializer {
    public static final String MOD_ID = "argguy";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    // ---------------------------------------------------------------- entity
    public static final ResourceKey<EntityType<?>> ARGGUY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("argguy"));
    public static final EntityType<ArgGuyEntity> ARGGUY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ARGGUY_KEY,
            EntityType.Builder.of(ArgGuyEntity::new, MobCategory.MONSTER)
                    .sized(0.75F, 2.85F)
                    .eyeHeight(2.6F)
                    .clientTrackingRange(10)
                    .build(ARGGUY_KEY));

    // ---------------------------------------------------------------- items (the three channels + the ending item)
    public static final Item TAPE_AZUL = item("tape_azul", p -> new LoreTapeItem(p, "azul"));
    public static final Item TAPE_FRIENDS = item("tape_friends", p -> new LoreTapeItem(p, "friends"));
    public static final Item TAPE_RESEARCHERS = item("tape_researchers", p -> new LoreTapeItem(p, "researchers"));
    public static final Item DEMO_RECORDER = item("demo_recorder", DemoRecorderItem::new);
    public static final Item CONTAINMENT_DRIVE = item("containment_drive", ContainmentDriveItem::new);

    private static Item item(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key).stacksTo(1)));
    }

    @Override
    public void onInitialize() {
        ModSounds.init();
        FabricDefaultAttributeRegistry.register(ARGGUY, ArgGuyEntity.createAttributes());

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(TAPE_AZUL);
            entries.accept(TAPE_FRIENDS);
            entries.accept(TAPE_RESEARCHERS);
            entries.accept(DEMO_RECORDER);
            entries.accept(CONTAINMENT_DRIVE);
        });

        ServerTickEvents.END_SERVER_TICK.register(RedRoom::tick);

        // TODO: restrict these to operators (the permission API differs between versions:
        // .requires(src -> src.hasPermission(2)) on older ones). Left open so you can test in singleplayer.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("argguy")
                        .then(Commands.literal("summon").executes(ctx -> summon(ctx.getSource())))
                        .then(Commands.literal("hunt").executes(ctx -> hunt(ctx.getSource())))
                        .then(Commands.literal("ending").executes(ctx -> ending(ctx.getSource())))
                        .then(Commands.literal("room").executes(ctx -> room(ctx.getSource())))
                        .then(Commands.literal("exit").executes(ctx -> exit(ctx.getSource())))));
    }

    // ---------------------------------------------------------------- commands
    private static int summon(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ArgGuyEntity.spawnNear(src.getLevel(), p, 24, 36);
        src.sendSuccess(() -> Component.literal("...it is watching."), false);
        return 1;
    }

    private static ArgGuyEntity nearest(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = src.getLevel();
        List<ArgGuyEntity> list = level.getEntitiesOfClass(ArgGuyEntity.class, p.getBoundingBox().inflate(160));
        return list.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
    }

    private static int hunt(CommandSourceStack src) throws CommandSyntaxException {
        ArgGuyEntity e = nearest(src);
        if (e == null) { src.sendFailure(Component.literal("No ARGGUY nearby.")); return 0; }
        e.forceHunt();
        return 1;
    }

    private static int ending(CommandSourceStack src) throws CommandSyntaxException {
        ArgGuyEntity e = nearest(src);
        if (e == null) { src.sendFailure(Component.literal("No ARGGUY nearby.")); return 0; }
        e.forceEscape(src.getPlayerOrException());
        return 1;
    }

    private static int room(CommandSourceStack src) throws CommandSyntaxException {
        RedRoom.enter(src.getPlayerOrException());
        return 1;
    }

    private static int exit(CommandSourceStack src) throws CommandSyntaxException {
        RedRoom.exit(src.getPlayerOrException().getUUID(), src.getServer());
        return 1;
    }
}
