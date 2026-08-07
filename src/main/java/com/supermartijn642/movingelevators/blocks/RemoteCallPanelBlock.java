package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.block.BlockProperties;
import com.supermartijn642.movingelevators.elevator.ElevatorBank;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.DimensionType;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * A landing call panel: a tall, narrow wall plate with the floor readout at the top and up/down call
 * buttons below it, like the fixture beside a real lift door.
 * <p>
 * Its buttons are <em>hall calls</em>, not the "move the cabin one floor" arrows of
 * {@link RemoteControllerBlock}. Pressing either one fetches the cabin to this landing and tells the
 * elevator which way you then want to travel, so it slots into the call queue and gets served in
 * sweep order along with everything else.
 * <p>
 * Mica: a panel may be linked to several elevators, in which case a press sends exactly one of them --
 * whichever is best placed to answer. Linked to one, which is the only thing it could once be, it is
 * unchanged: the press goes straight to that elevator.
 */
public class RemoteCallPanelBlock extends WallPanelBlock {

    /** Tall and narrow: the buttons stack beside the doors rather than above them. */
    private static final double MIN_X = 5 / 16d, MAX_X = 11 / 16d;
    private static final double MIN_Y = 1 / 16d, MAX_Y = 15 / 16d;

    /**
     * Where the plate stops being buttons and starts being screen, measured up the block. Below
     * {@link #DOWN_BUTTON_TOP} is the down arrow, up to {@link #SCREEN_BOTTOM} is the up arrow, and
     * above that is the readout, which does nothing when clicked.
     */
    public static final double DOWN_BUTTON_TOP = 5.5 / 16d;
    public static final double SCREEN_BOTTOM = 9.5 / 16d;

    public RemoteCallPanelBlock(BlockProperties properties){
        super(properties, MIN_X, MAX_X, MIN_Y, MAX_Y);
    }

    @Override
    public TileEntity createNewBlockEntity(){
        return new RemoteCallPanelBlockEntity();
    }

    @Override
    protected InteractionFeedback interact(IBlockState state, World level, BlockPos pos, EntityPlayer player, EnumHand hand, EnumFacing hitSide, Vec3d hitLocation){
        TileEntity entity = level.getTileEntity(pos);
        if(!(entity instanceof RemoteCallPanelBlockEntity))
            return InteractionFeedback.SUCCESS;
        RemoteCallPanelBlockEntity panel = (RemoteCallPanelBlockEntity)entity;

        // Sneaking asks what it is linked to, exactly as the bank lobby panel does. A panel that
        // serves several elevators looks identical to one that serves the wrong ones until a car
        // fails to arrive, so there has to be a way to ask rather than infer.
        if(player != null && player.isSneaking() && player.getHeldItem(hand).isEmpty()){
            if(!level.isRemote)
                panel.reportStatus(player);
            return InteractionFeedback.SUCCESS;
        }

        // Only the face the plate is on is clickable; the sides are just metal.
        if(hitSide != state.getValue(FACING))
            return InteractionFeedback.SUCCESS;

        double hitY = hitLocation.y - pos.getY();
        if(hitY >= SCREEN_BOTTOM){
            // The readout. Report what this panel is bound to, matching the other remote blocks.
            if(level.isRemote){
                BlockPos controllerPos = panel.getControllerPos();
                ITextComponent x = TextComponents.number(controllerPos.getX()).color(TextFormatting.GOLD).get();
                ITextComponent y = TextComponents.number(controllerPos.getY()).color(TextFormatting.GOLD).get();
                ITextComponent z = TextComponents.number(controllerPos.getZ()).color(TextFormatting.GOLD).get();
                player.sendStatusMessage(TextComponents.translation("movingelevators.remote_controller.controller_location", x, y, z).get(), true);
            }
            return InteractionFeedback.SUCCESS;
        }

        if(!level.isRemote){
            boolean up = hitY >= DOWN_BUTTON_TOP;
            List<ElevatorGroup> groups = panel.getGroups();
            // One elevator is passed the press whole rather than routed through dispatch: dispatch is
            // allowed to decline a car that is halted or off hall calls, and a panel wired to a
            // single shaft has never done anything but hand the call over.
            if(groups.size() == 1)
                groups.get(0).onHallCall(panel.getFloorLevel(), up, player);
            else if(!groups.isEmpty()){
                // Exactly one car, never all of them. Calling every bound elevator would bring the
                // whole bank to one landing for one passenger, which is the failure this exists to
                // avoid.
                ElevatorGroup group = ElevatorBank.pickForHallCall(groups, panel.getFloorLevel(), up);
                if(group == null)
                    player.sendStatusMessage(TextComponents.translation("movingelevators.bank_lobby.no_car").get(), true);
                else
                    group.onHallCall(panel.getFloorLevel(), up, player);
            }
        }
        return InteractionFeedback.SUCCESS;
    }

    @Override
    public void onBlockPlacedBy(World level, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack){
        super.onBlockPlacedBy(level, pos, state, placer, stack);
        TileEntity entity = level.getTileEntity(pos);
        if(!(entity instanceof RemoteCallPanelBlockEntity))
            return;
        RemoteCallPanelBlockEntity panel = (RemoteCallPanelBlockEntity)entity;

        // The item deliberately keeps its links after placing, so a corridor of panels all serving
        // the same elevators costs one binding walk rather than one per panel.
        List<BankLobbyPanelBlockEntity.Binding> bindings = MultiControllerBlockItem.readBindings(stack);
        if(!bindings.isEmpty()){
            panel.setBindings(bindings);
            return;
        }

        // A call panel bound before this item could hold a set carries the old single-controller tags
        // instead. Read those too, so one sitting in a chest since then still places bound.
        NBTTagCompound compound = stack.getTagCompound();
        if(compound == null || !compound.hasKey("controllerDim"))
            return;
        panel.setValues(
            new BlockPos(compound.getInteger("controllerX"), compound.getInteger("controllerY"), compound.getInteger("controllerZ")),
            compound.hasKey("controllerFacing", Constants.NBT.TAG_INT) ? EnumFacing.getHorizontal(compound.getInteger("controllerFacing")) : null
        );
    }

    @Override
    protected void appendItemInformation(ItemStack stack, @Nullable IBlockAccess level, Consumer<ITextComponent> info, boolean advanced){
        List<BankLobbyPanelBlockEntity.Binding> bindings = MultiControllerBlockItem.readBindings(stack);
        if(!bindings.isEmpty()){
            info.accept(TextComponents.translation("movingelevators.bank_lobby_panel.bound",
                TextComponents.number(bindings.size()).color(TextFormatting.GOLD).get()).get());
            return;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if(tag == null || !tag.hasKey("controllerDim"))
            info.accept(TextComponents.translation("movingelevators.remote_call_panel.tooltip").color(TextFormatting.AQUA).get());
        else{
            ITextComponent x = TextComponents.number(tag.getInteger("controllerX")).color(TextFormatting.GOLD).get();
            ITextComponent y = TextComponents.number(tag.getInteger("controllerY")).color(TextFormatting.GOLD).get();
            ITextComponent z = TextComponents.number(tag.getInteger("controllerZ")).color(TextFormatting.GOLD).get();
            ITextComponent dimension = TextComponents.dimension(DimensionType.getById(tag.getInteger("controllerDim"))).color(TextFormatting.GOLD).get();
            info.accept(TextComponents.translation("movingelevators.remote_controller.tooltip.bound", x, y, z, dimension).get());
        }
    }
}
