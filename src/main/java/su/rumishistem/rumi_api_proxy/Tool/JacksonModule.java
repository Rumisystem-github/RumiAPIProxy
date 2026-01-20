package su.rumishistem.rumi_api_proxy.Tool;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class JacksonModule extends SimpleModule{
	public JacksonModule() {
		addSerializer(Long.class, new JsonSerializer<Long>() {
			@Override
			public void serialize(Long v, JsonGenerator g, SerializerProvider p) throws IOException {
				if (v == null) {
					g.writeNull();
				} else {
					g.writeString(String.valueOf(v));
				}
			}
		});

		addSerializer(long.class, new JsonSerializer<Long>() {
			@Override
			public void serialize(Long v, JsonGenerator g, SerializerProvider p) throws IOException {
				g.writeString(String.valueOf(v));
			}
		});
	}
}
