#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <stdbool.h>
#include <microhttpd.h>
#include <curl/curl.h>

typedef struct {
	char *name;
	char *value;
} RequestHeader;

typedef struct {
	char *method;
	char *url;

	RequestHeader *header_list;
	size_t header_length;

	unsigned char *body;
	size_t body_length;
} RequestContext;

typedef struct {
	char *query;
	size_t length;
} URLParamBuilderData;

typedef struct {
	char *name;
	char *value;
} ResponseHeader;

typedef struct {
	ResponseHeader *header_list;
	size_t length;

	uint64_t content_length;
} ResponseHeaderList;

typedef struct {
	char *data;
	size_t length;
} Buffer;

//ヘッダー取得
static enum MHD_Result header_processor(void *cls, enum MHD_ValueKind kind, const char *key, const char *value) {
	RequestContext *ctx = cls;
	ctx->header_list = realloc(ctx->header_list, sizeof(RequestHeader) * (ctx->header_length + 1));
	ctx->header_list[ctx->header_length].name = strdup(key);
	ctx->header_list[ctx->header_length].value = strdup(value);
	ctx->header_length += 1;
	return MHD_YES;
}

//URL取得
static enum MHD_Result url_param_builder(void *cls, enum MHD_ValueKind kind, const char *key, const char *value) {
	URLParamBuilderData *data = cls;

	size_t add_length = strlen(key) + strlen(value) + 2;
	data->query = realloc(data->query, data->length + add_length + 1);
	if (data->length == 0) {
		sprintf(data->query, "%s=%s", key, value);
		data->length = strlen(data->query);
	} else {
		sprintf(data->query, "&%s=%s", key, value);
		data->length = strlen(data->query);
	}

	return MHD_YES;
}

//cURL
size_t server_header_callback(char *ptr, size_t size, size_t nmemb, void *data) {
	size_t total = size * nmemb;
	ResponseHeaderList *list = (ResponseHeaderList *)data;

	//1行コピー
	char *line = malloc(total + 1);
	memcpy(line, ptr, total);
	line[total] = 0x00;

	//改行を削除
	char *line2 = strchr(line, '\r');
	if (line2) *line2 = 0x00;

	//コロンを探す
	char *colon = strchr(line, ':');
	if (colon) {
		*colon = 0x00;
		char *name = line;
		char *value = colon + 1;

		//値の先頭にあるスペース削除
		while (*value == ' ') value += 1;

		//配列拡張
		list->header_list = realloc(list->header_list, sizeof(ResponseHeader) * (list->length + 1));

		//セット
		list->header_list[list->length].name = strdup(name);
		list->header_list[list->length].value = strdup(value);
		list->length += 1;

		//Content-Length?
		if (strcasecmp(name, "Content-Length") == 0) {
			list->content_length = atol(value);
		}
	}

	free(line);
	return total;
}

size_t server_body_callback(char *ptr, size_t size, size_t nmemb, void *data) {
	Buffer *buffer = (Buffer *)data;
	size_t total = size * nmemb;

	buffer->data = realloc(buffer->data, buffer->length + total);
	memcpy(buffer->data + buffer->length, ptr, total);
	buffer->length += total;

	return total;
}

//受付
static enum MHD_Result handler(void *cls, struct MHD_Connection *connection, const char *url, const char *method, const char *version, const char *upload_data, size_t *upload_data_size, void **connection_cls) {
	RequestContext *ctx = *connection_cls;

	//初回
	if (ctx == NULL) {
		ctx = calloc(1, sizeof(RequestContext));
		ctx->method = strdup(method);
		ctx->url = strdup(url);

		MHD_get_connection_values(connection, MHD_HEADER_KIND, header_processor, ctx);
		*connection_cls = ctx;
		return MHD_YES;
	}

	//body受信
	if (*upload_data_size > 0) {
		ctx->body = realloc(ctx->body, ctx->body_length + *upload_data_size);
		memcpy(ctx -> body + ctx->body_length, upload_data, *upload_data_size);
		ctx->body_length += *upload_data_size;
		*upload_data_size = 0;
		return MHD_YES;
	}

	//URLパラメーター取得
	URLParamBuilderData upd = {0x00};
	MHD_get_connection_values(connection, MHD_GET_ARGUMENT_KIND, url_param_builder, &upd);

