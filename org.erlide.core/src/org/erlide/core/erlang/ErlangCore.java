/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation
 *******************************************************************************/
package org.erlide.core.erlang;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Preferences;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.erlide.core.ErlangPlugin;
import org.erlide.core.ErlangProjectProperties;
import org.erlide.core.erlang.internal.ErlModelManager;
import org.erlide.runtime.ErlLogger;
import org.erlide.runtime.backend.Backend;
import org.erlide.runtime.backend.BackendManager;
import org.erlide.runtime.backend.RuntimeInfo;
import org.erlide.runtime.backend.RuntimeInfoManager;

/**
 * <p>
 * The single instance of this class can be accessed from any plug-in declaring
 * the Erlang model plug-in as a prerequisite via
 * <code>ErlangCore.getErlangCore()</code>. The Erlang model plug-in will be
 * activated automatically if not already active.
 * </p>
 */
public final class ErlangCore {

	/**
	 * The identifier for the Erlang model (value
	 * <code>"org.erlide.core.erlang.erlangmodel"</code>).
	 */
	public static final String MODEL_ID = ErlangPlugin.PLUGIN_ID
			+ ".erlangmodel"; //$NON-NLS-1$

	/**
	 * Name of the handle id attribute in a Erlang marker.
	 */
	public static final String ATT_HANDLE_ID = "org.erlide.core.erlang.internal.ErlModelManager.handleId"; //$NON-NLS-1$

	/**
	 * Name of the User Library Container id.
	 * 
	 */
	public static final String USER_LIBRARY_CONTAINER_ID = "org.erlide.core.USER_LIBRARY"; //$NON-NLS-1$

	// *************** Possible IDs for configurable options.
	// ********************

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_LOCAL_VARIABLE_ATTR = ErlangPlugin.PLUGIN_ID
			+ ".compiler.debug.localVariable"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_LINE_NUMBER_ATTR = ErlangPlugin.PLUGIN_ID
			+ ".compiler.debug.lineNumber"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_SOURCE_FILE_ATTR = ErlangPlugin.PLUGIN_ID
			+ ".compiler.debug.sourceFile"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_CODEGEN_UNUSED_LOCAL = ErlangPlugin.PLUGIN_ID
			+ ".compiler.codegen.unusedLocal"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_CODEGEN_TARGET_PLATFORM = ErlangPlugin.PLUGIN_ID
			+ ".compiler.codegen.targetPlatform"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_CODEGEN_INLINE_JSR_BYTECODE = ErlangPlugin.PLUGIN_ID
			+ ".compiler.codegen.inlineJsrBytecode"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_DOC_COMMENT_SUPPORT = ErlangPlugin.PLUGIN_ID
			+ ".compiler.doc.comment.support"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_PB_DEPRECATION = ErlangPlugin.PLUGIN_ID
			+ ".compiler.problem.deprecation"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_PB_UNUSED_LOCAL = ErlangPlugin.PLUGIN_ID
			+ ".compiler.problem.unusedLocal"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_PB_UNUSED_PARAMETER = ErlangPlugin.PLUGIN_ID
			+ ".compiler.problem.unusedParameter"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_PB_UNUSED_PRIVATE_FUNCTION = ErlangPlugin.PLUGIN_ID
			+ ".compiler.problem.unusedPrivateFunction"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_PB_LOCAL_VARIABLE_HIDING = ErlangPlugin.PLUGIN_ID
			+ ".compiler.problem.localVariableHiding"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_PB_INVALID_EDOC = ErlangPlugin.PLUGIN_ID
			+ ".compiler.problem.invalidEdoc"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_PB_INVALID_EDOC_TAGS = ErlangPlugin.PLUGIN_ID
			+ ".compiler.problem.invalidEdocTags"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_PB_MISSING_Edoc_TAGS = ErlangPlugin.PLUGIN_ID
			+ ".compiler.problem.missingEdocTags"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_PB_MISSING_Edoc_COMMENTS = ErlangPlugin.PLUGIN_ID
			+ ".compiler.problem.missingEdocComments"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_PB_MAX_PER_UNIT = ErlangPlugin.PLUGIN_ID
			+ ".compiler.maxProblemPerUnit"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_SOURCE = ErlangPlugin.PLUGIN_ID
			+ ".compiler.source"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_COMPLIANCE = ErlangPlugin.PLUGIN_ID
			+ ".compiler.compliance"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_TASK_PRIORITIES = ErlangPlugin.PLUGIN_ID
			+ ".compiler.taskPriorities"; //$NON-NLS-1$

