package com.supermartijn642.movingelevators.packets;

import com.supermartijn642.core.network.PacketContext;
import com.supermartijn642.movingelevators.MovingElevatorsConfig;
import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.elevator.ElevatorGroup;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;

/**
 * Created 4/3/2020 by SuperMartijn642
 */
public class PacketElevatorSpeed extends ElevatorGroupPacket {

    public double speed;

    public PacketElevatorSpeed(BlockPos pos, double speed){
        super(pos);
        this.speed = speed;
    }

    public PacketElevatorSpeed(){
    }

    @Override
    public void write(PacketBuffer buffer){
        super.write(buffer);
        buffer.writeDouble(this.speed);
    }

    @Override
    public void read(PacketBuffer buffer){
        super.read(buffer);
        this.speed = buffer.readDouble();
    }

    @Override
    public boolean verify(PacketContext context){
        // The ceiling comes from config rather than a literal, because the slider that produces this
        // value reads the same setting -- two copies of a bound are two chances to disagree.
        return this.speed >= 0.1 && this.speed <= MovingElevatorsConfig.maxCabinSpeed.get() / 10d;
    }

    @Override
    protected void handle(ElevatorGroup group, ControllerBlockEntity blockEntity, EntityPlayer player){
        group.setTargetSpeed(this.speed);
    }
}
