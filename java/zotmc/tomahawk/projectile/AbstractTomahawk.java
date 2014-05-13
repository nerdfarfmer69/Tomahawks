package zotmc.tomahawk.projectile;

import static net.minecraft.util.MathHelper.clamp_float;
import static net.minecraft.util.MathHelper.floor_double;
import static net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK;
import static net.minecraftforge.common.util.ForgeDirection.DOWN;
import static net.minecraftforge.common.util.ForgeDirection.UP;
import static zotmc.tomahawk.projectile.AbstractTomahawk.State.IN_AIR;
import static zotmc.tomahawk.projectile.AbstractTomahawk.State.IN_GROUND;
import static zotmc.tomahawk.projectile.AbstractTomahawk.State.ON_GROUND;
import static zotmc.tomahawk.util.Utils.PI;
import static zotmc.tomahawk.util.Utils.atan2;
import static zotmc.tomahawk.util.Utils.sqrt;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import zotmc.tomahawk.util.FieldAccess;
import zotmc.tomahawk.util.Obfs;

public abstract class AbstractTomahawk extends EntityArrow {
	
	private static final Field
	X = findField("field_145791_d"),
	Y = findField("field_145792_e"),
	Z = findField("field_145789_f"),
	TICKS_IN_GROUND = findField("ticksInGround", "field_70252_j"),
	TICKS_IN_AIR = findField("ticksInAir", "field_70257_an"),
	GROUND = findField("field_145790_g"),
	THROWER = findField("shootingEntity", "field_70250_c");
	
	protected final FieldAccess<Integer>
	x = FieldAccess.of(X, this),
	y = FieldAccess.of(Y, this),
	z = FieldAccess.of(Z, this),
	ticksInGround = FieldAccess.of(TICKS_IN_GROUND, this),
	ticksInAir = FieldAccess.of(TICKS_IN_AIR, this);
	protected final FieldAccess<Block>
	ground = FieldAccess.of(GROUND, this);
	protected final FieldAccess<Entity>
	thrower = FieldAccess.of(THROWER, this);
	
	private static Field findField(String... names) {
		return Obfs.findField(EntityArrow.class, names);
	}
	
	
	
	public enum State {
		IN_AIR,
		IN_GROUND,
		NO_REBOUNCE,
		ON_GROUND;
	}

	public AbstractTomahawk(World world) {
		super(world);
	}
	public AbstractTomahawk(World world, double x, double y, double z, float initialSpeed) {
		super(world, x, y, z);
		setSize(0.5F, 0.75F);
		setThrowableHeading(motionX, motionY, motionZ, initialSpeed * 1.5F, 1);
	}
	public AbstractTomahawk(World world, EntityLivingBase thrower, float initialSpeed) {
		super(world, thrower, initialSpeed);
		setSize(0.5F, 0.75F);
	}
	
	@Override protected void entityInit() {
		super.entityInit();
		
		getDataWatcher().addObject(5, (byte) 0);
	}
	
	protected void setState(State state) {
		getDataWatcher().updateObject(5, (byte) state.ordinal());
	}
	private void setState(byte state) {
		getDataWatcher().updateObject(5, state);
	}
	protected State getState() {
		return State.values()[getDataWatcher().getWatchableObjectByte(5)];
	}
	private byte getStateByte() {
		return getDataWatcher().getWatchableObjectByte(5);
	}
	
	@Override public void writeEntityToNBT(NBTTagCompound tags) {
		super.writeEntityToNBT(tags);
		
		tags.setByte("state", getStateByte());
	}
	@Override public void readEntityFromNBT(NBTTagCompound tags) {
		super.readEntityFromNBT(tags);
		
		setState(tags.getByte("state"));
	}
	
	public Entity getThrower() {
		return thrower.get();
	}
	
	protected int getLifespan() {
		return 1200;
	}
	
	
	
	@Override public void onUpdate() {
		onEntityUpdate();
	}
	
	protected boolean onLifespanTick(int lifespan) {
		if (ticksInGround.get() >= lifespan) {
			setDead();
			
			return true;
		}
		return false;
	}
	
