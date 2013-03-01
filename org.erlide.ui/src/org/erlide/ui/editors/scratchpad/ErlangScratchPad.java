package org.erlide.ui.editors.scratchpad;

import java.util.ResourceBundle;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension;
import org.eclipse.jface.text.ITextViewerExtension2;
import org.eclipse.jface.text.ITextViewerExtension4;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.information.IInformationProvider;
import org.eclipse.jface.text.information.IInformationProviderExtension;
import org.eclipse.jface.text.information.IInformationProviderExtension2;
import org.eclipse.jface.text.information.InformationPresenter;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.ISaveablePart2;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;
import org.eclipse.ui.texteditor.TextEditorAction;
import org.eclipse.ui.texteditor.TextOperationAction;
import org.erlide.model.erlang.ErlToken;
import org.erlide.model.erlang.IErlModule;
import org.erlide.model.erlang.IErlScanner;
import org.erlide.model.root.ErlModelManager;
import org.erlide.model.root.IErlElement;
import org.erlide.model.root.IErlProject;
import org.erlide.ui.actions.CompositeActionGroup;
import org.erlide.ui.actions.ErlangSearchActionGroup;
import org.erlide.ui.editors.erl.AbstractErlangEditor;
import org.erlide.ui.editors.erl.ColorManager;
import org.erlide.ui.editors.erl.ErlangSourceViewerConfiguration;
import org.erlide.ui.editors.erl.folding.IErlangFoldingStructureProvider;
import org.erlide.ui.editors.erl.scanner.IErlangPartitions;
import org.erlide.ui.internal.ErlideUIPlugin;
import org.erlide.util.ErlLogger;
import org.erlide.util.Util;

