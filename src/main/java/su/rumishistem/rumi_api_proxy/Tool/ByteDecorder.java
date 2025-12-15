package su.rumishistem.rumi_api_proxy.Tool;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.github.luben.zstd.ZstdInputStream;

import su.rumishistem.rumi_api_proxy.Type.EncodeType;

public class ByteDecorder {
	public static byte[] decode(EncodeType type, byte[] encoded) throws IOException {
		byte[] decoded;

		switch (type) {
			case Zstd:
				ByteArrayInputStream bais = new ByteArrayInputStream(encoded);
				ZstdInputStream zis = new ZstdInputStream(bais);
				decoded = zis.readAllBytes();
				break;
			default:
				throw new UnsupportedOperationException("未対応のエンコード形式：" + type.name());
		}

		return decoded;
	}
}
