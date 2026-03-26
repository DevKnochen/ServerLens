/*
 * Copyright 2026 DevKnochen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.devknochen.serverlens.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import de.devknochen.serverlens.Main;
import de.devknochen.serverlens.ServerDataUpdater;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;

@Mixin(DirectJoinServerScreen.class)
public abstract class DirectConnectScreenMixin extends Screen implements ServerDataUpdater {

    private static final Identifier PING_1 = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/ping_1.png");
    private static final Identifier PING_2 = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/ping_2.png");
    private static final Identifier PING_3 = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/ping_3.png");
    private static final Identifier PING_4 = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/ping_4.png");
    private static final Identifier PING_5 = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/ping_5.png");
    private static final Identifier PINGING_1 = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/pinging_1.png");
    private static final Identifier PINGING_2 = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/pinging_2.png");
    private static final Identifier PINGING_3 = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/pinging_3.png");
    private static final Identifier PINGING_4 = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/pinging_4.png");
    private static final Identifier PINGING_5 = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/pinging_5.png");
    private static final Identifier UNREACHABLE = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/unreachable.png");
    private static final Identifier INCOMPATIBLE = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/incompatible.png");
    private static final Identifier DEFAULT_ICON = Identifier.fromNamespaceAndPath("serverlens", "gui/serverlist/default_icon.png");

    @Shadow
    private EditBox ipEdit;

    private String lastAddress = "";
    private String serverName = "";
    private Component motdText = Component.empty();
    private String playerCount = "";
    private long pingValue = -1L;
    private ServerData.State serverState = ServerData.State.INITIAL;
    private FaviconTexture serverIcon = null;
    private byte[] lastFavicon = null;

    protected DirectConnectScreenMixin(Component title) {
        super(title);
    }

    @Override
    public void updateServerData(String name, Component motd, String players, long ping, ServerData.State state) {
        this.serverName = name != null ? name : "";
        this.motdText = motd != null ? motd : Component.empty();
        this.playerCount = players != null ? players : "";
        this.pingValue = ping;
        this.serverState = state != null ? state : ServerData.State.INITIAL;
    }

    @Override
    public void updateFavicon(byte[] faviconBytes) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            client.execute(() -> updateFavicon(faviconBytes));
            return;
        }

        if (faviconBytes == null) {
            if (serverIcon != null) {
                serverIcon.close();
                serverIcon = null;
            }
            lastFavicon = null;
            return;
        }

        if (lastFavicon != null && Arrays.equals(faviconBytes, lastFavicon)) {
            return;
        }
        lastFavicon = faviconBytes;

        byte[] valid = ServerData.validateIcon(faviconBytes);
        if (valid == null) {
            return;
        }

        try {
            if (serverIcon != null) {
                serverIcon.close();
            }

            String idSource = lastAddress != null && !lastAddress.isBlank() ? lastAddress : "directconnectmotd";
            serverIcon = FaviconTexture.forServer(client.getTextureManager(), idSource);
            serverIcon.upload(NativeImage.read(valid));
        } catch (Exception e) {
            if (serverIcon != null) {
                serverIcon.close();
                serverIcon = null;
            }
            if (Main.debugingMode) {
                e.printStackTrace();
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderExtras(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ipEdit != null) {
            String address = ipEdit.getValue();
            if (address != null && !address.isBlank() && !address.equals(lastAddress)) {
                lastAddress = address;
                serverName = address;
                motdText = Component.translatable("multiplayer.status.pinging");
                playerCount = "";
                pingValue = -1L;
                serverState = ServerData.State.PINGING;
                updateFavicon(null);
                Main.onAddressBarUpdate(address);
            }
        }

        int maxRowWidth = 305;
        int minRowWidth = 200;
        int sidePadding = 20;
        int rowWidth = Math.min(maxRowWidth, Math.max(minRowWidth, this.width - sidePadding * 2));
        int baseX = (this.width - rowWidth) / 2;
        int baseY = ipEdit != null ? ipEdit.getY() + ipEdit.getHeight() + 8 : this.height / 2 + 30;
        int iconSize = 32;

        if (serverIcon != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, serverIcon.textureLocation(), baseX, baseY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
        } else {
            context.blit(RenderPipelines.GUI_TEXTURED, DEFAULT_ICON, baseX, baseY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
        }

        Font font = this.font;
        int textX = baseX + iconSize + 3;
        context.text(font, Component.literal(serverName), textX, baseY, 0xFFFFFFFF, true);

        if (motdText != null && !motdText.getString().isEmpty()) {
            int motdY = baseY + 12;
            int availableWidth = rowWidth - iconSize - 10;
            List<FormattedCharSequence> lines = font.split(motdText, availableWidth);
            for (FormattedCharSequence line : lines) {
                context.text(font, line, textX, motdY, 0xFFFFFFFF, true);
                motdY += font.lineHeight;
            }
        }

        Identifier pingTexture = getPingTexture();
        int pingX = baseX + rowWidth - 15;
        int pingWidth = 10;
        int pingHeight = 8;
        context.blit(RenderPipelines.GUI_TEXTURED, pingTexture, pingX, baseY, 0.0F, 0.0F, pingWidth, pingHeight, pingWidth, pingHeight);

        if (serverState == ServerData.State.SUCCESSFUL && !playerCount.isBlank() && playerCount.contains("/")) {
            String[] parts = playerCount.split("/", 2);
            String players = parts[0];
            String maxPlayers = parts.length > 1 ? parts[1] : "0";
            String slash = "/";

            int playersWidth = font.width(players);
            int slashWidth = font.width(slash);
            int maxPlayersWidth = font.width(maxPlayers);
            int playerTextX = pingX - (playersWidth + slashWidth + maxPlayersWidth) - 5;

            context.text(font, Component.literal(players), playerTextX, baseY, 0xFFAAAAAA, true);
            context.text(font, Component.literal(slash), playerTextX + playersWidth, baseY, 0xFF555555, true);
            context.text(font, Component.literal(maxPlayers), playerTextX + playersWidth + slashWidth, baseY, 0xFFAAAAAA, true);
        }
    }

    private Identifier getPingTexture() {
        if (serverState == ServerData.State.UNREACHABLE) {
            return UNREACHABLE;
        }
        if (serverState == ServerData.State.INCOMPATIBLE) {
            return INCOMPATIBLE;
        }
        if (serverState == ServerData.State.PINGING || pingValue < 0L) {
            long tick = System.currentTimeMillis() / 100L;
            int frame = (int) (tick % 8);
            if (frame > 4) {
                frame = 8 - frame;
            }

            return switch (frame) {
                case 1 -> PINGING_2;
                case 2 -> PINGING_3;
                case 3 -> PINGING_4;
                case 4 -> PINGING_5;
                default -> PINGING_1;
            };
        }
        if (pingValue < 150L) {
            return PING_5;
        }
        if (pingValue < 300L) {
            return PING_4;
        }
        if (pingValue < 600L) {
            return PING_3;
        }
        if (pingValue < 1000L) {
            return PING_2;
        }
        return PING_1;
    }
}
