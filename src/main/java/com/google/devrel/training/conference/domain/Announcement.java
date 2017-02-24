package com.google.devrel.training.conference.domain;

/**
 * Created by Ideapad on 20-02-2017.
 * Wrapper class for announcement messages, as endpoint functions can not pass string messages.
 */
public class Announcement {
    private String message;

    public Announcement() {}

    public Announcement(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
