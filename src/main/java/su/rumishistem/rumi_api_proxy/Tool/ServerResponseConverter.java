package su.rumishistem.rumi_api_proxy.Tool;

import java.io.IOException;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import su.rumishistem.rumi_api_proxy.Type.DataType;
import su.rumishistem.rumi_java_lib.RSDF.*;

public class ServerResponseConverter {
	private byte[] client_return_body;
	private String client_return_type;

	public void convert(String server_response_type, byte[] server_response_body, DataType request_data_type) throws StreamReadException, DatabindException, IOException {
		//データ形式を変換
		LinkedHashMap<String, Object> dict;

		//サーバーからの応答の形式に合わせて辞書に変換
		if (server_response_type.startsWith("application/json")) {
			dict = new ObjectMapper().readValue(server_response_body, new TypeReference<LinkedHashMap<String, Object>>() {});
		} else if (server_response_type.equalsIgnoreCase("application/rsdf")) {
			dict = RSDFDecoder.decode(server_response_body).get_dict();
		} else {
			throw new UnsupportedOperationException("サーバーから未対応の形式が来ました：" + server_response_type);
		}

		switch (request_data_type) {
			case RSDF:
				client_return_body = RSDFEncoder.encode(dict);
				client_return_type = "application/rsdf";
				break;
			case JSON:
				client_return_body = new ObjectMapper().writeValueAsBytes(dict);
				client_return_type = "application/json; charset=UTF-8";
				break;
			default:
				throw new UnsupportedOperationException("クライアントが未対応の形式をリクエストしました：" + request_data_type.name());
		}
	}

	public byte[] get_body() {
		return client_return_body;
	}

	public String get_type() {
		return client_return_type;
	}
}
