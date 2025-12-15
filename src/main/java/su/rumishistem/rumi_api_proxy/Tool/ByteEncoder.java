package su.rumishistem.rumi_api_proxy.Tool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.github.luben.zstd.ZstdOutputStream;

import su.rumishistem.rumi_api_proxy.Type.EncodeType;

public class ByteEncoder {
	public static byte[] encode(EncodeType type, byte[] plain) throws IOException {
		byte[] encoded;

		switch (type) {
			case Zstd:
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ZstdOutputStream zos = new ZstdOutputStream(baos);
				zos.write(plain);
				zos.close();
				encoded = baos.toByteArray();
				baos.close();
				break;

			default:
				throw new UnsupportedOperationException("未対応のエンコード形式：" + type.name());
		}

		return encoded;
	}
}
