package willits.jhenry.mapping;

public enum StopReason {
	INVALID_HAZARD("hazard"),
	FALLING_BLOCK("falling block"),
	UNBREAKABLE("unbreakable"),
	MAX_DISTANCE("max distance"),
	UNLOADED("unloaded"),
	SIDESTEP_BLOCKED("sidestep blocked"),
	RETURN_BLOCKED("return blocked"),
	NO_VALID_LENGTH("no valid length"),
	NONE("none");

	private final String label;

	StopReason(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}
}
