/*
 * This file ("TileEntityBreaker.java") is part of the Actually Additions mod for Minecraft.
 * It is created and owned by Ellpeck and distributed
 * under the Actually Additions License to be found at
 * http://ellpeck.de/actaddlicense
 * View the source code at https://github.com/Ellpeck/ActuallyAdditions
 *
 * © 2015-2016 Ellpeck
 */

package de.ellpeck.actuallyadditions.mod.tile;


import de.ellpeck.actuallyadditions.mod.util.StackUtil;
import de.ellpeck.actuallyadditions.mod.util.WorldUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.IFluidBlock;

import java.util.List;

public class TileEntityBreaker extends TileEntityInventoryBase{

    public boolean isPlacer;
    private int currentTime;

    public TileEntityBreaker(int slots, String name){
        super(slots, name);
    }

    public TileEntityBreaker(){
        super(9, "breaker");
        this.isPlacer = false;
    }

    @Override
    public void writeSyncableNBT(NBTTagCompound compound, NBTType type){
        super.writeSyncableNBT(compound, type);
        if(type != NBTType.SAVE_BLOCK){
            compound.setInteger("CurrentTime", this.currentTime);
        }
    }

    @Override
    public void readSyncableNBT(NBTTagCompound compound, NBTType type){
        super.readSyncableNBT(compound, type);
        if(type != NBTType.SAVE_BLOCK){
            this.currentTime = compound.getInteger("CurrentTime");
        }
    }

    @Override
    public void updateEntity(){
        super.updateEntity();
        if(!this.worldObj.isRemote){
            if(!this.isRedstonePowered && !this.isPulseMode){
                if(this.currentTime > 0){
                    this.currentTime--;
                    if(this.currentTime <= 0){
                        this.doWork();
                    }
                }
                else{
                    this.currentTime = 15;
                }
            }
        }
    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack stack){
        return this.isPlacer;
    }

    private void doWork(){
        IBlockState state = this.worldObj.getBlockState(this.pos);
        EnumFacing sideToManipulate = WorldUtil.getDirectionByPistonRotation(state.getBlock().getMetaFromState(state));

        BlockPos coordsBlock = this.pos.offset(sideToManipulate);
        IBlockState stateToBreak = this.worldObj.getBlockState(coordsBlock);
        Block blockToBreak = stateToBreak.getBlock();
        if(!this.isPlacer && blockToBreak != null && !this.worldObj.isAirBlock(coordsBlock) && !(blockToBreak instanceof BlockLiquid) && !(blockToBreak instanceof IFluidBlock) && blockToBreak.getBlockHardness(stateToBreak, this.worldObj, coordsBlock) >= 0.0F){
            List<ItemStack> drops = blockToBreak.getDrops(this.worldObj, coordsBlock, stateToBreak, 0);
            float chance = WorldUtil.fireFakeHarvestEventsForDropChance(drops, this.worldObj, coordsBlock);

            if(chance > 0 && this.worldObj.rand.nextFloat() <= chance){
                if(WorldUtil.addToInventory(this, drops, false, true)){
                    this.worldObj.playEvent(2001, coordsBlock, Block.getStateId(stateToBreak));
                    this.worldObj.setBlockToAir(coordsBlock);
                    WorldUtil.addToInventory(this, drops, true, true);
                    this.markDirty();
                }
            }
        }
        else if(this.isPlacer){
            int theSlot = WorldUtil.findFirstFilledSlot(this.slots);
            this.setInventorySlotContents(theSlot, WorldUtil.useItemAtSide(sideToManipulate, this.worldObj, this.pos, this.slots[theSlot]));
            if(!StackUtil.isValid(this.slots[theSlot])){
                this.slots[theSlot] = StackUtil.getNull();
            }
        }
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
    public boolean isRedstoneToggle(){
        return true;
    }

    @Override
    public void activateOnPulse(){
        this.doWork();
    }

}
