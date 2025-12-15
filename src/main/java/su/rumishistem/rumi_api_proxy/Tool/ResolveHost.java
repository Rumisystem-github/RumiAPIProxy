package su.rumishistem.rumi_api_proxy.Tool;

import java.net.MalformedURLException;
import java.net.URI;

public class ResolveHost {
	public static String host(String url) throws MalformedURLException {
		URI uri = URI.create(url);
		return uri.getHost() + ":" + uri.getPort();
	}
}
