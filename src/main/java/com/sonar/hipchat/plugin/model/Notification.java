package com.sonar.hipchat.plugin.model;

public class Notification {
	private String message;
	private String message_format;

	public String getMessage() {
		return message;
	}

	public String getMessage_format() {
		return message_format;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public void setMessage_format(String message_format) {
		this.message_format = message_format;
	}
}
