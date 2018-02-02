package valkyrienwarfare.physics;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import valkyrienwarfare.NBTUtils;
import valkyrienwarfare.api.VWRotationMath;
import valkyrienwarfare.api.VectorVW;
import valkyrienwarfare.physicsmanagement.PhysicsObject;

public class FastPhysicsManager implements IPhysicsManager {

	public static final double BLOCKS_TO_METERS = 1.8D;
	public static final double AIR_DRAG = .99D;
	private float mass;
	private final PhysicsObject parent;
	private VectorVW linearMomentum;
	private VectorVW angularMomentum;
	private VectorVW angularVelocity;
	private VectorVW centerOfMass;
	private VectorVW torque;
	private List<BlockPos> activeForcePositions;
	private float[] MoITensor;
	private float[] invMoITensor;
	private float[] framedMOI;
	private float[] invFramedMOI;
	private int iterationsPerTick;
	private float physRawSpeed;
	private float physTickSpeed;

	public FastPhysicsManager(PhysicsObject physicsObject) {
		this.parent = physicsObject;
		this.linearMomentum = new VectorVW();
		this.angularMomentum = new VectorVW();
		this.angularVelocity = new VectorVW();
		this.centerOfMass = new VectorVW();

		this.MoITensor = VWRotationMath.getZeroMatrix(3);
		this.invMoITensor = VWRotationMath.getZeroMatrix(3);
		this.framedMOI = VWRotationMath.getZeroMatrix(3);
		this.invFramedMOI = VWRotationMath.getZeroMatrix(3);
		this.activeForcePositions = new ArrayList<BlockPos>();

		this.centerOfMass = new VectorVW(physicsObject.centerCoord);
		this.linearMomentum = new VectorVW();
		this.angularVelocity = new VectorVW();
		this.torque = new VectorVW();

		this.iterationsPerTick = 5;
		this.physRawSpeed = 0.05F;
		this.physTickSpeed = 0.01F;
	}

	@Override
	public void physicsPreTick() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void physicsPostTick() {
		parent.coordTransform.updateAllTransforms();
		
	}
	
	@Override
	public void processInitialPhysicsData() {
		IBlockState Air = Blocks.AIR.getDefaultState();
		for (BlockPos pos : parent.blockPositions) {
			onSetBlockState(Air, parent.VKChunkCache.getBlockState(pos), pos);
		}
	}

	@Override
	public void writeToNBTTag(NBTTagCompound compound) {
		compound.setFloat("mass", mass);
		NBTUtils.writeVectorToNBT("linear", linearMomentum, compound);
		NBTUtils.writeVectorToNBT("angularVelocity", angularVelocity, compound);
		NBTUtils.writeVectorToNBT("CM", centerOfMass, compound);
		NBTUtils.write3x3MatrixToNBT("MOI", MoITensor, compound);
	}

	@Override
	public void readFromNBTTag(NBTTagCompound compound) {
		mass = compound.getFloat("mass");
		linearMomentum = NBTUtils.readVectorFromNBT("linear", compound);
		angularVelocity = NBTUtils.readVectorFromNBT("angularVelocity", compound);
		centerOfMass = NBTUtils.readVectorFromNBT("CM", compound);
		MoITensor = NBTUtils.read3x3MatrixFromNBT("MOI", compound);
		invMoITensor = VWRotationMath.inverse3by3(MoITensor);
	}
	
	@Override
	public VectorVW getVelocityAtPoint(VectorVW inBodyWO) {
		VectorVW speed = angularVelocity.cross(inBodyWO);
		speed.X += (linearMomentum.X * getInvMass());
		speed.Y += (linearMomentum.Y * getInvMass());
		speed.Z += (linearMomentum.Z * getInvMass());
		return speed;
	}
	
	@Override
	public void addActiveForcePosition(BlockPos pos) {
		activeForcePositions.add(pos);
	}

	@Override
	public void setActAsArchimedes(boolean archimedes) {
		// TODO:
	}
	
	@Override
	public void addForceAtPoint(VectorVW inBodyWO, VectorVW forceToApply) {
		forceToApply.multiply(BLOCKS_TO_METERS);
		torque.add(inBodyWO.cross(forceToApply));
		linearMomentum.add(forceToApply);
	}

