// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.SearchableTextProvider;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.structureView.newStructureView.*;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl;
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.navigation.LocationPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.text.TextRangeUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xml.util.XmlStringUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

/**
 * @author Konstantin Bulenkov
 */
public final class FileStructurePopup implements Disposable, TreeActionsOwner {
  private static final Logger LOG = Logger.getInstance(FileStructurePopup.class);
  private static final @NonNls String NARROW_DOWN_PROPERTY_KEY = "FileStructurePopup.narrowDown";

  private final Project myProject;
  private final FileEditor myFileEditor;
  private final StructureViewModel myTreeModelWrapper;
  private final StructureViewModel myTreeModel;
  private final TreeStructureActionsOwner myTreeActionsOwner;

  private JBPopup myPopup;
  private @NlsContexts.PopupTitle String myTitle;

  private final Tree myTree;
  private final SmartTreeStructure myTreeStructure;
  private final FilteringTreeStructure myFilteringStructure;

  private final AsyncTreeModel myAsyncTreeModel;
  private final StructureTreeModel myStructureTreeModel;
  private final MyTreeSpeedSearch mySpeedSearch;

  private final Object myInitialElement;
  private final Map<Class, JBCheckBox> myCheckBoxes = new HashMap<>();
  private final List<JBCheckBox> myAutoClicked = new ArrayList<>();
  private String myTestSearchFilter;
  private final List<Pair<String, JBCheckBox>> myTriggeredCheckboxes = new ArrayList<>();
  private final TreeExpander myTreeExpander;
  private final CopyPasteDelegator myCopyPasteDelegator;

  private final long constructorCallTime = System.nanoTime();
  private long showTime;

  private boolean myCanClose = true;
  private boolean myDisposed;

  public FileStructurePopup(@NotNull Project project,
                            @NotNull FileEditor fileEditor,
                            @NotNull StructureViewModel treeModel) {
    myProject = project;
    myFileEditor = fileEditor;
    myTreeModel = treeModel;

    //Stop code analyzer to speed up the EDT
    DaemonCodeAnalyzer.getInstance(myProject).disableUpdateByTimer(this);

    myTreeActionsOwner = new TreeStructureActionsOwner(myTreeModel);
    myTreeActionsOwner.setActionIncluded(Sorter.ALPHA_SORTER, true);
    myTreeModelWrapper = new TreeModelWrapper(myTreeModel, myTreeActionsOwner);
    Disposer.register(this, myTreeModelWrapper);

    myTreeStructure = new SmartTreeStructure(project, myTreeModelWrapper) {
      @Override
      public void rebuildTree() {
        if (!ApplicationManager.getApplication().isUnitTestMode() && myPopup.isDisposed()) {
          return;
        }
        ProgressManager.getInstance().computePrioritized(() -> {
          super.rebuildTree();
          myFilteringStructure.rebuild();
          return null;
        });
      }

      @Override
      public boolean isToBuildChildrenInBackground(@NotNull Object element) {
        return getRootElement() == element;
      }

      @Override
      protected @NotNull TreeElementWrapper createTree() {
        return StructureViewComponent.createWrapper(myProject, myModel.getRoot(), myModel);
      }

      @Override
      public @NonNls String toString() {
        return "structure view tree structure(model=" + myTreeModelWrapper + ")";
      }
    };

    FileStructurePopupFilter filter = new FileStructurePopupFilter();
    myFilteringStructure = new FilteringTreeStructure(filter, myTreeStructure, false);

    myStructureTreeModel = new StructureTreeModel<>(myFilteringStructure, this);
    myAsyncTreeModel = new AsyncTreeModel(myStructureTreeModel, this);
    myTree = new MyTree(myAsyncTreeModel);
    StructureViewComponent.registerAutoExpandListener(myTree, myTreeModel);
    PopupUtil.applyNewUIBackground(myTree);
    myTree.getAccessibleContext().setAccessibleName(LangBundle.message("file.structure.tree.accessible.name"));

    ModelListener modelListener = () -> rebuild(false);
    myTreeModel.addModelListener(modelListener);
    Disposer.register(this, () -> myTreeModel.removeModelListener(modelListener));
    myTree.setCellRenderer(new NodeRenderer());
    myProject.getMessageBus().connect(this).subscribe(UISettingsListener.TOPIC, o -> rebuild(false));

    myTree.setTransferHandler(new TransferHandler() {
      @Override
      public boolean importData(@NotNull TransferSupport support) {
        String s = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
        if (s != null && !mySpeedSearch.isPopupActive()) {
          mySpeedSearch.showPopup(s);
          return true;
        }
        return false;
      }

      @Override
      protected @Nullable Transferable createTransferable(JComponent component) {
        JBIterable<Pair<FilteringTreeStructure.FilteringNode, PsiElement>> pairs = JBIterable.of(myTree.getSelectionPaths())
          .filterMap(TreeUtil::getLastUserObject)
          .filter(FilteringTreeStructure.FilteringNode.class)
          .filterMap(o -> o.getDelegate() instanceof PsiElement ? Pair.create(o, (PsiElement)o.getDelegate()) : null)
          .collect();
        if (pairs.isEmpty()) return null;
        Set<PsiElement> psiSelection = pairs.map(Functions.pairSecond()).toSet();

        String text = StringUtil.join(pairs, pair -> {
          PsiElement psi = pair.second;
          String defaultPresentation = pair.first.getPresentation().getPresentableText();
          if (psi == null) return defaultPresentation;
          for (PsiElement p = psi.getParent(); p != null; p = p.getParent()) {
            if (psiSelection.contains(p)) return null;
          }
          return ObjectUtils.chooseNotNull(psi.getText(), defaultPresentation);
        }, "\n");

        String htmlText = "<body>\n" + text + "\n</body>";
        return new TextTransferable(XmlStringUtil.wrapInHtml(htmlText), text);
      }

      @Override
      public int getSourceActions(JComponent component) {
        return COPY;
      }
    });

    mySpeedSearch = new MyTreeSpeedSearch();
    mySpeedSearch.setupListeners();
    mySpeedSearch.setComparator(new SpeedSearchComparator(false, true, " ()"));

    myTreeExpander = new DefaultTreeExpander(myTree);
    myCopyPasteDelegator = new CopyPasteDelegator(myProject, myTree);

    myInitialElement = myTreeModel.getCurrentEditorElement();
    TreeUtil.installActions(myTree);
  }

