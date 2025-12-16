package su.rumishistem.rumi_api_proxy.Tool;

import java.io.IOException;
import su.rumishistem.rumi_api_proxy.Type.EncodeType;

public class ByteDecorder {
	public static byte[] decode(EncodeType type, byte[] encoded) throws IOException, InterruptedException {
		byte[] decoded;

		switch (type) {
			case Zstd:
				decoded = new Zstd().decompress(encoded);
				break;
			default:
				throw new UnsupportedOperationException("未対応のエンコード形式：" + type.name());
		}

		return decoded;
	}
}
