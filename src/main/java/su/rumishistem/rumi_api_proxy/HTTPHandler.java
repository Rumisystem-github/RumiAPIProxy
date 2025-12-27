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
import su.rumishistem.rumi_java_lib.Ajax.Ajax;
import su.rumishistem.rumi_java_lib.Ajax.AjaxResult;
import su.rumishistem.rumi_java_lib.LOG_PRINT.LOG_TYPE;

public class HTTPHandler extends SimpleChannelInboundHandler<FullHttpRequest>{
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest r) throws Exception {
		String url = r.uri();
		HttpMethod request_method = r.method();
		DataType client_accept_type = DataType.None;				//クライアントが許可するデータ形式
		DataType client_contents_type = DataType.None;			//クライアントが送るデータ形式
		EncodeType client_accept_encode = EncodeType.Plain;		//クライアントが許可する圧縮方式
		EncodeType client_contents_encode = EncodeType.Plain;	//クライアントが送る圧縮方式
		boolean use_client_contents = false;

		String server_contents_type;								//サーバーが送るデータ形式(サーバーからはPNGとかも飛んでくる可能性があるので)
		int server_status = 0;
		byte[] server_body;

		//統計用
		int length_client_contents = 0;
		int length_client_encoded_contents = 0;
		int length_server_contents_length = 0;
		int length_server_encoded_contents = 0;

		//リクエストボディがあるタイプのメソッドか？
		if (request_method.equals(HttpMethod.POST) || request_method.equals(HttpMethod.PATCH)) {
			use_client_contents = true;
		}

		//URLを完成させる
		url = "http:/" + url;

		//Ajaxを用意
		Ajax ajax = new Ajax(url);

		//クライアントからのヘッダーを読み込む
		for (Map.Entry<String, String> header:r.headers()) {
			String name = header.getKey().toUpperCase();
			//触れてはいけないヘッダーを無視
			if (name.equals("HOST") || name.equals("CONTENT-LENGTH") || name.equals("CONNECTION") || name.equals("TRANSFER-ENCODING") || name.equals("UPGRADE") || name.equals("EXPECT")) continue;

			switch (name) {
				//クライアントが許可している圧縮方式
				case "RSV-ACCEPT-ENCODE":
					client_accept_encode = EncodeType.resolve(header.getValue());
					break;
				//クライアントが送信している圧縮方式
				case "RSV-CONTENT-ENCODE":
					client_contents_encode = EncodeType.resolve(header.getValue());
					break;
				//クライアントが許可しているデータ形式
				case "ACCEPT":
					client_accept_type = DataType.from_mimetype(header.getValue());
					if (client_accept_type == DataType.None) {
						ajax.set_header("Content-Type", header.getValue());

						//「APIにHTMLを要求するわけがない」ということでJSONにすり替え
						if (header.getValue().startsWith("text/html")) {
							client_accept_type = DataType.JSON;
						}
					}
					break;
				//クライアントが送信しているデータ形式
				case "CONTENT-TYPE":
					client_contents_type = DataType.from_mimetype(header.getValue());
					if (client_contents_type == DataType.None) {
						ajax.set_header("Content-Type", header.getValue());
					}
					break;
				//その他
				default:
					ajax.set_header(name, header.getValue());
					break;
			}
		}

		//サーバーへ送信
		if (use_client_contents) {
			//クライアントからのデータがある
			ByteBuf contents = r.content();
			length_client_contents = contents.readableBytes();

			//読み取る
			byte[] body = new byte[length_client_contents];
			contents.readBytes(body);

			//圧縮をデコード
			if (client_contents_encode != EncodeType.Plain) {
				body = ByteDecorder.decode(client_contents_encode, body);
			}

			//データ形式を変換(DICTな物に限る)
			if (client_contents_type != DataType.None) {
				body = ClientRequestConverter.convert(ResolveHost.host(url), client_contents_type, body);
			}

			//送信する
			AjaxResult result;
			if (request_method.equals(HttpMethod.POST)) {
				result = ajax.POST(body);
			} else {
				result = ajax.PATCH(body);
			}

			server_status = result.get_code();
			server_body = result.get_body_as_byte();
			server_contents_type = result.get_header("CONTENT-TYPE");
		} else {
			//ない
			AjaxResult result;
			if (request_method.equals(HttpMethod.GET)) {
				result = ajax.GET();
			} else {
				result = ajax.DELETE();
			}

			server_status = result.get_code();
			server_body = result.get_body_as_byte();
			server_contents_type = result.get_header("CONTENT-TYPE");
		}

		//サーバーからのデータを、クライアントが許可している形式に変換する(DICT系のみ)
		if (DataType.from_mimetype(server_contents_type) != DataType.None) {
			ServerResponseConverter converter = new ServerResponseConverter();
			converter.convert(server_contents_type, server_body, client_accept_type);
			server_body = converter.get_body();

			switch (client_accept_type) {
				case RSDF:
					server_contents_type = "application/rsdf";
					break;
				case JSON:
					server_contents_type = "application/json; charset=UTF-8";
					break;
			}
		}

		//クライアントが許可している圧縮方式にエンコードする
		if (client_accept_encode != EncodeType.Plain) {
			server_body = ByteEncoder.encode(client_accept_encode, server_body);
		}

		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(server_status), Unpooled.wrappedBuffer(server_body));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, server_contents_type);
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
		//response.headers().set("RSV-CONTENT-ENCODE", client_accept_encode.name()); TODO:←いつかやる
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
}
