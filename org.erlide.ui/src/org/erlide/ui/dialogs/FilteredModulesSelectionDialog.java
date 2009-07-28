/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.erlide.ui.dialogs;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ResourceWorkingSetFilter;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.XMLMemento;
import org.eclipse.ui.actions.WorkingSetFilterActionGroup;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.statushandlers.StatusManager;
import org.erlide.core.erlang.ErlangCore;
import org.erlide.core.erlang.IErlModel;
import org.erlide.core.erlang.util.PluginUtils;
import org.erlide.core.erlang.util.ResourceUtil;
import org.erlide.jinterface.backend.util.PreferencesUtils;
import org.erlide.ui.ErlideUIPlugin;
import org.erlide.ui.editors.erl.IErlangHelpContextIds;

/**
 * Shows a list of resources to the user with a text entry field for a string
 * pattern used to filter the list of resources.
 * 
 */
public class FilteredModulesSelectionDialog extends
		FilteredItemsSelectionDialog {

	private static final String DIALOG_SETTINGS = "org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog"; //$NON-NLS-1$
	private static final String WORKINGS_SET_SETTINGS = "WorkingSet"; //$NON-NLS-1$

	private final ModuleItemLabelProvider moduleItemLabelProvider;
	private final ModuleItemDetailsLabelProvider moduleItemDetailsLabelProvider;
	private WorkingSetFilterActionGroup workingSetFilterActionGroup;
	final CustomWorkingSetFilter workingSetFilter = new CustomWorkingSetFilter();
	private String title;
	final IContainer container;
	final int typeMask;

	/**
	 * Creates a new instance of the class
	 * 
	 * @param shell
	 *            the parent shell
	 * @param multi
	 *            the multi selection flag
	 * @param container
	 *            the container
	 * @param typesMask
	 *            the types mask
	 */
	public FilteredModulesSelectionDialog(Shell shell, boolean multi,
			IContainer container, int typesMask) {
		super(shell, multi);

		setSelectionHistory(new ModuleSelectionHistory());

		setTitle("Open Module");
		PlatformUI.getWorkbench().getHelpSystem().setHelp(shell,
				IErlangHelpContextIds.OPEN_MODULE_DIALOG);

		this.container = container;
		this.typeMask = typesMask;

		moduleItemLabelProvider = new ModuleItemLabelProvider();
		moduleItemDetailsLabelProvider = new ModuleItemDetailsLabelProvider();
		setListLabelProvider(moduleItemLabelProvider);
		setDetailsLabelProvider(moduleItemDetailsLabelProvider);
	}

	@Override
	public void setTitle(String title) {
		super.setTitle(title);
		this.title = title;
	}

	/**
	 * Adds or replaces subtitle of the dialog
	 * 
	 * @param text
	 *            the new subtitle
	 */
	void setSubtitle(String text) {
		if (text == null || text.length() == 0) {
			getShell().setText(title);
		} else {
			getShell().setText(title + " - " + text); //$NON-NLS-1$
		}
	}

	@Override
	protected IDialogSettings getDialogSettings() {
		IDialogSettings settings = ErlideUIPlugin.getDefault()
				.getDialogSettings().getSection(DIALOG_SETTINGS);

		if (settings == null) {
			settings = ErlideUIPlugin.getDefault().getDialogSettings()
					.addNewSection(DIALOG_SETTINGS);
		}

		return settings;
	}

	@Override
	protected void storeDialog(IDialogSettings settings) {
		super.storeDialog(settings);

		XMLMemento memento = XMLMemento.createWriteRoot("workingSet"); //$NON-NLS-1$
		workingSetFilterActionGroup.saveState(memento);
		workingSetFilterActionGroup.dispose();
		StringWriter writer = new StringWriter();
		try {
			memento.save(writer);
			settings.put(WORKINGS_SET_SETTINGS, writer.getBuffer().toString());
		} catch (IOException e) {
			StatusManager.getManager().handle(
					new Status(IStatus.ERROR, ErlideUIPlugin.PLUGIN_ID,
							IStatus.ERROR, "", e)); //$NON-NLS-1$
			// don't do anything. Simply don't store the settings
		}
	}

	@Override
	protected void restoreDialog(IDialogSettings settings) {
		super.restoreDialog(settings);

		String setting = settings.get(WORKINGS_SET_SETTINGS);
		if (setting != null) {
			try {
				IMemento memento = XMLMemento.createReadRoot(new StringReader(
						setting));
				workingSetFilterActionGroup.restoreState(memento);
			} catch (WorkbenchException e) {
				StatusManager.getManager().handle(
						new Status(IStatus.ERROR, ErlideUIPlugin.PLUGIN_ID,
								IStatus.ERROR, "", e)); //$NON-NLS-1$
				// don't do anything. Simply don't restore the settings
			}
		}

		addListFilter(workingSetFilter);

		applyFilter();
	}

	@Override
	protected void fillViewMenu(IMenuManager menuManager) {
		super.fillViewMenu(menuManager);

		workingSetFilterActionGroup = new WorkingSetFilterActionGroup(
				getShell(), new IPropertyChangeListener() {
					public void propertyChange(PropertyChangeEvent event) {
						String property = event.getProperty();

						if (WorkingSetFilterActionGroup.CHANGE_WORKING_SET
								.equals(property)) {

							IWorkingSet workingSet = (IWorkingSet) event
									.getNewValue();

							if (workingSet != null
									&& !(workingSet.isAggregateWorkingSet() && workingSet
											.isEmpty())) {
								workingSetFilter.setWorkingSet(workingSet);
								setSubtitle(workingSet.getLabel());
							} else {
								IWorkbenchWindow window = PlatformUI
										.getWorkbench()
										.getActiveWorkbenchWindow();

								if (window != null) {
									IWorkbenchPage page = window
											.getActivePage();
									workingSet = page.getAggregateWorkingSet();

									if (workingSet.isAggregateWorkingSet()
											&& workingSet.isEmpty()) {
										workingSet = null;
									}
								}

								workingSetFilter.setWorkingSet(workingSet);
								setSubtitle(null);
							}

							scheduleRefresh();
						}
					}
				});

		menuManager.add(new Separator());
		workingSetFilterActionGroup.fillContextMenu(menuManager);
	}

	@Override
	protected Control createExtendedContentArea(Composite parent) {
		return null;
	}

	@Override
	public Object[] getResult() {
		Object[] result = super.getResult();

		if (result == null) {
			return null;
		}

		List<Object> resultToReturn = new ArrayList<Object>();

		for (int i = 0; i < result.length; i++) {
			// if (result[i] instanceof IResource) {
			resultToReturn.add(result[i]);
			// }
		}

		return resultToReturn.toArray();
	}

	@Override
	public int open() {
		if (getInitialPattern() == null) {
			IWorkbenchWindow window = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow();
			if (window != null) {
				ISelection selection = window.getSelectionService()
						.getSelection();
				if (selection instanceof ITextSelection) {
					String text = ((ITextSelection) selection).getText();
					if (text != null) {
						text = text.trim();
						if (text.length() > 0) {
							IWorkspace workspace = ResourcesPlugin
									.getWorkspace();
							IStatus result = workspace.validateName(text,
									IResource.FILE);
							if (result.isOK()) {
								setInitialPattern(text);
							}
						}
					}
				}
			}
		}
		return super.open();
	}

	@Override
	public String getElementName(Object item) {
		if (item instanceof String) {
			return (String) item;
		}
		IResource resource = (IResource) item;
		return resource.getName();
	}

	@Override
	protected IStatus validateItem(Object item) {
		return new Status(IStatus.OK, ErlideUIPlugin.PLUGIN_ID, 0, "", null); //$NON-NLS-1$
	}

	@Override
	protected ItemsFilter createFilter() {
		return new ModuleFilter(container, typeMask);
	}

	@Override
	protected void applyFilter() {
		super.applyFilter();
	}

	@Override
	protected Comparator<Object> getItemsComparator() {
		return new Comparator<Object>() {

			public int compare(Object o1, Object o2) {
				Collator collator = Collator.getInstance();
				String s1 = (o1 instanceof IResource) ? ((IResource) o1)
						.getName() : (String) o1;
				String s2 = (o2 instanceof IResource) ? ((IResource) o2)
						.getName() : (String) o2;
				int comparability = collator.compare(s1, s2);
				if (comparability == 0) {
					// IPath p1 = resource1.getFullPath();
					// IPath p2 = resource2.getFullPath();
					// int c1 = p1.segmentCount();
					// int c2 = p2.segmentCount();
					// for (int i = 0; i < c1 && i < c2; i++) {
					// comparability = collator.compare(p1.segment(i), p2
					// .segment(i));
					// if (comparability != 0) {
					// return comparability;
					// }
					// }
					// comparability = c2 - c1;
				}

				return comparability;
			}
		};
	}

	@Override
	protected void fillContentProvider(AbstractContentProvider contentProvider,
			ItemsFilter itemsFilter, IProgressMonitor progressMonitor)
			throws CoreException {
		if (itemsFilter instanceof ModuleFilter) {

			container.accept(new ModuleProxyVisitor(contentProvider,
					(ModuleFilter) itemsFilter, progressMonitor),
					IResource.NONE);
		}
		if (progressMonitor != null) {
			progressMonitor.done();
		}

	}

	/**
	 * A label provider for ResourceDecorator objects. It creates labels with a
	 * resource full path for duplicates. It uses the Platform UI label
	 * decorator for providing extra resource info.
	 */
	private class ModuleItemLabelProvider extends LabelProvider implements
			ILabelProviderListener, IStyledLabelProvider {

		// Need to keep our own list of listeners
		private final ListenerList listeners = new ListenerList();

		WorkbenchLabelProvider provider = new WorkbenchLabelProvider();

		public ModuleItemLabelProvider() {
			super();
			provider.addListener(this);
		}

		@Override
		public Image getImage(Object element) {
			if (!(element instanceof IResource)) {
				return super.getImage(element);
			}

			IResource res = (IResource) element;

			return provider.getImage(res);
		}

		@Override
		public String getText(Object element) {
			if (!(element instanceof IResource)) {
				return super.getText(element);
			}

			IResource res = (IResource) element;
			String str = res.getName();

			// extra info for duplicates
			if (isDuplicateElement(element)) {
				str = str
						+ " - " + res.getParent().getFullPath().makeRelative().toString(); //$NON-NLS-1$
			}

			return str;
		}

		public StyledString getStyledText(Object element) {
			if (!(element instanceof IResource)) {
				return new StyledString(super.getText(element));
			}

			String text = getText(element);
			StyledString str = new StyledString(text);

			int index = text.indexOf(" - ");
			if (index != -1) {
				str.setStyle(index, text.length() - index,
						StyledString.QUALIFIER_STYLER);
			}
			return str;
		}

		@Override
		public void dispose() {
			provider.removeListener(this);
			provider.dispose();

			super.dispose();
		}

		@Override
		public void addListener(ILabelProviderListener listener) {
			listeners.add(listener);
		}

		@Override
		public void removeListener(ILabelProviderListener listener) {
			listeners.remove(listener);
		}

		public void labelProviderChanged(LabelProviderChangedEvent event) {
			Object[] l = listeners.getListeners();
			for (int i = 0; i < listeners.size(); i++) {
				((ILabelProviderListener) l[i]).labelProviderChanged(event);
			}
		}

	}

	/**
	 * A label provider for details of ResourceItem objects.
	 */
	class ModuleItemDetailsLabelProvider extends ModuleItemLabelProvider {

		@Override
		public Image getImage(Object element) {
			if (!(element instanceof IResource)) {
				return super.getImage(element);
			}

			IResource parent = ((IResource) element).getParent();
			return provider.getImage(parent);
		}

		@Override
		public String getText(Object element) {
			if (!(element instanceof IResource)) {
				return super.getText(element);
			}

			IResource parent = ((IResource) element).getParent();

			if (parent.getType() == IResource.ROOT) {
				// Get readable name for workspace root ("Workspace"), without
				// duplicating language-specific string here.
				return null;
			}

			return parent.getProjectRelativePath().makeRelative().toString()
					+ " - " + parent.getProject().getName();
		}

		@Override
		public void labelProviderChanged(LabelProviderChangedEvent event) {
			Object[] l = super.listeners.getListeners();
			for (int i = 0; i < super.listeners.size(); i++) {
				((ILabelProviderListener) l[i]).labelProviderChanged(event);
			}
		}
	}

	/**
	 * Viewer filter which filters resources due to current working set
	 */
	private class CustomWorkingSetFilter extends ViewerFilter {
		private final ResourceWorkingSetFilter resourceWorkingSetFilter = new ResourceWorkingSetFilter();

		/**
		 * Returns the active working set the filter is working with.
		 * 
		 * @return the active working set
		 */
		@SuppressWarnings("unused")
		public IWorkingSet getWorkingSet() {
			return resourceWorkingSetFilter.getWorkingSet();
		}

		/**
		 * Sets the active working set.
		 * 
		 * @param workingSet
		 *            the working set the filter should work with
		 */
		public void setWorkingSet(IWorkingSet workingSet) {
			resourceWorkingSetFilter.setWorkingSet(workingSet);
		}

		@Override
		public boolean select(Viewer viewer, Object parentElement,
				Object element) {
			return resourceWorkingSetFilter.select(viewer, parentElement,
					element);
		}
	}

	/**
	 * ResourceProxyVisitor to visit resource tree and get matched resources.
	 * During visit resources it updates progress monitor and adds matched
	 * resources to ContentProvider instance.
	 */
	private class ModuleProxyVisitor implements IResourceProxyVisitor {

		private final AbstractContentProvider proxyContentProvider;
		private final ModuleFilter resourceFilter;
		private final IProgressMonitor progressMonitor;
		private final List<IResource> projects;

		/**
		 * Creates new ResourceProxyVisitor instance.
		 * 
		 * @param contentProvider
		 * @param resourceFilter
		 * @param progressMonitor
		 * @throws CoreException
		 */
		public ModuleProxyVisitor(AbstractContentProvider contentProvider,
				ModuleFilter resourceFilter, IProgressMonitor progressMonitor)
				throws CoreException {
			super();
			this.proxyContentProvider = contentProvider;
			this.resourceFilter = resourceFilter;
			this.progressMonitor = progressMonitor;
			IResource[] resources = container.members();
			this.projects = new ArrayList<IResource>(Arrays.asList(resources));

			if (progressMonitor != null) {
				progressMonitor.beginTask("Searching", projects.size());
			}
		}

		public boolean visit(IResourceProxy proxy) {

			if (progressMonitor.isCanceled()) {
				return false;
			}

			IResource resource = proxy.requestResource();

			if (this.projects.remove((resource.getProject()))
					|| this.projects.remove((resource))) {
				progressMonitor.worked(1);
			}

			if (resource.getProject() == resource) {
				// navigate even "external" lists
				IErlModel model = ErlangCore.getModel();
				IProject prj = resource.getProject();
				if (prj != null) {
					String extMods = model.getExternal(model.findProject(prj),
							ErlangCore.EXTERNAL_MODULES);
					List<String> files = new ArrayList<String>();
					files.addAll(PreferencesUtils.unpackList(extMods));
					String extIncs = model.getExternal(model.findProject(prj),
							ErlangCore.EXTERNAL_INCLUDES);
					files.addAll(PreferencesUtils.unpackList(extIncs));

					final IPathVariableManager pvm = ResourcesPlugin
							.getWorkspace().getPathVariableManager();
					for (String str : files) {
						IResource fres;
						try {
							fres = ResourceUtil.recursiveFindNamedResource(prj,
									str, null);
						} catch (CoreException e) {
							fres = null;
						}
						if (fres != null) {
							List<String> lines = PreferencesUtils.readFile(fres
									.getLocation().toString());
							for (String pref : lines) {

								String path;
								IPath p = new Path(pref);
								String first = p.segment(0);
								IPath v = pvm.getValue(first);
								if (v != null) {
									path = v.append(p.removeFirstSegments(1))
											.toString();
								} else {
									if (p.isAbsolute()) {
										path = pref;
									} else {
										path = prj.getLocation() + "/" + pref;
									}
								}
								proxyContentProvider.add(path, resourceFilter);
							}
						}
					}
				}
			}

			if (resource.getType() == IResource.FOLDER && resource.isDerived()) {
				return false;
			}

			if (ResourceUtil.hasErlangExtension(resource)) {
				IContainer container = resource.getParent();
				if (PluginUtils.isOnSourcePath(container)
						|| PluginUtils.isOnIncludePath(container)) {
					proxyContentProvider.add(resource, resourceFilter);
				}
			}

			if (resource.getType() == IResource.FILE) {
				return false;
			}

			return true;
		}
	}

	/**
	 * Filters resources using pattern and showDerived flag. It overrides
	 * ItemsFilter.
	 */
	protected class ModuleFilter extends ItemsFilter {

		private final IContainer filterContainer;
		private final int filterTypeMask;

		/**
		 * Creates new ResourceFilter instance
		 * 
		 * @param container
		 * @param showDerived
		 *            flag which determine showing derived elements
		 * @param typeMask
		 */
		public ModuleFilter(IContainer container, int typeMask) {
			super();
			this.filterContainer = container;
			this.filterTypeMask = typeMask;
		}

		/**
		 * Creates new ResourceFilter instance
		 */
		public ModuleFilter() {
			super();
			this.filterContainer = container;
			this.filterTypeMask = typeMask;
		}

		/**
		 * @param item
		 *            Must be instance of IResource, otherwise
		 *            <code>false</code> will be returned.
		 * @see org.eclipse.ui.dialogs.FilteredItemsSelectionDialog.ItemsFilter#isConsistentItem(java.lang.Object)
		 */
		@Override
		public boolean isConsistentItem(Object item) {
			if (item instanceof String) {
				return true;
			}
			if (!(item instanceof IResource)) {
				return false;
			}
			IResource resource = (IResource) item;
			if (this.filterContainer.findMember(resource.getFullPath()) != null) {
				return true;
			}
			return false;
		}

		/**
		 * @param item
		 *            Must be instance of IResource, otherwise
		 *            <code>false</code> will be returned.
		 * @see org.eclipse.ui.dialogs.FilteredItemsSelectionDialog.ItemsFilter#matchItem(java.lang.Object)
		 */
		@Override
		public boolean matchItem(Object item) {
			if (item instanceof String) {
				Path path = new Path((String) item);
				return matches(path.lastSegment().toString());
			}
			if (!(item instanceof IResource)) {
				return false;
			}
			IResource resource = (IResource) item;
			if ((this.filterTypeMask & resource.getType()) == 0) {
				return false;
			}
			return matches(resource.getName());
		}

		@Override
		public boolean isSubFilter(ItemsFilter filter) {
			if (!super.isSubFilter(filter)) {
				return false;
			}
			if (filter instanceof ModuleFilter) {
				return true;
			}
			return false;
		}

		@Override
		public boolean equalsFilter(ItemsFilter iFilter) {
			if (!super.equalsFilter(iFilter)) {
				return false;
			}
			if (iFilter instanceof ModuleFilter) {
				return true;
			}
			return false;
		}

	}

	/**
	 * Extends the <code>SelectionHistory</code>, providing support for
	 * <code>OpenTypeHistory</code>.
	 */
	protected class ModuleSelectionHistory extends SelectionHistory {

		/**
		 * Creates new instance of TypeSelectionHistory
		 */
		public ModuleSelectionHistory() {
			super();
		}

		@Override
		public synchronized void accessed(Object object) {
			super.accessed(object);
		}

		@Override
		public synchronized boolean remove(Object element) {
			// OpenModuleHistory.getInstance().remove((TypeNameMatch) element);
			return super.remove(element);
		}

		@Override
		public void load(IMemento memento) {
			// TypeNameMatch[] types = OpenTypeHistory.getInstance()
			// .getTypeInfos();
			//
			// for (int i = types.length - 1; i >= 0; i--) { // see
			// // https://bugs.eclipse.org/bugs/show_bug.cgi?id=205314
			// TypeNameMatch type = types[i];
			// accessed(type);
			// }
		}

		@Override
		public void save(IMemento memento) {
			persistHistory();
		}

		/**
		 * Stores contents of the local history into persistent history
		 * container.
		 */
		private synchronized void persistHistory() {
			// if (getReturnCode() == OK) {
			// Object[] items = getHistoryItems();
			// for (int i = 0; i < items.length; i++) {
			// OpenTypeHistory.getInstance().accessed(
			// (TypeNameMatch) items[i]);
			// }
			// }
		}

		@Override
		protected Object restoreItemFromMemento(IMemento element) {
			return null;
		}

		@Override
		protected void storeItemToMemento(Object item, IMemento element) {

		}

	}

}
