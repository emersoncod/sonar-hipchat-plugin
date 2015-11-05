package com.sonar.hipchat.plugin;

import static com.sonar.hipchat.plugin.SonarHipChatProperties.*;
import static org.apache.commons.lang.StringUtils.isBlank;

import java.io.IOException;
import java.util.List;

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
import org.sonar.api.issue.Issue;
import org.sonar.api.issue.ProjectIssues;
import org.sonar.api.resources.Project;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.sonar.hipchat.plugin.model.Notification;
import com.sonar.hipchat.plugin.model.Notification.NotificationColor;

public class SonarHipChatNotifier implements PostJob {
	private Logger LOGGER = LoggerFactory.getLogger(SonarHipChatNotifier.class);

	private Settings settings;
	private ProjectIssues projectIssues;

	public SonarHipChatNotifier(Settings settings, ProjectIssues projectIssues) {
		this.settings = settings;
		this.projectIssues = projectIssues;
	}

	public void executeOn(Project project, SensorContext context) {
		if (!settings.getBoolean(DISABLED)) {
			String room = settings.getString(ROOM);
			String token = settings.getString(TOKEN);

			if (isBlank(room) || isBlank(token)) {
				LOGGER.warn("No Room or token information available. No notification is send");
				return;
			}

			String postUrl = String.format(URL_TEMPLATE, room, token);
			preNotification(project, postUrl);
			sendStatusNotification(project, postUrl);
		}
	}

	private void sendStatusNotification(Project project, String postUrl) {
		List<Issue> issues = Lists.newArrayList(projectIssues.issues());
		long newIssues = issues.stream().filter(i -> i.isNew()).count();
		List<Issue> resolvedIssues = Lists.newArrayList(projectIssues.resolvedIssues());
		
		NotificationColor color = determineNotificationColor(resolvedIssues.size(), newIssues, issues.size());
		String message = String.format("%s has been analysed at %s.%nStatus: %d new issues | %d resolved issues | %d total issues", project.getName(), project.getAnalysisDate().toString(), newIssues, resolvedIssues.size(), issues.size());
		sendNotification(postUrl, createJsonMessage(message, color));
	}

	private NotificationColor determineNotificationColor(int resolved, long newIssues, int totalIssues) {
		if (resolved > 0 && newIssues == 0) {
			return NotificationColor.green;
		}
		else if (newIssues > 0 || totalIssues > 0) {
			return NotificationColor.red;
		}
		return NotificationColor.gray;
	}

	private void preNotification(Project project, String postUrl) {
		String preMessage = settings.getString(MESSAGE);
		if (!isBlank(preMessage)) {
			String message = preMessage.replace("{project}", project.getName()).replace("{date}",
					project.getAnalysisDate().toString());
			sendNotification(postUrl, createJsonMessage(message, NotificationColor.gray));
		}
	}

	private void sendNotification(String postUrl, String request) {
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

		if (statusCode / 10 == 20) {
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

	private String createJsonMessage(String message, NotificationColor backgroundColor) {
		Notification notification = new Notification();
		notification.setColor(backgroundColor);
		notification.setMessage(message);
		return new Gson().toJson(notification);
	}
}
