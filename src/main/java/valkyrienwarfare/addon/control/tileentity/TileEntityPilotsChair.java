/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2015-2018 the Valkyrien Warfare team
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income unless it is to be used as a part of a larger project (IE: "modpacks"), nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from the Valkyrien Warfare team.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: The Valkyrien Warfare team), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package valkyrienwarfare.addon.control.tileentity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import valkyrienwarfare.addon.control.ValkyrienWarfareControl;
import valkyrienwarfare.addon.control.block.BlockShipPilotsChair;
import valkyrienwarfare.addon.control.piloting.ControllerInputType;
import valkyrienwarfare.addon.control.piloting.PilotControlsMessage;
import valkyrienwarfare.api.VWRotationMath;
import valkyrienwarfare.api.VectorVW;
import valkyrienwarfare.physicsmanagement.PhysicsObject;
import valkyrienwarfare.physicsmanagement.PhysicsWrapperEntity;

public class TileEntityPilotsChair extends ImplTileEntityPilotable {

    @Override
    void processControlMessage(PilotControlsMessage message, EntityPlayerMP sender) {
        IBlockState blockState = getWorld().getBlockState(getPos());
        if (blockState.getBlock() == ValkyrienWarfareControl.INSTANCE.pilotsChair) {
            PhysicsWrapperEntity wrapper = getParentPhysicsEntity();
            if (wrapper != null) {
                processCalculationsForControlMessageAndApplyCalculations(wrapper, message, blockState);
            }
        } else {
            setPilotEntity(null);
        }
    }

    @Override
    final ControllerInputType getControlInputType() {
        return ControllerInputType.PilotsChair;
    }

    @Override
    final boolean setClientPilotingEntireShip() {
        return true;
    }


    @Override
    public final void onStartTileUsage(EntityPlayer player) {
        getParentPhysicsEntity().wrapping.physicsProcessor.setActAsArchimedes(true);
    }

    @Override
    public final void onStopTileUsage() {
        getParentPhysicsEntity().wrapping.physicsProcessor.setActAsArchimedes(false);
    }

    private final void processCalculationsForControlMessageAndApplyCalculations(PhysicsWrapperEntity wrapper, PilotControlsMessage message, IBlockState state) {
        BlockPos chairPosition = getPos();
        PhysicsObject controlledShip = wrapper.wrapping;

        double pilotPitch = 0D;
        double pilotYaw = ((BlockShipPilotsChair) state.getBlock()).getChairYaw(state, chairPosition);
        double pilotRoll = 0D;

        float[] pilotRotationMatrix = VWRotationMath.getRotationMatrix(pilotPitch, pilotYaw, pilotRoll);

        VectorVW playerDirection = new VectorVW(1, 0, 0);

        VectorVW rightDirection = new VectorVW(0, 0, 1);

        VectorVW leftDirection = new VectorVW(0, 0, -1);

        VWRotationMath.applyTransform(pilotRotationMatrix, playerDirection);
        VWRotationMath.applyTransform(pilotRotationMatrix, rightDirection);
        VWRotationMath.applyTransform(pilotRotationMatrix, leftDirection);

        VectorVW upDirection = new VectorVW(0, 1, 0);

        VectorVW downDirection = new VectorVW(0, -1, 0);

        VectorVW idealAngularDirection = new VectorVW();

        VectorVW idealLinearVelocity = new VectorVW();

        VectorVW shipUp = new VectorVW(0, 1, 0);
        VectorVW shipUpPos = new VectorVW(0, 1, 0);

        if (message.airshipForward_KeyDown) {
            idealLinearVelocity.add(playerDirection);
        }
        if (message.airshipBackward_KeyDown) {
            idealLinearVelocity.subtract(playerDirection);
        }

        VWRotationMath.applyTransform(controlledShip.coordTransform.lToWRotation, idealLinearVelocity);

        VWRotationMath.applyTransform(controlledShip.coordTransform.lToWRotation, shipUp);

        if (message.airshipUp_KeyDown) {
            idealLinearVelocity.add(upDirection);
        }
        if (message.airshipDown_KeyDown) {
            idealLinearVelocity.add(downDirection);
        }


        if (message.airshipRight_KeyDown) {
            idealAngularDirection.add(rightDirection);
        }
        if (message.airshipLeft_KeyDown) {
            idealAngularDirection.add(leftDirection);
        }

        //Upside down if you want it
//		Vector shipUpOffset = shipUpPos.getSubtraction(shipUp);
        VectorVW shipUpOffset = shipUp.getSubtraction(shipUpPos);


        double mass = controlledShip.physicsProcessor.getMass();

//		idealAngularDirection.multiply(mass/2.5D);
        idealLinearVelocity.multiply(mass / 5D);
//		shipUpOffset.multiply(mass/2.5D);


        idealAngularDirection.multiply(1D / 6D);
        shipUpOffset.multiply(1D / 3D);

        VectorVW velocityCompenstationLinear = controlledShip.physicsProcessor.getLinearMomentum();

        VectorVW velocityCompensationAngular = controlledShip.physicsProcessor.getAngularVelocity().cross(playerDirection);

        VectorVW velocityCompensationAlignment = controlledShip.physicsProcessor.getAngularVelocity().cross(shipUpPos);

        velocityCompensationAlignment.multiply(controlledShip.physicsProcessor.getPhysRawSpeed());
        velocityCompensationAngular.multiply(2D * controlledShip.physicsProcessor.getPhysRawSpeed());

        shipUpOffset.subtract(velocityCompensationAlignment);
        velocityCompensationAngular.subtract(velocityCompensationAngular);

        VWRotationMath.applyTransform3by3(controlledShip.physicsProcessor.getFramedMOI(), idealAngularDirection);
        VWRotationMath.applyTransform3by3(controlledShip.physicsProcessor.getFramedMOI(), shipUpOffset);


        if (message.airshipSprinting) {
            idealLinearVelocity.multiply(2D);
        }

        idealLinearVelocity.subtract(idealAngularDirection);
        idealLinearVelocity.subtract(shipUpOffset);

        //TEMPORARY CODE!!!

        controlledShip.physicsProcessor.addForceAtPoint(playerDirection, idealAngularDirection);

        controlledShip.physicsProcessor.addForceAtPoint(shipUpPos, shipUpOffset);

        controlledShip.physicsProcessor.addForceAtPoint(new VectorVW(), idealLinearVelocity);

        controlledShip.physicsProcessor.convertTorqueToVelocity();
    }

}