	/**
	 * Possible configurable option value for COMPILER_TASK_PRIORITIES.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_TASK_PRIORITY_HIGH = "HIGH"; //$NON-NLS-1$

	/**
	 * Possible configurable option value for COMPILER_TASK_PRIORITIES.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_TASK_PRIORITY_LOW = "LOW"; //$NON-NLS-1$

	/**
	 * Possible configurable option value for COMPILER_TASK_PRIORITIES.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_TASK_PRIORITY_NORMAL = "NORMAL"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_TASK_TAGS = ErlangPlugin.PLUGIN_ID
			+ ".compiler.taskTags"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPILER_TASK_CASE_SENSITIVE = ErlangPlugin.PLUGIN_ID
			+ ".compiler.taskCaseSensitive"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CORE_ERLANG_BUILD_ORDER = ErlangPlugin.PLUGIN_ID
			+ ".computeErlangBuildOrder"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CORE_ERLANG_BUILD_RESOURCE_COPY_FILTER = ErlangPlugin.PLUGIN_ID
			+ ".builder.resourceCopyExclusionFilter"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CORE_ERLANG_BUILD_DUPLICATE_RESOURCE = ErlangPlugin.PLUGIN_ID
			+ ".builder.duplicateResourceTask"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CORE_ERLANG_BUILD_CLEAN_OUTPUT_FOLDER = ErlangPlugin.PLUGIN_ID
			+ ".builder.cleanOutputFolder"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CORE_INCOMPLETE_CLASSPATH = ErlangPlugin.PLUGIN_ID
			+ ".incompleteClasspath"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CORE_CIRCULAR_CLASSPATH = ErlangPlugin.PLUGIN_ID
			+ ".circularClasspath"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CORE_INCOMPATIBLE_ERTS_LEVEL = ErlangPlugin.PLUGIN_ID
			+ ".incompatibleERTSLevel"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CORE_ERLANG_BUILD_INVALID_CLASSPATH = ErlangPlugin.PLUGIN_ID
			+ ".builder.invalidClasspath"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CORE_ENCODING = ErlangPlugin.PLUGIN_ID
			+ ".encoding"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CORE_ENABLE_CLASSPATH_EXCLUSION_PATTERNS = ErlangPlugin.PLUGIN_ID
			+ ".classpath.exclusionPatterns"; //$NON-NLS-1$

	/**
	 * Possible configurable option ErlangPlugin.PLUGIN_ID.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CORE_ENABLE_CLASSPATH_MULTIPLE_OUTPUT_LOCATIONS = ErlangPlugin.PLUGIN_ID
			+ ".classpath.multipleOutputLocations"; //$NON-NLS-1$

	/**
	 * Default task tag
	 * 
	 */
	public static final String DEFAULT_TASK_TAGS = "TODO,FIXME,XXX"; //$NON-NLS-1$

	/**
	 * Default task priority
	 */
	public static final String DEFAULT_TASK_PRIORITIES = "NORMAL,HIGH,NORMAL"; //$NON-NLS-1$

	// *************** Possible values for configurable options.
	// ********************

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String GENERATE = "generate"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String DO_NOT_GENERATE = "do not generate"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String PRESERVE = "preserve"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String OPTIMIZE_OUT = "optimize out"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String VERSION_R9 = "R9"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String VERSION_R10 = "R10"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String ABORT = "abort"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String ERROR = "error"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String WARNING = "warning"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String IGNORE = "ignore"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPUTE = "compute"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String INSERT = "insert"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String DO_NOT_INSERT = "do not insert"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String PRESERVE_ONE = "preserve one"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CLEAR_ALL = "clear all"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String NORMAL = "normal"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String COMPACT = "compact"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String TAB = "tab"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String SPACE = "space"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String ENABLED = "enabled"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String DISABLED = "disabled"; //$NON-NLS-1$

