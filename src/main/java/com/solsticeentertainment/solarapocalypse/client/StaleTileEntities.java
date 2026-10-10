package com.solsticeentertainment.solarapocalypse.client;

import com.solsticeentertainment.solarapocalypse.SolarApocalypse;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Client only, Cubic Chunks worlds. A cube the server resends whole (64 or more changes in a tick, as erosion makes)
 * replaces the client's blocks but keeps its tile entities, even where the block is gone (Cubic Chunks 0.0.1271
 * PacketCubes; vanilla's chunk packets drop them): a destroyed spawner kept making its flames. Once a second, tile
 * entities whose block no longer has one are removed.
 */
@SideOnly(Side.CLIENT)
public final class StaleTileEntities {

    private static int ticks;

    private StaleTileEntities() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        WorldClient world = Minecraft.getMinecraft().world;
        if (event.phase != TickEvent.Phase.END || world == null || ++ticks % 20 != 0 || !SolarApocalypse.isCubic(world)) return;
        List<BlockPos> stale = new ArrayList<>();
        for (TileEntity te : world.loadedTileEntityList) {
            IBlockState state = world.getBlockState(te.getPos());
            if (!te.isInvalid() && !state.getBlock().hasTileEntity(state)) stale.add(te.getPos());
        }
        for (BlockPos pos : stale) world.removeTileEntity(pos);
    }
}
