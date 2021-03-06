package com.google.sps.servlets.user;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.EmbeddedEntity;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.Gson;
import com.google.sps.firebase.FirebaseAppManager;
import com.google.sps.models.UserData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/get-user")
public class GetUserData extends HttpServlet {
  private FirebaseAuth authInstance;
  private DatastoreService datastore;

  @Override
  public void init(ServletConfig config) throws ServletException {
    try {
      authInstance = FirebaseAuth.getInstance(FirebaseAppManager.getApp());
    } catch (IOException e) {
      throw new ServletException(e);
    }
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    datastore = DatastoreServiceFactory.getDatastoreService();

    try {
      // Find user ID
      String idToken = request.getParameter("idToken");
      FirebaseToken decodedToken = authInstance.verifyIdToken(idToken);
      String userID = decodedToken.getUid();
      String userEmail = decodedToken.getEmail();

      PreparedQuery queryUser =
          datastore.prepare(
              new Query("User")
                  .setFilter(new FilterPredicate("userEmail", FilterOperator.EQUAL, userEmail)));

      Entity userEntity;
      // If the student user entity doesnt exist yet, create one
      if (queryUser.countEntities() == 0) {
        userEntity = new Entity("User");
        userEntity.setProperty("userEmail", userEmail);
        userEntity.setProperty("registeredClasses", Collections.emptyList());
        userEntity.setProperty("ownedClasses", Collections.emptyList());
        userEntity.setProperty("taClasses", Collections.emptyList());

        datastore.put(userEntity);
      } else {
        userEntity = queryUser.asSingleEntity();
      }

      List<Key> registeredClassesList = (List<Key>) userEntity.getProperty("registeredClasses");
      List<Key> ownedClassesList = (List<Key>) userEntity.getProperty("ownedClasses");
      List<Key> taClassesList = (List<Key>) userEntity.getProperty("taClasses");

      List<UserData> userClasses = new ArrayList<UserData>();
      for (Key classKey : registeredClassesList) {
        String code = KeyFactory.keyToString(classKey);
        String name = (String) datastore.get(classKey).getProperty("name");
        String type = "registeredClasses";

        Entity classEntity = datastore.get(classKey);

        ArrayList<EmbeddedEntity> queue =
            (ArrayList<EmbeddedEntity>) classEntity.getProperty("studentQueue");
        Optional<EmbeddedEntity> studentEntity =
            queue.stream()
                .filter(elem -> ((String) elem.getProperty("uID")).equals(userID))
                .findFirst();

        EmbeddedEntity beingHelped = (EmbeddedEntity) classEntity.getProperty("beingHelped");

        boolean inQueue = studentEntity.isPresent() || beingHelped.hasProperty(userID);

        userClasses.add(new UserData(code, name, type, inQueue));
      }

      for (Key classKey : ownedClassesList) {
        String code = KeyFactory.keyToString(classKey);
        String name = (String) datastore.get(classKey).getProperty("name");
        String type = "ownedClasses";

        userClasses.add(new UserData(code, name, type));
      }

      for (Key classKey : taClassesList) {
        String code = KeyFactory.keyToString(classKey);
        String name = (String) datastore.get(classKey).getProperty("name");
        String type = "taClasses";

        userClasses.add(new UserData(code, name, type));
      }

      response.setContentType("application/json;");
      response.getWriter().print(new Gson().toJson(userClasses));

    } catch (EntityNotFoundException e) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
    } catch (IllegalArgumentException e) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    } catch (FirebaseAuthException e) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
  }
}
