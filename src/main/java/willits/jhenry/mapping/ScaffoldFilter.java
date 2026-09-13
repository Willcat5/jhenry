package willits.jhenry.mapping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class ScaffoldFilter {

	private static final Set<Block> ENABLED = new LinkedHashSet<>();

	private ScaffoldFilter() {
	}

	public static boolean isEmpty() {
		return ENABLED.isEmpty();
	}

	public static List<Block> enabled() {
		return new ArrayList<>(ENABLED);
	}

	public static boolean isPlacement(Block block) {
		return ENABLED.contains(block);
	}

	public static boolean isPlacement(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Block block = Block.byItem(stack.getItem());
		return block != Blocks.AIR && ENABLED.contains(block);
	}

	public static void setEnabled(List<Block> blocks) {
		ENABLED.clear();
		ENABLED.addAll(blocks);
	}

	public static Block resolve(String input) {
		if (input == null) {
			return null;
		}
		String trimmed = input.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		String text = trimmed.toLowerCase(Locale.ROOT).replace(' ', '_');
		Identifier id = text.contains(":") ? Identifier.tryParse(text) : Identifier.withDefaultNamespace(text);
		if (id == null) {
			return null;
		}
		Block block = BuiltInRegistries.BLOCK.getValue(id);
		return block == Blocks.AIR ? null : block;
	}

	public static Identifier idOf(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block);
	}
}