	/**
	 * Possible configurable option value.
	 * 
	 * @see #getDefaultOptions()
	 */
	public static final String CLEAN = "clean"; //$NON-NLS-1$

	public static final IErlModelManager getModelManager() {
		return ErlModelManager.getDefault();
	}

	public static final IErlModel getModel() {
		return getModelManager().getErlangModel();
	}

	public static final RuntimeInfoManager getRuntimeInfoManager() {
		return RuntimeInfoManager.getDefault();
	}

	public static final BackendManager getBackendManager() {
		return BackendManager.getDefault();
	}

	public static ErlangProjectProperties getProjectProperties(IProject project) {
		return getModelManager().getErlangModel().getErlangProject(
				project.getName()).getProperties();
	}

	/**
	 * If runtime is not set, try to locate one. The first one found as below is
	 * set as default. All "obvious" runtimes found are stored.
	 * <ul>
	 * <li>A system property <code>erlide.runtime</code> can be set to point to
	 * a location.</li>
	 * <li>A preference in the default scope
	 * <code>org.erlide.core/default_runtime</code> can be set to point to a
	 * location.</li>
	 * <li>Look for existing Erlang runtimes in a few obvious places and install
	 * them, choosing a suitable one as default.</li>
	 * </ul>
	 * 
	 */
	public static void initializeRuntime() {
		if (getRuntimeInfoManager().getDefaultRuntime() != null) {
			// TODO should we always run his check?
			return;
		}
		String[] locations = {
				System.getProperty("erlide.runtime"),
				new DefaultScope().getNode("org.erlide.core").get(
						"default_runtime", null), "c:/program files",
				"c:/programs", "c:/", "c:/apps",
				System.getProperty("user.home"), "/usr/lib/erlang",
		// TODO Mac?!
		};
		for (String loc : locations) {
			Collection<File> roots = findRuntime(loc);
			for (File root : roots) {
				RuntimeInfo rt = new RuntimeInfo();
				rt.setOtpHome(root.getPath());
				rt.setName(root.getName());
				getRuntimeInfoManager().addRuntime(rt);
			}
		}
		ArrayList<RuntimeInfo> list = new ArrayList<RuntimeInfo>(
				getRuntimeInfoManager().getRuntimes());
		Collections.sort(list, new Comparator<RuntimeInfo>() {
			public int compare(RuntimeInfo o1, RuntimeInfo o2) {
				int x = -o1.getVersion().compareTo(o2.getVersion());
				if (x != 0) {
					return x;
				}
				return -o1.getName().compareTo(o2.getName());
			}
		});
		getRuntimeInfoManager().setDefaultRuntime(list.get(0).getName());

	}

