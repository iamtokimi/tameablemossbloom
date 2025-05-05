package com.thecolonel63.tameableminisheep.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.satisfy.wildernature.entity.MiniSheepEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("WrongEntityDataParameterClass")
@Mixin(MiniSheepEntity.class)
public abstract class MiniSheepMixin extends Animal implements OwnableEntity, Saddleable, PlayerRideableJumping {
    @Shadow
    public abstract boolean isSheared();

    protected MiniSheepMixin(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(MiniSheepEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    @Unique
    private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(MiniSheepEntity.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final Ingredient TAMING_INGREDIENT = Ingredient.of(Items.GRASS);
    @Unique
    private static final int HEAL_AMOUNT = 2;
    @Unique
    private float jumpStrength;

    //MiniSheep Start
    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void onRegisterGoals(CallbackInfo ci) {
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.1, TAMING_INGREDIENT, false));
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void onDefineSynchedData(CallbackInfo ci) {
        this.entityData.define(OWNER, Optional.empty());
        this.entityData.define(SADDLED, false);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void onReadAdditionalSaveData(CompoundTag compoundTag, CallbackInfo ci) {
        UUID owner = compoundTag.hasUUID("Owner") ? compoundTag.getUUID("Owner") : OldUsersConverter.convertMobOwnerIfNecessary(this.getServer(), compoundTag.getString("Owner"));
        if (owner != null) this.setOwnerUUID(owner);
        this.setSaddled(compoundTag.getBoolean("Saddle"));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void onAddAdditionalSaveData(CompoundTag compoundTag, CallbackInfo ci) {
        if (this.getOwnerUUID() != null) compoundTag.putUUID("Owner", this.getOwnerUUID());
        compoundTag.putBoolean("Saddle", this.isSaddled());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        this.jumping = false;
        if (!this.level().isClientSide() && this.isUnderWater() && this.getControllingPassenger() instanceof Player player && player.isUnderWater())
            player.stopRiding();
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void onMobInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (TAMING_INGREDIENT.test(stack)) {
            if (this.getOwnerUUID() == null) {
                if (this.level().isClientSide()) {
                    cir.setReturnValue(InteractionResult.SUCCESS);
                } else {
                    this.usePlayerItem(player, hand, stack);
                    if (this.random.nextInt(4) == 0) {
                        this.setOwnerUUID(player.getUUID());
                        this.setPersistenceRequired();
                        this.level().broadcastEntityEvent(this, EntityEvent.TAMING_SUCCEEDED);
                    }
                    cir.setReturnValue(InteractionResult.CONSUME);
                }
            } else if (this.getHealth() < this.getMaxHealth()) {
                if (!this.level().isClientSide()) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    this.heal(HEAL_AMOUNT);
                }

                cir.setReturnValue(InteractionResult.sidedSuccess(this.level().isClientSide()));
            }
        } else if (this.isSaddled() && !this.isVehicle() && !player.isSecondaryUseActive()) {
            if (!this.level().isClientSide()) {
                player.startRiding(this);
            }
            cir.setReturnValue(InteractionResult.sidedSuccess(this.level().isClientSide()));
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void onHandleEntityEvent(byte event, CallbackInfo ci) {
        if (event == EntityEvent.TAMING_SUCCEEDED) {
            this.spawnHearts();
            ci.cancel();
        }
    }
    //MiniSheep End

    //Animal start
    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (this.isSaddled()) this.spawnAtLocation(Items.SADDLE);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return this.isSaddled() && this.getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    public void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        this.setRot(player.getYRot(), player.getXRot() * 0.5F);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
        if (this.isControlledByLocalInstance() && this.onGround()) {
            this.jump(this.jumpStrength, travelVector);
            this.jumpStrength = 0.0f;
        }
    }

    @Override
    public Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        float sidewaysSpeed = player.xxa * 0.5f;
        float forwardSpeed = player.zza;
        forwardSpeed = forwardSpeed < 0 ? forwardSpeed * 0.25f : forwardSpeed;
        return new Vec3(sidewaysSpeed, 0.0, forwardSpeed);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        int i = Mth.ceil((fallDistance * 0.5F - 3.0F) * multiplier);
        if (i < 0) return false;
        this.hurt(source, i);
        if (this.isVehicle()) {
            for (Entity entity : this.getIndirectPassengers()) {
                entity.hurt(source, i);
            }
        }
        this.playBlockFallSound();
        return true;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 vec3 = getCollisionHorizontalEscapeVector(
                this.getBbWidth(), passenger.getBbWidth(), this.getYRot() + (passenger.getMainArm() == HumanoidArm.RIGHT ? 90.0F : -90.0F)
        );
        Vec3 vec32 = this.getDismountLocationInDirection(vec3, passenger);
        if (vec32 != null) {
            return vec32;
        } else {
            Vec3 vec33 = getCollisionHorizontalEscapeVector(
                    this.getBbWidth(), passenger.getBbWidth(), this.getYRot() + (passenger.getMainArm() == HumanoidArm.LEFT ? 90.0F : -90.0F)
            );
            Vec3 vec34 = this.getDismountLocationInDirection(vec33, passenger);
            return vec34 != null ? vec34 : this.position();
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (this.hasPassenger(passenger)) {
            double shiftAmount = -7.0 / 16.0;
            double shiftX = -Mth.sin(this.getYRot() * Mth.TWO_PI / 360.0f) * shiftAmount;
            double shiftZ = Mth.cos(this.getYRot() * Mth.TWO_PI / 360.0f) * shiftAmount;

            double d = this.getY() + this.getPassengersRidingOffset() + passenger.getMyRidingOffset() + -1.0 / 16 + (this.isSheared() ? -1.0 / 16.0 : 0);
            callback.accept(passenger, this.getX() + shiftX, d, this.getZ() + shiftZ);
        }
    }
    //Animal end

    //OwnableEntity Start
    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER).orElse(null);
    }

    @Unique
    private void setOwnerUUID(@Nullable UUID ownerUUID) {
        this.entityData.set(OWNER, Optional.ofNullable(ownerUUID));
    }
    //OwnableEntity End

    //Saddleable Start
    @Override
    public boolean isSaddleable() {
        return this.getOwnerUUID() != null && !this.isBaby();
    }

    @Override
    public void equipSaddle(@Nullable SoundSource source) {
        this.setSaddled(true);
        if (source != null) this.level().playSound(null, this, SoundEvents.HORSE_SADDLE, source, 0.5F, 1.0F);
    }

    @Override
    public boolean isSaddled() {
        return this.entityData.get(SADDLED);
    }

    @Unique
    private void setSaddled(boolean saddled) {
        this.entityData.set(SADDLED, saddled);
    }
    //Saddleable End

    //PlayerRideableJumping Start
    @Override
    public void onPlayerJump(int jumpPower) {
        if (this.isSaddled() && !this.isInWater()) {
            if (jumpPower < 0) {
                jumpPower = 0;
            } else {
                this.jumping = true;
            }
        }

        if (jumpPower >= 90) {
            this.jumpStrength = 1.0F;
        } else {
            this.jumpStrength = 0.4F + 0.4F * (float) jumpPower / 90.0F;
        }
    }

    @Override
    public boolean canJump() {
        return this.isSaddled();
    }

    @Override
    public void handleStartJump(int jumpPower) {
        this.jumping = true;
    }
    //PlayerRideableJumping end

    @Unique
    protected void jump(float strength, Vec3 travelVector) {
        double d = 0.7f * strength * this.getBlockJumpFactor();
        double e = d + this.getJumpBoostPower();
        Vec3 vec3 = this.getDeltaMovement();
        this.setDeltaMovement(vec3.x, e, vec3.z);
        this.hasImpulse = true;
        if (travelVector.z > 0.0) {
            float f = Mth.sin(this.getYRot() * 0.017453292F);
            float g = Mth.cos(this.getYRot() * 0.017453292F);
            this.setDeltaMovement(this.getDeltaMovement().add(-0.4F * f * strength, 0.0, 0.4F * g * strength));
        }
    }

    @Unique
    private void spawnHearts() {
        ParticleOptions particleOptions = ParticleTypes.HEART;

        for (int i = 0; i < 7; ++i) {
            double d = this.random.nextGaussian() * 0.02;
            double e = this.random.nextGaussian() * 0.02;
            double f = this.random.nextGaussian() * 0.02;
            this.level().addParticle(particleOptions, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), d, e, f);
        }
    }

    @Unique
    @Nullable
    private Vec3 getDismountLocationInDirection(Vec3 direction, LivingEntity passenger) {
        double d = this.getX() + direction.x;
        double e = this.getBoundingBox().minY;
        double f = this.getZ() + direction.z;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Pose pose : passenger.getDismountPoses()) {
            mutableBlockPos.set(d, e, f);
            double g = this.getBoundingBox().maxY + 0.75;

            do {
                double h = this.level().getBlockFloorHeight(mutableBlockPos);
                if (mutableBlockPos.getY() + h > g) {
                    break;
                }

                if (DismountHelper.isBlockFloorValid(h)) {
                    AABB aABB = passenger.getLocalBoundsForPose(pose);
                    Vec3 vec3 = new Vec3(d, mutableBlockPos.getY() + h, f);
                    if (DismountHelper.canDismountTo(this.level(), passenger, aABB.move(vec3))) {
                        passenger.setPose(pose);
                        return vec3;
                    }
                }

                mutableBlockPos.move(Direction.UP);
            } while (!(mutableBlockPos.getY() < g));
        }

        return null;
    }
}
