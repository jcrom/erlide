/*******************************************************************************
 * Copyright (c) 2004 Eric Merritt and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/

package org.erlide.runtime;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.erlide.core.ErlangPlugin;
import org.erlide.core.erlang.ErlangCore;
import org.erlide.runtime.backend.BuildBackend;
import org.erlide.runtime.backend.IBackend;
import org.erlide.runtime.backend.RuntimeInfo;
import org.erlide.runtime.backend.exceptions.BackendException;
import org.osgi.service.prefs.BackingStoreException;

public class ErlangProjectProperties {

	private static final String PATH_SEP = ";";

	private IProject project;

	private String fSourceDirs = ProjectPreferencesConstants.DEFAULT_SOURCE_DIRS;
	private String fUsePathZ = ProjectPreferencesConstants.DEFAULT_USE_PATHZ;
	private String fOutputDir = ProjectPreferencesConstants.DEFAULT_OUTPUT_DIR;
	private String fIncludeDirs = ProjectPreferencesConstants.DEFAULT_INCLUDE_DIRS;
	private String fExternalIncludes = ProjectPreferencesConstants.DEFAULT_EXTERNAL_INCLUDES;
	private String fExternalModules = ProjectPreferencesConstants.DEFAULT_EXTERNAL_MODULES;

	private String fRuntimeName;
	private String fNodeName;
	private String fCookie;

	private boolean fUnique = true;

	public enum ProjectType {
		NORMAL, REFERENCE, OTP
	};

	private ProjectType type = ProjectType.NORMAL;

	/**
	 * Name of file containing project classpath
	 */
	public static final String CODEPATH_FILENAME = ".codepath"; //$NON-NLS-1$

	public ErlangProjectProperties() {
		fRuntimeName = ProjectPreferencesConstants.DEFAULT_RUNTIME_NAME;
	}

	public ErlangProjectProperties(final IProject prj) {
		this(prj, ProjectType.NORMAL);
	}

	public ErlangProjectProperties(final IProject prj, final ProjectType type) {
		super();
		project = prj;
		// TODO load() should not be in constructor!
		load();
		this.type = type;
	}

	public ErlangProjectProperties load() {
		if (project == null) {
			return this;
		}

		// if .codepath exists, read from it, otherwise from .settings

		boolean loaded = false;
		final IFile cp = project.getFile(CODEPATH_FILENAME);
		if (cp.exists()) {
			final File codepath = cp.getRawLocation().toFile();
			final Properties prefs = new Properties();
			FileInputStream stream;
			try {
				stream = new FileInputStream(codepath);
				prefs.load(stream);
				stream.close();
				loaded = true;
			} catch (final FileNotFoundException e) {
				ErlLogger.warn(e);
			} catch (final IOException e) {
				ErlLogger.warn(e);
			}

			fSourceDirs = prefs.getProperty(
					ProjectPreferencesConstants.SOURCE_DIRS,
					ProjectPreferencesConstants.DEFAULT_SOURCE_DIRS);
			fIncludeDirs = prefs.getProperty(
					ProjectPreferencesConstants.INCLUDE_DIRS,
					ProjectPreferencesConstants.DEFAULT_INCLUDE_DIRS);
			fOutputDir = prefs.getProperty(
					ProjectPreferencesConstants.OUTPUT_DIR,
					ProjectPreferencesConstants.DEFAULT_OUTPUT_DIR);
			fUsePathZ = prefs.getProperty(
					ProjectPreferencesConstants.USE_PATHZ,
					ProjectPreferencesConstants.DEFAULT_USE_PATHZ);
			fRuntimeName = prefs.getProperty(
					ProjectPreferencesConstants.RUNTIME_NAME,
					ProjectPreferencesConstants.DEFAULT_RUNTIME_NAME);
			fNodeName = prefs.getProperty(
					ProjectPreferencesConstants.NODE_NAME,
					ProjectPreferencesConstants.DEFAULT_NODENAME);
			fUnique = Boolean.parseBoolean(prefs.getProperty(
					ProjectPreferencesConstants.MK_UNIQUE, "true"));
			fCookie = prefs.getProperty(ProjectPreferencesConstants.COOKIE,
					ProjectPreferencesConstants.DEFAULT_COOKIE);
			fExternalModules = prefs.getProperty(
					ProjectPreferencesConstants.PROJECT_EXTERNAL_MODULES,
					ProjectPreferencesConstants.DEFAULT_EXTERNAL_MODULES);
			fExternalIncludes = prefs.getProperty(
					ProjectPreferencesConstants.EXTERNAL_INCLUDES,
					ProjectPreferencesConstants.DEFAULT_EXTERNAL_INCLUDES);
			type = ProjectType.valueOf(prefs.getProperty(
					ProjectPreferencesConstants.PROJECT_TYPE,
					ProjectPreferencesConstants.DEFAULT_PROJECT_TYPE));
		}
		if (!loaded) {
			// ErlLogger.debug("project %s, loading from .settings", project
			// .getName());

			final ProjectScope s = new ProjectScope(project);
			final IEclipsePreferences node = s.getNode(ErlangPlugin.PLUGIN_ID);

			// new settings
			fSourceDirs = node.get(ProjectPreferencesConstants.SOURCE_DIRS,
					ProjectPreferencesConstants.DEFAULT_SOURCE_DIRS);
			fIncludeDirs = node.get(ProjectPreferencesConstants.INCLUDE_DIRS,
					ProjectPreferencesConstants.DEFAULT_INCLUDE_DIRS);
			fOutputDir = node.get(ProjectPreferencesConstants.OUTPUT_DIR,
					ProjectPreferencesConstants.DEFAULT_OUTPUT_DIR);
			fUsePathZ = node.get(ProjectPreferencesConstants.USE_PATHZ,
					ProjectPreferencesConstants.DEFAULT_USE_PATHZ);
			fRuntimeName = node.get(ProjectPreferencesConstants.RUNTIME_NAME,
					ProjectPreferencesConstants.DEFAULT_RUNTIME_NAME);
			fNodeName = node.get(ProjectPreferencesConstants.NODE_NAME,
					ProjectPreferencesConstants.DEFAULT_NODENAME);
			fUnique = Boolean.parseBoolean(node.get(
					ProjectPreferencesConstants.MK_UNIQUE, "true"));
			fCookie = node.get(ProjectPreferencesConstants.COOKIE,
					ProjectPreferencesConstants.DEFAULT_COOKIE);
			fExternalModules = node.get(
					ProjectPreferencesConstants.PROJECT_EXTERNAL_MODULES,
					ProjectPreferencesConstants.DEFAULT_EXTERNAL_MODULES);
			fExternalIncludes = node.get(
					ProjectPreferencesConstants.EXTERNAL_INCLUDES,
					ProjectPreferencesConstants.DEFAULT_EXTERNAL_INCLUDES);
			type = ProjectType.valueOf(node.get(
					ProjectPreferencesConstants.PROJECT_TYPE,
					ProjectPreferencesConstants.DEFAULT_PROJECT_TYPE));
		}
		return this;
	}

	public void store() {
		if (project == null) {
			return;
		}

		// save in .settings
		// ErlLogger.debug("project %s, saving to .settings",
		// project.getName());

		final ProjectScope s = new ProjectScope(project);
		final IEclipsePreferences node = s.getNode(ErlangPlugin.PLUGIN_ID);

		node.put(ProjectPreferencesConstants.SOURCE_DIRS, fSourceDirs);
		node.put(ProjectPreferencesConstants.INCLUDE_DIRS, fIncludeDirs);
		node.put(ProjectPreferencesConstants.OUTPUT_DIR, fOutputDir);
		node.put(ProjectPreferencesConstants.USE_PATHZ, fUsePathZ);
		node.put(ProjectPreferencesConstants.EXTERNAL_INCLUDES,
				fExternalIncludes);
		node.put(ProjectPreferencesConstants.RUNTIME_NAME, fRuntimeName);
		node.put(ProjectPreferencesConstants.NODE_NAME, fNodeName);
		node.put(ProjectPreferencesConstants.MK_UNIQUE, Boolean
				.toString(fUnique));
		node.put(ProjectPreferencesConstants.COOKIE, fCookie);
		node.put(ProjectPreferencesConstants.PROJECT_EXTERNAL_MODULES,
				fExternalModules);
		node.put(ProjectPreferencesConstants.PROJECT_TYPE, type.toString());

		try {
			node.flush();
		} catch (final BackingStoreException e1) {
		}

		final IFile cp = project.getFile(CODEPATH_FILENAME);
		if (cp.exists()) {
			// save in .codepath
			// ErlLogger.debug("project %s, saving to .codepath", project
			// .getName());

			final File codepath = cp.getRawLocation().toFile();
			final Properties prefs = new Properties();

			prefs.put(ProjectPreferencesConstants.SOURCE_DIRS, fSourceDirs);
			prefs.put(ProjectPreferencesConstants.INCLUDE_DIRS, fIncludeDirs);
			prefs.put(ProjectPreferencesConstants.OUTPUT_DIR, fOutputDir);
			prefs.put(ProjectPreferencesConstants.USE_PATHZ, fUsePathZ);
			prefs.put(ProjectPreferencesConstants.EXTERNAL_INCLUDES,
					fExternalIncludes);
			prefs.put(ProjectPreferencesConstants.RUNTIME_NAME, fRuntimeName);
			prefs.put(ProjectPreferencesConstants.NODE_NAME, fNodeName);
			prefs.put(ProjectPreferencesConstants.MK_UNIQUE, Boolean
					.toString(fUnique));
			prefs.put(ProjectPreferencesConstants.COOKIE, fCookie);
			prefs.put(ProjectPreferencesConstants.PROJECT_EXTERNAL_MODULES,
					fExternalModules);
			prefs
					.put(ProjectPreferencesConstants.PROJECT_TYPE, type
							.toString());

			try {
				final FileOutputStream out = new FileOutputStream(codepath);
				try {
					prefs.store(out, null);
				} finally {
					out.close();
				}
			} catch (final IOException e) {
			}
		}
	}

	public String getIncludeDirsString() {
		return fIncludeDirs;
	}

	public void setIncludeDirsString(final String includeDirs) {
		fIncludeDirs = includeDirs;
	}

	public String[] getIncludeDirs() {
		return unpack(fIncludeDirs);
	}

	public void setIncludeDirs(final String[] includeDirs) {
		fIncludeDirs = pack(includeDirs);
	}

	public String getOutputDir() {
		return fOutputDir;
	}

	public void setOutputDir(final String outputDir) {
		if (!fOutputDir.equals(outputDir)) {
			try {
				final BuildBackend b = ErlangCore.getBackendManager()
						.getBuildBackend(project);
				String p = project.getLocation().append(fOutputDir).toString();
				b.removePath(getUsePathZ(), p);

				p = project.getLocation().append(outputDir).toString();
				b.addPath(getUsePathZ(), p);
			} catch (final BackendException e) {
				ErlLogger.info(e);
			}

		}
		fOutputDir = outputDir;
	}

	public boolean getUsePathZ() {
		return Boolean.parseBoolean(fUsePathZ);
	}

	public void setUsePathZ(final boolean pz) {
		final boolean z = Boolean.parseBoolean(fUsePathZ);
		if (z != pz) {
			for (final IBackend b : ErlangCore.getBackendManager()
					.getExecution(project)) {

				final String p = project.getLocation().append(fOutputDir)
						.toString();
				b.removePath(z, p);
				b.addPath(pz, p);
			}
		}
		fUsePathZ = Boolean.toString(pz);
	}

	public String getSourceDirsString() {
		return fSourceDirs;
	}

	public void setSourceDirsString(final String sourceDirs) {
		fSourceDirs = sourceDirs;
	}

	public String[] getSourceDirs() {
		return unpack(fSourceDirs);
	}

	public void setSourceDirs(final String[] sourceDirs) {
		fSourceDirs = pack(sourceDirs);
	}

	public String buildCommandLine() {
		if (project != null) {
			final String incs = buildIncludeDirs(getIncludeDirs());
			return " -pa " + project.getLocation().append(fOutputDir) + incs;
		}
		return "";
	}

	public String buildIncludeDirs(final String[] dirs) {
		final StringBuilder incs = new StringBuilder();
		for (final String element : dirs) {
			final IPath loc = project.getLocation();
			IPath inc = new Path(element);
			ErlLogger.debug("* " + inc);
			if (!inc.isAbsolute()) {
				ErlLogger.debug("  not abs!");
				inc = loc.append(inc);
				ErlLogger.debug("  " + inc);
			}
			incs.append(" -I").append(inc.toString());
		}
		return incs.toString();
	}

	public void copyFrom(final ErlangProjectProperties bprefs) {
		fIncludeDirs = bprefs.fIncludeDirs;
		fSourceDirs = bprefs.fSourceDirs;
		fOutputDir = bprefs.fOutputDir;
		fRuntimeName = "";
	}

	public static String pack(final String[] strs) {
		final StringBuilder b = new StringBuilder();
		for (int i = 0; i < strs.length; i++) {
			if (strs[i] != null && strs[i].length() > 0) {
				b.append(strs[i]);
				b.append(PATH_SEP);
			}
		}
		if (b.length() > 0) {
			b.deleteCharAt(b.length() - 1);
		}
		return b.toString();
	}

	public String[] getExternalIncludes() {
		return unpack(fExternalIncludes);
	}

	private String[] unpack(final String str) {
		final String[] res = str.split(PATH_SEP);
		for (int i = 0; i < res.length; i++) {
			res[i] = res[i].trim();
		}
		return res;
	}

	public String getExternalIncludesString() {
		return fExternalIncludes;
	}

	public void setExternalIncludes(final String[] externalIncludes) {
		final String packed = pack(externalIncludes);
		setExternalIncludes(packed);
	}

	/**
	 * @param packed
	 */
	public void setExternalIncludes(final String packed) {
		fExternalIncludes = packed;
	}

	public IProject getProject() {
		return project;
	}

	public void setRuntimeName(final String backendName) {
		// TODO validate!
		fRuntimeName = backendName;
	}

	public void setExternalModules(final String fExternalModules) {
		this.fExternalModules = fExternalModules;
	}

	public String getExternalModules() {
		return fExternalModules;
	}

	public String getRuntimeName() {
		return fRuntimeName;
	}

	public RuntimeInfo getRuntimeInfo() {
		final RuntimeInfo rt = RuntimeInfo.copy(ErlangCore
				.getRuntimeInfoManager().getRuntime(fRuntimeName), false);
		if (rt != null) {
			rt.setNodeName(fNodeName);
			rt.setUniqueName(fUnique);
			rt.setCookie(fCookie);
		}
		return rt;
	}

	public void setCookie(final String text) {
		fCookie = text.trim();
	}

	public String getCookie() {
		return fCookie;
	}

	public String getNodeName() {
		return fNodeName;
	}

	public void setNodeName(final String text) {
		fNodeName = text.trim();
	}

	public void setUniqueName(final boolean unique) {
		fUnique = unique;
	}

	public boolean isUniqueName() {
		return fUnique;
	}

	public boolean hasSourceDir(final IPath fullPath) {
		final String f = fullPath.removeFirstSegments(1).toString();
		for (final String s : getSourceDirs()) {
			if (s.equals(f)) {
				return true;
			}
		}
		return false;
	}

	public boolean isOtp() {
		return type == ProjectType.OTP;
	}

	public boolean isReference() {
		return type != ProjectType.NORMAL;
	}

	public boolean isNormal() {
		return type == ProjectType.NORMAL;
	}

	public void setType(final ProjectType type) {
		this.type = type;
	}

}
