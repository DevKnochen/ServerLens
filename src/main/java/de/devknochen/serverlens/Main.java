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

package de.devknochen.serverlens;

import de.devknochen.serverlens.logic.DirectConnectLogic;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class Main implements ClientModInitializer {

    private boolean hasListener = false;

    // --- Debounce variables ---
    private static String lastAddress = "";
    private static long lastPingTime = 0;
    private static final long PING_DEBOUNCE_MS = 500; // 0.5s delay
    public static boolean debugingMode = false; // made static for static access

    @Override
    public void onInitializeClient() {
        System.out.println("ServerLens initialized!");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (hasListener && !(client.currentScreen instanceof net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen)) {
                hasListener = false;
            }
        });
    }

    // Called from Mixin when the address field changes
    public static void onAddressBarUpdate(String address) {
        if (address == null || address.isBlank()) return;

        long now = System.currentTimeMillis();
        if (address.equals(lastAddress) && now - lastPingTime < PING_DEBOUNCE_MS) {
            return;
        }

        lastAddress = address;
        lastPingTime = now;

        new Thread(() -> {
            DirectConnectLogic.pingServer(address, serverInfo -> {
                if (serverInfo != null) {
                    if (debugingMode) {
                        System.out.println("=== Server Ping Result ===");
                        System.out.println("MOTD: " + (serverInfo.label != null ? serverInfo.label.getString() : "N/A"));
                        System.out.println("Players: " + (serverInfo.playerCountLabel != null ? serverInfo.playerCountLabel.getString() : "0/0"));
                        System.out.println("Ping: " + serverInfo.ping);
                        System.out.println("Version: " + (serverInfo.version != null ? serverInfo.version.getString() : "Unknown"));
                    }

                    MinecraftClient client = MinecraftClient.getInstance();
                    client.execute(() -> {
                        Screen screen = client.currentScreen;
                        if (screen instanceof ServerDataUpdater updater) {
                            // Set the MOTD as a Text object (keeps colors)
                            if (serverInfo.label != null) {
                                updater.setMotdText(serverInfo.label);
                            } else {
                                updater.setMotdText(Text.empty());
                            }

                            // Keep string-based data for other fields
                            String[] motdLines = serverInfo.label != null ? serverInfo.label.getString().split("\n") : new String[]{""};
                            updater.updateServerData(
                                    serverInfo.name != null ? serverInfo.name : "",
                                    motdLines,
                                    serverInfo.playerCountLabel != null ? serverInfo.playerCountLabel.getString() : "0/0",
                                    serverInfo.ping
                            );

                            updater.updateFavicon(serverInfo.getFavicon());
                        }
                    });

                } else {
                    if (debugingMode) System.out.println("=== Server Ping Failed ===");
                }
            });
        }).start();
    }
}
