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

package valkyrienwarfare.physicsmanagement;

import java.util.ArrayList;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.border.WorldBorder;
import valkyrienwarfare.ValkyrienWarfareMod;
import valkyrienwarfare.api.VWRotationMath;
import valkyrienwarfare.api.VectorVW;
import valkyrienwarfare.interaction.IDraggable;
import valkyrienwarfare.network.EntityRelativePositionMessage;
import valkyrienwarfare.network.PhysWrapperPositionMessage;

/**
 * Handles ALL functions for moving between Ship coordinates and world
 * coordinates
 *
 * @author thebest108
 */
public class CoordTransformObject {

	public PhysicsObject parent;

	public float[] lToWRotation = VWRotationMath.getFloatIdentity();
	public float[] wToLRotation = VWRotationMath.getFloatIdentity();
	public float[] lToWTransform = VWRotationMath.getFloatIdentity();
	public float[] wToLTransform = VWRotationMath.getFloatIdentity();

	public float[] RlToWRotation = VWRotationMath.getFloatIdentity();
	public float[] RwToLRotation = VWRotationMath.getFloatIdentity();
	public float[] RlToWTransform = VWRotationMath.getFloatIdentity();
	public float[] RwToLTransform = VWRotationMath.getFloatIdentity();

	public float[] prevlToWTransform;
	public float[] prevwToLTransform;
	public float[] prevLToWRotation;
	public float[] prevWToLRotation;

	public VectorVW[] normals = VectorVW.generateAxisAlignedNorms();

	public ShipTransformationStack stack = new ShipTransformationStack();

	public CoordTransformObject(PhysicsObject object) {
		parent = object;
		updateAllTransforms();
		prevlToWTransform = lToWTransform;
		prevwToLTransform = wToLTransform;
	}

	public void updateMatricesOnly() {
		lToWTransform = VWRotationMath.getTranslationMatrix(parent.wrapper.posX, parent.wrapper.posY,
				parent.wrapper.posZ);

		lToWTransform = VWRotationMath.rotateAndTranslate(lToWTransform, parent.wrapper.pitch, parent.wrapper.yaw,
				parent.wrapper.roll, parent.centerCoord);

		lToWRotation = VWRotationMath.getFloatIdentity();

		lToWRotation = VWRotationMath.rotateOnly(lToWRotation, parent.wrapper.pitch, parent.wrapper.yaw,
				parent.wrapper.roll);

		wToLTransform = VWRotationMath.inverse(lToWTransform);
		wToLRotation = VWRotationMath.inverse(lToWRotation);

		RlToWTransform = lToWTransform;
		RwToLTransform = wToLTransform;
		RlToWRotation = lToWRotation;
		RwToLRotation = wToLRotation;
	}

	public void updateRenderMatrices(double x, double y, double z, double pitch, double yaw, double roll) {
		RlToWTransform = VWRotationMath.getTranslationMatrix(x, y, z);

		RlToWTransform = VWRotationMath.rotateAndTranslate(RlToWTransform, pitch, yaw, roll, parent.centerCoord);

		RwToLTransform = VWRotationMath.inverse(RlToWTransform);

		RlToWRotation = VWRotationMath.rotateOnly(VWRotationMath.getFloatIdentity(), pitch, yaw, roll);
		RwToLRotation = VWRotationMath.inverse(RlToWRotation);
	}

	// Used for the moveRiders() method
	public void setPrevMatrices() {
		prevlToWTransform = lToWTransform;
		prevwToLTransform = wToLTransform;
		prevLToWRotation = lToWRotation;
		prevWToLRotation = wToLRotation;
	}

	public void updateAllTransforms() {
		updatePosRelativeToWorldBorder();
		updateMatricesOnly();
		updateParentAABB();
		updateParentNormals();
		updatePassengerPositions();
	}

	/**
	 * Keeps the Ship from exiting the world border
	 */
	public void updatePosRelativeToWorldBorder() {
		WorldBorder border = parent.worldObj.getWorldBorder();
		AxisAlignedBB shipBB = parent.collisionBB;

		if (shipBB.maxX > border.maxX()) {
			parent.wrapper.posX += border.maxX() - shipBB.maxX;
		}
		if (shipBB.minX < border.minX()) {
			parent.wrapper.posX += border.minX() - shipBB.minX;
		}
		if (shipBB.maxZ > border.maxZ()) {
			parent.wrapper.posZ += border.maxZ() - shipBB.maxZ;
		}
		if (shipBB.minZ < border.minZ()) {
			parent.wrapper.posZ += border.minZ() - shipBB.minZ;
		}
	}

	public void updatePassengerPositions() {
		for (Entity entity : parent.wrapper.riddenByEntities) {
			parent.wrapper.updatePassenger(entity);
		}
	}