	private static Collection<File> findRuntime(String loc) {
		Collection<File> result = new ArrayList<File>();
		if (loc == null) {
			return result;
		}
		File folder = new File(loc);
		if (folder == null || !folder.exists()) {
			return result;
		}
		File[] candidates = folder.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.isDirectory()
						&& pathname.getName().startsWith("erl");
			}
		});
		for (File f : candidates) {
			if (RuntimeInfo.validateLocation(f.getPath())) {
				result.add(f);
			}
		}
		return result;
	}

	/**
	 * Returns a table of all known configurable options with their default
	 * values. These options allow to configure the behaviour of the underlying
	 * components. The client may safely use the result as a template that they
	 * can modify and then pass to <code>setOptions</code>.
	 * 
	 * Helper constants have been defined on ErlangCore for each of the option
	 * PLUGIN_ID and their possible constant values.
	 * 
	 * Note: more options might be added in further releases.
	 * 
	 * <pre>
	 *                           RECOGNIZED OPTIONS:
	 *                           COMPILER / Generating Source Debug Attribute
	 *                              When generated, this attribute will enable the debugger to present the
	 *                              corresponding source code.
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.debug.sourceFile&quot;
	 *                               - possible values:   { &quot;generate&quot;, &quot;do not generate&quot; }
	 *                               - default:           &quot;generate&quot;
	 *                           COMPILER / Edoc Comment Support
	 *                              When this support is disabled, the compiler will ignore all Edoc problems options settings
	 *                              and will not report any Edoc problem. It will also not find any reference in Edoc comment and
	 *                              DOM AST Edoc node will be only a flat text instead of having structured tag elements.
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.doc.comment.support&quot;
	 *                               - possible values:   { &quot;enabled&quot;, &quot;disabled&quot; }
	 *                               - default:           &quot;enabled&quot;
	 *                           COMPILER / Reporting Deprecation
	 *                              When enabled, the compiler will signal use of deprecated API either as an
	 *                              error or a warning.
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.problem.deprecation&quot;
	 *                               - possible values:   { &quot;error&quot;, &quot;warning&quot;, &quot;ignore&quot; }
	 *                               - default:           &quot;warning&quot;
	 *                           COMPILER / Reporting Unused Local
	 *                              When enabled, the compiler will issue an error or a warning for unused local
	 *                              variables (that is, variables never read from)
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.problem.unusedLocal&quot;
	 *                               - possible values:   { &quot;error&quot;, &quot;warning&quot;, &quot;ignore&quot; }
	 *                               - default:           &quot;ignore&quot;
	 *                           COMPILER / Reporting Unused Parameter
	 *                              When enabled, the compiler will issue an error or a warning for unused method
	 *                              parameters (that is, parameters never read from)
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.problem.unusedParameter&quot;
	 *                               - possible values:   { &quot;error&quot;, &quot;warning&quot;, &quot;ignore&quot; }
	 *                               - default:           &quot;ignore&quot;
	 *                           COMPILER / Reporting Unused Private Functions
	 *                              When enabled, the compiler will issue an error or a warning whenever a private
	 *                              method or field is declared but never used within the same unit.
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.problem.unusedPrivateFunctions&quot;
	 *                               - possible values:   { &quot;error&quot;, &quot;warning&quot;, &quot;ignore&quot; }
	 *                               - default:           &quot;ignore&quot;
	 *                           COMPILER / Reporting Local Variable Declaration Hiding another Variable
	 *                              When enabled, the compiler will issue an error or a warning whenever a local variable
	 *                              declaration is hiding some field or local variable (either locally, inherited or defined in enclosing type).
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.problem.localVariableHiding&quot;
	 *                               - possible values:   { &quot;error&quot;, &quot;warning&quot;, &quot;ignore&quot; }
	 *                               - default:           &quot;ignore&quot;
	 *                           COMPILER / Reporting Invalid Edoc Comment
	 *                              This is the generic control for the severity of Edoc problems.
	 *                              When enabled, the compiler will issue an error or a warning for a problem in Edoc.
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.problem.invalidEdoc&quot;
	 *                               - possible values:   { &quot;error&quot;, &quot;warning&quot;, &quot;ignore&quot; }
	 *                               - default:           &quot;ignore&quot;
	 *                           COMPILER / Reporting Invalid Edoc Tags
	 *                              When enabled, the compiler will signal unbound or unexpected reference tags in Edoc.
	 *                              A 'throws' tag referencing an undeclared exception would be considered as unexpected.
	 * &lt;br&gt;
	 *                              Note that this diagnosis can be enabled based on the visibility of the construct associated with the Edoc;
	 *                              also see the setting &quot;org.erlide.core.erlang.compiler.problem.invalidEdocTagsVisibility&quot;.
	 * &lt;br&gt;
	 *                              The severity of the problem is controlled with option &quot;org.erlide.core.erlang.compiler.problem.invalidEdoc&quot;.
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.problem.invalidEdocTags&quot;
	 *                               - possible values:   { &quot;disabled&quot;, &quot;enabled&quot; }
	 *                               - default:           &quot;enabled&quot;
	 *                           COMPILER / Reporting Missing Edoc Tags
	 *                              This is the generic control for the severity of Edoc missing tag problems.
	 *                              When enabled, the compiler will issue an error or a warning when tags are missing in Edoc comments.
	 * &lt;br&gt;
	 *                              Note that this diagnosis can be enabled based on the visibility of the construct associated with the Edoc;
	 *                              also see the setting &quot;org.erlide.core.erlang.compiler.problem.missingEdocTagsVisibility&quot;.
	 * &lt;br&gt;
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.problem.missingEdocTags&quot;
	 *                               - possible values:   { &quot;error&quot;, &quot;warning&quot;, &quot;ignore&quot; }
	 *                               - default:           &quot;ignore&quot;
	 *                           COMPILER / Reporting Missing Edoc Comments
	 *                              This is the generic control for the severity of missing Edoc comment problems.
	 *                              When enabled, the compiler will issue an error or a warning when Edoc comments are missing.
	 * &lt;br&gt;
	 *                              Note that this diagnosis can be enabled based on the visibility of the construct associated with the expected Edoc;
	 *                              also see the setting &quot;org.erlide.core.erlang.compiler.problem.missingEdocCommentsVisibility&quot;.
	 * &lt;br&gt;
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.problem.missingEdocComments&quot;
	 *                               - possible values:   { &quot;error&quot;, &quot;warning&quot;, &quot;ignore&quot; }
	 *                               - default:           &quot;ignore&quot;
	 *                           COMPILER / Setting Compliance Level
	 *                              Select the compliance level for the compiler. In &quot;R9&quot; mode, source and target settings
	 *                              should not go beyond &quot;R9&quot; level.
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.compliance&quot;
	 *                               - possible values:   { &quot;R9&quot;, &quot;R10&quot; }
	 *                               - default:           &quot;R10&quot;
	 *                           COMPILER / Maximum number of problems reported per compilation unit
	 *                              Specify the maximum number of problems reported on each compilation unit.
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.maxProblemPerUnit&quot;
	 *                               - possible values:    &quot;&lt;n&gt;&quot; where &lt;n&gt; is zero or a positive integer (if zero then all problems are reported).
	 *                               - default:           &quot;100&quot;
	 *                           COMPILER / Define the Automatic Task Tags
	 *                              When the tag list is not empty, the compiler will issue a task marker whenever it encounters
	 *                              one of the corresponding tag inside any comment in Erlang source code.
	 *                              Generated task messages will include the tag, and range until the next line separator or comment ending.
	 *                              Note that tasks messages are trimmed. If a tag is starting with a letter or digit, then it cannot be leaded by
	 *                              another letter or digit to be recognized (&quot;fooToDo&quot; will not be recognized as a task for tag &quot;ToDo&quot;, but &quot;foo#ToDo&quot;
	 *                              will be detected for either tag &quot;ToDo&quot; or &quot;#ToDo&quot;). Respectively, a tag ending with a letter or digit cannot be followed
	 *                              by a letter or digit to be recognized (&quot;ToDofoo&quot; will not be recognized as a task for tag &quot;ToDo&quot;, but &quot;ToDo:foo&quot; will
	 *                              be detected either for tag &quot;ToDo&quot; or &quot;ToDo:&quot;).
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.taskTags&quot;
	 *                               - possible values:   { &quot;&lt;tag&gt;[,&lt;tag&gt;]*&quot; } where &lt;tag&gt; is a String without any wild-card or leading/trailing spaces
	 *                               - default:           &quot;TODO,FIXME,XXX&quot;
	 *                           COMPILER / Define the Automatic Task Priorities
	 *                              In parallel with the Automatic Task Tags, this list defines the priorities (high, normal or low)
	 *                              of the task markers issued by the compiler.
	 *                              If the default is specified, the priority of each task marker is &quot;NORMAL&quot;.
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.taskPriorities&quot;
	 *                               - possible values:   { &quot;&lt;priority&gt;[,&lt;priority&gt;]*&quot; } where &lt;priority&gt; is one of &quot;HIGH&quot;, &quot;NORMAL&quot; or &quot;LOW&quot;
	 *                               - default:           &quot;NORMAL,HIGH,NORMAL&quot;
	 *                           COMPILER / Determine whether task tags are case-sensitive
	 *                              When enabled, task tags are considered in a case-sensitive way.
	 *                               - option id:         &quot;org.erlide.core.erlang.compiler.taskCaseSensitive&quot;
	 *                               - possible values:   { &quot;enabled&quot;, &quot;disabled&quot; }
	 *                               - default:           &quot;enabled&quot;
	 *                           BUILDER / Abort if Invalid Classpath
	 *                              Allow to toggle the builder to abort if the classpath is invalid
	 *                               - option id:         &quot;org.erlide.core.erlang.builder.invalidClasspath&quot;
	 *                               - possible values:   { &quot;abort&quot;, &quot;ignore&quot; }
	 *                               - default:           &quot;abort&quot;
	 *                           BUILDER / Cleaning Output Folder(s)
	 *                              Indicate whether the ErlangBuilder is allowed to clean the output folders
	 *                              when performing full build operations.
	 *                               - option id:         &quot;org.erlide.core.erlang.builder.cleanOutputFolder&quot;
	 *                               - possible values:   { &quot;clean&quot;, &quot;ignore&quot; }
	 *                               - default:           &quot;clean&quot;
	 *                           BUILDER / Reporting Duplicate Resources
	 *                              Indicate the severity of the problem reported when more than one occurrence
	 *                              of a resource is to be copied into the output location.
	 *                               - option id:         &quot;org.erlide.core.erlang.builder.duplicateResourceTask&quot;
	 *                               - possible values:   { &quot;error&quot;, &quot;warning&quot; }
	 *                               - default:           &quot;warning&quot;
	 *                           ErlangCORE / Computing Project Build Order
	 *                              Indicate whether ErlangCore should enforce the project build order to be based on
	 *                              the classpath prerequisite chain. When requesting to compute, this takes over
	 *                              the platform default order (based on project references).
	 *                               - option id:         &quot;org.erlide.core.erlang.computeErlangBuildOrder&quot;
	 *                               - possible values:   { &quot;compute&quot;, &quot;ignore&quot; }
	 *                               - default:           &quot;ignore&quot;
	 *                           ErlangCORE / Reporting Incomplete Classpath
	 *                              Indicate the severity of the problem reported when an entry on the classpath does not exist,
	 *                              is not legite or is not visible (for example, a referenced project is closed).
	 *                               - option id:         &quot;org.erlide.core.erlang.incompleteClasspath&quot;
	 *                               - possible values:   { &quot;error&quot;, &quot;warning&quot;}
	 *                               - default:           &quot;error&quot;
	 *                           ErlangCORE / Reporting Classpath Cycle
	 *                              Indicate the severity of the problem reported when a project is involved in a cycle.
	 *                               - option id:         &quot;org.erlide.core.erlang.circularClasspath&quot;
	 *                               - possible values:   { &quot;error&quot;, &quot;warning&quot; }
	 *                               - default:           &quot;error&quot;
	 *                           ErlangCORE / Reporting Incompatible ERTS Level for Required Binaries
	 *                              Indicate the severity of the problem reported when a project prerequisites another project
	 *                              or library with an incompatible target ERTS level (e.g. project targeting R7 vm, but compiled against R10 libraries).
	 *                               - option id:         &quot;org.erlide.core.erlang.incompatibleJDKLevel&quot;
	 *                               - possible values:   { &quot;error&quot;, &quot;warning&quot;, &quot;ignore&quot; }
	 *                               - default:           &quot;ignore&quot;
	 * 
	 * 
	 * 
	 * </pre>
	 * 
	 * @return a mutable table containing the default settings of all known
	 *         options (key type: <code>String</code>; value type:
	 *         <code>String</code>)
	 * @see #setOptions(Hashtable)
	 */
	public static Hashtable<String, String> getDefaultOptions() {

		final Hashtable<String, String> defaultOptions = new Hashtable<String, String>(
				10);

		// see #initializeDefaultPluginPreferences() for changing default
		// settings
		final Preferences preferences = ErlangPlugin.getDefault()
				.getPluginPreferences();
		final HashSet<String> optionNames = getModelManager().getOptionNames();

		// initialize preferences to their default
		final Iterator<String> iterator = optionNames.iterator();
		while (iterator.hasNext()) {
			final String propertyName = iterator.next();
			defaultOptions.put(propertyName, preferences
					.getDefaultString(propertyName));
		}
		// get encoding through resource plugin
		defaultOptions.put(CORE_ENCODING, getEncoding());

		return defaultOptions;
	}

	/**
	 * Returns the workspace root default charset encoding.
	 * 
	 * @return the name of the default charset encoding for workspace root.
	 * @see IContainer#getDefaultCharset()
	 * @see ResourcesPlugin#getEncoding()
	 */
	public static String getEncoding() {
		// Verify that workspace is not shutting down (see bug
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=60687)
		final IWorkspace workspace = ResourcesPlugin.getWorkspace();
		if (workspace != null) {
			try {
				return workspace.getRoot().getDefaultCharset();
			} catch (final CoreException e) {
				// fails silently and return plugin global encoding if core
				// exception occurs
			}
		}
		return ResourcesPlugin.getEncoding();
	}

	/**
	 * Helper method for returning one option value only. Equivalent to
	 * <code>(String)ErlangCore.getOptions().get(optionName)</code> Note that it
	 * may answer <code>null</code> if this option does not exist.
	 * <p>
	 * For a complete description of the configurable options, see
	 * <code>getDefaultOptions</code>.
	 * </p>
	 * 
	 * @param optionName
	 *            the name of an option
	 * @return the String value of a given option
	 * @see ErlangCore#getDefaultOptions()
	 */
	public static String getOption(final String optionName) {

		if (ErlangCore.CORE_ENCODING.equals(optionName)) {
			return getEncoding();
		}
		final String propertyName = optionName;
		if (ErlangCore.getModelManager().getOptionNames()
				.contains(propertyName)) {
			final Preferences preferences = ErlangPlugin.getDefault()
					.getPluginPreferences();
			return preferences.getString(propertyName).trim();
		}
		return null;
	}

	/**
	 * Returns the table of the current options. Initially, all options have
	 * their default values, and this method returns a table that includes all
	 * known options.
	 * <p>
	 * For a complete description of the configurable options, see
	 * <code>getDefaultOptions</code>.
	 * </p>
	 * 
	 * @return table of current settings of all options (key type:
	 *         <code>String</code>; value type: <code>String</code>)
	 * @see ErlangCore#getDefaultOptions()
	 */
	public static Hashtable<String, String> getOptions() {

		final Hashtable<String, String> options = new Hashtable<String, String>(
				10);

		// see #initializeDefaultPluginPreferences() for changing default
		// settings
		final Plugin thePlugin = ErlangPlugin.getDefault();
		if (thePlugin != null) {
			final Preferences preferences = thePlugin.getPluginPreferences();
			final HashSet<String> optionNames = ErlangCore.getModelManager()
					.getOptionNames();

			// initialize preferences to their default
			final Iterator<String> iterator = optionNames.iterator();
			while (iterator.hasNext()) {
				final String propertyName = iterator.next();
				options.put(propertyName, preferences
						.getDefaultString(propertyName));
			}
			// get preferences not set to their default
			final String[] propertyNames = preferences.propertyNames();
			for (final String propertyName : propertyNames) {
				final String value = preferences.getString(propertyName).trim();
				if (optionNames.contains(propertyName)) {
					options.put(propertyName, value);
				}
			}
			// get encoding through resource plugin
			options.put(ErlangCore.CORE_ENCODING, getEncoding());
		}
		return options;
	}

	public static void registerOpenProjects() {
		final IWorkspace root = ResourcesPlugin.getWorkspace();
		final IProject[] projects = root.getRoot().getProjects();

		// String[] nameOfProjects = new String[projects.length];

		for (final IProject project : projects) {
			try {
				if (project.isOpen()
						&& project.hasNature(ErlangPlugin.NATURE_ID)) {
					final ErlangProjectProperties prefs = ErlangCore
							.getProjectProperties(project);
					final String path = project.getLocation().append(
							prefs.getOutputDir()).toString();
					final Backend b = ErlangCore.getBackendManager()
							.getIdeBackend();
					if (b != null) {
						b.addPath(prefs.getUsePathZ(), path);
					}
				}
			} catch (final CoreException e) {
				ErlLogger.warn(e);
			}
		}
	}

	/**
	 * Configures the given marker attribute map for the given Erlang element.
	 * Used for markers, which denote a Erlang element rather than a resource.
	 * 
	 * @param attributes
	 *            the mutable marker attribute map (key type:
	 *            <code>String</code>, value type: <code>String</code>)
	 * @param element
	 *            the Erlang element for which the marker needs to be configured
	 */
	public static void addErlangElementMarkerAttributes(
			final Map<String, String> attributes, final IErlElement element) {
		// if (element instanceof IMember)
		// element = ((IMember) element).getClassFile();
		if (attributes != null && element != null) {
			attributes.put(ErlangCore.ATT_HANDLE_ID, "element handle id");
		}
	}

	/**
	 * Configures the given marker for the given Erlang element. Used for
	 * markers, which denote a Erlang element rather than a resource.
	 * 
	 * @param marker
	 *            the marker to be configured
	 * @param element
	 *            the Erlang element for which the marker needs to be configured
	 * @throws CoreException
	 *             if the <code>IMarker.setAttribute</code> on the marker fails
	 */
	public void configureErlangElementMarker(final IMarker marker,
			final IErlElement element) throws CoreException {
		// if (element instanceof IMember)
		// element = ((IMember) element).getClassFile();
		if (marker != null && element != null) {
			marker.setAttribute(ErlangCore.ATT_HANDLE_ID, "element handle id");
		}
	}

	/**
	 * Runs the given action as an atomic Erlang model operation.
	 * <p>
	 * After running a method that modifies Erlang elements, registered
	 * listeners receive after-the-fact notification of what just transpired, in
	 * the form of a element changed event. This method allows clients to call a
	 * number of methods that modify Erlang elements and only have element
	 * changed event notifications reported at the end of the entire batch.
	 * </p>
	 * <p>
	 * If this method is called outside the dynamic scope of another such call,
	 * this method runs the action and then reports a single element changed
	 * event describing the net effect of all changes done to Erlang elements by
	 * the action.
	 * </p>
	 * <p>
	 * If this method is called in the dynamic scope of another such call, this
	 * method simply runs the action.
	 * </p>
	 * 
	 * @param action
	 *            the action to perform
	 * @param monitor
	 *            a progress monitor, or <code>null</code> if progress reporting
	 *            and cancellation are not desired
	 * @throws CoreException
	 *             if the operation failed.
	 */
	public static void run(final IWorkspaceRunnable action,
			final IProgressMonitor monitor) throws CoreException {
		run(action, ResourcesPlugin.getWorkspace().getRoot(), monitor);
	}

	/**
	 * Runs the given action as an atomic Erlang model operation.
	 * <p>
	 * After running a method that modifies Erlang elements, registered
	 * listeners receive after-the-fact notification of what just transpired, in
	 * the form of a element changed event. This method allows clients to call a
	 * number of methods that modify Erlang elements and only have element
	 * changed event notifications reported at the end of the entire batch.
	 * </p>
	 * <p>
	 * If this method is called outside the dynamic scope of another such call,
	 * this method runs the action and then reports a single element changed
	 * event describing the net effect of all changes done to Erlang elements by
	 * the action.
	 * </p>
	 * <p>
	 * If this method is called in the dynamic scope of another such call, this
	 * method simply runs the action.
	 * </p>
	 * <p>
	 * The supplied scheduling rule is used to determine whether this operation
	 * can be run simultaneously with workspace changes in other threads. See
	 * <code>IWorkspace.run(...)</code> for more details.
	 * </p>
	 * 
	 * @param action
	 *            the action to perform
	 * @param rule
	 *            the scheduling rule to use when running this operation, or
	 *            <code>null</code> if there are no scheduling restrictions for
	 *            this operation.
	 * @param monitor
	 *            a progress monitor, or <code>null</code> if progress reporting
	 *            and cancellation are not desired
	 * @throws CoreException
	 *             if the operation failed.
	 */
	public static void run(final IWorkspaceRunnable action,
			final ISchedulingRule rule, final IProgressMonitor monitor)
			throws CoreException {
		final IWorkspace workspace = ResourcesPlugin.getWorkspace();
		if (workspace.isTreeLocked()) {
			// new BatchOperation(action).run(monitor);
		} else {
			// use IWorkspace.run(...) to ensure that a build will be done in
			// autobuild mode
			// workspace.run(new BatchOperation(action), rule,
			// IWorkspace.AVOID_UPDATE, monitor);
		}
	}

}