  public void show(){
    showWithResult();
  }

  public Promise<TreePath> showWithResult() {
    JComponent panel = createCenterPanel();
    myTree.addTreeSelectionListener(__ -> {
      if (myPopup.isVisible()) {
        PopupUpdateProcessor updateProcessor = myPopup.getUserData(PopupUpdateProcessor.class);
        if (updateProcessor != null) {
          AbstractTreeNode node = getSelectedNode();
          updateProcessor.updatePopup(node);
        }
      }
    });

    myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, myTree)
      .setTitle(myTitle)
      .setResizable(true)
      .setModalContext(false)
      .setFocusable(true)
      .setRequestFocus(true)
      .setMovable(true)
      .setBelongsToGlobalPopupStack(true)
      //.setCancelOnClickOutside(false) //for debug and snapshots
      .setCancelOnOtherWindowOpen(true)
      .setCancelKeyEnabled(false)
      .setDimensionServiceKey(myProject, getDimensionServiceKey(), true)
      .setCancelCallback(() -> {
        FileStructurePopupListener listener = myProject.getMessageBus().syncPublisher(FileStructurePopupListener.TOPIC);
        listener.stateChanged(false);
        return myCanClose;
      })
      .setAdvertiser(new SpeedSearchAdvertiser().addSpeedSearchAdvertisement())
      .createPopup();

    Disposer.register(myPopup, this);
    myTree.getEmptyText().setText(CommonBundle.getLoadingTreeNodeText());
    myPopup.showCenteredInCurrentWindow(myProject);

    ((AbstractPopup)myPopup).setShowHints(true);

    IdeFocusManager.getInstance(myProject).requestFocus(myTree, true);

