/*
 * Copyright 2025 Hugo Steiner
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

import de.devknochen.serverlens.Main;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.screen.world.WorldIcon;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.devknochen.serverlens.ServerDataUpdater;

import java.util.List;

@Mixin(DirectConnectScreen.class)
public abstract class DirectConnectScreenMixin extends Screen implements ServerDataUpdater {

    protected DirectConnectScreenMixin(Text title) {
        super(title);
    }

    // --- Textures ---
    private static final Identifier PING_1 = Identifier.of("serverlens", "gui/serverlist/ping_1.png");
    private static final Identifier PING_2 = Identifier.of("serverlens", "gui/serverlist/ping_2.png");
    private static final Identifier PING_3 = Identifier.of("serverlens", "gui/serverlist/ping_3.png");
    private static final Identifier PING_4 = Identifier.of("serverlens", "gui/serverlist/ping_4.png");
    private static final Identifier PING_5 = Identifier.of("serverlens", "gui/serverlist/ping_5.png");
    private static final Identifier PINGING_1 = Identifier.of("serverlens", "gui/serverlist/pinging_1.png");
    private static final Identifier PINGING_2 = Identifier.of("serverlens", "gui/serverlist/pinging_2.png");
    private static final Identifier PINGING_3 = Identifier.of("serverlens", "gui/serverlist/pinging_3.png");
    private static final Identifier PINGING_4 = Identifier.of("serverlens", "gui/serverlist/pinging_4.png");
    private static final Identifier PINGING_5 = Identifier.of("serverlens", "gui/serverlist/pinging_5.png");
    private static final Identifier UNREACHABLE = Identifier.of("serverlens", "gui/serverlist/unreachable.png");
    private static final Identifier INCOMPATIBLE = Identifier.of("serverlens", "gui/serverlist/incompatible.png");

    @Shadow
    private TextFieldWidget addressField;

    private String lastAddress = "";

    // --- Server data fields ---
    private String serverName = "";
    private Text motdText = Text.empty();
    private String playerCount = "0/0";
    private long pingValue = -1;

    // --- Favicon ---
    private WorldIcon serverIcon = null;
    private byte[] lastFavicon = null;

    @Override
    public void updateServerData(String name, String[] motd, String players, long ping) {
        this.serverName = name != null ? name : "";
        this.playerCount = players != null ? players : "0/0";
        this.pingValue = ping;
        // Do NOT set motdText here; we will set it via setMotdText(Text) directly
    }

    // Setter to accept the formatted MOTD Text from Main
    public void setMotdText(Text motd) {
        this.motdText = motd != null ? motd : Text.empty();
    }

    @Override
    public void updateFavicon(byte[] faviconBytes) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) {
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

        if (lastFavicon != null && java.util.Arrays.equals(faviconBytes, lastFavicon)) {
            return; // already loaded
        }
        lastFavicon = faviconBytes;

        byte[] valid = net.minecraft.client.network.ServerInfo.validateFavicon(faviconBytes);
        if (valid == null) {
            System.out.println("Invalid favicon received.");
            return;
        }

        try {
            if (serverIcon != null) {
                serverIcon.close();
                serverIcon = null;
            }

            String idSource = lastAddress != null && !lastAddress.isBlank() ? lastAddress : "directconnectmotd";
            serverIcon = WorldIcon.forServer(client.getTextureManager(), idSource);

            NativeImage img = NativeImage.read(valid);
            serverIcon.load(img);
            if (Main.debugingMode) System.out.println("Favicon successfully loaded!");
        } catch (Exception e) {
            if (serverIcon != null) {
                serverIcon.close();
                serverIcon = null;
            }
            if (Main.debugingMode) System.out.println("Failed to load favicon: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderExtras(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // detect address changes and trigger ping
        if (addressField != null) {
            String address = addressField.getText();
            if (address != null && !address.isBlank() && !address.equals(lastAddress)) {
                lastAddress = address;
                de.devknochen.serverlens.Main.onAddressBarUpdate(address);
            }
        }

        // dynamic layout positions
        int maxRowWidth = 305;
        int minRowWidth = 200;
        int sidePadding = 20; // left+right padding
        int rowWidth = Math.min(maxRowWidth, Math.max(minRowWidth, this.width - sidePadding * 2));
        int baseX = (this.width - rowWidth) / 2;

        // Y: place just under the address field if available, otherwise fallback
        int baseY = (addressField != null)
                ? addressField.getY() + addressField.getHeight() + 8
                : this.height / 2 + 30;

        int iconSize = 32;

        // --- Favicon ---
        if (serverIcon != null) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, serverIcon.getTextureId(),
                    baseX, baseY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        } else {
            Identifier fallbackIcon = Identifier.of("serverlens", "gui/serverlist/default_icon.png");
            context.drawTexture(RenderPipelines.GUI_TEXTURED, fallbackIcon,
                    baseX, baseY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        }

        int textX = baseX + iconSize + 3;
        context.drawTextWithShadow(this.textRenderer, Text.literal(serverName), textX, baseY, 0xFFFFFFFF);

        // --- MOTD ---
        if (motdText != null && !motdText.getString().isEmpty()) {
            int motdY = baseY + 12;
            int lineHeight = this.textRenderer.fontHeight;
            int availableWidth = rowWidth - iconSize - 10; // vanilla-style width
            List<OrderedText> lines = this.textRenderer.wrapLines(motdText, availableWidth);
            for (OrderedText line : lines) {
                context.drawTextWithShadow(this.textRenderer, line, textX, motdY, 0xFFFFFFFF);
                motdY += lineHeight;
            }
        }

        // --- Ping icon ---
        Identifier pingTexture;
        if (pingValue < 0) {
            // Animate ping bars while server is being pinged
            long tick = System.currentTimeMillis() / 100L;
            int frame = (int) (tick % 8);
            if (frame > 4) frame = 8 - frame;

            switch (frame) {
                case 1 -> pingTexture = PINGING_2;
                case 2 -> pingTexture = PINGING_3;
                case 3 -> pingTexture = PINGING_4;
                case 4 -> pingTexture = PINGING_5;
                default -> pingTexture = PINGING_1;
            }
        } else if (pingValue < 150L) pingTexture = PING_5;
        else if (pingValue < 300L) pingTexture = PING_4;
        else if (pingValue < 600L) pingTexture = PING_3;
        else if (pingValue < 1000L) pingTexture = PING_2;
        else pingTexture = PING_1;

        // use vanilla-like offset: entryWidth - 10 - 5
        int pingX = baseX + rowWidth - 10 - 5;
        int pingWidth = 10;
        int pingHeight = 8;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, pingTexture, pingX, baseY,
                0, 0, pingWidth, pingHeight, pingWidth, pingHeight);

        // --- Player count ---
        if (pingValue >= 0) { // only show player count when ping is valid
            String players = playerCount.contains("/") ? playerCount.split("/")[0] : playerCount;
            String maxPlayers = playerCount.contains("/") ? playerCount.split("/")[1] : "0";
            String slash = "/";

            int playersWidth = this.textRenderer.getWidth(players);
            int slashWidth = this.textRenderer.getWidth(slash);
            int maxPlayersWidth = this.textRenderer.getWidth(maxPlayers);

            // position like vanilla: i - j - 5 (where i is pingX)
            int playerTextX = pingX - (playersWidth + slashWidth + maxPlayersWidth) - 5;

            context.drawTextWithShadow(this.textRenderer, Text.literal(players),
                    playerTextX, baseY, 0xFFAAAAAA);
            context.drawTextWithShadow(this.textRenderer, Text.literal(slash),
                    playerTextX + playersWidth, baseY, 0xFF555555);
            context.drawTextWithShadow(this.textRenderer, Text.literal(maxPlayers),
                    playerTextX + playersWidth + slashWidth, baseY, 0xFFAAAAAA);
        }
    }

}
