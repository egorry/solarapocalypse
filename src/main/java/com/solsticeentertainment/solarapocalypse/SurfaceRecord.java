package com.solsticeentertainment.solarapocalypse;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Arrays;

/**
 * Per column (chunk) record of each x/z's terrain surface, taken the first time the column's top is known, for
 * world.depthReference = SURFACE where the surface cannot be computed from the generator. Saved with the column.
 */
public final class SurfaceRecord {

    public static final int NONE = Integer.MIN_VALUE;
    private static final ResourceLocation KEY = new ResourceLocation(Tags.MOD_ID, "surface");

    @CapabilityInject(SurfaceRecord.class)
    public static Capability<SurfaceRecord> CAPABILITY = null;

    private final int[] y = new int[256];

    public SurfaceRecord() {
        Arrays.fill(y, NONE);
    }

    public int get(int localX, int localZ) {
        return y[localZ << 4 | localX];
    }

    public void set(int localX, int localZ, int value) {
        y[localZ << 4 | localX] = value;
    }

    /** The record of a column, or null if it has none (inactive world). */
    public static SurfaceRecord of(Chunk column) {
        return CAPABILITY == null ? null : column.getCapability(CAPABILITY, null);
    }

    public static void register() {
        CapabilityManager.INSTANCE.register(SurfaceRecord.class, new Capability.IStorage<SurfaceRecord>() {
            @Override
            public NBTBase writeNBT(Capability<SurfaceRecord> capability, SurfaceRecord instance, EnumFacing side) {
                return instance.write();
            }

            @Override
            public void readNBT(Capability<SurfaceRecord> capability, SurfaceRecord instance, EnumFacing side, NBTBase nbt) {
                instance.read((NBTTagIntArray) nbt);
            }
        }, SurfaceRecord::new);
        MinecraftForge.EVENT_BUS.register(SurfaceRecord.class);
    }

    private NBTTagIntArray write() {
        for (int v : y) if (v != NONE) return new NBTTagIntArray(y.clone());
        return new NBTTagIntArray(new int[0]);
    }

    private void read(NBTTagIntArray nbt) {
        int[] data = nbt.getIntArray();
        if (data.length == y.length) System.arraycopy(data, 0, y, 0, y.length);
    }

    // Can run on Cubic Chunks' I/O thread: only creates the object.
    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Chunk> event) {
        World world = event.getObject().getWorld();
        if (world == null || world.isRemote || !SolarApocalypse.isActive(world)) return;
        SurfaceRecord record = new SurfaceRecord();
        event.addCapability(KEY, new ICapabilitySerializable<NBTTagIntArray>() {
            @Override
            public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
                return capability == CAPABILITY;
            }

            @Override
            public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
                return capability == CAPABILITY ? CAPABILITY.cast(record) : null;
            }

            @Override
            public NBTTagIntArray serializeNBT() {
                return record.write();
            }

            @Override
            public void deserializeNBT(NBTTagIntArray nbt) {
                record.read(nbt);
            }
        });
    }
}
