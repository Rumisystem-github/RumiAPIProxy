package su.rumishistem.rumi_api_proxy.Type;

public enum DataType {
	None,
	RSDF,
	JSON;

	public static DataType from_mimetype(String mimetype) {
		if (mimetype.startsWith("application/json")) {
			return JSON;
		} else if (mimetype.startsWith("application/rsdf")) {
			return RSDF;
		} else {
			throw new IllegalArgumentException("非対応：" + mimetype);
		}
	}
}
