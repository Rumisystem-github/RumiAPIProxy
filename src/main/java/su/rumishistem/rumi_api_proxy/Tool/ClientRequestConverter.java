package su.rumishistem.rumi_api_proxy.Tool;

import java.io.IOException;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import su.rumishistem.rumi_api_proxy.Main;
import su.rumishistem.rumi_api_proxy.Type.DataType;
import su.rumishistem.rsdf_java.*;

public class ClientRequestConverter {
	public static byte[] convert(String host, DataType request_type, byte[] request_body) throws IOException {
		LinkedHashMap<String, Object> dict;

		switch (request_type) {
			case RSDF:
				dict = RSDFDecoder.decode(request_body).get_dict();
				break;
			case JSON:
				dict = new ObjectMapper().readValue(request_body, new TypeReference<LinkedHashMap<String, Object>>() {});
				break;
			default:
				throw new UnsupportedOperationException("非対応な形式" + request_type.name());
		}

		DataType to = DataType.RSDF;
		if (Main.config.get("SERVER").getData(host) != null) {
			if (Main.config.get("SERVER").getData(host).asString().equals("JSON")) {
				to = DataType.JSON;
			}
		}

		byte[] result;
		switch (to) {
			case RSDF:
				result = RSDFEncoder.encode(dict);
				break;
			case JSON:
				result = new ObjectMapper().writeValueAsBytes(dict);
				break;
			default:
				throw new UnsupportedOperationException("非対応な形式" + request_type.name());
		}

		return result;
	}
}
