package su.rumishistem.rumi_api_proxy.Tool;

import java.io.IOException;
import su.rumishistem.rumi_api_proxy.Type.EncodeType;

public class ByteEncoder {
	public static byte[] encode(EncodeType type, byte[] plain) throws IOException, InterruptedException {
		byte[] encoded;

		switch (type) {
			case Zstd:
				encoded = new Zstd().compress(plain);
				break;

			default:
				throw new UnsupportedOperationException("未対応のエンコード形式：" + type.name());
		}

		return encoded;
	}
}