	public void sendPositionToPlayers() {
		PhysWrapperPositionMessage posMessage = new PhysWrapperPositionMessage(parent.wrapper);

		ArrayList<Entity> entityList = new ArrayList<Entity>();

		for (Entity entity : parent.worldObj.loadedEntityList) {
			if (entity instanceof IDraggable) {
				IDraggable draggable = (IDraggable) entity;
				if (draggable.getWorldBelowFeet() == parent.wrapper) {
					entityList.add(entity);
				}
			}
		}

		EntityRelativePositionMessage otherPositionMessage = new EntityRelativePositionMessage(parent.wrapper,
				entityList);

		for (EntityPlayerMP player : parent.watchingPlayers) {
			ValkyrienWarfareMod.physWrapperNetwork.sendTo(posMessage, player);
			ValkyrienWarfareMod.physWrapperNetwork.sendTo(otherPositionMessage, player);
		}
	}

	public void updateParentNormals() {
		normals = new VectorVW[15];
		// Used to generate Normals for the Axis Aligned World
		VectorVW[] alignedNorms = VectorVW.generateAxisAlignedNorms();
		VectorVW[] rotatedNorms = generateRotationNormals();
		for (int i = 0; i < 6; i++) {
			VectorVW currentNorm = null;
			if (i < 3) {
				currentNorm = alignedNorms[i];
			} else {
				currentNorm = rotatedNorms[i - 3];
			}
			normals[i] = currentNorm;
		}
		int cont = 6;
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				VectorVW norm = normals[i].crossAndUnit(normals[j + 3]);
				normals[cont] = norm;
				cont++;
			}
		}
		for (int i = 0; i < normals.length; i++) {
			if (normals[i].isZero()) {
				normals[i] = new VectorVW(0.0D, 1.0D, 0.0D);
			}
		}
		normals[0] = new VectorVW(1.0D, 0.0D, 0.0D);
		normals[1] = new VectorVW(0.0D, 1.0D, 0.0D);
		normals[2] = new VectorVW(0.0D, 0.0D, 1.0D);
	}

	public VectorVW[] generateRotationNormals() {
		VectorVW[] norms = VectorVW.generateAxisAlignedNorms();
		for (int i = 0; i < 3; i++) {
			VWRotationMath.applyTransform(lToWRotation, norms[i]);
		}
		return norms;
	}

	public VectorVW[] getSeperatingAxisWithShip(PhysicsObject other) {
		// Note: This Vector array still contains potential 0 vectors, those are removed
		// later
		VectorVW[] normals = new VectorVW[15];
		VectorVW[] otherNorms = other.coordTransform.normals;
		VectorVW[] rotatedNorms = normals;
		for (int i = 0; i < 6; i++) {
			if (i < 3) {
				normals[i] = otherNorms[i];
			} else {
				normals[i] = rotatedNorms[i - 3];
			}
		}
		int cont = 6;
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				VectorVW norm = normals[i].crossAndUnit(normals[j + 3]);
				if (!norm.isZero()) {
					normals[cont] = norm;
				} else {
					normals[cont] = normals[1];
				}
				cont++;
			}
		}
		return normals;
	}

	// TODO: FinishME
	public void updateParentAABB() {
		double mnX = 0, mnY = 0, mnZ = 0, mxX = 0, mxY = 0, mxZ = 0;

		VectorVW currentLocation = new VectorVW();

		mnX = mxX = (float) parent.wrapper.posX;
		mnY = mxY = (float) parent.wrapper.posY;
		mnZ = mxZ = (float) parent.wrapper.posZ;

		for (BlockPos pos : parent.blockPositions) {
			currentLocation.X = pos.getX() + .5F;
			currentLocation.Y = pos.getY() + .5F;
			currentLocation.Z = pos.getZ() + .5F;

			fromLocalToGlobal(currentLocation);

			if (currentLocation.X < mnX) {
				mnX = currentLocation.X;
			}
			if (currentLocation.X > mxX) {
				mxX = currentLocation.X;
			}

			if (currentLocation.Y < mnY) {
				mnY = currentLocation.Y;
			}
			if (currentLocation.Y > mxY) {
				mxY = currentLocation.Y;
			}

			if (currentLocation.Z < mnZ) {
				mnZ = currentLocation.Z;
			}
			if (currentLocation.Z > mxZ) {
				mxZ = currentLocation.Z;
			}

		}
		AxisAlignedBB enclosingBB = new AxisAlignedBB(mnX, mnY, mnZ, mxX, mxY, mxZ).grow(1D);// .expand(.6D, .6D, .6D);
		parent.collisionBB = enclosingBB;
		parent.wrapper.boundingBox = (enclosingBB);

		// System.out.println(parent.collisionBB);
	}

	public void fromGlobalToLocal(VectorVW inGlobal) {
		VWRotationMath.applyTransform(wToLTransform, inGlobal);
	}

	public void fromLocalToGlobal(VectorVW inLocal) {
		VWRotationMath.applyTransform(lToWTransform, inLocal);
	}

}
