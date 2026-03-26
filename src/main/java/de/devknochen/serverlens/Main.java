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

package de.devknochen.serverlens;

import de.devknochen.serverlens.logic.DirectConnectLogic;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;

public class Main implements ClientModInitializer {

    private static String lastAddress = "";
    private static long lastPingTime = 0;
    private static final long PING_DEBOUNCE_MS = 500;
    public static boolean debugingMode = false;

    @Override
    public void onInitializeClient() {
        System.out.println("ServerLens initialized!");
    }
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
                    ServerStatus.Players players = serverInfo.players;
                    String playerCount = players != null ? players.online() + "/" + players.max() : "";

                    if (debugingMode) {
                        System.out.println("=== Server Ping Result ===");
                        System.out.println("MOTD: " + (serverInfo.motd != null ? serverInfo.motd.getString() : "N/A"));
                        System.out.println("Players: " + (!playerCount.isEmpty() ? playerCount : "0/0"));
                        System.out.println("Ping: " + serverInfo.ping);
                        System.out.println("Version: " + (serverInfo.version != null ? serverInfo.version.getString() : "Unknown"));
                        System.out.println("State: " + serverInfo.state());
                    }

                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> {
                        Screen screen = client.screen;
                        if (screen instanceof ServerDataUpdater updater) {
                            updater.updateServerData(
                                    serverInfo.name != null ? serverInfo.name : "",
                                    serverInfo.motd != null ? serverInfo.motd : Component.empty(),
                                    playerCount,
                                    serverInfo.ping,
                                    serverInfo.state()
                            );

                            updater.updateFavicon(serverInfo.getIconBytes());
                        }
                    });

                } else {
                    if (debugingMode) System.out.println("=== Server Ping Failed ===");
                }
            });
        }, "ServerLens-Pinger").start();
    }
}
