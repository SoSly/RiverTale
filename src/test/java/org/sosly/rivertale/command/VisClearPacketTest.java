package org.sosly.rivertale.command;

import java.util.List;
import java.util.Set;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.client.ClientRegionCache;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.RegionType;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisClearPacketTest {
    @Test
    void clearsTheClientRegionCacheWithoutChangingEnabledModes() {
        ClientRegionCache.init();
        try {
            ClientRegionCache.setEnabledModes(Set.of(VisMode.CELLS));
            ClientRegionCache cache = ClientRegionCache.get();
            cache.put(new ClientRegionCache.Region(new RegionPos(0, 0), RegionType.OCEANIC,
                List.of(), List.of(), List.of(), List.of(), List.of()));
            NetworkEvent.Context context = mock(NetworkEvent.Context.class);
            when(context.getDirection()).thenReturn(NetworkDirection.PLAY_TO_CLIENT);
            doAnswer(call -> {
                call.getArgument(0, Runnable.class).run();
                return null;
            }).when(context).enqueueWork(any(Runnable.class));

            VisCommand.ClearPacket.handle(new VisCommand.ClearPacket(), () -> context);

            assertTrue(cache.getRegions().isEmpty());
            assertTrue(ClientRegionCache.isModeEnabled(VisMode.CELLS));
        } finally {
            ClientRegionCache.shutdown();
        }
    }
}
