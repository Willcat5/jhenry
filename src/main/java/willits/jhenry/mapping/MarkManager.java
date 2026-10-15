package willits.jhenry.mapping;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class MarkManager {

	private static final MapConfig CONFIG = new MapConfig();
	private static final List<TunnelMark> MARKS = new ArrayList<>();
	private static final List<TunnelPlan> PLANS = new ArrayList<>();
	private static final List<BlockPos> VOLUME_CORNERS = new ArrayList<>();
	private static final List<VolumePlan> VOLUME_PLANS = new ArrayList<>();
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

	public static List<BlockPos> volumeCorners() {
		return VOLUME_CORNERS;
	}

	public static void addVolumeCorner(BlockPos pos) {
		VOLUME_CORNERS.add(pos.immutable());
	}

	public static void clearVolumeCorners() {
		VOLUME_CORNERS.clear();
	}

	public static List<VolumePlan> volumePlans() {
		return VOLUME_PLANS;
	}

	public static void addVolumePlan(VolumePlan plan) {
		VOLUME_PLANS.add(plan);
	}

	public static void clearVolumePlans() {
		VOLUME_PLANS.clear();
	}

	public static void addMark(TunnelMark mark) {
		MARKS.add(mark);
	}

	public static void removeMark(TunnelMark mark) {
		MARKS.remove(mark);
	}

	public static void removeMarkAt(BlockPos entrance, ResourceKey<Level> dimension) {
		MARKS.removeIf(mark -> mark.entrance().equals(entrance) && mark.dimension().equals(dimension));
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

	public static void removePlanAt(BlockPos entrance, ResourceKey<Level> dimension) {
		PLANS.removeIf(plan -> plan.mark().entrance().equals(entrance) && plan.mark().dimension().equals(dimension));
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
