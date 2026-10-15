package willits.jhenry.mapping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class OreFilter {

	private static final Set<Block> DEFAULT_ORES = Set.of(
			Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
			Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
			Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
			Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
			Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
			Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
			Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
			Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
			Blocks.ANCIENT_DEBRIS, Blocks.NETHER_QUARTZ_ORE);

	private static List<Block> available;
	private static final Set<Block> enabled = new LinkedHashSet<>();

	private OreFilter() {
	}

	public static boolean isOre(BlockState state) {
		ensureInit();
		return enabled.contains(state.getBlock());
	}

	public static List<Block> available() {
		ensureInit();
		return available;
	}

	public static List<Block> enabled() {
		ensureInit();
		return new ArrayList<>(enabled);
	}

	public static boolean isEnabled(Block block) {
		ensureInit();
		return enabled.contains(block);
	}

	public static void enable(Block block) {
		ensureInit();
		enabled.add(block);
	}

	public static void disable(Block block) {
		ensureInit();
		enabled.remove(block);
	}

	public static void toggle(Block block) {
		if (isEnabled(block)) {
			disable(block);
		} else {
			enable(block);
		}
	}

	public static void setEnabled(List<Block> blocks) {
		ensureInit();
		enabled.clear();
		enabled.addAll(blocks);
	}

	public static void reset() {
		ensureInit();
		enabled.clear();
		enabled.addAll(available);
	}

	public static Block resolve(String input) {
		ensureInit();
		if (input == null) {
			return null;
		}
		String trimmed = input.trim();
		if (trimmed.isEmpty()) {
			return null;
		}

		String text = trimmed.toLowerCase(Locale.ROOT).replace(' ', '_');
		Identifier id = text.contains(":") ? Identifier.tryParse(text) : Identifier.withDefaultNamespace(text);
		if (id != null) {
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			if (block != null && block != Blocks.AIR) {
				return block;
			}
		}

		for (Block block : available) {
			if (block.getName().getString().equalsIgnoreCase(trimmed)) {
				return block;
			}
		}
		return null;
	}

	public static Identifier idOf(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block);
	}

	private static void ensureInit() {
		if (available != null) {
			return;
		}
		List<Block> list = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			if (isDefaultOre(block.defaultBlockState())) {
				list.add(block);
			}
		}
		available = list;
		enabled.addAll(list);
	}

	private static boolean isDefaultOre(BlockState state) {
		return DEFAULT_ORES.contains(state.getBlock());
	}
}
