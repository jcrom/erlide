package com.rytong.lua;

import org.eclipse.dltk.ui.text.AbstractScriptScanner;
import org.eclipse.dltk.ui.text.IColorManager;
import org.eclipse.dltk.ui.text.ScriptPresentationReconciler;
import org.eclipse.dltk.ui.text.ScriptSourceViewerConfiguration;
import org.eclipse.dltk.ui.text.SingleTokenScriptScanner;
import org.eclipse.dltk.ui.text.completion.ContentAssistPreference;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.texteditor.ITextEditor;

public class LuaSourceViewerConfiguration extends
        ScriptSourceViewerConfiguration {

    private AbstractScriptScanner fCodeScanner;
    private AbstractScriptScanner fStringScanner;
    private AbstractScriptScanner fSingleQuoteStringScanner;
    private AbstractScriptScanner fCommentScanner;
    private AbstractScriptScanner fMultilineCommentScanner;
    private AbstractScriptScanner fNumberScanner;
    
    public LuaSourceViewerConfiguration(IColorManager colorManager, IPreferenceStore preferenceStore, ITextEditor editor, String partitioning) {
        super(colorManager, preferenceStore, editor, partitioning);
    }
    
    @Override
    protected ContentAssistPreference getContentAssistPreference() {
        return LuaContentAssistPreference.getDefault();
    }
    
    public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
        PresentationReconciler reconciler = new ScriptPresentationReconciler();
        reconciler.setDocumentPartitioning(this.getConfiguredDocumentPartitioning(sourceViewer));

        DefaultDamagerRepairer dr;
        dr = new DefaultDamagerRepairer(this.fCodeScanner);
        reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
        reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);

        dr = new DefaultDamagerRepairer(this.fStringScanner);
        reconciler.setDamager(dr, ILuaPartitions.LUA_STRING);
        reconciler.setRepairer(dr, ILuaPartitions.LUA_STRING);

        dr = new DefaultDamagerRepairer(this.fSingleQuoteStringScanner);
        reconciler.setDamager(dr, ILuaPartitions.LUA_SINGLE_QUOTE_STRING);
        reconciler.setRepairer(dr, ILuaPartitions.LUA_SINGLE_QUOTE_STRING);

        dr = new DefaultDamagerRepairer(this.fMultilineCommentScanner);
        reconciler.setDamager(dr, ILuaPartitions.LUA_MULTI_LINE_COMMENT);
        reconciler.setRepairer(dr, ILuaPartitions.LUA_MULTI_LINE_COMMENT);

        dr = new DefaultDamagerRepairer(this.fCommentScanner);
        reconciler.setDamager(dr, ILuaPartitions.LUA_COMMENT);
        reconciler.setRepairer(dr, ILuaPartitions.LUA_COMMENT);

        dr = new DefaultDamagerRepairer(this.fNumberScanner);
        reconciler.setDamager(dr, ILuaPartitions.LUA_NUMBER);
        reconciler.setRepairer(dr, ILuaPartitions.LUA_NUMBER);

        return reconciler;
    }

    /**
     * This method is called from base class.
     */
    protected void initializeScanners() {
        // This is our code scanner
        this.fCodeScanner = new LuaCodeScanner(this.getColorManager(), this.fPreferenceStore);

        // This is default scanners for partitions with same color.
        this.fStringScanner = new SingleTokenScriptScanner(this.getColorManager(), this.fPreferenceStore, ILuaColorConstants.LUA_STRING);
        this.fSingleQuoteStringScanner = new SingleTokenScriptScanner(this.getColorManager(), this.fPreferenceStore, ILuaColorConstants.LUA_STRING);
        this.fMultilineCommentScanner = new SingleTokenScriptScanner(this.getColorManager(), this.fPreferenceStore,
                ILuaColorConstants.LUA_MULTI_LINE_COMMENT);
        this.fCommentScanner = new SingleTokenScriptScanner(this.getColorManager(), this.fPreferenceStore, ILuaColorConstants.LUA_SINGLE_LINE_COMMENT);
        this.fNumberScanner = new SingleTokenScriptScanner(this.getColorManager(), this.fPreferenceStore, ILuaColorConstants.LUA_NUMBER);
    }

    public void handlePropertyChangeEvent(PropertyChangeEvent event) {
        if (this.fCodeScanner.affectsBehavior(event)) {
            this.fCodeScanner.adaptToPreferenceChange(event);
        }
        if (this.fStringScanner.affectsBehavior(event)) {
            this.fStringScanner.adaptToPreferenceChange(event);
        }
        if (this.fSingleQuoteStringScanner.affectsBehavior(event)) {
            this.fSingleQuoteStringScanner.adaptToPreferenceChange(event);
        }
        if (this.fMultilineCommentScanner.affectsBehavior(event)) {
            this.fMultilineCommentScanner.adaptToPreferenceChange(event);
        }
        if (this.fCommentScanner.affectsBehavior(event)) {
            this.fCommentScanner.adaptToPreferenceChange(event);
        }
        if (this.fNumberScanner.affectsBehavior(event)) {
            this.fNumberScanner.adaptToPreferenceChange(event);
        }
    }

    public boolean affectsTextPresentation(PropertyChangeEvent event) {
        return this.fCodeScanner.affectsBehavior(event) || this.fStringScanner.affectsBehavior(event)
                || this.fSingleQuoteStringScanner.affectsBehavior(event) || this.fMultilineCommentScanner.affectsBehavior(event)
                || this.fCommentScanner.affectsBehavior(event) || this.fNumberScanner.affectsBehavior(event);
    }

    /**
     * Lua specific one line comment
     * 
     * @see ScriptSourceViewerConfiguration#getCommentPrefix()
     */
    @Override
    protected String getCommentPrefix() {
        return LuaConstants.COMMENT_STRING;
    }

    @Override
    public String[] getConfiguredContentTypes(final ISourceViewer sourceViewer) {
        return ILuaPartitions.LUA_PARTITION_TYPES;
    }

}
