package com.google.devrel.training.conference.domain;

import com.google.appengine.repackaged.com.google.common.collect.ImmutableList;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.annotation.Cache;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;

import java.util.ArrayList;
import java.util.List;


// This class is an Entity for user profile and mem-cache is enabled for it.
@Entity
@Cache
public class Profile {
	String displayName;
	String mainEmail;
	TeeShirtSize teeShirtSize;

	// userId is to be used in the Entity's key
	@Id String userId;
    
    /**
     * Public constructor for Profile.
     * @param userId The user id, obtained from the email
     * @param displayName Any string user wants us to display him/her on this system.
     * @param mainEmail User's main e-mail address.
     * @param teeShirtSize The User's tee shirt size
     * 
     */
    public Profile (String userId, String displayName, String mainEmail, TeeShirtSize teeShirtSize) {
    	this.userId = userId;
    	this.displayName = displayName;
    	this.mainEmail = mainEmail;
    	this.teeShirtSize = teeShirtSize;
    }
    
	public String getDisplayName() {
		return displayName;
	}

	public String getMainEmail() {
		return mainEmail;
	}

	public TeeShirtSize getTeeShirtSize() {
		return teeShirtSize;
	}

	public String getUserId() {
		return userId;
	}

    /**
     * Update the profile with given displayName and teeShirtSize
     *
     * @param name
     * @param size
     */
	public void update(String name, TeeShirtSize size) {
    	if(name != null) {
            this.displayName = name;
        }
	    if(size != null) {
            this.teeShirtSize = size;
        }
    }

	/**
     * Just making the default constructor private.
     */
    private Profile() {}

	/**
     * For registering an user, a property "conferenceKeysToAttend" is used
     */
    private List<String> conferenceKeysToAttend = new ArrayList<>(0);

	public List<String> getConferenceKeysToAttend() {
		return ImmutableList.copyOf(conferenceKeysToAttend);
	}

	public void addToConferenceKeysToAttend(String conferenceKey) {
		conferenceKeysToAttend.add(conferenceKey);
	}

	/**
	 * To deregister from the conference. Removes conferenceKey from the list of conferenceKeysToAttend.
	 * @param conferenceKey a websafe string representation of the Conference key
	 */
	public void unregisterFromConference(String conferenceKey) {
		if (conferenceKeysToAttend.contains(conferenceKey)) {
			conferenceKeysToAttend.remove(conferenceKey);
		} else {
			throw new IllegalArgumentException("The conference with key " +conferenceKey+ " has not been registered.");
		}
	}

}
