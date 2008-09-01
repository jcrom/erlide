/*******************************************************************************
 * Copyright (c) 2007 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.core.builder;

import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.erlide.runtime.backend.BackendManager;
import org.erlide.runtime.backend.IBackendEventListener;
import org.erlide.runtime.backend.IdeBackend;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangPid;
import com.ericsson.otp.erlang.OtpErlangTuple;

public class ErlangErlBuilder extends IncrementalProjectBuilder implements
		IBackendEventListener {

	@SuppressWarnings("unchecked")
	@Override
	protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
			throws CoreException {

		final IResourceDelta delta = getDelta(getProject());
		if (kind == FULL_BUILD) {
			start_builder();
		} else {
			start_builder(delta);
			// OtpErlangPid builder = (kind == FULL_BUILD) ? start_builder()
			// : start_builder(delta);
		}

		// ?????
		final IdeBackend ideBackend = BackendManager.getDefault()
				.getIdeBackend();
		if (ideBackend != null) {
			ideBackend.addEventListener("build", this);
		}

		while (!monitor.isCanceled()) {

			final OtpErlangObject msg = receive_msg();
			if (msg == null) {
				continue;
			}

			if (msg instanceof OtpErlangAtom) {
				final OtpErlangAtom cmd = (OtpErlangAtom) msg;
				if ("done".equals(cmd.atomValue())) {
					monitor.done();
					break;
				}
			}
			if (msg instanceof OtpErlangTuple) {
				// OtpErlangTuple event = (OtpErlangTuple) msg;

			}

		}

		return null;
	}

	private OtpErlangPid start_builder(IResourceDelta delta) {
		// TODO Auto-generated method stub
		return null;
	}

	private OtpErlangPid start_builder() {
		// TODO Auto-generated method stub
		return null;
	}

	private OtpErlangObject receive_msg() {
		return null;
	}

	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		// TODO Auto-generated method stub
		super.clean(monitor);
	}

	public void eventReceived(OtpErlangObject event) {
		// TODO Auto-generated method stub

	}

}