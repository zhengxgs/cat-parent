package com.dianping.cat.report.alert.sender.sender;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.List;

import com.alibaba.fastjson.JSONObject;
import com.dianping.cat.Cat;
import com.dianping.cat.home.heavy.entity.Url;
import com.dianping.cat.report.alert.sender.AlertChannel;
import com.dianping.cat.report.alert.sender.AlertMessageEntity;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.avro.data.Json;
import org.apache.commons.codec.binary.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.util.JSONPObject;
import org.unidal.helper.Files;

public class WeixinSender extends AbstractSender {

	public static final String ID = AlertChannel.WEIXIN.getName();

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public boolean send(AlertMessageEntity message) {
		com.dianping.cat.home.sender.entity.Sender sender = querySender();
		boolean batchSend = sender.getBatchSend();
		boolean result = false;

		if (batchSend) {
			String weixins = message.getReceiverString();

			result = sendWeixin(message, weixins, sender);
		} else {
			List<String> weixins = message.getReceivers();

			for (String weixin : weixins) {
				boolean success = sendWeixin(message, weixin, sender);
				result = result || success;
			}
		}
		return result;
	}

	/**
	 * 发送微信
	 * @param message
	 * @param receiver
	 * @param sender
	 * @return
	 */
	private boolean sendWeixin(AlertMessageEntity message, String receiver, com.dianping.cat.home.sender.entity.Sender sender) {
		String domain = message.getGroup();
		String title = message.getTitle().replaceAll(",", " ");
		String content = message.getContent().replaceAll(",", " ").replaceAll("<a href.*(?=</a>)</a>", "");

		// ext attr - > weixin (toparty:6 || touser:16)

		// 添加的微信支持，重新编译cat项目就会被覆盖，所以写死
		// String extAttr = sender.getExtAttr();
		// String[] attr = org.apache.commons.lang.StringUtils.split(extAttr, ":");

		JSONObject jsonObject = new JSONObject();
		jsonObject.put("msgtype", "text");
		jsonObject.put("agentid", "1");
		jsonObject.put("safe", "0");
		// jsonObject.put(attr[0], attr[1]);
		jsonObject.put("touser", "16");

		JSONObject j = new JSONObject();
		j.put("content", domain + " " + title + " " + content);
		jsonObject.put("text", j);

		try {
			sendWeixinHttp(getToken(), jsonObject.toJSONString());
		} catch (Exception e) {
			Cat.logError(new RuntimeException("Illegal request type: post"));
			return false;
		}
		return true;
	}

	/*
	private boolean sendWeixin(AlertMessageEntity message, String receiver, com.dianping.cat.home.sender.entity.Sender sender) {
		String domain = message.getGroup();
		String title = message.getTitle().replaceAll(",", " ");
		String content = message.getContent().replaceAll(",", " ").replaceAll("<a href.*(?=</a>)</a>", "");
		String urlPrefix = sender.getUrl();
		String urlPars = m_senderConfigManager.queryParString(sender);
	
		try {
			urlPars = urlPars.replace("${domain}", URLEncoder.encode(domain, "utf-8")).replace("${receiver}", URLEncoder.encode(receiver, "utf-8"))
					.replace("${title}", URLEncoder.encode(title, "utf-8")).replace("${content}", URLEncoder.encode(content, "utf-8"))
					.replace("${type}", URLEncoder.encode(message.getType(), "utf-8"));
		} catch (Exception e) {
			Cat.logError(e);
		}
		return httpSend(sender.getSuccessCode(), sender.getType(), urlPrefix, urlPars);
	}
	*/

	private void sendWeixinHttp(String token, String params) {
		String url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token;
		// String params = "{\"toparty\":\"6\",\"msgtype\":\"text\",\"agentid\":\"1\",\"text\":{\"content\": \"" + content + "\"},\"safe\",\"0\"}";

		System.out.println("url:" + url + " params:" + params);
		String response = httpPostSend(url, params);

		JsonParser jsonParser = new JsonParser();
		JsonElement parse = jsonParser.parse(response);
		JsonElement access_token = parse.getAsJsonObject().get("errmsg");
		if (!access_token.getAsString().equals("ok")) {
			System.out.println("微信通知失败");
		}
	}

	/**
	 * 获取微信token
	 * @return
	 */
	private String getToken() {
		String corpId = "wx6810a1001ac73cec";
		String corpsecret = "OcPsQwhN8kt9ko3CA9tJeK6ErW3rWuo4vG857hg44qYgiRBp5_1JCjvl-N_lzOQT";
		String tokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s";

		String url = String.format(tokenUrl, corpId, corpsecret);
		String response = httpGetSend(url);
		JsonParser jsonParser = new JsonParser();
		JsonElement parse = jsonParser.parse(response);
		JsonElement access_token = parse.getAsJsonObject().get("access_token");
		return access_token.getAsString();
	}

	private String httpGetSend(String getUrl) {
		URL url = null;
		InputStream in = null;
		URLConnection conn = null;
		try {
			url = new URL(getUrl);
			conn = url.openConnection();

			conn.setConnectTimeout(2000);
			conn.setReadTimeout(3000);

			in = conn.getInputStream();
			StringBuilder sb = new StringBuilder();
			sb.append(Files.forIO().readFrom(in, "utf-8")).append("");
			return sb.toString();
		} catch (Exception e) {
			return null;
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
			}
		}
	}

	private String httpPostSend(String urlPrefix, String content) {
		URL url = null;
		InputStream in = null;
		OutputStreamWriter writer = null;
		URLConnection conn = null;
		try {
			url = new URL(urlPrefix);
			conn = url.openConnection();

			conn.setConnectTimeout(2000);
			conn.setReadTimeout(3000);
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestProperty("content-type", "application/x-www-form-urlencoded;charset=UTF-8");
			writer = new OutputStreamWriter(conn.getOutputStream());

			writer.write(content);
			writer.flush();

			in = conn.getInputStream();
			StringBuilder sb = new StringBuilder();

			sb.append(Files.forIO().readFrom(in, "utf-8")).append("");
			return sb.toString();
		} catch (Exception e) {
			return null;
		} finally {
			try {
				if (in != null) {
					in.close();
				}
				if (writer != null) {
					writer.close();
				}
			} catch (IOException e) {
			}
		}
	}

}
