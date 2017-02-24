package com.google.devrel.training.conference.spi;

import static com.google.devrel.training.conference.service.OfyService.factory;
import static com.google.devrel.training.conference.service.OfyService.ofy;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiMethod.HttpMethod;
import com.google.api.server.spi.config.Named;
import com.google.api.server.spi.response.ConflictException;
import com.google.api.server.spi.response.ForbiddenException;
import com.google.api.server.spi.response.NotFoundException;
import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.users.User;
import com.google.devrel.training.conference.Constants;
import com.google.devrel.training.conference.domain.Announcement;
import com.google.devrel.training.conference.domain.Conference;
import com.google.devrel.training.conference.domain.Profile;
import com.google.devrel.training.conference.form.ConferenceForm;
import com.google.devrel.training.conference.form.ConferenceQueryForm;
import com.google.devrel.training.conference.form.ProfileForm;
import com.google.devrel.training.conference.form.ProfileForm.TeeShirtSize;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.cmd.Query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * Defines conference APIs.
 */
@Api(name = "conference", version = "v1", scopes = { Constants.EMAIL_SCOPE },
        clientIds = { Constants.WEB_CLIENT_ID, Constants.API_EXPLORER_CLIENT_ID },
        description = "API for the Conference Central Backend application.")
public class ConferenceApi {

    /*
     * Get the display name from the user's email. For example, if the email is
     * lemoncake@example.com, then the display name becomes "lemoncake."
     */
    private static String extractDefaultDisplayNameFromEmail(String email) {
        return email == null ? null : email.substring(0, email.indexOf("@"));
    }

    /**
     * Creates or updates a Profile object associated with the given user
     * object.
     *
     * @param user A User object injected by the cloud endpoints.
     * @param profileForm A profile object.
     *
     * @return Profile object just created.
     * @throws UnauthorizedException
     *             when the User object is null.
     */

    // Declare this method as a method available externally through Endpoints
    @ApiMethod(name = "saveProfile", path = "profile", httpMethod = HttpMethod.POST)
    // The request that invokes this method should provide data that
    // conforms to the fields defined in ProfileForm

    // Pass the User and ProfileForm parameters
    // Always pass the User as first parameter
    public Profile saveProfile(final User user, ProfileForm profileForm) throws UnauthorizedException {

        String userId = null;
        String mainEmail = null;
        String displayName = "Your name will go here";
        TeeShirtSize teeShirtSize = TeeShirtSize.NOT_SPECIFIED;

        // If the user is not logged in, throw an UnauthorizedException
        if(user == null)
            throw new UnauthorizedException("You need to sign in first.");

        // Get the userId and mainEmail
        userId = user.getUserId();
        mainEmail = user.getEmail();

        // Get the displayName and teeShirtSize sent by request
        displayName = profileForm.getDisplayName();
        teeShirtSize = profileForm.getTeeShirtSize();

        // Get the profile from datastore if it exists
        // otherwise create a new one
        Profile profile = ofy().load().key(Key.create(Profile.class, userId)).now();

        if(profile == null) {
            // Populate displayName and teeShirtSize with default values
            // if not sent in the request
            if(displayName == null)
                displayName = extractDefaultDisplayNameFromEmail(user.getEmail());
            if(teeShirtSize == null) {
                teeShirtSize = teeShirtSize.NOT_SPECIFIED;
            }
            // Now create a new Profile entity
            profile = new Profile(userId, displayName, mainEmail, teeShirtSize);
        } else {
            // The Profile already exists
            // Update the Profile entity
            profile.update(displayName, teeShirtSize);
        }

        // Save the Profile entity in the datastore
        ofy().save().entities(profile).now();

        // Return the profile
        return profile;
    }

