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

package de.devknochen.serverlens.logic;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;

import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

public class DirectConnectLogic {

    public interface PingCallback {
        void onFinished(ServerData serverInfo);
    }

    public static void pingServer(String address, PingCallback callback) {
        ServerData serverInfo = new ServerData(address, address, ServerData.Type.OTHER);
        ServerStatusPinger pinger = new ServerStatusPinger();
        AtomicBoolean finished = new AtomicBoolean(false);

        serverInfo.ping = -1L;
        serverInfo.setState(ServerData.State.PINGING);

        try {
            pinger.pingServer(serverInfo, () -> {
            }, () -> {
                if (finished.compareAndSet(false, true)) {
                    callback.onFinished(serverInfo);
                }
            }, EventLoopGroupHolder.remote(true));

            while (!finished.get()) {
                pinger.tick();
                Thread.sleep(50L);
            }
        } catch (UnknownHostException e) {
            serverInfo.motd = Component.literal("Unknown host");
            serverInfo.status = Component.empty();
            serverInfo.setState(ServerData.State.UNREACHABLE);
            callback.onFinished(serverInfo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            serverInfo.motd = Component.literal("Ping cancelled");
            serverInfo.status = Component.empty();
            serverInfo.setState(ServerData.State.UNREACHABLE);
            callback.onFinished(serverInfo);
        } catch (Exception e) {
            serverInfo.motd = Component.literal("Cannot connect");
            serverInfo.status = Component.empty();
            serverInfo.setState(ServerData.State.UNREACHABLE);
            callback.onFinished(serverInfo);
        } finally {
            pinger.removeAll();
        }
    }
}
