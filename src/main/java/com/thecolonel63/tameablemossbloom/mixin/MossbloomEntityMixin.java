package com.thecolonel63.tameablemossbloom.mixin;

import net.emilsg.clutter.entity.custom.MossbloomEntity;
import net.emilsg.clutter.entity.custom.parent.ClutterAnimalEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.server.ServerConfigHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.EntityView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("WrongEntityDataParameterClass")
@Mixin(MossbloomEntity.class)
public class MossbloomEntityMixin extends ClutterAnimalEntity implements Tameable, Saddleable, JumpingMount {
    protected MossbloomEntityMixin(EntityType<? extends AnimalEntity> entityType, World world) {
        super(entityType, world);
    }

    @Unique
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(MossbloomEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    @Unique
    private static final TrackedData<Boolean> SADDLED = DataTracker.registerData(MossbloomEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    @Unique
    private static final Ingredient TAMING_INGREDIENT = Ingredient.ofItems(Items.GLOW_BERRIES);
    @Unique
    private float jumpStrength;

    @Unique
    private void setOwnerUUID(@Nullable UUID owner) {
        this.dataTracker.set(OWNER, Optional.ofNullable(owner));
    }

    @Unique
    private void setSaddled(boolean saddled) {
        this.dataTracker.set(SADDLED, saddled);
    }

    @Unique
    protected void spawnHeartParticles() {
        ParticleEffect particleEffect = ParticleTypes.HEART;

        for (int i = 0; i < 7; ++i) {
            double d = this.random.nextGaussian() * 0.02;
            double e = this.random.nextGaussian() * 0.02;
            double f = this.random.nextGaussian() * 0.02;
            this.getWorld().addParticle(particleEffect, this.getParticleX(1.0), this.getRandomBodyY() + 0.5, this.getParticleZ(1.0), d, e, f);
        }
    }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void onInitGoals(CallbackInfo ci) {
        this.goalSelector.add(4, new TemptGoal(this, 1.25, TAMING_INGREDIENT, false));
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void onInitDataTracker(CallbackInfo ci) {
        this.dataTracker.startTracking(OWNER, Optional.empty());
        this.dataTracker.startTracking(SADDLED, false);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void onReadNBT(NbtCompound nbt, CallbackInfo ci) {
        UUID owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : ServerConfigHandler.getPlayerUuidByName(this.getServer(), nbt.getString("Owner"));
        if (owner != null) {
            this.setOwnerUUID(owner);
        }
        this.setSaddled(nbt.getBoolean("Saddle"));
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void onWriteNBT(NbtCompound nbt, CallbackInfo ci) {
        if (this.getOwnerUuid() != null) {
            nbt.putUuid("Owner", this.getOwnerUuid());
        }
        nbt.putBoolean("Saddle", this.isSaddled());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        this.jumping = false;
        if (!this.getWorld().isClient() && this.isSubmergedInWater() && this.getControllingPassenger() instanceof PlayerEntity playerEntity && playerEntity.isSubmergedInWater())
            playerEntity.stopRiding();
    }

    @Override
    protected void dropInventory() {
        super.dropInventory();
        if (this.isSaddled()) {
            this.dropItem(Items.SADDLE);
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        Item item = stack.getItem();
        if (TAMING_INGREDIENT.test(stack)) {
            if (this.getOwnerUuid() == null) {
                if (this.getWorld().isClient()) {
                    return ActionResult.SUCCESS;
                } else {
                    this.eat(player, hand, stack);
                    this.setPersistent();
                    this.setOwnerUUID(player.getUuid());
                    this.getWorld().sendEntityStatus(this, EntityStatuses.ADD_POSITIVE_PLAYER_REACTION_PARTICLES);
                    return ActionResult.CONSUME;
                }
            } else if (this.getHealth() < this.getMaxHealth() && item.getFoodComponent() != null) {
                if (!this.getWorld().isClient) {
                    if (!player.getAbilities().creativeMode) {
                        stack.decrement(1);
                    }
                    this.heal(stack.getItem().getFoodComponent().getHunger());
                }

                return ActionResult.success(this.getWorld().isClient);
            }
        } else if (this.isSaddled() && !this.hasPassengers() && !player.shouldCancelInteraction()) {
            if (!this.getWorld().isClient) {
                player.startRiding(this);
            }
            return ActionResult.success(this.getWorld().isClient);
        }
        return super.interactMob(player, hand);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return this.isSaddled() && this.getFirstPassenger() instanceof PlayerEntity entity ? entity : null;
    }

    @Override
    public void tickControlled(PlayerEntity controllingPlayer, Vec3d movementInput) {
        super.tickControlled(controllingPlayer, movementInput);
        this.setRotation(controllingPlayer.getYaw(), controllingPlayer.getPitch() * 0.5F);
        this.prevYaw = this.bodyYaw = this.headYaw = this.getYaw();
        if (this.isLogicalSideForUpdatingMovement() && this.isOnGround()) {
            this.jump(this.jumpStrength, movementInput);
            this.jumpStrength = 0.0f;
        }
    }

    @Unique
    protected void jump(float strength, Vec3d movementInput) {
        double d = 0.7f * (double) strength * (double) this.getJumpVelocityMultiplier();
        double e = d + (double) this.getJumpBoostVelocityModifier();
        Vec3d vec3d = this.getVelocity();
        this.setVelocity(vec3d.x, e, vec3d.z);
        this.velocityDirty = true;
        if (movementInput.z > 0.0) {
            float f = MathHelper.sin(this.getYaw() * (float) (Math.PI / 180.0));
            float g = MathHelper.cos(this.getYaw() * (float) (Math.PI / 180.0));
            this.setVelocity(this.getVelocity().add(-0.4F * f * strength, 0.0, 0.4F * g * strength));
        }
    }

    @Override
    protected Vec3d getControlledMovementInput(PlayerEntity controllingPlayer, Vec3d movementInput) {
        float sidewaysSpeed = controllingPlayer.sidewaysSpeed * 0.5f;
        float forwardSpeed = controllingPlayer.forwardSpeed;
        forwardSpeed = forwardSpeed < 0 ? forwardSpeed * 0.25f : forwardSpeed;
        return new Vec3d(sidewaysSpeed, 0.0, forwardSpeed);
    }

    @Override
    protected float getSaddledSpeed(PlayerEntity controllingPlayer) {
        return (float) this.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
    }

    @Override
    public EntityView method_48926() {
        return this.getWorld();
    }

    @Override
    public boolean canBeSaddled() {
        return this.getOwnerUuid() != null && !this.isBaby();
    }

    @Override
    public void saddle(@Nullable SoundCategory sound) {
        this.setSaddled(true);
        if (sound != null) {
            this.getWorld().playSoundFromEntity(null, this, SoundEvents.ENTITY_HORSE_SADDLE, sound, 0.5f, 1.0f);
        }
    }

    @Override
    public boolean isSaddled() {
        return this.dataTracker.get(SADDLED);
    }

    @Override
    public void handleStatus(byte status) {
        if (status == EntityStatuses.ADD_POSITIVE_PLAYER_REACTION_PARTICLES) {
            this.spawnHeartParticles();
        } else {
            super.handleStatus(status);
        }
    }

    @Nullable
    @Override
    public UUID getOwnerUuid() {
        return this.dataTracker.get(OWNER).orElse(null);
    }

    @Override
    public void setJumpStrength(int strength) {
        if (this.isSaddled() && !this.isTouchingWater()) {
            if (strength < 0) {
                strength = 0;
            } else {
                this.jumping = true;
            }

            if (strength >= 90) {
                this.jumpStrength = 1.0F;
            } else {
                this.jumpStrength = 0.4F + 0.4F * (float) strength / 90.0F;
            }
        }
    }

    @Override
    public boolean canJump() {
        return this.isSaddled();
    }

    @Override
    public void startJumping(int height) {
        this.jumping = true;
    }

    @Override
    public void stopJumping() {
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        int i = MathHelper.ceil((fallDistance * 0.5F - 3.0F) * damageMultiplier);
        if (i < 0) return false;
        this.damage(damageSource, (float) i);
        if (this.hasPassengers()) {
            for (Entity entity : this.getPassengersDeep()) {
                entity.damage(damageSource, (float) i);
            }
        }
        this.playBlockFallSound();
        return true;
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        Vec3d vec3d = getPassengerDismountOffset(
                this.getWidth(), passenger.getWidth(), this.getYaw() + (passenger.getMainArm() == Arm.RIGHT ? 90.0F : -90.0F)
        );
        Vec3d vec3d2 = this.locateSafeDismountingPos(vec3d, passenger);
        if (vec3d2 != null) {
            return vec3d2;
        } else {
            Vec3d vec3d3 = getPassengerDismountOffset(
                    this.getWidth(), passenger.getWidth(), this.getYaw() + (passenger.getMainArm() == Arm.LEFT ? 90.0F : -90.0F)
            );
            Vec3d vec3d4 = this.locateSafeDismountingPos(vec3d3, passenger);
            return vec3d4 != null ? vec3d4 : this.getPos();
        }
    }

    @Unique
    @Nullable
    private Vec3d locateSafeDismountingPos(Vec3d offset, LivingEntity passenger) {
        double d = this.getX() + offset.x;
        double e = this.getBoundingBox().minY;
        double f = this.getZ() + offset.z;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (EntityPose entityPose : passenger.getPoses()) {
            mutable.set(d, e, f);
            double g = this.getBoundingBox().maxY + 0.75;

            do {
                double h = this.getWorld().getDismountHeight(mutable);
                if ((double) mutable.getY() + h > g) {
                    break;
                }

                if (Dismounting.canDismountInBlock(h)) {
                    Box box = passenger.getBoundingBox(entityPose);
                    Vec3d vec3d = new Vec3d(d, (double) mutable.getY() + h, f);
                    if (Dismounting.canPlaceEntityAt(this.getWorld(), passenger, box.offset(vec3d))) {
                        passenger.setPose(entityPose);
                        return vec3d;
                    }
                }

                mutable.move(Direction.UP);
            } while (!((double) mutable.getY() < g));
        }

        return null;
    }
}