	@Override public void onEntityUpdate() {
		lastTickPosX = posX;
		lastTickPosY = posY;
		lastTickPosZ = posZ;
		
		super.onEntityUpdate();
		
		/*
		if (arrowShake > 0)
			--arrowShake;
		*/
		
		
		switch (getState()) {
		case IN_GROUND:
			if (worldObj.getBlock(x.get(), y.get(), z.get()) == ground.get()) {
				ticksInGround.set(ticksInGround.get() + 1);
				
				onLifespanTick(getLifespan());
				return;
			}

			setState(IN_AIR);
			motionX *= rand.nextFloat() * 0.2F;
			motionY *= rand.nextFloat() * 0.2F;
			motionZ *= rand.nextFloat() * 0.2F;
			ticksInGround.set(0);
			ticksInAir.set(0);
			
			break;
			
		case NO_REBOUNCE:
			if (onGround)
				setState(ON_GROUND);
			
			ticksInAir.set(ticksInAir.get() + 1);
			
		case ON_GROUND:
			ticksInGround.set(ticksInGround.get() + 1);
			if (onLifespanTick(getLifespan()))
				return;
			
			double hv2 = motionX * motionX + motionZ * motionZ;
			double v2 = hv2 + motionY * motionY;
			float resistance = !onGround ? getDragFactor() : worldObj
					.getBlock(floor_double(posX), floor_double(boundingBox.minY) - 1, floor_double(posZ))
					.slipperiness * 0.98F;
			
			onMotionTick(sqrt(hv2), sqrt(v2), resistance);
			
			noClip = func_145771_j(posX, (boundingBox.minY + boundingBox.maxY) / 2.0D, posZ);
			moveEntity(motionX, motionY, motionZ);

			if (onGround)
				this.motionY *= -0.5D;
			
			setPosition(posX, posY, posZ);
			func_145775_I();
			
			return;
			
		default:
			ticksInAir.set(ticksInAir.get() + 1);
			
		}
		
		
		Vec3 pos = worldObj.getWorldVec3Pool().getVecFromPool(posX, posY, posZ);
		Vec3 pos1 = worldObj.getWorldVec3Pool().getVecFromPool(
				posX + motionX, posY + motionY, posZ + motionZ);
		
		MovingObjectPosition mop = worldObj.func_147447_a(pos, pos1, false, true, false);
		
		pos = worldObj.getWorldVec3Pool().getVecFromPool(posX, posY, posZ);
		pos1 = worldObj.getWorldVec3Pool().getVecFromPool(
				posX + motionX, posY + motionY, posZ + motionZ);
		
		if (mop != null)
			pos1 = worldObj.getWorldVec3Pool().getVecFromPool(
					mop.hitVec.xCoord, mop.hitVec.yCoord, mop.hitVec.zCoord);
		

		if (!worldObj.isRemote) {
			
			Entity nearest = null;
			MovingObjectPosition nearestIntercept = null;
			
			@SuppressWarnings("unchecked")
			List<Entity> onTrack = worldObj.getEntitiesWithinAABBExcludingEntity(
					this, boundingBox.addCoord(motionX, motionY, motionZ).expand(1, 1, 1));
			
			double min = 0;
			Entity thrower = getThrower();

			for (int i = 0; i < onTrack.size(); i++) {
				Entity candidate = onTrack.get(i);
				
				if (candidate.isEntityAlive() && candidate.canBeCollidedWith()
						&& (candidate != thrower || ticksInAir.get() >= 5)) {
					float f = 0.3F;
					AxisAlignedBB aabb = candidate.boundingBox.expand(f, f, f);
					MovingObjectPosition intersect = calculateIntercept(worldObj, aabb, pos, pos1);
					
					if (intersect != null) {
						double d = pos.distanceTo(intersect.hitVec);
						
						if (d < min || min == 0) {
							nearest = candidate;
							nearestIntercept = intersect;
							min = d;
						}
					}
				}
			}
			
			if (nearest != null) {
				mop = new MovingObjectPosition(nearest, nearestIntercept.hitVec);
				mop.hitInfo = nearestIntercept.hitInfo;
			}
		}
		
		if (mop != null)
			/*
			if (mop.typeOfHit == BLOCK
					&& worldObj.getBlock(mop.blockX, mop.blockY, mop.blockZ) == Blocks.portal)
				setInPortal();
			else
				*/
				onImpact(mop);
		
		
		posX += motionX;
		posY += motionY;
		posZ += motionZ;
		
		float hv = sqrt(motionX * motionX + motionZ * motionZ);
		rotationYaw = atan2(motionX, motionZ) * 180 / PI;
		
		for (rotationPitch = atan2(motionY, hv) * 180 / PI;
				rotationPitch - prevRotationPitch < -180;
				prevRotationPitch -= 360.0F);
		
		while (rotationPitch - prevRotationPitch >= 180)
			prevRotationPitch += 360;
		
		while (rotationYaw - prevRotationYaw < -180)
			prevRotationYaw -= 360;
		
		while (rotationYaw - prevRotationYaw >= 180)
			prevRotationYaw += 360;
		
		rotationPitch = prevRotationPitch + (rotationPitch - prevRotationPitch) * 0.2F;
		rotationYaw = prevRotationYaw + (rotationYaw - prevRotationYaw) * 0.2F;
		
		
		float v = sqrt(hv * hv + motionY * motionY);
		onMotionTick(hv, v, getDragFactor());
		
		setPosition(posX, posY, posZ);
		func_145775_I();
		
	}
	