	//パス + ホスト
	char *host_url;
	size_t host_url_length;
	const char *host = MHD_lookup_connection_value(connection, MHD_HEADER_KIND, "Host");
	if (host) {
		host_url_length = strlen("http://") + strlen(host) + strlen(ctx->url);
		host_url = malloc(host_url_length);
		strcpy(host_url, "http://");
		strcat(host_url, host);
		strcat(host_url, ctx->url);
	}

	//パス + URLパラメーター
	char *full_url;
	if (upd.length > 0) {
		full_url = malloc(host_url_length + upd.length + 2);
		sprintf(full_url, "%s?%s", host_url, upd.query);
	} else {
		full_url = strdup(host_url);
	}

	//殺す
	free(ctx->url);
	ctx->url = full_url;
	free(upd.query);
	free(host_url);

	//サーバーURL
	char server_url[1024];
	strcpy(server_url, "http://");
	uint8_t slash_count = 0;
	for (size_t i = 0; ctx->url[i] != 0x00; i++) {
		if (ctx->url[i] == '/') {
			slash_count += 1;
			if (slash_count == 3) {
				strcat(server_url, &ctx->url[i + 1]);
				break;
			}
		}
	}

	printf("[ INFO ] <%s> -[ﾋﾟﾛｷｼ]-> %s\n", ctx->method, server_url);

	//cURL
	CURL *curl = curl_easy_init();
	curl_easy_setopt(curl, CURLOPT_URL, server_url);
	curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, ctx->method);
	struct curl_slist *to_server_header_list = NULL;
		for (size_t i = 0; i < ctx->header_length; i++) {
		char header_text[strlen(ctx->header_list[i].name) + strlen(ctx->header_list[i].value) + 3];
		strcpy(header_text, ctx->header_list[i].name);
		strcat(header_text, ": ");
		strcat(header_text, ctx->header_list[i].value);
		to_server_header_list = curl_slist_append(to_server_header_list, header_text);
	}
	curl_easy_setopt(curl, CURLOPT_HTTPHEADER, to_server_header_list);
	if (ctx->body_length != 0) curl_easy_setopt(curl, CURLOPT_POSTFIELDS, ctx->body);

	//ヘッダー受信
	ResponseHeaderList header_list = {0};
	curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, server_header_callback);
	curl_easy_setopt(curl, CURLOPT_HEADERDATA, &header_list);

	//body受信
	Buffer *buffer = calloc(1, sizeof(Buffer));
	buffer->data = NULL;
	buffer->length = 0;
	curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, server_body_callback);
	curl_easy_setopt(curl, CURLOPT_WRITEDATA, buffer);

	//RUN
	curl_easy_perform(curl);

	//応答
	struct MHD_Response *response = MHD_create_response_from_buffer(buffer->length, (void *)buffer->data, MHD_RESPMEM_PERSISTENT);

	for (size_t i = 0; i < header_list.length; i++) {
		MHD_add_response_header(response, header_list.header_list[i].name, header_list.header_list[i].value);
	}

	enum MHD_Result ret = MHD_queue_response(connection, 200, response);
	MHD_destroy_response(response);

	//殺す
	free(ctx->method);
	free(ctx->url);
	for (size_t i = 0; i < ctx->header_length; i++) {
		free(ctx->header_list[i].name);
		free(ctx->header_list[i].value);
	}
	free(ctx->header_list);
	free(ctx->body);
	free(ctx);
	for (size_t i = 0; i < header_list.length; i++) {
		free(header_list.header_list[i].name);
		free(header_list.header_list[i].value);
	}
	free(header_list.header_list);
	curl_easy_cleanup(curl);
	*connection_cls = NULL;

	return ret;
}

int main(int argc, char const *argv[]) {
	if (argc < 2) {
		printf("引数にポート番号が必要です\n");
		return 1;
	}

	//引数のポート番号を変換
	char *end;
	long value = strtol(argv[1], &end, 10);
	if (*end != 0x00) {
		printf("ポート番号が不正です\n");
		return 1;
	}

	int port = (int)value;
	if (port < 0 || port > 65535) {
		printf("ポート番号がデカすぎます\n");
		return 1;
	}

	//サーバー起動
	struct MHD_Daemon *daemon;
	daemon = MHD_start_daemon(MHD_USE_SELECT_INTERNALLY, port, NULL, NULL, handler, NULL, MHD_OPTION_END);
	if (!daemon) {
		printf("HTTPサーバーの起動に失敗しました");
		return 1;
	}

	printf("OK\n");
	getchar();

	MHD_stop_daemon(daemon);

	return 0;
}
