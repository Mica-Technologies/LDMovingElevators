package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import net.minecraft.util.text.TextFormatting;
import com.supermartijn642.core.block.BaseBlockEntity;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * A landing readout for a whole bank: every car's floor and direction, side by side on one plate.
 * <p>
 * A lobby with four shafts otherwise needs four indicators, each speaking for one lift, and a
 * passenger reads four separate answers to decide where to stand. This gives the same information as
 * one glance.
 * <p>
 * Kept as its own block rather than folded into the single indicator. A builder with one shaft should
 * not carry the machinery for banks, and somebody who prefers one readout per shaft — which is what a
 * real lobby often has — should not be argued out of it.
 * <p>
 * Created for the Mica Technologies fork.
 */
public class BankIndicatorBlockEntity extends BaseBlockEntity {

    /**
     * How many elevators fit on the face before it stops widening.
     * <p>
     * A plate may reach one block beyond its own on each side and no further, and six columns is
     * already the point at which each is narrow enough to be squinted at. Anything past this is still
     * linked and still works -- it simply has no column, which the status readout says out loud rather
     * than leaving somebody to count.
     */
    public static final int MAX_SHOWN = 6;

    private final List<BankLobbyPanelBlockEntity.Binding> bindings = new ArrayList<>();

    public BankIndicatorBlockEntity(){
        super(MovingElevators.bank_indicator_tile);
    }

    public void setBindings(List<BankLobbyPanelBlockEntity.Binding> bindings){
        this.bindings.clear();
        this.bindings.addAll(bindings);
        this.dataChanged();
    }

    public List<BankLobbyPanelBlockEntity.Binding> getBindings(){
        return new ArrayList<>(this.bindings);
    }

    public boolean isBound(){
        return !this.bindings.isEmpty();
    }

    public EnumFacing getFacing(){
        if(this.world == null)
            return EnumFacing.NORTH;
        IBlockState state = this.world.getBlockState(this.pos);
        return state.getBlock() instanceof WallPanelBlock ? state.getValue(WallPanelBlock.FACING) : EnumFacing.NORTH;
    }

    /** The elevators to show, in the order they were linked, so the readout does not reshuffle. */
    public List<ElevatorGroup> getGroups(){
        return BankLobbyPanelBlockEntity.resolveGroups(this.world, this.bindings);
    }

    public void reportStatus(EntityPlayer player){
        BankLobbyPanelBlockEntity.reportStatus(player, this.world,
            "movingelevators.bank_indicator.status.header", null, this.bindings);
        // The shared readout lists every elevator; the face cannot. Saying which ones are missing is
        // the difference between a readout that is incomplete and one that looks broken.
        int hidden = this.getGroups().size() - MAX_SHOWN;
        if(hidden > 0)
            player.sendMessage(TextComponents.translation("movingelevators.bank_indicator.status.overflow",
                TextComponents.number(hidden).color(TextFormatting.GOLD).get(),
                TextComponents.number(MAX_SHOWN).color(TextFormatting.GOLD).get()).color(TextFormatting.YELLOW).get());
    }

    /**
     * A block wider than its block has to say so, or it disappears early.
     * <p>
     * Culling uses this box, and the default is the single block the fixture sits in -- so a plate
     * spread across three would vanish while both its ends were still plainly on screen. Claiming the
     * full three blocks costs a little more drawing at the edge of the view and avoids a fixture that
     * blinks out as you turn your head.
     */
    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox(){
        return new net.minecraft.util.math.AxisAlignedBB(this.pos.add(-1, 0, -1), this.pos.add(2, 1, 2));
    }

    @Override
    protected NBTTagCompound writeData(){
        NBTTagCompound compound = new NBTTagCompound();
        compound.setTag("bindings", BankLobbyPanelBlockEntity.writeBindings(this.bindings));
        return compound;
    }

    @Override
    protected void readData(NBTTagCompound compound){
        this.bindings.clear();
        this.bindings.addAll(BankLobbyPanelBlockEntity.readBindings(
            compound.getTagList("bindings", Constants.NBT.TAG_COMPOUND)));
    }

    /** Not dispatched past where the indicator's renderer stops drawing; see {@link TextRenderCutoff}. */
    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public double getMaxRenderDistanceSquared(){
        return TextRenderCutoff.dispatchRangeSquared(TextRenderCutoff.PANEL);
    }
}
