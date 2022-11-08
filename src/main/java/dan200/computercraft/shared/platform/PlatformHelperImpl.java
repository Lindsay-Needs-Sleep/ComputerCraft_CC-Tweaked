/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */
package dan200.computercraft.shared.platform;

import com.google.auto.service.AutoService;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.network.wired.IWiredElement;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.Capabilities;
import dan200.computercraft.shared.Peripherals;
import dan200.computercraft.shared.network.NetworkMessage;
import dan200.computercraft.shared.network.client.ClientNetworkContext;
import dan200.computercraft.shared.network.container.ContainerData;
import dan200.computercraft.shared.util.CapabilityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.util.NonNullConsumer;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@AutoService(dan200.computercraft.impl.PlatformHelper.class)
public class PlatformHelperImpl implements PlatformHelper {
    @Override
    public <T> ResourceLocation getRegistryKey(ResourceKey<Registry<T>> registry, T object) {
        var key = RegistryManager.ACTIVE.getRegistry(registry).getKey(object);
        if (key == null) throw new IllegalArgumentException(object + " was not registered in " + registry);
        return key;
    }

    @Override
    public <T> T getRegistryObject(ResourceKey<Registry<T>> registry, ResourceLocation id) {
        var value = RegistryManager.ACTIVE.getRegistry(registry).getValue(id);
        if (value == null) throw new IllegalArgumentException(id + " was not registered in " + registry);
        return value;
    }

    @Override
    public <T> Registries.RegistryWrapper<T> wrap(ResourceKey<Registry<T>> key) {
        return new RegistryWrapperImpl<>(key.location(), RegistryManager.ACTIVE.getRegistry(key));
    }

    @Override
    public <T> RegistrationHelper<T> createRegistrationHelper(ResourceKey<Registry<T>> registry) {
        return new RegistrationHelperImpl<>(DeferredRegister.create(registry, ComputerCraftAPI.MOD_ID));
    }

    @Nullable
    @Override
    public <K> K tryGetRegistryObject(ResourceKey<Registry<K>> registry, ResourceLocation id) {
        return RegistryManager.ACTIVE.getRegistry(registry).getValue(id);
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block block) {
        return new BlockEntityType<>(factory::apply, Set.of(block), null);
    }

    @Override
    public <C extends AbstractContainerMenu, T extends ContainerData> MenuType<C> createMenuType(Function<FriendlyByteBuf, T> reader, ContainerData.Factory<C, T> factory) {
        return IForgeMenuType.create((id, player, data) -> factory.create(id, player, reader.apply(data)));
    }

    @Override
    public void openMenu(Player player, MenuProvider owner, ContainerData menu) {
        NetworkHooks.openScreen((ServerPlayer) player, owner, menu::toBytes);
    }

    @Override
    public void sendToPlayer(NetworkMessage<ClientNetworkContext> message, ServerPlayer player) {
        NetworkHandler.sendToPlayer(message, player);
    }

    @Override
    public void sendToPlayers(NetworkMessage<ClientNetworkContext> message, Collection<ServerPlayer> players) {
        NetworkHandler.sendToPlayers(message, players);
    }

    @Override
    public void sendToAllPlayers(NetworkMessage<ClientNetworkContext> message, MinecraftServer server) {
        NetworkHandler.sendToAllPlayers(message);
    }

    @Override
    public void sendToAllAround(NetworkMessage<ClientNetworkContext> message, ServerLevel level, Vec3 pos, float distance) {
        NetworkHandler.sendToAllAround(message, level, pos, distance);
    }

    @Override
    public void sendToAllTracking(NetworkMessage<ClientNetworkContext> message, LevelChunk chunk) {
        NetworkHandler.sendToAllTracking(message, chunk);
    }

    @Override
    public ComponentAccess<IPeripheral> createPeripheralAccess(Consumer<Direction> invalidate) {
        return new PeripheralAccess(invalidate);
    }

    @Override
    public ComponentAccess<IWiredElement> createWiredElementAccess(Consumer<Direction> invalidate) {
        return new CapabilityAccess<>(Capabilities.CAPABILITY_WIRED_ELEMENT, invalidate);
    }

