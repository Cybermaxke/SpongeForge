package org.spongepowered.mod.mixin.core.forge.fluids;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import co.aikar.timings.SpongeTimings;
import co.aikar.timings.Timing;
import com.google.common.collect.ImmutableSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.RegistryDelegate;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataHolder;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.MemoryDataContainer;
import org.spongepowered.api.data.Property;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.data.merge.MergeFunction;
import org.spongepowered.api.data.value.BaseValue;
import org.spongepowered.api.data.value.immutable.ImmutableValue;
import org.spongepowered.api.extra.fluid.FluidKeys;
import org.spongepowered.api.extra.fluid.FluidStackSnapshot;
import org.spongepowered.api.extra.fluid.FluidType;
import org.spongepowered.api.util.persistence.InvalidDataException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.data.DataProcessor;
import org.spongepowered.common.data.SpongeDataManager;
import org.spongepowered.common.data.ValueProcessor;
import org.spongepowered.common.data.util.DataQueries;
import org.spongepowered.common.interfaces.data.IMixinCustomDataHolder;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

@Mixin(FluidStack.class)
public class MixinFluidStack implements org.spongepowered.api.extra.fluid.FluidStack {

    @Shadow public int amount;
    @Shadow public NBTTagCompound tag;
    @Shadow private RegistryDelegate<Fluid> fluidDelegate;

