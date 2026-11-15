package ar.hxi.ez2rs.mixin;

import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.resource.repository.ResourceRepositoryImpl;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native repositories retain sticky entries on clear; a reconnect replaces that set explicitly. */
@Mixin(ResourceRepositoryImpl.class)
public interface ResourceRepositoryAccessor {
    @Accessor(value = "stickyResources", remap = false)
    Set<ResourceKey> ez2rs$stickyResources();
}
