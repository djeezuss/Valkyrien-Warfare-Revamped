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

package valkyrienwarfare.addon.control.controlsystems;

import java.util.HashSet;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import valkyrienwarfare.addon.control.nodenetwork.Node;
import valkyrienwarfare.addon.control.tileentity.ThrustModulatorTileEntity;
import valkyrienwarfare.addon.control.tileentity.TileEntityNormalEtherCompressor;
import valkyrienwarfare.api.VWRotationMath;
import valkyrienwarfare.api.VectorVW;
import valkyrienwarfare.api.block.ethercompressor.TileEntityEtherCompressor;
import valkyrienwarfare.math.BigBastardMath;
import valkyrienwarfare.physics.IPhysicsManager;

public class ShipPulseImpulseControlSystem {

    public final ThrustModulatorTileEntity parentTile;

    public double linearVelocityBias = 1D;
    public double angularVelocityBias = 50D;
    public double angularConstant = 500000000D;
    public double linearConstant = 1000000D;
    private double bobspeed = 10D;
    private double bobmagnitude = 3D;
    private double totalSecondsRunning = 0D;
    //    public double stabilityBias = .45D;
    private VectorVW normalVector = new VectorVW(0, 1, 0);

    public ShipPulseImpulseControlSystem(ThrustModulatorTileEntity parentTile) {
        this.parentTile = parentTile;

        totalSecondsRunning = Math.random() * bobspeed;
    }

