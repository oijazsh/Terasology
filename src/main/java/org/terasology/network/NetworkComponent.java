/*
 * Copyright 2013 Moving Blocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.network;

import org.terasology.entitySystem.Component;

/**
 * @author Immortius
 */
public class NetworkComponent implements Component {
    // Network identifier for the entity
    @Replicate
    private int networkId;

    public enum ReplicateMode {
        ALWAYS, // Always replicate this entity to all clients
        RELEVANT, // Replicate to client which this entity is relevant to (based on distance)
        OWNER; // Always replicate this entity to its owner
    }

    public ReplicateMode replicateMode = ReplicateMode.RELEVANT;

    public void setNetworkId(int networkId) {
        this.networkId = networkId;
    }

    public int getNetworkId() {
        return networkId;
    }
}
