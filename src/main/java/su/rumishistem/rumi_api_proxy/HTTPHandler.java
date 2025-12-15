package su.rumishistem.rumi_api_proxy;

import static su.rumishistem.rumi_java_lib.LOG_PRINT.Main.LOG;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import su.rumishistem.rumi_api_proxy.Tool.ByteDecorder;
import su.rumishistem.rumi_api_proxy.Tool.ByteEncoder;
import su.rumishistem.rumi_api_proxy.Tool.ClientRequestConverter;
import su.rumishistem.rumi_api_proxy.Tool.ResolveHost;
import su.rumishistem.rumi_api_proxy.Tool.ServerResponseConverter;
import su.rumishistem.rumi_api_proxy.Type.DataType;
import su.rumishistem.rumi_api_proxy.Type.EncodeType;
import su.rumishistem.rumi_java_lib.EXCEPTION_READER;
import su.rumishistem.rumi_java_lib.Ajax.Ajax;
import su.rumishistem.rumi_java_lib.Ajax.AjaxResult;
import su.rumishistem.rumi_java_lib.LOG_PRINT.LOG_TYPE;

public class HTTPHandler extends SimpleChannelInboundHandler<FullHttpRequest>{
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest r) throws Exception {
		String url = r.uri();
		HttpMethod request_method = r.method();
		EncodeType request_encode = EncodeType.Plain;
		DataType request_data_type = DataType.None;
		int request_body_length = 0;

		//URLを完成させる
		url = "http:/" + url;

		//Ajaxの用意
		Ajax ajax = new Ajax(url);

		//ヘッダー
		HttpHeaders header_list = r.headers();
		for (Map.Entry<String, String> header:header_list) {
			if (header.getKey().equalsIgnoreCase("HOST")) continue;
			if (header.getKey().equalsIgnoreCase("Content-Length")) continue;
			if (header.getKey().equalsIgnoreCase("Connection")) continue;
			if (header.getKey().equalsIgnoreCase("Transfer-Encoding")) continue;
			if (header.getKey().equalsIgnoreCase("Upgrade")) continue;
			if (header.getKey().equalsIgnoreCase("Expect")) continue;

			//エンコード設定
			if (header.getKey().equalsIgnoreCase("RSV-ENCODE")) {
				request_encode = EncodeType.resolve(header.getValue());
				continue;
			}

			//データ形式
			if (header.getKey().equalsIgnoreCase("ACCEPT")) {
				try {
					request_data_type = DataType.from_mimetype(header.getValue());
				} catch (IllegalArgumentException ex) {
					request_data_type = DataType.None;
				}
				continue;
			}

			//Ajaxのヘッダーをセット
			ajax.set_header(header.getKey(), header.getValue());
		}

		//サーバーからの応答
		byte[] server_response_body;
		int server_response_code;
		String server_response_type;
		//List<String> server_response_header_list = new ArrayList<String>();

		if (request_method.equals(HttpMethod.POST) || request_method.equals(HttpMethod.PATCH)) {
			//リクエストボディーがあるタイプのメソッド
			ByteBuf content = r.content();
			request_body_length = content.readableBytes();
			byte[] request_body = new byte[request_body_length];
			content.readBytes(request_body);

			//デコード
			if (request_encode != EncodeType.Plain) {
				request_body = ByteDecorder.decode(request_encode, request_body);
			}

			if (request_data_type != DataType.None) {
				//変換
				request_body = ClientRequestConverter.convert(ResolveHost.host(url), request_data_type, request_body);
			}

			AjaxResult result;
			if (request_method.equals(HttpMethod.POST)) {
				result = ajax.POST(request_body);
			} else if (request_method.equals(HttpMethod.PATCH)) {
				result = ajax.PATCH(request_body);
			} else {
				throw new UnsupportedOperationException("非対応なメソッド：" + request_method.name());
			}

			server_response_body = result.get_body_as_byte();
			server_response_code = result.get_code();
			server_response_type = result.get_header("CONTENT-TYPE");
		} else {
			//無い
			AjaxResult result;
			if (request_method.equals(HttpMethod.GET)) {
				result = ajax.GET();
			} else if (request_method.equals(HttpMethod.DELETE)) {
				result = ajax.DELETE();
			} else {
				throw new UnsupportedOperationException("非対応なメソッド：" + request_method.name());
			}

			server_response_body = result.get_body_as_byte();
			server_response_code = result.get_code();
			server_response_type = result.get_header("CONTENT-TYPE");
		}

		request_log(url, request_encode, request_data_type, request_body_length);

		//サーバーからの応答を変換する
		byte[] client_return_body;
		String client_return_type;
		if (server_response_type.equalsIgnoreCase("application/rsdf") || server_response_type.startsWith("application/json")) {
			if (request_data_type != DataType.None) {
				ServerResponseConverter converter = new ServerResponseConverter();
				converter.convert(server_response_type, server_response_body, request_data_type);
				client_return_body = converter.get_body();
				client_return_type = converter.get_type();
			} else {
				ServerResponseConverter converter = new ServerResponseConverter();
				converter.convert(server_response_type, server_response_body, DataType.JSON);
				client_return_body = converter.get_body();
				client_return_type = converter.get_type();
			}
		} else {
			//データをそのまま送り返す
			client_return_body = server_response_body;
			client_return_type = server_response_type;
		}

		//エンコード
		if (request_encode != EncodeType.Plain) {
			client_return_body = ByteEncoder.encode(request_encode, client_return_body);
		}

		response_log(url, server_response_body.length, request_encode, server_response_type);

		//応答
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(server_response_code), Unpooled.wrappedBuffer(client_return_body));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, client_return_type);
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
		ctx.writeAndFlush(response);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable ex) throws Exception {
		try {
			if (ex instanceof TooLongFrameException) {
				//リクエストがでかすぎる場合の処理
				FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, Unpooled.copiedBuffer("おっきぃ♡", CharsetUtil.UTF_8));
				response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
				response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
				ctx.writeAndFlush(response);
			} else {
				//その他のエラー
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				ex.printStackTrace(pw);
				pw.flush();
				String trace = sw.toString();
				sw.close();
				pw.close();

				FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.copiedBuffer("PROXY SERVER ERROR\n\n" + trace, CharsetUtil.UTF_8));
				response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
				response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
				ctx.writeAndFlush(response);
			}
		} finally {
			ctx.close();
		}
	}

	private void request_log(String url, EncodeType encode, DataType type, int body_length) {
		LOG(LOG_TYPE.INFO, "<Client>→["+encode.name()+" | "+type.name()+" | "+body_length+"byte]→{Plain}→<"+url+">");
	}
	
	private void response_log(String url, int response_length, EncodeType encode, String response_type) {
		String type = response_type;
		try {
			type = DataType.from_mimetype(type).name();
		} catch (IllegalArgumentException ex) {}
		LOG(LOG_TYPE.INFO, "<"+url+">→[Plain | "+type+" | "+response_length+"byte]→{"+encode.name()+"}→<Client>");
	}
}