    @SuppressWarnings("unchecked")
    @Override
    public <T extends DataManipulator<?, ?>> Optional<T> get(Class<T> containerClass) {
        try (Timing timing = SpongeTimings.dataGetManipulator.startTiming()) {
            final Optional<DataProcessor<?, ?>> optional = SpongeDataManager.getInstance().getWildProcessor(containerClass);
            if (optional.isPresent()) {
                return (Optional<T>) optional.get().from(this);
            }
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends DataManipulator<?, ?>> Optional<T> getOrCreate(Class<T> containerClass) {
        try (Timing timing = SpongeTimings.dataGetOrCreateManipulator.startTiming()) {
            final Optional<DataProcessor<?, ?>> optional = SpongeDataManager.getInstance().getWildProcessor(containerClass);
            if (optional.isPresent()) {
                return (Optional<T>) optional.get().createFrom(this);
            } else if (this instanceof IMixinCustomDataHolder) {
                return ((IMixinCustomDataHolder) this).getCustom(containerClass);
            }
            return Optional.empty();
        }
    }

    @Override
    public boolean supports(Class<? extends DataManipulator<?, ?>> holderClass) {
        try (Timing timing = SpongeTimings.dataSupportsManipulator.startTiming()) {
            final Optional<DataProcessor<?, ?>> optional = SpongeDataManager.getInstance().getWildProcessor(holderClass);
            return optional.isPresent() && optional.get().supports(this);
        }
    }

    @Override
    public <E> DataTransactionResult offer(Key<? extends BaseValue<E>> key, E value) {
        try (Timing timing = SpongeTimings.dataOfferKey.startTiming()) {
            final Optional<ValueProcessor<E, ? extends BaseValue<E>>> optional = SpongeDataManager.getInstance().getBaseValueProcessor(key);
            if (optional.isPresent()) {
                return optional.get().offerToStore(this, value);
            } else if (this instanceof IMixinCustomDataHolder) {
                return ((IMixinCustomDataHolder) this).offerCustom(key, value);
            }
            return DataTransactionResult.builder().result(DataTransactionResult.Type.FAILURE).build();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public DataTransactionResult offer(DataManipulator<?, ?> valueContainer, MergeFunction function) {
        try (Timing timing = SpongeTimings.dataOfferManipulator.startTiming()) {
            final Optional<DataProcessor> optional = SpongeDataManager.getInstance().getWildDataProcessor(valueContainer.getClass());
            if (optional.isPresent()) {
                return optional.get().set(this, valueContainer, checkNotNull(function));
            } else if (this instanceof IMixinCustomDataHolder) {
                return ((IMixinCustomDataHolder) this).offerCustom(valueContainer, function);
            }
            return DataTransactionResult.failResult(valueContainer.getValues());
        }
    }

    @Override
    public DataTransactionResult offer(Iterable<DataManipulator<?, ?>> valueContainers) {
        try (Timing timing = SpongeTimings.dataOfferMultiManipulators.startTiming()) {
            DataTransactionResult.Builder builder = DataTransactionResult.builder();
            for (DataManipulator<?, ?> manipulator : valueContainers) {
                final DataTransactionResult result = offer(manipulator);
                if (!result.getRejectedData().isEmpty()) {
                    builder.reject(result.getRejectedData());
                }
                if (!result.getReplacedData().isEmpty()) {
                    builder.replace(result.getReplacedData());
                }
                if (!result.getSuccessfulData().isEmpty()) {
                    builder.success(result.getSuccessfulData());
                }
                final DataTransactionResult.Type type = result.getType();
                builder.result(type);
                switch (type) {
                    case UNDEFINED:
                    case ERROR:
                    case CANCELLED:
                        return builder.build();
                    default:
                        break;
                }
            }
            return builder.build();
        }
    }

    @Override
    public DataTransactionResult remove(Class<? extends DataManipulator<?, ?>> containerClass) {
        try (Timing timing = SpongeTimings.dataRemoveManipulator.startTiming()) {
            final Optional<DataProcessor<?, ?>> optional = SpongeDataManager.getInstance().getWildProcessor(containerClass);
            if (optional.isPresent()) {
                return optional.get().remove(this);
            } else if (this instanceof IMixinCustomDataHolder) {
                return ((IMixinCustomDataHolder) this).removeCustom(containerClass);
            }
            return DataTransactionResult.failNoData();
        }
    }

    @Override
    public DataTransactionResult remove(Key<?> key) {
        try (Timing timing = SpongeTimings.dataRemoveKey.startTiming()) {
            final Optional<ValueProcessor<?, ?>> optional = SpongeDataManager.getInstance().getWildValueProcessor(checkNotNull(key));
            if (optional.isPresent()) {
                return optional.get().removeFrom(this);
            } else if (this instanceof IMixinCustomDataHolder) {
                return ((IMixinCustomDataHolder) this).removeCustom(key);
            }
            return DataTransactionResult.failNoData();
        }
    }

    @Override
    public DataTransactionResult undo(DataTransactionResult result) {
        try (Timing timing = SpongeTimings.dataOfferManipulator.startTiming()) {
            if (result.getReplacedData().isEmpty() && result.getSuccessfulData().isEmpty()) {
                return DataTransactionResult.successNoData();
            }
            final DataTransactionResult.Builder builder = DataTransactionResult.builder();
            for (ImmutableValue<?> replaced : result.getReplacedData()) {
                builder.absorbResult(offer(replaced));
            }
            for (ImmutableValue<?> successful : result.getSuccessfulData()) {
                builder.absorbResult(remove(successful));
            }
            return builder.build();
        }
    }

    @Override
    public DataTransactionResult copyFrom(DataHolder that, MergeFunction function) {
        return offer(that.getContainers(), function);
    }

    @Override
    public Collection<DataManipulator<?, ?>> getContainers() {
        return null;
    }

    @Override
    public <E> Optional<E> get(Key<? extends BaseValue<E>> key) {
        try (Timing timing = SpongeTimings.dataGetByKey.startTiming()) {
            final Optional<ValueProcessor<E, ? extends BaseValue<E>>>
                    optional =
                    SpongeDataManager.getInstance().getBaseValueProcessor(checkNotNull(key));
            if (optional.isPresent()) {
                return optional.get().getValueFromContainer(this);
            }
            return Optional.empty();
        }
    }

    @Override
    public <E, V extends BaseValue<E>> Optional<V> getValue(Key<V> key) {
        try (Timing timing = SpongeTimings.dataGetValue.startTiming()) {
            final Optional<ValueProcessor<E, V>> optional = SpongeDataManager.getInstance().getValueProcessor(checkNotNull(key));
            if (optional.isPresent()) {
                return optional.get().getApiValueFromContainer(this);
            }
            return Optional.empty();
        }
    }

    @Override
    public boolean supports(Key<?> key) {
        try (Timing timing = SpongeTimings.dataSupportsKey.startTiming()) {
            final Optional<ValueProcessor<?, ?>> optional = SpongeDataManager.getInstance().getWildValueProcessor(checkNotNull(key));
            return optional.isPresent() && optional.get().supports(this);
        }
    }

    @Override
    public DataHolder copy() {
        FluidStack fluidStack = new FluidStack(this.fluidDelegate.get(), this.amount,
                (NBTTagCompound) this.tag.copy());
        return (DataHolder) fluidStack;
    }

    @Override
    public Set<Key<?>> getKeys() {
        return ImmutableSet.of();
    }

    @Override
    public Set<ImmutableValue<?>> getValues() {
        return ImmutableSet.of();
    }

    @Override
    public FluidType getFluid() {
        return (FluidType) this.fluidDelegate.get();
    }

    @Override
    public int getVolume() {
        return this.amount;
    }

    @Override
    public org.spongepowered.api.extra.fluid.FluidStack setVolume(int volume) {
        checkArgument(volume > 0);
        this.amount = volume;
        return this;
    }

    @Override
    public FluidStackSnapshot createSnapshot() {
        return null;
    }

    @Override
    public boolean validateRawData(DataContainer container) {
        return false;
    }

    @Override
    public void setRawData(DataContainer container) throws InvalidDataException {

    }

    @Override
    public int getContentVersion() {
        return 1;
    }

    @Override
    public DataContainer toContainer() {
        return new MemoryDataContainer()
                .set(DataQueries.FLUID_TYPE, this.fluidDelegate.get().getName());
    }

    @Override
    public <T extends Property<?, ?>> Optional<T> getProperty(Class<T> propertyClass) {
        return null;
    }

    @Override
    public Collection<Property<?, ?>> getApplicableProperties() {
        return null;
    }
}
