package com.google.sps.servlets.course;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.common.annotations.VisibleForTesting;
import com.google.sps.authentication.Authenticator;
import com.google.sps.tasks.TaskSchedulerFactory;
import com.google.sps.tasks.servlets.PullNewEnvironment;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/queueEnvPull")
public class QueueEnvPull extends HttpServlet {
  private TaskSchedulerFactory taskSchedulerFactory;
  private Authenticator auth;
  @VisibleForTesting protected String QUEUE_NAME;

  @Override
  public void init() throws ServletException {
    this.taskSchedulerFactory = TaskSchedulerFactory.getInstance();
    QUEUE_NAME = System.getenv("EXECUTION_QUEUE_ID");

    try {
      auth = new Authenticator();
    } catch (IOException e) {
      throw new ServletException(e);
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    String classID = req.getParameter("classID");
    String image = req.getParameter("image");
    String tag = req.getParameter("tag");
    String name = req.getParameter("name");
    String idToken = req.getParameter("idToken");
    Key classKey = KeyFactory.stringToKey(classID);

    if (auth.verifyTaOrOwner(idToken, classKey)) {
      DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();

      PreparedQuery q =
          datastore.prepare(
              new Query("Environment")
                  .setFilter(
                      CompositeFilterOperator.and(
                          new FilterPredicate(
                              "image",
                              FilterOperator.EQUAL,
                              PullNewEnvironment.getImageName(classID, image)),
                          new FilterPredicate("class", FilterOperator.EQUAL, classKey),
                          new FilterPredicate("tag", FilterOperator.EQUAL, tag))));

      if (q.countEntities(FetchOptions.Builder.withLimit(1)) == 0) {
        Entity e = new Entity("Environment");
        e.setProperty("status", "pulling");
        e.setProperty("class", classKey);
        e.setProperty("name", name);
        datastore.put(e);
        String envID = KeyFactory.keyToString(e.getKey());

        taskSchedulerFactory
            .create(QUEUE_NAME, "/tasks/pullEnv")
            .schedule(String.join(",", envID, classID, image, tag));

        resp.getWriter().print(envID);
      } else {
        resp.sendError(HttpServletResponse.SC_CONFLICT);
      }
    } else {
      resp.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
  }
}
