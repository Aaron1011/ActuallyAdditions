/*
 * This file ("TileEntityMiner.java") is part of the Actually Additions mod for Minecraft.
 * It is created and owned by Ellpeck and distributed
 * under the Actually Additions License to be found at
 * http://ellpeck.de/actaddlicense
 * View the source code at https://github.com/Ellpeck/ActuallyAdditions
 *
 * © 2015-2016 Ellpeck
 */

package de.ellpeck.actuallyadditions.mod.tile;

import cofh.api.energy.EnergyStorage;
import de.ellpeck.actuallyadditions.mod.config.values.ConfigStringListValues;
import de.ellpeck.actuallyadditions.mod.items.ItemDrill;
import de.ellpeck.actuallyadditions.mod.network.gui.IButtonReactor;
import de.ellpeck.actuallyadditions.mod.util.AssetUtil;
import de.ellpeck.actuallyadditions.mod.util.WorldUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.oredict.OreDictionary;

import java.util.List;

public class TileEntityMiner extends TileEntityInventoryBase implements ICustomEnergyReceiver, IButtonReactor, IEnergyDisplay{

    public static final int ENERGY_USE_PER_BLOCK = 1500;
    public static final int DEFAULT_RANGE = 2;
    public final EnergyStorage storage = new EnergyStorage(200000);
    public int layerAt = -1;
    public boolean onlyMineOres;
    private int oldLayerAt;
    private int oldEnergy;

    public TileEntityMiner(){
        super(9, "miner");
    }

    @Override
    public void writeSyncableNBT(NBTTagCompound compound, NBTType type){
        super.writeSyncableNBT(compound, type);
        this.storage.writeToNBT(compound);
        if(type != NBTType.SAVE_BLOCK){
            compound.setInteger("Layer", this.layerAt);
        }
        if(type != NBTType.SAVE_BLOCK || this.onlyMineOres){
            compound.setBoolean("OnlyOres", this.onlyMineOres);
        }
    }

    @Override
    public void readSyncableNBT(NBTTagCompound compound, NBTType type){
        super.readSyncableNBT(compound, type);
        this.storage.readFromNBT(compound);
        if(type != NBTType.SAVE_BLOCK){
            this.layerAt = compound.getInteger("Layer");
        }
        this.onlyMineOres = compound.getBoolean("OnlyOres");
    }

    @Override
    public void updateEntity(){
        super.updateEntity();
        if(!this.worldObj.isRemote){
            if(this.layerAt == -1){
                this.layerAt = this.getPos().getY()-1;
            }

            if(!this.isRedstonePowered && this.ticksElapsed%5 == 0){

                if(this.layerAt > 0){
                    if(this.mine(TileEntityPhantomface.upgradeRange(DEFAULT_RANGE, this.worldObj, this.pos))){
                        this.layerAt--;
                    }
                }
            }

            if((this.oldEnergy != this.storage.getEnergyStored() || this.oldLayerAt != this.layerAt) && this.sendUpdateWithInterval()){
                this.oldEnergy = this.storage.getEnergyStored();
                this.oldLayerAt = this.layerAt;
            }
        }
    }

    private boolean mine(int range){
        for(int anX = -range; anX <= range; anX++){
            for(int aZ = -range; aZ <= range; aZ++){
                int actualUse = ENERGY_USE_PER_BLOCK*(this.onlyMineOres ? 5 : 1);
                if(this.storage.getEnergyStored() >= actualUse){
                    BlockPos pos = new BlockPos(this.pos.getX()+anX, this.layerAt, this.pos.getZ()+aZ);

                    IBlockState state = this.worldObj.getBlockState(pos);
                    Block block = state.getBlock();
                    int meta = block.getMetaFromState(state);
                    if(!block.isAir(this.worldObj.getBlockState(pos), this.worldObj, pos)){
                        if(block.getHarvestLevel(this.worldObj.getBlockState(pos)) <= ItemDrill.HARVEST_LEVEL && state.getBlockHardness(this.worldObj, pos) >= 0F && !(block instanceof BlockLiquid) && !(block instanceof IFluidBlock) && this.isMinable(block, meta)){
                            List<ItemStack> drops = block.getDrops(this.worldObj, pos, this.worldObj.getBlockState(pos), 0);
                            float chance = WorldUtil.fireFakeHarvestEventsForDropChance(drops, this.worldObj, pos);

                            if(chance > 0 && this.worldObj.rand.nextFloat() <= chance){
                                if(WorldUtil.addToInventory(this, drops, false, true)){
                                    this.worldObj.playEvent(2001, pos, Block.getStateId(this.worldObj.getBlockState(pos)));
                                    this.worldObj.setBlockToAir(pos);

                                    WorldUtil.addToInventory(this, drops, true, true);
                                    this.markDirty();

                                    this.storage.extractEnergy(actualUse, false);
                                    this.shootParticles(pos.getX(), pos.getY(), pos.getZ());
                                }
                            }
                            return false;
                        }
                    }
                }
                else{
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isMinable(Block block, int meta){
        if(block != null){
            if(!this.isBlacklisted(block)){
                if(!this.onlyMineOres){
                    return true;
                }
                else{
                    ItemStack stack = new ItemStack(block, 1, meta);
                    if(stack.getItem() != null){
                        int[] ids = OreDictionary.getOreIDs(stack);
                        for(int id : ids){
                            String name = OreDictionary.getOreName(id);
                            if(name.startsWith("ore") || name.startsWith("denseore")){
                                return true;
                            }
                        }

                        String reg = block.getRegistryName().toString();
                        if(!reg.isEmpty()){
                            for(String string : ConfigStringListValues.MINER_EXTRA_WHITELIST.getValue()){
                                if(reg.equals(string)){
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private void shootParticles(int endX, int endY, int endZ){
        AssetUtil.spawnLaserWithTimeServer(this.worldObj, this.getPos().getX(), this.getPos().getY(), this.getPos().getZ(), endX, endY, endZ, new float[]{65F/255F, 150F/255F, 2F/255F}, 10, 120, 0.1F, 0.8F);
    }

    private boolean isBlacklisted(Block block){
        String reg = block.getRegistryName().toString();
        if(!reg.isEmpty()){
            for(String string : ConfigStringListValues.MINER_BLACKLIST.getValue()){
                if(reg.equals(string)){
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack){
        return false;
    }

    @Override
    public int receiveEnergy(EnumFacing from, int maxReceive, boolean simulate){
        return this.storage.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int getEnergyStored(EnumFacing from){
        return this.storage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored(EnumFacing from){
        return this.storage.getMaxEnergyStored();
    }

    @Override
    public boolean canConnectEnergy(EnumFacing from){
        return true;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, EnumFacing side){
        return this.isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, EnumFacing side){
        return true;
    }

    @Override
    public void onButtonPressed(int buttonID, EntityPlayer player){
        if(buttonID == 0){
            this.onlyMineOres = !this.onlyMineOres;
            this.sendUpdate();
        }
        else if(buttonID == 1){
            this.layerAt = -1;
        }
    }

    @Override
    public EnergyStorage getEnergyStorage(){
        return this.storage;
    }

    @Override
    public boolean needsHoldShift(){
        return false;
    }
}