    return rebuildAndSelect(false, myInitialElement, null).onProcessed(path -> UIUtil.invokeLaterIfNeeded(() -> {
      TreeUtil.ensureSelection(myTree);
      myProject.getService(FileStructurePopupLoadingStateUpdater.class).installUpdater(this::installUpdater, myProject, myTreeModel);
      showTime = System.nanoTime();
    }));
  }


  private void installUpdater(final int delayMillis) {
    if (ApplicationManager.getApplication().isUnitTestMode() || myPopup.isDisposed()) {
      return;
    }
    Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myPopup);
    alarm.addRequest(new Runnable() {
      String filter = "";

      @Override
      public void run() {
        alarm.cancelAllRequests();
        String prefix = mySpeedSearch.getEnteredPrefix();
        myTree.getEmptyText().setText(StringUtil.isEmpty(prefix) ? LangBundle.message("status.text.structure.empty")
                                                                 : "'" + prefix + "' " +
                                                                   LangBundle.message("status.text.structure.empty.not.found"));
        if (prefix == null) prefix = "";

        if (!filter.equals(prefix)) {
          boolean isBackspace = prefix.length() < filter.length();
          filter = prefix;
          rebuild(true).onProcessed(ignore -> UIUtil.invokeLaterIfNeeded(() -> {
            if (isDisposed()) return;
            TreeUtil.promiseExpandAll(myTree);
            if (isBackspace && handleBackspace(filter)) {
              return;
            }
            if (myFilteringStructure.getRootElement().getChildren().length == 0) {
              for (JBCheckBox box : myCheckBoxes.values()) {
                if (!box.isSelected()) {
                  myAutoClicked.add(box);
                  myTriggeredCheckboxes.add(0, Pair.create(filter, box));
                  box.doClick();
                  filter = "";
                  break;
                }
              }
            }
          }));
        }
        if (!alarm.isDisposed()) {
          alarm.addRequest(this, delayMillis);
        }
      }
    }, delayMillis);
  }

  private boolean handleBackspace(String filter) {
    boolean clicked = false;
    Iterator<Pair<String, JBCheckBox>> iterator = myTriggeredCheckboxes.iterator();
    while (iterator.hasNext()) {
      Pair<String, JBCheckBox> next = iterator.next();
      if (next.getFirst().length() < filter.length()) break;

      iterator.remove();
      next.getSecond().doClick();
      clicked = true;
    }
    return clicked;
  }

  public @NotNull Promise<TreePath> select(Object element) {
    int editorOffset;
    if (myFileEditor instanceof TextEditor textEditor) {
      editorOffset = textEditor.getEditor().getCaretModel().getOffset();
    }
    else {
      editorOffset = -1;
    }
    var state = new StructureViewSelectVisitorState();
    TreeVisitor visitor = new TreeVisitor() {
      @Override
      public @NotNull TreeVisitor.VisitThread visitThread() {
        return VisitThread.BGT;
      }

      @Override
      public @NotNull Action visit(@NotNull TreePath path) {
        return StructureViewComponent.visitPathForElementSelection(path, element, editorOffset, state);
      }
    };
    Function<TreePath, Promise<TreePath>> action = path -> {
      myTree.expandPath(path);
      TreeUtil.selectPath(myTree, path);
      TreeUtil.ensureSelection(myTree);
      return Promises.resolvedPromise(path);
    };
    Function<TreePath, Promise<TreePath>> fallback = new Function<>() {
      @Override
      public Promise<TreePath> fun(TreePath path) {
        if (path == null && state.isOptimizationUsed()) {
          // Some structure views merge unrelated psi elements into a structure node (MarkdownStructureViewModel).
          // So turn off the isAncestor() optimization and retry once.
          state.disableOptimization();
          return myAsyncTreeModel.accept(visitor).thenAsync(this);
        }
        else {
          TreePath adjusted = path == null ? state.getBestMatch() : path;
          if (path == null && adjusted != null && !state.isExactMatch() && element instanceof PsiElement) {
            Object minChild = findClosestPsiElement((PsiElement)element, adjusted, myAsyncTreeModel);
            if (minChild != null) adjusted = adjusted.pathByAddingChild(minChild);
          }
          return adjusted == null ? Promises.rejectedPromise() : action.fun(adjusted);
        }
      }
    };

    return myAsyncTreeModel
      .accept(visitor)
      .thenAsync(fallback);
  }

  @TestOnly
  public AsyncPromise<Void> rebuildAndUpdate() {
    AsyncPromise<Void> result = new AsyncPromise<>();
    TreeVisitor visitor = path -> {
      AbstractTreeNode node = TreeUtil.getLastUserObject(AbstractTreeNode.class, path);
      if (node != null) node.update();
      return TreeVisitor.Action.CONTINUE;
    };
    rebuild(false).onProcessed(ignore1 -> myAsyncTreeModel.accept(visitor).onProcessed(ignore2 -> result.setResult(null)));
    return result;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public void dispose() {
    myDisposed = true;
    if (showTime != 0) {
      FileStructurePopupTimeTracker.logShowTime(System.nanoTime() - showTime);
    }
    FileStructurePopupTimeTracker.logPopupLifeTime(System.nanoTime() - constructorCallTime);
  }

  private static boolean isShouldNarrowDown() {
    return PropertiesComponent.getInstance().getBoolean(NARROW_DOWN_PROPERTY_KEY, true);
  }

  private static @NonNls String getDimensionServiceKey() {
    return "StructurePopup";
  }

  public @Nullable PsiElement getCurrentElement(final @Nullable PsiFile psiFile) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    Object elementAtCursor = myTreeModelWrapper.getCurrentEditorElement();
    if (elementAtCursor instanceof PsiElement) {
      return (PsiElement)elementAtCursor;
    }

    if (psiFile != null && myFileEditor instanceof TextEditor) {
      return psiFile.getViewProvider().findElementAt(((TextEditor)myFileEditor).getEditor().getCaretModel().getOffset());
    }

    return null;
  }

  public JComponent createCenterPanel() {
    List<FileStructureFilter> fileStructureFilters = new ArrayList<>();
    List<FileStructureNodeProvider> fileStructureNodeProviders = new ArrayList<>();
    if (myTreeActionsOwner != null) {
      for (Filter filter : myTreeModel.getFilters()) {
        if (filter instanceof FileStructureFilter fsFilter) {
          myTreeActionsOwner.setActionIncluded(fsFilter, true);
          fileStructureFilters.add(fsFilter);
        }
      }

      if (myTreeModel instanceof ProvidingTreeModel) {
        for (NodeProvider provider : ((ProvidingTreeModel)myTreeModel).getNodeProviders()) {
          if (provider instanceof FileStructureNodeProvider) {
            fileStructureNodeProviders.add((FileStructureNodeProvider)provider);
          }
        }
      }
    }

    int checkBoxCount = fileStructureNodeProviders.size() + fileStructureFilters.size();
    JPanel panel = new JPanel(new BorderLayout());
    panel.setPreferredSize(JBUI.size(540, 500));
    var cols = checkBoxCount > 0 && checkBoxCount % 4 == 0 ? checkBoxCount / 2 : 3;
    var singleRow = checkBoxCount <= cols;
    JPanel chkPanel = new JPanel(new GridLayout(0, cols,
                                                JBUIScale.scale(UIUtil.DEFAULT_HGAP), 0));
    chkPanel.setOpaque(false);

    Shortcut[] F4 = ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet().getShortcuts();
    Shortcut[] ENTER = CustomShortcutSet.fromString("ENTER").getShortcuts();
    CustomShortcutSet shortcutSet = new CustomShortcutSet(ArrayUtil.mergeArrays(F4, ENTER));
    new NavigateSelectedElementAction(panel).registerCustomShortcutSet(shortcutSet, panel);

    DumbAwareAction.create(e -> {
      if (mySpeedSearch != null && mySpeedSearch.isPopupActive()) {
        mySpeedSearch.hidePopup();
      }
      else {
        myPopup.cancel();
      }
    }).registerCustomShortcutSet(CustomShortcutSet.fromString("ESCAPE"), myTree);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        TreePath path = myTree.getClosestPathForLocation(e.getX(), e.getY());
        Rectangle bounds = path == null ? null : myTree.getPathBounds(path);
        if (bounds == null ||
            bounds.x > e.getX() ||
            bounds.y > e.getY() || bounds.y + bounds.height < e.getY()) return false;
        navigateSelectedElement();
        return true;
      }
    }.installOn(myTree);

    for (FileStructureFilter filter : fileStructureFilters) {
      addCheckbox(chkPanel, filter);
    }

    for (FileStructureNodeProvider provider : fileStructureNodeProviders) {
      addCheckbox(chkPanel, provider);
    }
    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.add(chkPanel, BorderLayout.WEST);

    topPanel.add(createSettingsButton(), BorderLayout.EAST);

    topPanel.setBackground(JBUI.CurrentTheme.Popup.toolbarPanelColor());
    Dimension prefSize = topPanel.getPreferredSize();
    if (singleRow) {
      prefSize.height = JBUI.CurrentTheme.Popup.toolbarHeight();
    }
    topPanel.setPreferredSize(prefSize);
    topPanel.setBorder(JBUI.Borders.emptyLeft(UIUtil.DEFAULT_HGAP));

    panel.add(topPanel, BorderLayout.NORTH);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    scrollPane.setBorder(IdeBorderFactory.createBorder(JBUI.CurrentTheme.Popup.toolbarBorderColor(), SideBorder.TOP | SideBorder.BOTTOM));
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        myPopup.cancel();
      }
    });

    return UiDataProvider.wrapComponent(panel, sink -> uiDataSnapshot(sink));
  }

  private void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(CommonDataKeys.PROJECT, myProject);
    sink.set(PlatformCoreDataKeys.FILE_EDITOR, myFileEditor);
    if (myFileEditor instanceof TextEditor o) {
      sink.set(OpenFileDescriptor.NAVIGATE_IN_EDITOR, o.getEditor());
    }
    sink.set(LangDataKeys.POSITION_ADJUSTER_POPUP, myPopup);
    sink.set(PlatformDataKeys.COPY_PROVIDER, myCopyPasteDelegator.getCopyProvider());
    sink.set(PlatformDataKeys.TREE_EXPANDER, myTreeExpander);

    TreePath[] selection = myTree.getSelectionPaths();
    JBIterable<Object> selectedElements = JBIterable.of(selection)
      .filterMap(o -> StructureViewComponent.unwrapValue(o.getLastPathComponent()));
    sink.lazy(CommonDataKeys.PSI_ELEMENT, () -> {
      return selectedElements.filter(PsiElement.class).first();
    });
    sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, () -> {
      return PsiUtilCore.toPsiElementArray(selectedElements.filter(PsiElement.class).toList());
    });
    sink.lazy(CommonDataKeys.NAVIGATABLE, () -> {
      return selectedElements.filter(Navigatable.class).first();
    });
    sink.lazy(CommonDataKeys.NAVIGATABLE_ARRAY, () -> {
      List<Navigatable> result = selectedElements.filter(Navigatable.class).toList();
      return result.isEmpty() ? null : result.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
    });
  }

  private @NotNull JComponent createSettingsButton() {
    JLabel label = new JLabel(AllIcons.General.GearPlain);
    label.setBorder(JBUI.Borders.empty(0, 4));
    label.setHorizontalAlignment(SwingConstants.RIGHT);
    label.setVerticalAlignment(SwingConstants.CENTER);
    label.getAccessibleContext().setAccessibleName(LangBundle.message("file.structure.settings.accessible.name"));

    List<AnAction> sorters = createSorters();
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        DefaultActionGroup group = new DefaultActionGroup();
        if (!sorters.isEmpty()) {
          group.addAll(sorters);
          group.addSeparator();
        }
        //addGroupers(group);
        //addFilters(group);

        group.add(new ToggleNarrowDownAction());

        DataManager dataManager = DataManager.getInstance();
        ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
          null, group, dataManager.getDataContext(label), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
        popup.addListener(new JBPopupListener() {
          @Override
          public void onClosed(@NotNull LightweightWindowEvent event) {
            myCanClose = true;
          }
        });
        myCanClose = false;
        popup.showUnderneathOf(label);
        return true;
      }
    }.installOn(label);
    return label;
  }

  private List<AnAction> createSorters() {
    List<AnAction> actions = new ArrayList<>();
    for (Sorter sorter : myTreeModel.getSorters()) {
      if (sorter.isVisible()) {
        actions.add(new MyTreeActionWrapper(sorter));
      }
    }
    return actions;
  }

  private static @Nullable Object findClosestPsiElement(@NotNull PsiElement element,
                                                        @NotNull TreePath adjusted,
                                                        @NotNull TreeModel treeModel) {
    TextRange range = element.getTextRange();
    if (range == null) return null;
    Object parent = adjusted.getLastPathComponent();
    int minDistance = 0;
    Object minChild = null;
    for (int i = 0, count = treeModel.getChildCount(parent); i < count; i++) {
      Object child = treeModel.getChild(parent, i);
      Object value = StructureViewComponent.unwrapValue(child);
      if (value instanceof StubBasedPsiElement && ((StubBasedPsiElement<?>)value).getStub() != null) continue;
      TextRange r = value instanceof PsiElement ? ((PsiElement)value).getTextRange() : null;
      if (r == null) continue;
      int distance = TextRangeUtil.getDistance(range, r);
      if (minChild == null || distance < minDistance) {
        minDistance = distance;
        minChild = child;
      }
    }
    return minChild;
  }

  private final class MyTreeActionWrapper extends TreeActionWrapper {
    private final TreeAction myAction;

    MyTreeActionWrapper(TreeAction action) {
      super(action, myTreeActionsOwner);
      myAction = action;
      myTreeActionsOwner.setActionIncluded(action, getDefaultValue(action));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setIcon(null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      boolean actionState = TreeModelWrapper.shouldRevert(myAction) != state;
      myTreeActionsOwner.setActionIncluded(myAction, actionState);
      saveState(myAction, state);
      rebuild(false).onProcessed(ignore -> {
        if (mySpeedSearch.isPopupActive()) {
          mySpeedSearch.refreshSelection();
        }
      });
    }
  }

  private @Nullable AbstractTreeNode getSelectedNode() {
    TreePath path = myTree.getSelectionPath();
    Object o = StructureViewComponent.unwrapNavigatable(path == null ? null : path.getLastPathComponent());
    return o instanceof AbstractTreeNode ? (AbstractTreeNode)o : null;
  }

  private boolean navigateSelectedElement() {
    AbstractTreeNode selectedNode = getSelectedNode();
    if (ApplicationManager.getApplication().isInternal()) {
      String enteredPrefix = mySpeedSearch.getEnteredPrefix();
      String itemText = getSpeedSearchText(selectedNode);
      if (StringUtil.isNotEmpty(enteredPrefix) && StringUtil.isNotEmpty(itemText)) {
        LOG.info("Chosen in file structure popup by prefix '" + enteredPrefix + "': '" + itemText + "'");
      }
    }

    Ref<Boolean> succeeded = new Ref<>();
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(myProject, () -> {
      if (selectedNode != null) {
        if (selectedNode.canNavigateToSource()) {
          selectedNode.navigate(true);
          myPopup.cancel();
          succeeded.set(true);
        }
        else {
          succeeded.set(false);
        }
      }
      else {
        succeeded.set(false);
      }

      IdeDocumentHistory.getInstance(myProject).includeCurrentCommandAsNavigation();
    }, LangBundle.message("command.name.navigate"), null);
    return succeeded.get();
  }

  private void addCheckbox(JPanel panel, TreeAction action) {
    String text = action instanceof FileStructureFilter ? ((FileStructureFilter)action).getCheckBoxText() :
                  action instanceof FileStructureNodeProvider ? ((FileStructureNodeProvider<?>)action).getCheckBoxText() : null;

    if (text == null) return;

    Shortcut[] shortcuts = extractShortcutFor(action);


    JBCheckBox checkBox = new JBCheckBox();
    checkBox.setOpaque(false);
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, checkBox);

    boolean selected = getDefaultValue(action);
    checkBox.setSelected(selected);
    boolean isRevertedStructureFilter = action instanceof FileStructureFilter && ((FileStructureFilter)action).isReverted();
    myTreeActionsOwner.setActionIncluded(action, isRevertedStructureFilter != selected);
    checkBox.addActionListener(actionEvent -> {
      logFileStructureCheckboxClick(action);

      boolean state = checkBox.isSelected();
      if (!myAutoClicked.contains(checkBox)) {
        saveState(action, state);
      }
      myTreeActionsOwner.setActionIncluded(action, isRevertedStructureFilter != state);
      rebuild(false).onProcessed(ignore -> {
        if (mySpeedSearch.isPopupActive()) {
          mySpeedSearch.refreshSelection();
        }
      });
    });
    checkBox.setFocusable(false);

    if (shortcuts.length > 0) {
      text += " (" + KeymapUtil.getShortcutText(shortcuts[0]) + ")";
      DumbAwareAction.create(e -> checkBox.doClick())
        .registerCustomShortcutSet(new CustomShortcutSet(shortcuts), myTree);
    }
    checkBox.setText(text);
    panel.add(checkBox);

    myCheckBoxes.put(action.getClass(), checkBox);
  }

  private void logFileStructureCheckboxClick(TreeAction action) {
    FileType fileType = myFileEditor.getFile().getFileType();
    Language language = fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : null;

    ActionsCollectorImpl.recordActionInvoked(myProject, pairs -> {
      pairs.add(EventFields.PluginInfoFromInstance.with(action));
      pairs.add(EventFields.ActionPlace.with(ActionPlaces.FILE_STRUCTURE_POPUP));
      pairs.add(EventFields.CurrentFile.with(language));
      pairs.add(ActionsEventLogGroup.ACTION_CLASS.with(action.getClass()));
      pairs.add(ActionsEventLogGroup.ACTION_ID.with(action.getClass().getName()));
      return Unit.INSTANCE;
    });
  }

  private @NotNull Promise<Void> rebuild(boolean refilterOnly) {
    Object selection = JBIterable.of(myTree.getSelectionPaths())
                                 .filterMap(o -> StructureViewComponent.unwrapValue(o.getLastPathComponent())).first();
    return rebuildAndSelect(refilterOnly, selection, null).then(o -> null);
  }

  private @NotNull Promise<TreePath> rebuildAndSelect(boolean refilterOnly, Object selection, @Nullable Long rebuildStartTime) {
    if (rebuildStartTime == null) {
      rebuildStartTime = System.nanoTime();
    }

    AsyncPromise<TreePath> result = new AsyncPromise<>();
    Long finalLastRebuildStartTime = rebuildStartTime;
    myStructureTreeModel.getInvoker().invoke(() -> {
      if (refilterOnly) {
        myFilteringStructure.refilter();
        myStructureTreeModel.invalidateAsync().thenRun(() -> {
          (selection == null ? myAsyncTreeModel.accept(o -> TreeVisitor.Action.CONTINUE) : select(selection))
            .onError(ignore2 -> {
              result.setError("rejected");
              mySpeedSearch.refreshSelection(); // Selection failed, let the speed search reflect that by coloring itself red.
            })
            .onSuccess(p -> EdtInvocationManager.invokeLaterIfNeeded(() -> {
              //maybe readaction
              WriteIntentReadAction.run((Runnable)() -> {
                TreeUtil.expand(getTree(),
                                myTreeModel instanceof StructureViewCompositeModel
                                ? 3
                                : 2);
                TreeUtil.ensureSelection(myTree);
                mySpeedSearch.refreshSelection();
                result.setResult(p);
                FileStructurePopupTimeTracker.logRebuildTime(System.nanoTime() - finalLastRebuildStartTime);
              });
            }));
        });
      }
      else {
        myTreeStructure.rebuildTree();
        myStructureTreeModel.invalidateAsync().thenRun(() -> rebuildAndSelect(true, selection, finalLastRebuildStartTime).processed(result));
      }
    }).onError(throwable -> result.setError(throwable));
    return result;
  }

  static Shortcut @NotNull [] extractShortcutFor(@NotNull TreeAction action) {
    if (action instanceof ActionShortcutProvider) {
      String actionId = ((ActionShortcutProvider)action).getActionIdForShortcut();
      return KeymapUtil.getActiveKeymapShortcuts(actionId).getShortcuts();
    }
    return action instanceof FileStructureFilter ?
                           ((FileStructureFilter)action).getShortcut() : ((FileStructureNodeProvider<?>)action).getShortcut();
  }

  private static boolean getDefaultValue(TreeAction action) {
    String propertyName = action instanceof PropertyOwner ? ((PropertyOwner)action).getPropertyName() : action.getName();
    final var defaultValue = Sorter.ALPHA_SORTER.equals(action) || hasEnabledStateByDefault(action);
    return PropertiesComponent.getInstance().getBoolean(TreeStructureUtil.getPropertyName(propertyName), defaultValue);
  }

  private static boolean hasEnabledStateByDefault(@NotNull TreeAction action) {
    return action instanceof TreeActionWithDefaultState && ((TreeActionWithDefaultState)action).isEnabledByDefault();
  }

  private static void saveState(TreeAction action, boolean state) {
    String propertyName = action instanceof PropertyOwner ? ((PropertyOwner)action).getPropertyName() : action.getName();
    final var defaultValue = Sorter.ALPHA_SORTER.equals(action) || hasEnabledStateByDefault(action);
    PropertiesComponent.getInstance().setValue(TreeStructureUtil.getPropertyName(propertyName), state, defaultValue);
  }

  public void setTitle(@NlsContexts.PopupTitle String title) {
    myTitle = title;
  }

  public @NotNull Tree getTree() {
    return myTree;
  }

  @TestOnly
  public TreeSpeedSearch getSpeedSearch() {
    return mySpeedSearch;
  }

  @TestOnly
  public void setSearchFilterForTests(String filter) {
    myTestSearchFilter = filter;
  }

  public void setTreeActionState(Class<? extends TreeAction> action, boolean state) {
    JBCheckBox checkBox = myCheckBoxes.get(action);
    if (checkBox != null) {
      checkBox.setSelected(state);
      for (ActionListener listener : checkBox.getActionListeners()) {
        listener.actionPerformed(new ActionEvent(this, 1, ""));
      }
    }
  }

  public static @Nullable String getSpeedSearchText(Object object) {
    String text = String.valueOf(object);
    Object value = StructureViewComponent.unwrapWrapper(object);
    if (text != null) {
      if (value instanceof SearchableTextProvider searchableTextProvider) {
        String searchableText = searchableTextProvider.getSearchableText();
        if (searchableText != null) return searchableText;
      }
      if (value instanceof PsiTreeElementBase && ((PsiTreeElementBase<?>)value).isSearchInLocationString()) {
           String locationString = ((PsiTreeElementBase<?>)value).getLocationString();
          if (!StringUtil.isEmpty(locationString)) {
            String locationPrefix = null;
            String locationSuffix = null;
            if (value instanceof LocationPresentation) {
              locationPrefix = ((LocationPresentation)value).getLocationPrefix();
              locationSuffix = ((LocationPresentation)value).getLocationSuffix();
            }

          return text +
                 StringUtil.notNullize(locationPrefix, LocationPresentation.DEFAULT_LOCATION_PREFIX) +
                 locationString +
                 StringUtil.notNullize(locationSuffix, LocationPresentation.DEFAULT_LOCATION_SUFFIX);
        }
      }
      return text;
    }
    // NB!: this point is achievable if the following method returns null
    // see com.intellij.ide.util.treeView.NodeDescriptor.toString
    if (value instanceof TreeElement) {
      return ReadAction.compute(() -> {
        if (value instanceof SearchableTextProvider searchableTextProvider) {
          String searchableText = searchableTextProvider.getSearchableText();
          if (searchableText != null) return searchableText;
        }
        return ((TreeElement)value).getPresentation().getPresentableText();
      });
    }

    return null;
  }

  @Override
  public void setActionActive(String name, boolean state) {

  }

  @Override
  public boolean isActionActive(String name) {
    return false;
  }

  private final class FileStructurePopupFilter implements ElementFilter {
    private String myLastFilter;
    private final Set<Object> myVisibleParents = new HashSet<>();
    private final boolean isUnitTest = ApplicationManager.getApplication().isUnitTestMode();

    @Override
    public boolean shouldBeShowing(Object value) {
      if (!isShouldNarrowDown()) return true;

      String filter = getSearchPrefix();
      if (!StringUtil.equals(myLastFilter, filter)) {
        myVisibleParents.clear();
        myLastFilter = filter;
      }
      if (filter != null) {
        if (myVisibleParents.contains(value)) {
          return true;
        }

        String text = getSpeedSearchText(value);
        if (text == null) return false;

        if (matches(filter, text)) {
          Object o = value;
          while (o instanceof FilteringTreeStructure.FilteringNode && (o = ((FilteringTreeStructure.FilteringNode)o).getParent()) != null) {
            myVisibleParents.add(o);
          }
          return true;
        }
        else {
          return false;
        }
      }
      return true;
    }

    private boolean matches(@NotNull String filter, @NotNull String text) {
      return (isUnitTest || mySpeedSearch.isPopupActive()) &&
             StringUtil.isNotEmpty(filter) &&
             mySpeedSearch.getComparator().matchingFragments(filter, text) != null;
    }
  }

  private @Nullable String getSearchPrefix() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return myTestSearchFilter;

    return mySpeedSearch != null && !StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix())
           ? mySpeedSearch.getEnteredPrefix() : null;
  }

  private final class MyTreeSpeedSearch extends TreeSpeedSearch {
    private volatile boolean myPopupVisible;

    private MyTreeSpeedSearch() {
      super(myTree, true, null, path -> getSpeedSearchText(TreeUtil.getLastUserObject(path)));
    }

    @Override
    public void showPopup(String searchText) {
      super.showPopup(searchText);
      myPopupVisible = true;
    }

    @Override
    public void hidePopup() {
      super.hidePopup();
      myPopupVisible = false;
    }

    @Override
    public boolean isPopupActive() {
      return myPopupVisible;
    }

    @Override
    protected Point getComponentLocationOnScreen() {
      return myPopup.getContent().getLocationOnScreen();
    }

    @Override
    protected Rectangle getComponentVisibleRect() {
      return myPopup.getContent().getVisibleRect();
    }

    @Override
    public Object findElement(@NotNull String s) {
      List<SpeedSearchObjectWithWeight> elements = SpeedSearchObjectWithWeight.findElement(s, this);
      SpeedSearchObjectWithWeight best = ContainerUtil.getFirstItem(elements);
      if (best == null) return null;
      if (myInitialElement instanceof PsiElement initial) {
        // find children of the initial element
        SpeedSearchObjectWithWeight bestForParent = find(initial, elements, FileStructurePopup::isParent);
        if (bestForParent != null) return bestForParent.node;
        // find siblings of the initial element
        PsiElement parent = initial.getParent();
        if (parent != null) {
          SpeedSearchObjectWithWeight bestSibling = find(parent, elements, FileStructurePopup::isParent);
          if (bestSibling != null) return bestSibling.node;
        }
        // find grand children of the initial element
        SpeedSearchObjectWithWeight bestForAncestor = find(initial, elements, FileStructurePopup::isAncestor);
        if (bestForAncestor != null) return bestForAncestor.node;
      }
      return best.node;
    }
  }

  private static @Nullable SpeedSearchObjectWithWeight find(@NotNull PsiElement element,
                                                            @NotNull List<? extends SpeedSearchObjectWithWeight> objects,
                                                            @NotNull BiPredicate<? super PsiElement, ? super TreePath> predicate) {
    return ContainerUtil.find(objects, object -> predicate.test(element, ObjectUtils.tryCast(object.node, TreePath.class)));
  }

  private static boolean isElement(@NotNull PsiElement element, @Nullable TreePath path) {
    return element.equals(StructureViewComponent.unwrapValue(TreeUtil.getLastUserObject(FilteringTreeStructure.FilteringNode.class, path)));
  }

  private static boolean isParent(@NotNull PsiElement parent, @Nullable TreePath path) {
    return path != null && isElement(parent, path.getParentPath());
  }

  private static boolean isAncestor(@NotNull PsiElement ancestor, @Nullable TreePath path) {
    while (path != null) {
      if (isElement(ancestor, path)) return true;
      path = path.getParentPath();
    }
    return false;
  }

  static final class MyTree extends DnDAwareTree implements PlaceProvider {

    MyTree(TreeModel treeModel) {
      super(treeModel);
      setRootVisible(false);
      setShowsRootHandles(true);

      HintUpdateSupply.installHintUpdateSupply(this, o -> {
        Object value = StructureViewComponent.unwrapValue(o);
        return value instanceof PsiElement ? (PsiElement)value : null;
      });
    }

    @Override
    public String getPlace() {
      return ActionPlaces.STRUCTURE_VIEW_POPUP;
    }
  }

  private final class NavigateSelectedElementAction extends DumbAwareAction {
    private final JPanel myPanel;

    private NavigateSelectedElementAction(JPanel panel) {
      myPanel = panel;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      boolean succeeded = navigateSelectedElement();
      if (succeeded) {
        unregisterCustomShortcutSet(myPanel);
      }
    }
  }

  private final class ToggleNarrowDownAction extends ToggleAction {
    private ToggleNarrowDownAction() {
      super(IdeBundle.message("checkbox.narrow.down.on.typing"));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return isShouldNarrowDown();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      PropertiesComponent.getInstance().setValue(NARROW_DOWN_PROPERTY_KEY, Boolean.toString(state));
      if (mySpeedSearch.isPopupActive() && !StringUtil.isEmpty(mySpeedSearch.getEnteredPrefix())) {
        rebuild(true);
      }
    }
  }
}

final class FileStructurePopupTimeTracker extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("file.structure.popup", 2);
  private static final EventId1<Long> LIFE = GROUP.registerEvent("popup.disposed", EventFields.DurationMs);
  private static final EventId1<Long> SHOW = GROUP.registerEvent("data.shown", EventFields.DurationMs);
  private static final EventId1<Long> REBUILD = GROUP.registerEvent("data.filled", EventFields.DurationMs);

  static void logRebuildTime(long elapsedTimeNanos) {
    long elapsedTimesMs = TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos);
    REBUILD.log(elapsedTimesMs);
  }

  static void logShowTime(long elapsedTimeNanos) {
    long elapsedTimesMs = TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos);
    SHOW.log(elapsedTimesMs);
  }

  static void logPopupLifeTime(long elapsedTimeNanos) {
    long elapsedTimesMs = TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos);
    LIFE.log(elapsedTimesMs);
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}