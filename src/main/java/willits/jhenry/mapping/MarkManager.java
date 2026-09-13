package willits.jhenry.mapping;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

public final class MarkManager {

	private static final MapConfig CONFIG = new MapConfig();
	private static final List<TunnelMark> MARKS = new ArrayList<>();
	private static final List<TunnelPlan> PLANS = new ArrayList<>();
	private static final List<BlockPos> EXPOSED_ORES = new ArrayList<>();

	private MarkManager() {
	}

	public static MapConfig config() {
		return CONFIG;
	}

	public static List<TunnelMark> marks() {
		return MARKS;
	}

	public static List<TunnelPlan> plans() {
		return PLANS;
	}

	public static void addMark(TunnelMark mark) {
		MARKS.add(mark);
	}

	public static void removeMark(TunnelMark mark) {
		MARKS.remove(mark);
	}

	public static void clearMarks() {
		MARKS.clear();
	}

	public static void addPlan(TunnelPlan plan) {
		PLANS.add(plan);
	}

	public static void clearPlans() {
		PLANS.clear();
	}

	public static List<BlockPos> exposedOres() {
		return EXPOSED_ORES;
	}

	public static boolean addExposedOre(BlockPos pos) {
		BlockPos immutable = pos.immutable();
		if (EXPOSED_ORES.contains(immutable)) {
			return false;
		}
		EXPOSED_ORES.add(immutable);
		return true;
	}

	public static void clearExposedOres() {
		EXPOSED_ORES.clear();
	}
}
