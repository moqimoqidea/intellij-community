// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.history.CopyData;
import org.jetbrains.idea.svn.history.FirstInBranch;
import org.jetbrains.idea.svn.history.SvnChangeList;

import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.idea.svn.SvnBundle.message;

@Service(Service.Level.PROJECT)
public final class SvnMergeInfoCache {

  private static final Logger LOG = Logger.getInstance(SvnMergeInfoCache.class);

  private final @NotNull Project myProject;
  // key - working copy root url
  private final @NotNull Map<Url, MyCurrentUrlData> myCurrentUrlMapping;

  @Topic.ProjectLevel
  public static final Topic<SvnMergeInfoCacheListener> SVN_MERGE_INFO_CACHE =
    new Topic<>("SVN_MERGE_INFO_CACHE", SvnMergeInfoCacheListener.class);

  private SvnMergeInfoCache(@NotNull Project project) {
    myProject = project;
    myCurrentUrlMapping = new HashMap<>();
  }

  public static SvnMergeInfoCache getInstance(@NotNull Project project) {
    return project.getService(SvnMergeInfoCache.class);
  }

  public void clear(@NotNull WCInfoWithBranches info, String branchPath) {
    BranchInfo branchInfo = getBranchInfo(info, branchPath);

    if (branchInfo != null) {
      branchInfo.clear();
    }
  }

  public @Nullable MergeInfoCached getCachedState(@NotNull WCInfoWithBranches info, String branchPath) {
    BranchInfo branchInfo = getBranchInfo(info, branchPath);

    return branchInfo != null ? branchInfo.getCached() : null;
  }

  // only refresh might have changed; for branches/roots change, another method is used
  public MergeCheckResult getState(@NotNull WCInfoWithBranches info,
                                   @NotNull SvnChangeList list,
                                   @NotNull WCInfoWithBranches.Branch selectedBranch,
                                   final String branchPath) {
    MyCurrentUrlData rootMapping = myCurrentUrlMapping.get(info.getUrl());
    BranchInfo mergeChecker = null;
    if (rootMapping == null) {
      rootMapping = new MyCurrentUrlData();
      myCurrentUrlMapping.put(info.getUrl(), rootMapping);
    } else {
      mergeChecker = rootMapping.getBranchInfo(branchPath);
    }
    if (mergeChecker == null) {
      mergeChecker = new BranchInfo(SvnVcs.getInstance(myProject), info, selectedBranch);
      rootMapping.addBranchInfo(branchPath, mergeChecker);
    }

    return mergeChecker.checkList(list, branchPath);
  }

  public boolean isMixedRevisions(@NotNull WCInfoWithBranches info, final String branchPath) {
    BranchInfo branchInfo = getBranchInfo(info, branchPath);

    return branchInfo != null && branchInfo.isMixedRevisionsFound();
  }

  private @Nullable BranchInfo getBranchInfo(@NotNull WCInfoWithBranches info, String branchPath) {
    MyCurrentUrlData rootMapping = myCurrentUrlMapping.get(info.getUrl());

    return rootMapping != null ? rootMapping.getBranchInfo(branchPath) : null;
  }

  static class CopyRevison {
    private final String myPath;
    private volatile long myRevision;

    CopyRevison(final SvnVcs vcs, final String path, @NotNull Url repositoryRoot, @NotNull Url branchUrl, @NotNull Url trunkUrl) {
      myPath = path;
      myRevision = -1;

      Task.Backgroundable task = new Task.Backgroundable(vcs.getProject(), message("progress.title.calculating.copy.revision"), false) {
        private CopyData myData;

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            myData = new FirstInBranch(vcs, repositoryRoot, branchUrl, trunkUrl).run();
          }
          catch (VcsException e) {
            logAndShow(e);
          }
        }

        @Override
        public void onSuccess() {
          if (myData != null) {
            myRevision = myData.getCopySourceRevision();
            if (myRevision != -1) {
              BackgroundTaskUtil.syncPublisher(vcs.getProject(), SVN_MERGE_INFO_CACHE).copyRevisionUpdated();
            }
          }
        }

        @Override
        public void onThrowable(@NotNull Throwable error) {
          logAndShow(error);
        }

        private void logAndShow(@NotNull Throwable error) {
          LOG.info(error);
          VcsBalloonProblemNotifier.showOverChangesView(vcs.getProject(), error.getMessage(), MessageType.ERROR);
        }
      };
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new EmptyProgressIndicator());
    }

    public String getPath() {
      return myPath;
    }

    public long getRevision() {
      return myRevision;
    }
  }

  private static final class MyCurrentUrlData {

    // key - working copy local path
    private final @NotNull Map<String, BranchInfo> myBranchInfo = CollectionFactory.createSoftMap();

    private MyCurrentUrlData() {
    }

    public BranchInfo getBranchInfo(final String branchUrl) {
      return myBranchInfo.get(branchUrl);
    }

    public void addBranchInfo(@NotNull String branchUrl, @NotNull BranchInfo mergeChecker) {
      myBranchInfo.put(branchUrl, mergeChecker);
    }
  }

  public interface SvnMergeInfoCacheListener {
    void copyRevisionUpdated();
  }
}
