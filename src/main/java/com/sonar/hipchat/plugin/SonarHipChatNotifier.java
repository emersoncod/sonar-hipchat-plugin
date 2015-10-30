package com.sonar.hipchat.plugin;

import static com.sonar.hipchat.plugin.SonarHipChatProperties.DISABLED;
import static com.sonar.hipchat.plugin.SonarHipChatProperties.ROOM;
import static com.sonar.hipchat.plugin.SonarHipChatProperties.TOKEN;
import static com.sonar.hipchat.plugin.SonarHipChatProperties.URL_TEMPLATE;

import java.io.IOException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.PostJob;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.config.Settings;
import org.sonar.api.resources.Project;

import com.google.gson.Gson;
import com.sonar.hipchat.plugin.model.Notification;

public class SonarHipChatNotifier implements PostJob {
	private Logger LOGGER = LoggerFactory.getLogger(SonarHipChatNotifier.class);

	private Settings settings;

	public SonarHipChatNotifier(Settings settings) {
		this.settings = settings;
	}

	public void executeOn(Project project, SensorContext context) {
		if (!settings.getBoolean(DISABLED)) {
			String postUrl = String.format(URL_TEMPLATE, settings.getString(ROOM), settings.getString(TOKEN));
			sendNotification(postUrl, createJsonMessage());
		}
	}

	private void sendNotification(String postUrl, String request) {
		LOGGER.info("Sending notification to " + postUrl);
		HttpPost post = new HttpPost(postUrl);
		post.setEntity(new StringEntity(request, ContentType.APPLICATION_JSON));
		send(post);
	}

	private void send(HttpPost post) {
		CloseableHttpClient httpClient = HttpClientBuilder.create().build();
		try {
			CloseableHttpResponse response = httpClient.execute(post);
			checkStatus(response);
		}
		catch (IOException e) {
			LOGGER.error("Failed ot send notification", e);
		}
		finally {
			closeClient(httpClient);
		}
	}

	private void checkStatus(CloseableHttpResponse response) {
		int statusCode = response.getStatusLine().getStatusCode();
		
		if (statusCode / 100 == 20) {
			LOGGER.info("Send successfully notification to HipChat");
		}
		else {
			LOGGER.warn("Failed to notify room: " + statusCode + ". " + response.getStatusLine().getReasonPhrase());
		}
	}

	private void closeClient(CloseableHttpClient httpClient) {
		try {
			httpClient.close();
		}
		catch (IOException e) {
			// thats ok
		}
	}

	private String createJsonMessage() {
		Notification notification = new Notification();
		notification.setMessage_format("text");
		notification.setMessage("Message send from sonar-hipchat-plugin");
		return new Gson().toJson(notification);
	}
}
