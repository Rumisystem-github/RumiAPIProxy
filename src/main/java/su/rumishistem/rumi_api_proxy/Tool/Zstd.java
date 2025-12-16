package su.rumishistem.rumi_api_proxy.Tool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Zstd {
	private static final String ZSTD_PATH = "/usr/bin/zstd";
	private static final String[] ZSTD_COMPRESS = new String[] {ZSTD_PATH, "-9", "-q", "-c"};
	private static final String[] ZSTD_DECOMPRESS = new String[] {ZSTD_PATH, "-q", "-d", "-c"};

	public byte[] compress(byte[] data) throws IOException, InterruptedException {
		Process p = new ProcessBuilder(ZSTD_COMPRESS).start();
		OutputStream stdin = p.getOutputStream();
		InputStream stdout = p.getInputStream();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try {
			stdin.write(data);
			stdin.close();
			stdout.transferTo(baos);

			int code = p.waitFor();
			if (code != 0) throw new RuntimeException("Zstd Error");

			return baos.toByteArray();
		} finally {
			stdout.close();
		}
	}

	public byte[] decompress(byte[] data) throws IOException, InterruptedException {
		Process p = new ProcessBuilder(ZSTD_DECOMPRESS).start();
		OutputStream stdin = p.getOutputStream();
		InputStream stdout = p.getInputStream();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try {
			stdin.write(data);
			stdin.close();
			stdout.transferTo(baos);

			int code = p.waitFor();
			if (code != 0) throw new RuntimeException("Zstd Error");

			return baos.toByteArray();
		} finally {
			stdout.close();
		}
	}
}