public class ErlangScratchPad extends AbstractErlangEditor implements
        ISaveablePart2 {

    private ColorManager colorManager;
    private InformationPresenter fInformationPresenter;
    private IErlangFoldingStructureProvider fProjectionModelUpdater;
    private CompositeActionGroup fActionGroups;
    private CompositeActionGroup fContextMenuGroup;
    private IErlScanner erlScanner = null;

    /**
     * Simple constructor
     * 
     */
    public ErlangScratchPad() {
        super();
        registerListeners();
    }

    private void registerListeners() {
    }

    /**
     * Simple disposer
     * 
     * @see org.eclipse.ui.IWorkbenchPart#dispose()
     */
    @Override
    public void dispose() {
        if (colorManager != null) {
            colorManager.dispose();
            colorManager = null;
        }

        final ISourceViewer sourceViewer = getSourceViewer();
        if (sourceViewer instanceof ITextViewerExtension) {
            ((ITextViewerExtension) sourceViewer)
                    .removeVerifyKeyListener(getBracketInserter());
        }
        if (fActionGroups != null) {
            fActionGroups.dispose();
            fActionGroups = null;
        }
        if (fProjectionModelUpdater != null) {
            fProjectionModelUpdater.uninstall();
        }

        super.dispose();
    }

    @Override
    protected void initializeEditor() {
        colorManager = new ColorManager();
        setDocumentProvider(new TextFileDocumentProvider());

        final IPreferenceStore store = getErlangEditorPreferenceStore();
        setPreferenceStore(store);

        final ErlangSourceViewerConfiguration cfg = new ErlangScratchPadConfiguration(
                getPreferenceStore(), colorManager, this);
        setSourceViewerConfiguration(cfg);
    }

    @Override
    public IErlProject getProject() {
        final IFile file = getFile();
        if (file != null) {
            final IProject project = file.getProject();
            if (project != null) {
                return ErlModelManager.getErlangModel().findProject(project);
            }
        }
        return null;
    }

    public static ChainedPreferenceStore getErlangEditorPreferenceStore() {
        final IPreferenceStore generalTextStore = EditorsUI
                .getPreferenceStore();
        return new ChainedPreferenceStore(new IPreferenceStore[] {
                ErlideUIPlugin.getDefault().getPreferenceStore(),
                generalTextStore });
    }

    @Override
    public void createPartControl(final Composite parent) {
        super.createPartControl(parent);

        setupBracketInserter();

        final ProjectionViewer v = (ProjectionViewer) getSourceViewer();
        v.doOperation(ProjectionViewer.TOGGLE);

        final IInformationControlCreator informationControlCreator = getSourceViewerConfiguration()
                .getInformationControlCreator(getSourceViewer());

        fInformationPresenter = new InformationPresenter(
                informationControlCreator);
        // sizes: see org.eclipse.jface.text.TextViewer.TEXT_HOVER_*_CHARS
        fInformationPresenter.setSizeConstraints(100, 12, true, true);
        fInformationPresenter.install(getSourceViewer());
        fInformationPresenter
                .setDocumentPartitioning(getSourceViewerConfiguration()
                        .getConfiguredDocumentPartitioning(getSourceViewer()));
    }

    @Override
    protected void createActions() {
        super.createActions();
        // ActionGroup oeg, ovg, jsg;
        ActionGroup esg;
        fActionGroups = new CompositeActionGroup(new ActionGroup[] {
        // oeg= new OpenEditorActionGroup(this),
        // ovg= new OpenViewActionGroup(this),
        esg = new ErlangSearchActionGroup(this) });
        fContextMenuGroup = new CompositeActionGroup(new ActionGroup[] { esg });

        createCommonActions();

        // if (ErlideUtil.isTest()) {
        // testAction = new TestAction(ErlangEditorMessages
        // .getBundleForConstructedKeys(), "Test.", this, getModule());
        // testAction
        // .setActionDefinitionId(IErlangEditorActionDefinitionIds.TEST);
        // setAction("Test", testAction);
        // markAsStateDependentAction("Test", true);
        // markAsSelectionDependentAction("Test", true);
        // // PlatformUI.getWorkbench().getHelpSystem().setHelp(indentAction,
        // // IErlangHelpContextIds.INDENT_ACTION);
        // }

    }

    // FIXME Copied from ErlangEditor
    /**
     * This action behaves in two different ways: If there is no current text
     * hover, the javadoc is displayed using information presenter. If there is
     * a current text hover, it is converted into a information presenter in
     * order to make it sticky.
     */
    class InformationDispatchAction extends TextEditorAction {

        /** The wrapped text operation action. */
        private final TextOperationAction fTextOperationAction;

        /**
         * Creates a dispatch action.
         * 
         * @param resourceBundle
         *            the resource bundle
         * @param prefix
         *            the prefix
         * @param textOperationAction
         *            the text operation action
         */
        public InformationDispatchAction(final ResourceBundle resourceBundle,
                final String prefix,
                final TextOperationAction textOperationAction) {
            super(resourceBundle, prefix, ErlangScratchPad.this);
            if (textOperationAction == null) {
                throw new IllegalArgumentException();
            }
            fTextOperationAction = textOperationAction;
        }

        /*
         * @see org.eclipse.jface.action.IAction#run()
         */
        @SuppressWarnings("synthetic-access")
        @Override
        public void run() {

            /**
             * Information provider used to present the information.
             * 
             * @since 3.0
             */
            class InformationProvider implements IInformationProvider,
                    IInformationProviderExtension,
                    IInformationProviderExtension2 {

                private final IRegion fHoverRegion;

                private final String fHoverInfo;

                private final IInformationControlCreator fControlCreator;

                InformationProvider(final IRegion hoverRegion,
                        final String hoverInfo,
                        final IInformationControlCreator controlCreator) {
                    fHoverRegion = hoverRegion;
                    fHoverInfo = hoverInfo;
                    fControlCreator = controlCreator;
                }

                /*
                 * @seeorg.eclipse.jface.text.information.IInformationProvider#
                 * getSubject(org.eclipse.jface.text.ITextViewer, int)
                 */

                @Override
                public IRegion getSubject(final ITextViewer textViewer,
                        final int invocationOffset) {
                    return fHoverRegion;
                }

                @Override
                public Object getInformation2(final ITextViewer textViewer,
                        final IRegion subject) {
                    return fHoverInfo;
                }

                /*
                 * @see
                 * org.eclipse.jface.text.information.IInformationProviderExtension2
                 * #getInformationPresenterControlCreator()
                 * 
                 * @since 3.0
                 */

                @Override
                public IInformationControlCreator getInformationPresenterControlCreator() {
                    return fControlCreator;
                }

                @Override
                @Deprecated
                public String getInformation(final ITextViewer textViewer,
                        final IRegion subject) {
                    return null;
                }
            }

            final ISourceViewer sourceViewer = getSourceViewer();
            if (sourceViewer == null) {
                fTextOperationAction.run();
                return;
            }

            if (sourceViewer instanceof ITextViewerExtension4) {
                final ITextViewerExtension4 extension4 = (ITextViewerExtension4) sourceViewer;
                if (extension4.moveFocusToWidgetToken()) {
                    return;
                }
            }

            if (!(sourceViewer instanceof ITextViewerExtension2)) {
                fTextOperationAction.run();
                return;
            }

            final ITextViewerExtension2 textViewerExtension2 = (ITextViewerExtension2) sourceViewer;

            // does a text hover exist?
            final ITextHover textHover = textViewerExtension2
                    .getCurrentTextHover();
            if (textHover == null) {
                // TODO this crashes...
                // fTextOperationAction.run();
                return;
            }

            final Point hoverEventLocation = textViewerExtension2
                    .getHoverEventLocation();
            final int offset = computeOffsetAtLocation(sourceViewer,
                    hoverEventLocation.x, hoverEventLocation.y);
            if (offset == -1) {
                fTextOperationAction.run();
                return;
            }

            try {
                // get the text hover content
                final String contentType = TextUtilities.getContentType(
                        sourceViewer.getDocument(),
                        IErlangPartitions.ERLANG_PARTITIONING, offset, true);

                final IRegion hoverRegion = textHover.getHoverRegion(
                        sourceViewer, offset);
                if (hoverRegion == null) {
                    return;
                }

                final String hoverInfo = "";
                if (textHover instanceof ITextHoverExtension2) {
                    ((ITextHoverExtension2) textHover).getHoverInfo2(
                            sourceViewer, hoverRegion);
                }

                IInformationControlCreator controlCreator = null;
                if (textHover instanceof IInformationProviderExtension2) {
                    controlCreator = ((IInformationProviderExtension2) textHover)
                            .getInformationPresenterControlCreator();
                }

                final IInformationProvider informationProvider = new InformationProvider(
                        hoverRegion, hoverInfo, controlCreator);

                fInformationPresenter.setOffset(offset);
                fInformationPresenter
                        .setDocumentPartitioning(IErlangPartitions.ERLANG_PARTITIONING);
                fInformationPresenter.setInformationProvider(
                        informationProvider, contentType);
                fInformationPresenter.showInformation();
            } catch (final BadLocationException e) {
            }
        }

        // modified version from TextViewer
        private int computeOffsetAtLocation(final ITextViewer textViewer,
                final int x, final int y) {

            final StyledText styledText = textViewer.getTextWidget();
            final IDocument document = textViewer.getDocument();

            if (document == null) {
                return -1;
            }

            try {
                final int widgetLocation = styledText
                        .getOffsetAtLocation(new Point(x, y));
                if (textViewer instanceof ITextViewerExtension5) {
                    final ITextViewerExtension5 extension = (ITextViewerExtension5) textViewer;
                    return extension.widgetOffset2ModelOffset(widgetLocation);
                }
                final IRegion visibleRegion = textViewer.getVisibleRegion();
                return widgetLocation + visibleRegion.getOffset();
            } catch (final IllegalArgumentException e) {
                return -1;
            }

        }
    }

    @Override
    protected void editorContextMenuAboutToShow(final IMenuManager menu) {
        super.editorContextMenuAboutToShow(menu);

        // if (ErlideUtil.isTest()) {
        // menu.prependToGroup(IContextMenuConstants.GROUP_OPEN, testAction);
        // }
        addCommonActions(menu);
        final ActionContext context = new ActionContext(getSelectionProvider()
                .getSelection());
        fContextMenuGroup.setContext(context);
        fContextMenuGroup.fillContextMenu(menu);
        fContextMenuGroup.setContext(null);
    }

    // Auto-saving when quitting or closing, through ISaveablePart2

    @Override
    public int promptToSaveOnClose() {
        doSave(getProgressMonitor());
        return NO;
    }

    @Override
    protected void initializeKeyBindingScopes() {
        setKeyBindingScopes(new String[] { "org.erlide.ui.erlangEditorScope" }); //$NON-NLS-1$
    }

    @Override
    public void reconcileNow() {
        // TODO Auto-generated method stub

    }

    @Override
    public IErlElement getElementAt(final int offset, final boolean b) {
        return null;
    }

    @Override
    public IErlModule getModule() {
        return null;
    }

    @Override
    public IDocument getDocument() {
        return getDocumentProvider().getDocument(this);
    }

    @Override
    public ErlToken getTokenAt(final int offset) {
        return getScanner().getTokenAt(offset);
    }

    private IFile getFile() {
        final IEditorInput editorInput = getEditorInput();
        if (editorInput instanceof IFileEditorInput) {
            final IFileEditorInput input = (IFileEditorInput) editorInput;
            return input.getFile();
        }
        return null;
    }

    @Override
    public IErlScanner getScanner() {
        if (erlScanner == null) {
            final IFile file = getFile();
            if (file != null) {
                try {
                    final String filePath = file.getLocation()
                            .toPortableString();
                    String initialText;
                    initialText = Util.getInputStreamAsString(
                            file.getContents(), file.getCharset());
                    erlScanner = ErlModelManager
                            .getErlangModel()
                            .getToolkit()
                            .createScanner(getScannerName(), initialText,
                                    filePath, false);
                } catch (final CoreException e) {
                    ErlLogger.warn(e);
                }
            }
        }
        return erlScanner;
    }

    @Override
    public String getScannerName() {
        final IFile file = getFile();
        if (file != null) {
            final IPath fullPath = file.getFullPath();
            final String scannerName = "scratchPad"
                    + fullPath.toPortableString().hashCode() + "_"
                    + fullPath.removeFileExtension().lastSegment();
            return scannerName;
        }
        return null;
    }

    @Override
    protected void addFoldingSupport(final ISourceViewer viewer) {
        // TODO Not yet
    }

}
