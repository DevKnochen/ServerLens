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

package de.devknochen.serverlens.logic;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo.Status;
import net.minecraft.text.Text;

import java.net.UnknownHostException;

public class DirectConnectLogic {

    public interface PingCallback {
        void onFinished(ServerInfo serverInfo);
    }

    public static void pingServer(String address, PingCallback callback) {
        ServerInfo serverInfo = new ServerInfo(address, address, ServerInfo.ServerType.OTHER);
        MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();

        try {
            pinger.add(serverInfo, () -> {}, () -> {
                // When ping finishes, call callback
                callback.onFinished(serverInfo);
            });
        } catch (UnknownHostException e) {
            serverInfo.label = Text.literal("Unknown host");
            serverInfo.playerCountLabel = Text.literal("0/0");
            serverInfo.setStatus(Status.UNREACHABLE);
            callback.onFinished(serverInfo);
        }
    }
}