    public void solveThrustValues(IPhysicsManager calculations) {
        double physTickSpeed = calculations.getPhysTickSpeed();
        double totalThrust = 0;

        double totalPotentialThrust = getMaxThrustForAllThrusters();
        double currentThrust = getTotalThrustForAllThrusters();

        float[] rotationMatrix = calculations.getParent().coordTransform.lToWRotation;
        float[] rotationAndTranslationMatrix = calculations.getParent().coordTransform.lToWTransform;
        float[] invRotationAndTranslationMatrix = calculations.getParent().coordTransform.wToLTransform;
        float[] invMOIMatrix = calculations.getInvFramedMOI();

        VectorVW posInWorld = new VectorVW(calculations.getParent().wrapper.posX, calculations.getParent().wrapper.posY, calculations.getParent().wrapper.posZ);
        VectorVW angularVelocity = new VectorVW(calculations.getAngularVelocity());
        VectorVW linearMomentum = new VectorVW(calculations.getLinearMomentum());
        VectorVW linearVelocity = new VectorVW(linearMomentum, (float) calculations.getInvMass());

        BlockPos shipRefrencePos = calculations.getParent().refrenceBlockPos;

        double maxYDelta = parentTile.maximumYVelocity;
        double idealHeight = parentTile.idealYHeight + getBobForTime();

        VectorVW linearMomentumError = getIdealMomentumErrorForSystem(calculations, posInWorld, maxYDelta, idealHeight);

        double engineThrustToChange = linearMomentumError.Y;

        double newTotalThrust = currentThrust + engineThrustToChange;

        if (!(newTotalThrust > 0 && newTotalThrust < totalPotentialThrust)) {
//			System.out.println("Current result impossible");
        }

        double linearThama = 4.5D;
        double angularThama = 1343.5D;

        VectorVW theNormal = new VectorVW(0, 1, 0);

        VectorVW idealNormal = new VectorVW(theNormal);
        VectorVW currentNormal = new VectorVW(theNormal, calculations.getParent().coordTransform.lToWRotation);

        VectorVW currentNormalError = currentNormal.getSubtraction(idealNormal);

        linearVelocityBias = calculations.getPhysTickSpeed();

        for (Node node : getNetworkedNodesList()) {
            if (node.parentTile instanceof TileEntityEtherCompressor && !((TileEntityEtherCompressor) node.parentTile).updateParentShip()) {
                TileEntityEtherCompressor forceTile = (TileEntityEtherCompressor) node.parentTile;

                VectorVW angularVelocityAtNormalPosition = angularVelocity.cross(currentNormalError);

                forceTile.updateTicksSinceLastRecievedSignal();

                //Assume zero change
                double currentErrorY = (posInWorld.Y - idealHeight) + linearThama * (linearMomentum.Y * calculations.getInvMass());

                double currentEngineErrorAngularY = getEngineDistFromIdealAngular(forceTile.getPos(), rotationAndTranslationMatrix, angularVelocity, calculations.getCenterOfMass(), calculations.getPhysTickSpeed());


                VectorVW potentialMaxForce = new VectorVW(0, forceTile.getMaxThrust(), 0);
                potentialMaxForce.multiply(calculations.getInvMass());
                potentialMaxForce.multiply(calculations.getPhysTickSpeed());
                VectorVW potentialMaxThrust = forceTile.getPositionInLocalSpaceWithOrientation().cross(potentialMaxForce);
                VWRotationMath.applyTransform3by3(invMOIMatrix, potentialMaxThrust);
                potentialMaxThrust.multiply(calculations.getPhysTickSpeed());

                double futureCurrentErrorY = currentErrorY + linearThama * potentialMaxForce.Y;
                double futureEngineErrorAngularY = getEngineDistFromIdealAngular(forceTile.getPos(), rotationAndTranslationMatrix, angularVelocity.getAddition(potentialMaxThrust), calculations.getCenterOfMass(), calculations.getPhysTickSpeed());


                boolean doesForceMinimizeError = false;

                if (Math.abs(futureCurrentErrorY) < Math.abs(currentErrorY) && Math.abs(futureEngineErrorAngularY) < Math.abs(currentEngineErrorAngularY)) {
                    doesForceMinimizeError = true;
                    if (Math.abs(linearMomentum.Y * calculations.getInvMass()) > maxYDelta) {
                        if (Math.abs((potentialMaxForce.Y + linearMomentum.Y) * calculations.getInvMass()) > Math.abs(linearMomentum.Y * calculations.getInvMass())) {
                            doesForceMinimizeError = false;
                        }
                    } else {
                        if (Math.abs((potentialMaxForce.Y + linearMomentum.Y) * calculations.getInvMass()) > maxYDelta) {
                            doesForceMinimizeError = false;
                        }
                    }

                }

                if (doesForceMinimizeError) {
                    forceTile.setThrust(forceTile.getMaxThrust());
                    if (Math.abs(currentErrorY) < 1D) {
                        forceTile.setThrust(forceTile.getMaxThrust() * Math.pow(Math.abs(currentErrorY), 3D));
                    }
                } else {
                    forceTile.setThrust(0);
                }

                VectorVW forceOutputWithRespectToTime = forceTile.getForceOutputOriented(calculations.getPhysTickSpeed());
                linearMomentum.add(forceOutputWithRespectToTime);
                VectorVW torque = forceTile.getPositionInLocalSpaceWithOrientation().cross(forceOutputWithRespectToTime);
                VWRotationMath.applyTransform3by3(invMOIMatrix, torque);
                angularVelocity.add(torque);
            }
        }




		/*for(Node node : getNetworkedNodesList()) {
            if(node.parentTile instanceof TileEntityEtherCompressor && !((TileEntityEtherCompressor) node.parentTile).updateParentShip()) {
				TileEntityEtherCompressor forceTile = (TileEntityEtherCompressor) node.parentTile;

				Vector tileForce = getForceForEngine(forceTile, forceTile.getPos(), calculations.invMass, linearMomentum, angularVelocity, rotationAndTranslationMatrix, posInWorld, calculations.centerOfMass, calculations.physTickSpeed);

				tileForce.multiply(1D / calculations.physTickSpeed);

				Vector forcePos = forceTile.getPositionInLocalSpaceWithOrientation();

				double tileForceMagnitude = tileForce.length();

				forceTile.setThrust(BigBastardMath.limitToRange(tileForceMagnitude, 0D, forceTile.getMaxThrust()));

				Vector forceOutputWithRespectToTime = forceTile.getForceOutputOriented(calculations.physTickSpeed);

				linearMomentum.add(forceOutputWithRespectToTime);

				Vector torque = forceTile.getPositionInLocalSpaceWithOrientation().cross(forceOutputWithRespectToTime);
				RotationMatrices.applyTransform3by3(invMOIMatrix, torque);
				angularVelocity.add(torque);
			}
		}*/

        totalSecondsRunning += calculations.getPhysTickSpeed();
    }

    private double getBobForTime() {
        double fraction = totalSecondsRunning / bobspeed;

        double degrees = (fraction * 360D) % 360D;

        double sinVal = Math.sin(Math.toRadians(degrees));

//		sinVal = math.signum(sinVal) * math.pow(math.abs(sinVal), 1.5D);

        return sinVal * bobmagnitude;
    }

    public VectorVW getIdealMomentumErrorForSystem(IPhysicsManager calculations, VectorVW posInWorld, double maxYDelta, double idealHeight) {
        double yErrorDistance = idealHeight - posInWorld.Y;
        double idealYLinearMomentumMagnitude = BigBastardMath.limitToRange(yErrorDistance, -maxYDelta, maxYDelta);
        VectorVW idealLinearMomentum = new VectorVW(0, 1, 0);
        idealLinearMomentum.multiply(idealYLinearMomentumMagnitude * calculations.getMass());

        VectorVW linearMomentumError = calculations.getLinearMomentum().getSubtraction(idealLinearMomentum);

        return linearMomentumError;
    }