	@Override
	public void convertTorqueToVelocity() {
		if (!torque.isZero()) {
			angularVelocity.add(VWRotationMath.get3by3TransformedVec(invFramedMOI, torque));
			torque.zero();
		}
	}
	
	@Override
	public void updateParentCenterOfMass() {
		VectorVW parentCM = parent.centerCoord;
		if (!parent.centerCoord.equals(centerOfMass) && false) {
			VectorVW CMDif = centerOfMass.getSubtraction(parentCM);
			VWRotationMath.applyTransform(parent.coordTransform.lToWRotation, CMDif);
			parent.wrapper.posX -= CMDif.X;
			parent.wrapper.posY -= CMDif.Y;
			parent.wrapper.posZ -= CMDif.Z;
			parent.centerCoord = new VectorVW(centerOfMass);
			parent.coordTransform.updateAllTransforms();
		}
	}
	
	@Override
	public double getPhysTickSpeed() {
		return physTickSpeed;
	}

	@Override
	public double getPhysRawSpeed() {
		return physRawSpeed;
	}

	@Override
	public void onSetBlockState(IBlockState oldState, IBlockState newState, BlockPos posAt) {
		if (newState == oldState) {
			// Nothing changed, so don't do anything
			// Or, liquids were involved, so still don't do anything
			return;
		}
		World worldObj = parent.worldObj;
		if (oldState.getBlock() == Blocks.AIR) {
			if (BlockForce.basicForces.isBlockProvidingForce(newState, posAt, worldObj)) {
				activeForcePositions.add(posAt);
			}
		} else {
			// int index = activeForcePositions.indexOf(pos);
			// if(BlockForce.basicForces.isBlockProvidingForce(newState, pos, worldObj)){
			// if(index==-1){
			// activeForcePositions.add(pos);
			// }
			// }else{
			// if(index!=-1){
			// activeForcePositions.remove(index);
			// }
			// }
			if (activeForcePositions.contains(posAt)) {
				if (!BlockForce.basicForces.isBlockProvidingForce(newState, posAt, worldObj)) {
					activeForcePositions.remove(posAt);
				}
			} else {
				if (BlockForce.basicForces.isBlockProvidingForce(newState, posAt, worldObj)) {
					activeForcePositions.add(posAt);
				}
			}
		}
		if (newState.getBlock() == Blocks.AIR) {
			activeForcePositions.remove(posAt);
		}

		float oldMassAtPos = (float) BlockMass.basicMass.getMassFromState(oldState, posAt, worldObj);
		float newMassAtPos = (float) BlockMass.basicMass.getMassFromState(newState, posAt, worldObj);
		// Don't change anything if the mass is the same
		if (oldMassAtPos != newMassAtPos) {
			final float notAHalf = .4F;
			final float x = posAt.getX() + .5F;
			final float y = posAt.getY() + .5F;
			final float z = posAt.getZ() + .5F;

			if (oldMassAtPos > 0D) {
				oldMassAtPos /= -9.0D;
				addMassAt(x, y, z, oldMassAtPos);
				addMassAt(x + notAHalf, y + notAHalf, z + notAHalf, oldMassAtPos);
				addMassAt(x + notAHalf, y + notAHalf, z - notAHalf, oldMassAtPos);
				addMassAt(x + notAHalf, y - notAHalf, z + notAHalf, oldMassAtPos);
				addMassAt(x + notAHalf, y - notAHalf, z - notAHalf, oldMassAtPos);
				addMassAt(x - notAHalf, y + notAHalf, z + notAHalf, oldMassAtPos);
				addMassAt(x - notAHalf, y + notAHalf, z - notAHalf, oldMassAtPos);
				addMassAt(x - notAHalf, y - notAHalf, z + notAHalf, oldMassAtPos);
				addMassAt(x - notAHalf, y - notAHalf, z - notAHalf, oldMassAtPos);
			}
			if (newMassAtPos > 0D) {
				newMassAtPos /= 9.0D;
				addMassAt(x, y, z, newMassAtPos);
				addMassAt(x + notAHalf, y + notAHalf, z + notAHalf, newMassAtPos);
				addMassAt(x + notAHalf, y + notAHalf, z - notAHalf, newMassAtPos);
				addMassAt(x + notAHalf, y - notAHalf, z + notAHalf, newMassAtPos);
				addMassAt(x + notAHalf, y - notAHalf, z - notAHalf, newMassAtPos);
				addMassAt(x - notAHalf, y + notAHalf, z + notAHalf, newMassAtPos);
				addMassAt(x - notAHalf, y + notAHalf, z - notAHalf, newMassAtPos);
				addMassAt(x - notAHalf, y - notAHalf, z + notAHalf, newMassAtPos);
				addMassAt(x - notAHalf, y - notAHalf, z - notAHalf, newMassAtPos);
			}
		}
	}

