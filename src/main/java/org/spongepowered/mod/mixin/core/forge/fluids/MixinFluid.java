package org.spongepowered.mod.mixin.core.forge.fluids;

import net.minecraft.block.Block;
import net.minecraftforge.fluids.Fluid;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.data.Property;
import org.spongepowered.api.data.property.PropertyStore;
import org.spongepowered.api.extra.fluid.FluidType;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.data.property.SpongePropertyRegistry;

import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nullable;

@Mixin(Fluid.class)
@Implements(@Interface(iface = FluidType.class, prefix = "fluid$"))
public abstract class MixinFluid implements FluidType {

    @Shadow @Nullable protected Block block;

    @Override
    public Optional<BlockType> getBlockTypeBase() {
        return Optional.ofNullable((BlockType) this.block);
    }

    public String fluid$getId() {
        return this.getName();
    }

    @Override
    public <T extends Property<?, ?>> Optional<T> getProperty(Class<T> propertyClass) {
        final Optional<PropertyStore<T>> optional = SpongePropertyRegistry.getInstance().getStore(propertyClass);
        if (optional.isPresent()) {
            return optional.get().getFor(this);
        }
        return Optional.empty();
    }

    @Override
    public Collection<Property<?, ?>> getApplicableProperties() {
        return SpongePropertyRegistry.getInstance().getPropertiesFor(this);
    }
}
