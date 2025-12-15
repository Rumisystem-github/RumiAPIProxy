package su.rumishistem.rumi_api_proxy.Type;

public enum EncodeType {
	Plain,
	Zstd;

	public static EncodeType resolve(String input) {
		for (EncodeType type:EncodeType.values()) {
			if (type.name().equalsIgnoreCase(input)) {
				return type;
			}
		}

		throw new IllegalArgumentException("エンコードタイプが不正");
	}
}
