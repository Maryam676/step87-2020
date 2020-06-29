package com.google.sps.workspace;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Provides an interface to access and modify workspaces in the datastore. The contents of this
 * object will only reflect the current state of the entities. It will not update with the
 * datastore.
 */
public class Workspace {
  private final DatabaseReference reference;

  protected Workspace(DatabaseReference reference) {
    this.reference = Objects.requireNonNull(reference);
  }

  /** @return the studentUID */
  public Future<String> getStudentUID() {
    CompletableFuture<String> future = new CompletableFuture<>();

    reference
        .child("student")
        .addListenerForSingleValueEvent(
            new ValueEventListener() {

              @Override
              public void onDataChange(DataSnapshot snapshot) {
                future.complete((String) snapshot.getValue());
              }

              @Override
              public void onCancelled(DatabaseError error) {
                future.completeExceptionally(error.toException());
              }
            });

    return future;
  }

  /** @return the taUID */
  public Future<String> getTaUID() {
    CompletableFuture<String> future = new CompletableFuture<>();

    reference
        .child("ta")
        .addListenerForSingleValueEvent(
            new ValueEventListener() {

              @Override
              public void onDataChange(DataSnapshot snapshot) {
                future.complete((String) snapshot.getValue());
              }

              @Override
              public void onCancelled(DatabaseError error) {
                future.completeExceptionally(error.toException());
              }
            });

    return future;
  }

  public Future<List<WorkspaceFile>> getFiles() {
    CompletableFuture<List<WorkspaceFile>> future = new CompletableFuture<>();

    reference
        .child("files")
        .addListenerForSingleValueEvent(
            new ValueEventListener() {

              @Override
              public void onDataChange(DataSnapshot snapshot) {
                try {
                  ArrayList<WorkspaceFile> children = new ArrayList<>();
                  for (DataSnapshot child : snapshot.getChildren()) {
                    children.add(new WorkspaceFile(child));
                  }

                  future.complete(children);
                } catch (Exception e) {
                  future.completeExceptionally(e);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                future.completeExceptionally(error.toException());
              }
            });

    return future;
  }

  /** @return the workspaceID */
  public String getWorkspaceID() {
    return reference.getKey();
  }

  public WorkspaceArchive getArchive() {
    return new WorkspaceArchive(this);
  }
}
