package valkyrienwarfare.physics;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import valkyrienwarfare.api.Vector;
import valkyrienwarfare.physicsmanagement.PhysicsObject;

/**
 * Used as a way to encapsulate the behavior of the physics manager, to
 * guarantee no more random ships disappearing.
 * @author Alex
 *
 */
public interface IPhysicsManager {

	public float[] getInvFramedMOI();
	
	public float[] getFramedMOI();
	
	public void setActAsArchimedes(boolean archimedes);
	
	public PhysicsObject getParent();
	
	public Vector getLinearMomentum();
	
	public Vector getAngularMomentum();
	
	public Vector getAngularVelocity();
	
	public Vector getVelocityAtPoint(Vector point);
	
	public Vector getCenterOfMass();
	
	public double getMass();
	
	public double getPhysTickSpeed();
	
	public double getPhysRawSpeed();
	
	public default double getInvMass() {
		return 1D / getMass();
	}

	public void addForceAtPoint(Vector point, Vector force);

	public void convertTorqueToVelocity();

	public void onSetBlockState(IBlockState oldState, IBlockState newState,
								BlockPos posAt);

	public void processInitialPhysicsData();

	public void updateParentCenterOfMass();
	
	public void addActiveForcePosition(BlockPos pos);

	public void writeToNBTTag(NBTTagCompound compound);

	public void readFromNBTTag(NBTTagCompound compound);
}
