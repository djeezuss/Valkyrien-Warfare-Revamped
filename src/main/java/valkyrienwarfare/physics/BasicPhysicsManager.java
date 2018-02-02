package valkyrienwarfare.physics;

import java.util.ArrayList;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import valkyrienwarfare.api.VWRotationMath;
import valkyrienwarfare.api.Vector;
import valkyrienwarfare.physicsmanagement.PhysicsObject;

public class BasicPhysicsManager implements IPhysicsManager {

	public static final double BLOCKS_TO_METERS = 1.8D;
	public static final double AIR_DRAG = .99D;
	private double mass;
	private final PhysicsObject parent;
	private Vector linearMomentum;
	private Vector angularMomentum;
	private Vector angularVelocity;
	private Vector centerOfMass;
	public ArrayList<BlockPos> activeForcePositions = new ArrayList<BlockPos>();
	public float[] MoITensor;
	public float[] invMoITensor;
	public float[] framedMOI;
	public float[] invFramedMOI;
	
	public BasicPhysicsManager(PhysicsObject physicsObject) {
		this.parent = physicsObject;
		this.linearMomentum = new Vector();
		this.angularMomentum = new Vector();
		this.angularVelocity = new Vector();
		this.centerOfMass = new Vector();
		this.mass = 1D;
		this.MoITensor = VWRotationMath.getZeroMatrix(3);
		this.invMoITensor = VWRotationMath.getZeroMatrix(3);
		this.framedMOI = VWRotationMath.getZeroMatrix(3);
		this.invFramedMOI = VWRotationMath.getZeroMatrix(3);
	}

	@Override
	public float[] getInvFramedMOI() {
		return invFramedMOI;
	}

	@Override
	public float[] getFramedMOI() {
		return framedMOI;
	}

	@Override
	public void setActAsArchimedes(boolean archimedes) {

	}

	@Override
	public PhysicsObject getParent() {
		return parent;
	}

	@Override
	public Vector getLinearMomentum() {
		return linearMomentum;
	}

	@Override
	public Vector getAngularMomentum() {
		return angularMomentum;
	}

	@Override
	public Vector getAngularVelocity() {
		return angularVelocity;
	}

	@Override
	public Vector getVelocityAtPoint(Vector point) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Vector getCenterOfMass() {
		return centerOfMass;
	}

	@Override
	public double getMass() {
		return mass;
	}

	@Override
	public double getPhysTickSpeed() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getPhysRawSpeed() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void addForceAtPoint(Vector point, Vector force) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void convertTorqueToVelocity() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSetBlockState(IBlockState oldState, IBlockState newState, BlockPos posAt) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void processInitialPhysicsData() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateParentCenterOfMass() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addActiveForcePosition(BlockPos pos) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void writeToNBTTag(NBTTagCompound compound) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void readFromNBTTag(NBTTagCompound compound) {
		// TODO Auto-generated method stub
		
	}

}
