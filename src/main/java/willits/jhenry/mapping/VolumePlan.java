package willits.jhenry.mapping;

import java.util.List;

public record VolumePlan(VolumeMark mark, List<Partition> partitions, int width, int height, int length, String detail) {
}