    @Override
    public boolean hasWiredElementIn(Level level, BlockPos pos, Direction direction) {
        if (!level.isLoaded(pos)) return false;

        var blockEntity = level.getBlockEntity(pos.relative(direction));
        return blockEntity != null && blockEntity.getCapability(Capabilities.CAPABILITY_WIRED_ELEMENT, direction.getOpposite()).isPresent();
    }

    @Nullable
    @Override
    public CompoundTag getShareTag(ItemStack item) {
        return item.getShareTag();
    }

    private record RegistryWrapperImpl<T>(
        ResourceLocation name, ForgeRegistry<T> registry
    ) implements Registries.RegistryWrapper<T> {
        @Override
        public int getId(T object) {
            var id = registry.getID(object);
            if (id == -1) throw new IllegalStateException(object + " was not registered in " + name);
            return id;
        }

        @Override
        public ResourceLocation getKey(T object) {
            var key = registry.getKey(object);
            if (key == null) throw new IllegalStateException(object + " was not registered in " + name);
            return key;
        }

        @Override
        public T get(ResourceLocation location) {
            var object = registry.getValue(location);
            if (object == null) throw new IllegalStateException(location + " was not registered in " + name);
            return object;
        }

        @Nullable
        @Override
        public T tryGet(ResourceLocation location) {
            return registry.getValue(location);
        }

        @Override
        public T get(int id) {
            var object = registry.getValue(id);
            if (object == null) throw new IllegalStateException(id + " was not registered in " + name);
            return object;
        }

        @Override
        public Iterator<T> iterator() {
            return registry.iterator();
        }
    }

    private record RegistrationHelperImpl<T>(DeferredRegister<T> registry) implements RegistrationHelper<T> {
        @Override
        public <U extends T> RegistryEntry<U> register(String name, Supplier<U> create) {
            return new RegistryEntryImpl<>(registry.register(name, create));
        }

        @Override
        public void register() {
            registry.register(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }

    private record RegistryEntryImpl<T>(RegistryObject<T> object) implements RegistryEntry<T> {
        @Override
        public ResourceLocation id() {
            return object().getId();
        }

        @Override
        public T get() {
            return object.get();
        }
    }

    private abstract static class ComponentAccessImpl<T> implements ComponentAccess<T> {
        private final NonNullConsumer<Object>[] invalidators;
        private @Nullable Level level;
        private @Nullable BlockPos pos;

        ComponentAccessImpl(Consumer<Direction> invalidate) {
            // Generate a cache of invalidation functions so we can guarantee we only ever have one registered per
            // capability - there's no way to remove these callbacks!
            @SuppressWarnings({ "unchecked", "rawtypes" })
            var invalidators = this.invalidators = new NonNullConsumer[6];
            for (var dir : Direction.values()) invalidators[dir.ordinal()] = x -> invalidate.accept(dir);
        }

        @Nullable
        protected abstract T get(ServerLevel world, BlockPos pos, Direction side, NonNullConsumer<Object> invalidate);

        @Nullable
        @Override
        public T get(ServerLevel level, BlockPos pos, Direction direction) {
            if (this.level != null && this.level != level) throw new IllegalStateException("Level has changed");
            if (this.pos != null && this.pos != pos) throw new IllegalStateException("Position has changed");

            this.level = level;
            this.pos = pos;
            return get(level, pos.relative(direction), direction.getOpposite(), invalidators[direction.ordinal()]);
        }
    }

    private static class PeripheralAccess extends ComponentAccessImpl<IPeripheral> {
        PeripheralAccess(Consumer<Direction> invalidate) {
            super(invalidate);
        }

        @Nullable
        @Override
        protected IPeripheral get(ServerLevel world, BlockPos pos, Direction side, NonNullConsumer<Object> invalidate) {
            return Peripherals.getPeripheral(world, pos, side, invalidate);
        }
    }

    private static class CapabilityAccess<T> extends ComponentAccessImpl<T> {
        private final Capability<T> capability;

        CapabilityAccess(Capability<T> capability, Consumer<Direction> invalidate) {
            super(invalidate);
            this.capability = capability;
        }

        @Nullable
        @Override
        protected T get(ServerLevel world, BlockPos pos, Direction side, NonNullConsumer<Object> invalidate) {
            if (!world.isLoaded(pos)) return null;

            var blockEntity = world.getBlockEntity(pos);
            return blockEntity != null ? CapabilityUtil.unwrap(blockEntity.getCapability(capability, side), invalidate) : null;
        }
    }
}