    public VectorVW getForceForEngine(TileEntityEtherCompressor engine, BlockPos enginePos, double invMass, VectorVW linearMomentum, VectorVW angularVelocity, float[] rotationAndTranslationMatrix, VectorVW shipPos, VectorVW centerOfMass, double secondsToApply, double idealHeight) {
        double stabilityVal = .145D;

        VectorVW shipVel = new VectorVW(linearMomentum);

        shipVel.multiply(invMass);

        double linearDist = -getControllerDistFromIdealY(rotationAndTranslationMatrix, invMass, shipPos.Y, linearMomentum, idealHeight);
        double angularDist = -getEngineDistFromIdealAngular(enginePos, rotationAndTranslationMatrix, angularVelocity, centerOfMass, secondsToApply);

        engine.angularThrust.Y -= (angularConstant * secondsToApply) * angularDist;
        engine.linearThrust.Y -= (linearConstant * secondsToApply) * linearDist;

        engine.angularThrust.Y = (float) Math.max(engine.angularThrust.Y, 0D);
        engine.linearThrust.Y = (float) Math.max(engine.linearThrust.Y, 0D);

        engine.angularThrust.Y = (float) Math.min(engine.angularThrust.Y, engine.getMaxThrust() * stabilityVal);
        engine.linearThrust.Y = (float) Math.min(engine.linearThrust.Y, engine.getMaxThrust() * (1D - stabilityVal));

        VectorVW aggregateForce = engine.linearThrust.getAddition(engine.angularThrust);
        aggregateForce.multiply(secondsToApply);

        return aggregateForce;
    }

    public double getEngineDistFromIdealAngular(BlockPos enginePos, float[] lToWRotation, VectorVW angularVelocity, VectorVW centerOfMass, double secondsToApply) {
        BlockPos pos = parentTile.getPos();

        VectorVW controllerPos = new VectorVW(pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D);
        VectorVW enginePosVec = new VectorVW(enginePos.getX() + .5D, enginePos.getY() + .5D, enginePos.getZ() + .5D);

        controllerPos.subtract(centerOfMass);
        enginePosVec.subtract(centerOfMass);

        VectorVW unOrientedPosDif = new VectorVW(enginePosVec.X - controllerPos.X, enginePosVec.Y - controllerPos.Y, enginePosVec.Z - controllerPos.Z);

        double idealYDif = unOrientedPosDif.dot(normalVector);

        VWRotationMath.doRotationOnly(lToWRotation, controllerPos);
        VWRotationMath.doRotationOnly(lToWRotation, enginePosVec);

        double inWorldYDif = enginePosVec.Y - controllerPos.Y;

        VectorVW angularVelocityAtPoint = angularVelocity.cross(enginePosVec);
        angularVelocityAtPoint.multiply(secondsToApply);

        return idealYDif - (inWorldYDif + angularVelocityAtPoint.Y * angularVelocityBias);
    }

    public double getControllerDistFromIdealY(float[] lToWTransform, double invMass, double posY, VectorVW linearMomentum, double idealHeight) {
        BlockPos pos = parentTile.getPos();
        VectorVW controllerPos = new VectorVW(pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D);
        controllerPos.transform(lToWTransform);
        return idealHeight - (posY + (linearMomentum.Y * invMass * linearVelocityBias));
    }

    public double getTotalThrustForAllThrusters() {
        double totalThrust = 0D;
        for (Node otherNode : getNetworkedNodesList()) {
            TileEntity nodeTile = otherNode.parentTile;
            if (nodeTile instanceof TileEntityNormalEtherCompressor) {
                TileEntityNormalEtherCompressor ether = (TileEntityNormalEtherCompressor) nodeTile;
                totalThrust += ether.getThrust();
            }
        }
        return totalThrust;
    }

    public double getMaxThrustForAllThrusters() {
        double totalThrustAvaliable = 0D;
        for (Node otherNode : getNetworkedNodesList()) {
            TileEntity nodeTile = otherNode.parentTile;
            if (nodeTile instanceof TileEntityNormalEtherCompressor) {
                TileEntityNormalEtherCompressor ether = (TileEntityNormalEtherCompressor) nodeTile;
                totalThrustAvaliable += ether.getMaxThrust();
            }
        }
        return totalThrustAvaliable;
    }

    private HashSet<Node> getNetworkedNodesList() {
        return parentTile.tileNode.getNodeNetwork().networkedNodes;
    }

}
