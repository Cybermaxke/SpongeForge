/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.mod.mixin.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOverlayDebug;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.interfaces.world.IMixinWorldInfo;
import org.spongepowered.common.registry.type.world.WorldPropertyRegistryModule;
import org.spongepowered.common.world.storage.SpongePlayerDataHandler;
import org.spongepowered.mod.client.interfaces.IMixinMinecraft;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft implements IMixinMinecraft {

    private static final String LOAD_WORLD = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V";
    private static final String ENTITY_PLAYER_PREPARE_TO_SPAWN = "Lnet/minecraft/client/entity/EntityPlayerSP;preparePlayerToSpawn()V";
    private static final String SAVE_HANDLER_SAVE_WORLD_INFO =
            "Lnet/minecraft/world/storage/ISaveHandler;saveWorldInfo(Lnet/minecraft/world/storage/WorldInfo;)V";
    private static final String FORGE_TRANSFORMER_EXIT_VISITOR =
            "Lnet/minecraftforge/fml/common/asm/transformers/TerminalTransformer$ExitVisitor;systemExitCalled(I)V";
    private GuiOverlayDebug debugGui;

    @Group(name = "launchIntegratedServer", min = 1, max = 1)
    @Inject(method = "launchIntegratedServer", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/server/integrated/IntegratedServer;startServerThread()V", remap = false, shift = At.Shift.BEFORE),
            @At(value = "INVOKE", target = "Lnet/minecraft/server/integrated/IntegratedServer;func_71256_s()V", remap = false, shift = At.Shift.BEFORE),
    }, locals = LocalCapture.CAPTURE_FAILHARD)
    public void onlaunchIntegratedServerBeforeStart(String folderName, String worldName, WorldSettings worldSettingsIn, CallbackInfo ci,
            ISaveHandler isavehandler, WorldInfo worldInfo) {
        WorldPropertyRegistryModule.getInstance().registerWorldProperties((WorldProperties) worldInfo);
    }

    @Inject(method = "launchIntegratedServer", at = @At(value = "INVOKE", target = SAVE_HANDLER_SAVE_WORLD_INFO, shift = At.Shift.AFTER) , locals = LocalCapture.CAPTURE_FAILHARD)
    public void onlaunchIntegratedServerAfterSaveWorldInfo(String folderName, String worldName, WorldSettings worldSettingsIn, CallbackInfo ci,
            ISaveHandler isavehandler, WorldInfo worldInfo) {
        // initialize overworld properties
        UUID uuid = UUID.randomUUID();
        ((IMixinWorldInfo) worldInfo).setUUID(uuid);
    }

    @Override
    public void setDebugGui(GuiOverlayDebug debugGui) {
        this.debugGui = debugGui;
    }

    @Override
    public GuiOverlayDebug getDebugGui() {
        return this.debugGui;
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At(value = "INVOKE", target = FORGE_TRANSFORMER_EXIT_VISITOR, remap = false))
    public void onShutdownDelegate(CallbackInfo ci) {
        SpongeImpl.postShutdownEvents();
    }

    /**
     * This will inject at the moment before the single player instance is
     * spawning the EntityPlayerSP client into the world. This checks if
     * it is single player, and if so, attempts to retrieve if the player
     * has joined this world before, if not, it thinks that this is the
     * first time joining and creates the necessary data.
     *
     * @param client The client
     * @param name The world name
     * @param callbackInfo The necessary callback info
     */
    @Inject(method = LOAD_WORLD, at = @At(value = "INVOKE", target = ENTITY_PLAYER_PREPARE_TO_SPAWN, shift = At.Shift.AFTER))
    private void onSpawn(WorldClient client, String name, CallbackInfo callbackInfo) {
        try {
            if (MinecraftServer.getServer().isSinglePlayer()) {
                EntityPlayer player = Minecraft.getMinecraft().thePlayer;
                UUID uuid = player.getUniqueID();
                Optional<Instant> joined = SpongePlayerDataHandler.getFirstJoined(uuid);
                if (!joined.isPresent()) {
                    Instant newJoined = Instant.now();
                    SpongePlayerDataHandler.setPlayerInfo(uuid, newJoined, newJoined);
                }
            }
        } catch (Exception e) {
            SpongeImpl.getLogger().error("Could not retrieve the player instance or single player instance to get the join data.");
        }

    }
}
