# Conference-Central

This project is based on coursework for ```Developing Scalable Apps in Java``` in Udacity. The goal of this project is to host a web application in [Google App Engine][1] (a PaaS solution), to benefit from Google cloud platform.

This project uses following APIs:
- [Google Cloud Endpoints][2]
- [Google App Engine Maven plugin][3]

## Project Setup
The project has following dependencies:
- [Java][5]
- [Apache Maven][6]
- Java IDE(Eclipse, IntelliJ IDEA etc)
Download the project in your local machine and import it as an existing Maven project. The pom.xml file includes appengine-sdk and other dependencies, which will be downloaded automatically.

## Deploying application to App Engine
1. Update the value of application in appengine-web.xml to the app ID of the application registered in the App Engine admin console.
2. Update the values in ```src/main/java/com/google/devrel/training/conference/Constants.java``` to reflect the respective client IDs as registered in the Developer Console.
3. (Optional) Mark this file as unchanged as follows: 
```sh
$ git update-index --assume-unchanged src/main/java/com/google/devrel/training/conference/Constants.java
```
4. mvn clean install
5. Run the application with mvn appengine:devserver, and ensure it's running by visiting local server's address (by default ```localhost:8080```.)
6. Get the client library with ```mvn appengine:endpoints_get_client_lib```
7. Deploy the application as ```<your-app-id>.appspot.com```.

**For more detailed instructions with screenshots, refer to this [link][4]**.

## Project Structure
```
src
    | main
        | java
            | <includes all java files>
        | webapps
            | <includes html, js, css and web.xml files>
    | test
        | <includes junit test files>
```
This project has following java sources:
* Constants.java : Includes web-client id, email scope etc details.
* in domain package:-
    * Announcement.java: Wrapper class for announcement messages.
    * Conference.java: Entity class for conference database object.
    * Profile.java: Entity class for profile database object.
* in form package:-
    * ConferenceForm.java: POJO for conferences.
    * ConferenceQueryForm.java: POJO representing query options for a conference.
    * ProfileForm.java: POJO for user profile.
* in service package:-
    * OfyService.java: Custom [objectify][7] service used by the application.
* in servlet package:-
    * SendConfirmationEmailServlet.java: Servlet for notification email.
    * SetAnnouncementServlet.java: Servlet for announcements in conferences and putting them in [mem-cache][8].
* in spi package:-
    * ConferenceApi.java: Includes all APIs for conderence application.

[1]: https://cloud.google.com/appengine/
[2]: https://cloud.google.com/appengine/docs/standard/java/endpoints/
[3]: https://cloud.google.com/appengine/docs/standard/java/tools/maven
[4]: https://docs.google.com/document/d/1EO3pQ53DQP3SW4LCnOLiUtgAKCWbqXVprBWj0TOlRUM/pub
[5]: https://java.com/en/
[6]: https://maven.apache.org/
[7]: https://github.com/objectify/objectify/wiki/BasicOperations
[8]: https://cloud.google.com/appengine/docs/standard/python/memcache/