    /**
     * Returns a Profile object associated with the given user object. The cloud
     * endpoints system automatically inject the User object.
     *
     * @param user
     *            A User object injected by the cloud endpoints.
     * @return Profile object.
     * @throws UnauthorizedException
     *             when the User object is null.
     */
    @ApiMethod(name = "getProfile", path = "profile", httpMethod = HttpMethod.GET)
    public Profile getProfile(final User user) throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // load the Profile Entity
        String userId = user.getUserId();
        Key key = Key.create(Profile.class, userId);
        Profile profile = (Profile) ofy().load().key(key).now();
        return profile;
    }

    /**
     * Gets the Profile entity for the current user
     * or creates it if it doesn't exist
     * @param user
     * @return user's Profile
     */
    private static Profile getProfileFromUser(User user) {
        // First fetch the user's Profile from the datastore.
        Profile profile = ofy().load().key(
                Key.create(Profile.class, user.getUserId())).now();
        if (profile == null) {
            // Create a new Profile if it doesn't exist.
            // Use default displayName and teeShirtSize
            String email = user.getEmail();
            profile = new Profile(user.getUserId(),
                    extractDefaultDisplayNameFromEmail(email), email, TeeShirtSize.NOT_SPECIFIED);
        }
        return profile;
    }

    /**
     * Creates a new Conference object and stores it to the datastore.
     *
     * @param user A user who invokes this method, null when the user is not signed in.
     * @param conferenceForm A ConferenceForm object representing user's inputs.
     * @return A newly created Conference Object.
     * @throws UnauthorizedException when the user is not signed in.
     */
    @ApiMethod(name = "createConference", path = "conference", httpMethod = HttpMethod.POST)
    public Conference createConference(final User user, final ConferenceForm conferenceForm)
            throws UnauthorizedException {
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        // Get the userId of the logged in User
        final String userId = user.getUserId();
        // Get the key for the User's Profile
        Key<Profile> profileKey = Key.create(Profile.class, userId);
        // Allocate a key for the conference -- let App Engine allocate the ID
        final Key<Conference> conferenceKey = factory().allocateId(profileKey, Conference.class);
        // Get the Conference Id from the Key
        final long conferenceId = conferenceKey.getId();
        // Create a default queue
        final Queue queue = QueueFactory.getDefaultQueue();
        // Start a transaction
        Conference conference = ofy().transact(new Work<Conference>() {
            @Override
            public Conference run() {
                // Get the existing Profile entity for the current user if there is one
                // Otherwise create a new Profile entity with default values
                Profile profile = getProfileFromUser(user);
                // Create a new Conference Entity, specifying the user's Profile entity
                // as the parent of the conference
                Conference conference = new Conference(conferenceId, userId, conferenceForm);
                // Save Conference and Profile Entities
                ofy().save().entities(profile, conference).now();
                // Add the task to the queue inside a transaction
                queue.add(ofy().getTransaction(), TaskOptions.Builder.withUrl("/tasks/send_confirmation_email")
                                                    .param("email", profile.getMainEmail())
                                                    .param("conferenceInfo", conference.toString()));
                return conference;
            }
        });
        return conference;
    }

    /**
     * Queries against the data-store with the given filters and returns the result.
     *
     * @param //conference_Query_Form A form object representing the query
     * @return A list of conferences that match the query
     */
    @ApiMethod(
            name = "queryConferences",
            path = "queryConferences",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> queryConferences(ConferenceQueryForm conferenceQueryForm) {
        // Find all entities of type Conference sorted by its name
        // Query query = ofy().load().type(Conference.class).order("name");
        // return query.list();
        //return conferenceQueryForm.getQuery().list();
        Iterable<Conference> conferenceIterable = conferenceQueryForm.getQuery();
        List<Conference> result = new ArrayList<>(0);
        List<Key<Profile>> organizersKeyList = new ArrayList<>(0);
        for (Conference conference : conferenceIterable) {
            organizersKeyList.add(Key.create(Profile.class, conference.getOrganizerUserId()));
            result.add(conference);
        }
        // To avoid separate datastore gets for each Conference, pre-fetch the Profiles.
        ofy().load().keys(organizersKeyList);
        return result;
    }

    /**
     * Returns a list of conferences that the user created.
     * In order to receive the websafeConferenceKey via JSON params, a POST method is used.
     *
     * @param user A user that invokes this API
     * @return a list of conferences that the user created
     * @throws UnauthorizedException when the user is not signed in
     */
    @ApiMethod(
            name = "getConferencesCreated",
            path = "getConferencesCreated",
            httpMethod = HttpMethod.POST
    )
    public List<Conference> getConferencesCreated(final User user)
        throws UnauthorizedException {
        // If user is not signed on, throw a 401 error
        if(user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        String userId = user.getUserId();
        Key userKey = Key.create(Profile.class, userId);
        return ofy().load().type(Conference.class).ancestor(userKey).order("name").list();
    }

    public List<Conference> queryFiterByProperty() {
        // Query all conferences sorted by name
        //Query<Conference> query = ofy().load().type(Conference.class).order("name"); // Remove oder by "name" from here
        Query<Conference> query = ofy().load().type(Conference.class);
        // Filter for city name
        query = query.filter("city =", "Tokyo");
        // Filter for topics name
        query = query.filter("topics =", "Medical Innovations");
        // Filter by month(integer value)
        query = query.filter("month =", 2);
        // Filter by max attendees

        // Following will thrown an Error: First sort must be same as the one to which inequality filter is applied.
        // query = query.filter("maxAttendees >", 10); // Error: First sort must be same as the one to which inequality filter is applied.
        // To fix it we need to remove order("name") from first statement and change above filter as follows
        query = query.filter("maxAttendees >", 10).order("maxAttendees").order("name");

        return query.list();
    }

    /**
     * Returns a Conference object with the given conferenceId.
     *
     * @param websafeConferenceKey The String representation of the Conference Key.
     * @return a Conference object with the given conferenceId.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(
            name = "getConference",
            path = "conference/{websafeConferenceKey}",
            httpMethod = HttpMethod.GET
    )
    public Conference getConference(
            @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws NotFoundException {
        Key<Conference> conferenceKey = Key.create(websafeConferenceKey);
        Conference conference = ofy().load().key(conferenceKey).now();
        if (conference == null) {
            throw new NotFoundException("No Conference found with key: " + websafeConferenceKey);
        }
        return conference;
    }

    /**
     * Just a wrapper for Boolean.
     * We need this wrapped Boolean because endpoints functions must return
     * an object instance, they can't return a Type class such as
     * String or Integer or Boolean
     */
    public static class WrappedBoolean {

        private final Boolean result;
        private final String reason;

        public WrappedBoolean(Boolean result) {
            this.result = result;
            this.reason = "";
        }

        public WrappedBoolean(Boolean result, String reason) {
            this.result = result;
            this.reason = reason;
        }

        public Boolean getResult() {
            return result;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Register to attend the specified Conference.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @param websafeConferenceKey The String representation of the Conference Key.
     * @return Boolean true when success, otherwise false
     * @throws UnauthorizedException when the user is not signed in.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(
            name = "registerForConference",
            path = "conference/{websafeConferenceKey}/registration",
            httpMethod = HttpMethod.POST
    )
    public WrappedBoolean registerForConference (final User user,
            @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws UnauthorizedException, NotFoundException,
            ForbiddenException, ConflictException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }

        // Get the userId
        final String userId = user.getUserId();

        // Start transaction
        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
            @Override
            public WrappedBoolean run() {
                try {
                    // Get the conference key which we can get from websafeConferenceKey
                    // Will throw ForbiddenException if the key cannot be created
                    Key<Conference> conferenceKey = Key.create(websafeConferenceKey);

                    // Get the Conference entity from the datastore
                    Conference conference = ofy().load().key(conferenceKey).now();

                    // 404 when there is no Conference with the given conferenceId.
                    if (conference == null) {
                        return new WrappedBoolean (false,
                                "No Conference found with key: "
                                        + websafeConferenceKey);
                    }

                    // Get the user's Profile entity
                    Profile profile = getProfileFromUser(user);

                    // Has the user already registered to attend this conference?
                    if (profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
                        return new WrappedBoolean (false, "Already registered");
                    } else if (conference.getSeatsAvailable() <= 0) {
                        return new WrappedBoolean (false, "No seats available");
                    } else {
                        // All looks good, go ahead and book the seat
                        // Add the websafeConferenceKey to the profile's conferencesToAttend property
                        profile.addToConferenceKeysToAttend(websafeConferenceKey);
                        // Decrease the conference's seatsAvailable
                        // You can use the bookSeats() method on Conference
                        conference.bookSeats(1);
                        // Save the Conference and Profile entities
                        ofy().save().entities(profile, conference).now();
                        // We are booked!
                        return new WrappedBoolean(true, "Registration successful");
                    }

                }
                catch (Exception e) {
                    return new WrappedBoolean(false, "Unknown exception");
                }
            }
        });

        throwError(result);
        return result;
    }

    /**
     * Returns a collection of Conference Object that the user is going to attend.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @return a Collection of Conferences that the user is going to attend.
     * @throws UnauthorizedException when the User object is null.
     */
    @ApiMethod(
            name = "getConferencesToAttend",
            path = "getConferencesToAttend",
            httpMethod = HttpMethod.GET
    )
    public Collection<Conference> getConferencesToAttend(final User user)
            throws UnauthorizedException, NotFoundException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        // Get the Profile entity for the user
        Profile profile = getProfileFromUser(user);
        if (profile == null) {
            throw new NotFoundException("Profile doesn't exist.");
        }
        // Get the value of the profile's conferenceKeysToAttend property
        List<String> keyStringsToAttend = profile.getConferenceKeysToAttend();

        // Iterate over keyStringsToAttend, and return a Collection of the
        // Conference entities that the user has registered to attend
        List<Key<Conference>> keysToAttend = new ArrayList<>();
        for(String keyString : keyStringsToAttend) {
            keysToAttend.add(Key.<Conference>create(keyString));
        }
        // To return the entities when a list of their keys is available
        return ofy().load().keys(keysToAttend).values();
    }

    /**
     * Unregister from the specified Conference.
     *
     * @param user An user who invokes this method, null when the user is not signed in.
     * @param websafeConferenceKey The String representation of the Conference Key
     * to unregister from.
     * @return Boolean true when success, otherwise false.
     * @throws UnauthorizedException when the user is not signed in.
     * @throws NotFoundException when there is no Conference with the given conferenceId.
     */
    @ApiMethod(name = "unregisterFromConference",
               path = "conference/{websafeConferenceKey}/unregistration",
               httpMethod = HttpMethod.DELETE)
    public WrappedBoolean unregisterFromConference(final User user,
                                                   @Named("websafeConferenceKey") final String websafeConferenceKey)
            throws UnauthorizedException, NotFoundException,
            ForbiddenException, ConflictException {
        // If not signed in, throw a 401 error.
        if (user == null) {
            throw new UnauthorizedException("Authorization required");
        }
        // Get the userId
        final String userId = user.getUserId();

        // Start transaction
        WrappedBoolean result = ofy().transact(new Work<WrappedBoolean>() {
            @Override
            public WrappedBoolean run() {
                try {
                    // Get the conference key which we can get from websafeConferenceKey
                    // Will throw ForbiddenException if the key cannot be created
                    Key<Conference> conferenceKey = Key.create(websafeConferenceKey);

                    // Get the Conference entity from the datastore
                    Conference conference = ofy().load().key(conferenceKey).now();

                    // 404 when there is no Conference with the given conferenceId.
                    if (conference == null) {
                        return new WrappedBoolean (false,
                                "No Conference found with key: "
                                        + websafeConferenceKey);
                    }

                    // Get the user's Profile entity
                    Profile profile = getProfileFromUser(user);

                    // Throw error if user hasn't already registered to attend the conference
                    if (!profile.getConferenceKeysToAttend().contains(websafeConferenceKey)) {
                        return new WrappedBoolean (false, "User not registered");
                    } else {
                        // All looks good, go ahead and give back the seat
                        // Remove the websafeConferenceKey from the profile's conferencesToAttend property
                        profile.unregisterFromConference(websafeConferenceKey);
                        // Increase the conference's seatsAvailable by calling giveBackSeats() method on Conference
                        conference.giveBackSeats(1);
                        // Save the Conference and Profile entities
                        ofy().save().entities(profile, conference).now();
                        // User is unregistered!
                        return new WrappedBoolean(true, "Un-registration successful");
                    }

                }
                catch (Exception e) {
                    return new WrappedBoolean(false, "Unknown exception");
                }
            }
        });

        throwError(result);
        return result;
    }

    private void throwError(WrappedBoolean result) throws NotFoundException, ConflictException, ForbiddenException {
        // if result is false
        if (!result.getResult()) {
            if (result.getReason().contains("No Conference found with key")) {
                throw new NotFoundException (result.getReason());
            }
            else if (result.getReason() == "Already registered") {
                throw new ConflictException("You have already registered");
            }
            else if (result.getReason() == "No seats available") {
                throw new ConflictException("There are no seats available");
            }
            else if (result.getReason() == "User not registered") {
                throw new ConflictException("You have not registered yet");
            }
            else {
                throw new ForbiddenException("Unknown exception");
            }
        }
    }

    /**
     * Function for retrieving announcements.
     * @return an announcement message
     */
    @ApiMethod(
            name = "getAnnouncement",
            path = "announcement",
            httpMethod = HttpMethod.GET
    )
    public Announcement getAnouncement() {
        MemcacheService memcacheService = MemcacheServiceFactory.getMemcacheService();
        String announcementKey = Constants.MEMCACHE_ANNOUNCEMENTS_KEY;
        Object message = memcacheService.get(announcementKey);
        if (message != null) {
            return new Announcement(message.toString());
        }
        return null;
    }
}