	private void addMassAt(float x, float y, float z, float addedMass) {
		VectorVW prevCenterOfMass = new VectorVW(centerOfMass);
		if (mass > .0001F) {
			centerOfMass.multiply(mass);
			centerOfMass.add(new VectorVW(x, y, z).getProduct(addedMass));
			centerOfMass.multiply(1.0D / (mass + addedMass));
		} else {
			centerOfMass = new VectorVW(x, y, z);
			MoITensor = VWRotationMath.getZeroMatrix(3);
		}
		float cmShiftX = prevCenterOfMass.X - centerOfMass.X;
		float cmShiftY = prevCenterOfMass.Y - centerOfMass.Y;
		float cmShiftZ = prevCenterOfMass.Z - centerOfMass.Z;
		float rx = x - centerOfMass.X;
		float ry = y - centerOfMass.Y;
		float rz = z - centerOfMass.Z;

		MoITensor[0] = MoITensor[0] + (cmShiftY * cmShiftY + cmShiftZ * cmShiftZ) * mass
				+ (ry * ry + rz * rz) * addedMass;
		MoITensor[1] = MoITensor[1] - cmShiftX * cmShiftY * mass - rx * ry * addedMass;
		MoITensor[2] = MoITensor[2] - cmShiftX * cmShiftZ * mass - rx * rz * addedMass;
		MoITensor[3] = MoITensor[1];
		MoITensor[4] = MoITensor[4] + (cmShiftX * cmShiftX + cmShiftZ * cmShiftZ) * mass
				+ (rx * rx + rz * rz) * addedMass;
		MoITensor[5] = MoITensor[5] - cmShiftY * cmShiftZ * mass - ry * rz * addedMass;
		MoITensor[6] = MoITensor[2];
		MoITensor[7] = MoITensor[5];
		MoITensor[8] = MoITensor[8] + (cmShiftX * cmShiftX + cmShiftY * cmShiftY) * mass
				+ (rx * rx + ry * ry) * addedMass;

		mass += addedMass;
		invMoITensor = VWRotationMath.inverse3by3(MoITensor);
		// angularVelocity = RotationMatrices.get3by3TransformedVec(oldMOI, torque);
		// angularVelocity = RotationMatrices.get3by3TransformedVec(invMoITensor,
		// torque);
		// System.out.println(MoITensor[0]+":"+MoITensor[1]+":"+MoITensor[2]);
		// System.out.println(MoITensor[3]+":"+MoITensor[4]+":"+MoITensor[5]);
		// System.out.println(MoITensor[6]+":"+MoITensor[7]+":"+MoITensor[8]);
	}

	@Override
	public PhysicsObject getParent() {
		return parent;
	}

	@Override
	public VectorVW getLinearMomentum() {
		return linearMomentum;
	}

	@Override
	public VectorVW getAngularMomentum() {
		return angularMomentum;
	}

	@Override
	public VectorVW getAngularVelocity() {
		return angularVelocity;
	}

	@Override
	public VectorVW getCenterOfMass() {
		return centerOfMass;
	}

	@Override
	public double getMass() {
		return mass;
	}

	@Override
	public float[] getInvFramedMOI() {
		return invFramedMOI;
	}

	@Override
	public float[] getFramedMOI() {
		return framedMOI;
	}

}