	public static MovingObjectPosition calculateIntercept(World world, AxisAlignedBB aabb, Vec3 a, Vec3 b) {
		if (aabb.isVecInside(a))
			return null;
		
		MovingObjectPosition ret = aabb.calculateIntercept(a, b);
		
		if (ret != null) {
			Vec3 normal;
			if (ret.sideHit == UP.ordinal())
				normal = world.getWorldVec3Pool().getVecFromPool(0, 1, 0);
			else if (ret.sideHit == DOWN.ordinal())
				normal = world.getWorldVec3Pool().getVecFromPool(0, -1, 0);
			else {
				Vec3 hba = world.getWorldVec3Pool()
						.getVecFromPool(
								a.xCoord - b.xCoord,
								0,
								a.zCoord - b.zCoord)
						.normalize();
				
				normal = ret.hitVec
						.addVector(
								-(aabb.minX + aabb.maxX) / 2,
								-ret.hitVec.yCoord,
								-(aabb.minZ + aabb.maxZ) / 2)
						.normalize();
				
				normal = normal
					.addVector(
							hba.xCoord * 2.5,
							0,
							hba.zCoord * 2.5)
					.normalize();
			}
			
			ret.hitInfo = normal;
		}
		
		return ret;
	}
	
	protected void onMotionTick(float hv, float v, float resistense) {
		float r = MathHelper.clamp_float(1 - resistense / v, 0, 1);
		float g = getGravity();

		if (isInWater()) {
			for (int i = 0; i < 4; ++i) {
				float tail = 0.25F;
				worldObj.spawnParticle("bubble",
						posX - motionX * tail, posY - motionY * tail, posZ - motionZ * tail,
						motionX, motionY, motionZ);
			}

			r *= 0.8F;
		}

		motionX *= r;
		motionY *= r;
		motionZ *= r;
		motionY -= g;
		
	}
	
	
	protected void onImpact(MovingObjectPosition mop) {
		if (mop.typeOfHit == BLOCK) {
			x.set(mop.blockX);
			y.set(mop.blockY);
			z.set(mop.blockZ);
			ground.set(worldObj.getBlock(x.get(), y.get(), z.get()));
			
			motionX = mop.hitVec.xCoord - posX;
			motionY = mop.hitVec.yCoord - posY;
			motionZ = mop.hitVec.zCoord - posZ;
			double v2 = motionX * motionX + motionY * motionY + motionZ * motionZ;
			float v = sqrt(v2);
			posX -= motionX / v * 0.05;
			posY -= motionY / v * 0.05;
			posZ -= motionZ / v * 0.05;
			
			playHitSound(true, ground.get(),
					ground.get().getBlockHardness(worldObj, x.get(), y.get(), z.get()));
			setState(IN_GROUND);
			
			if (ground.get().getMaterial() != Material.air)
				ground.get().onEntityCollidedWithBlock(this.worldObj, x.get(), y.get(), z.get(), this);
			
		}
		
	}
	
	
	protected void playHitSound(boolean isStationary, Block block, float hardness) {
		if (!worldObj.isRemote)
			playSound(
					"random.bowhit",
					1.4F,
					(1.2F / rand.nextFloat() * 0.2F + 0.9F) * 3/5F);
	}
	
	protected void playInAirSound(double v2) {
		if (!worldObj.isRemote)
			playSound(
					"random.bow",
					clamp_float((float) v2 * 16, 0, 1),
					1 / (rand.nextFloat() * 0.4F + 1.2F) + 0.5F);
	}
	
	
	protected void rebounce(MovingObjectPosition mop, double reactFactor, boolean onEntity) {
		if (onEntity) {
			posX += motionX;
			posY += motionY;
			posZ += motionZ;
		}
		
		Vec3 nVec = (Vec3) mop.hitInfo;
		/*
		if (!worldObj.isRemote)
			phy4j().debug("Rebounce normal: %s", nVec);
		*/
		
		Vec3 vVec = worldObj.getWorldVec3Pool().getVecFromPool(motionX, motionY, motionZ);
		
		onRebounce(mop, nVec.yCoord, nVec.lengthVector(), -reactFactor * nVec.dotProduct(vVec));
		
		double r = 2 * (motionX * nVec.xCoord + motionY * nVec.yCoord + motionZ * nVec.zCoord);
		motionX = reactFactor * (motionX - r * nVec.xCoord);
		motionY = reactFactor * (motionY - r * nVec.yCoord);
		motionZ = reactFactor * (motionZ - r * nVec.zCoord);
		
	}
	
	protected abstract void onRebounce(MovingObjectPosition mop, double nY, double n, double react);
	
	
	
	protected float getDragFactor() {
		return 0.0375F;
	}
	
	protected float getGravity() {
		return 0.1200F;
	}
	
}