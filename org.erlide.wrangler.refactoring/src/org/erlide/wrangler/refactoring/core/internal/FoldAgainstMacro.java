package org.erlide.wrangler.refactoring.core.internal;

import java.util.ArrayList;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.swt.widgets.Shell;
import org.erlide.wrangler.refactoring.backend.IRefactoringRpcMessage;
import org.erlide.wrangler.refactoring.backend.WranglerBackendManager;
import org.erlide.wrangler.refactoring.backend.internal.ExpressionPosRpcMessage;
import org.erlide.wrangler.refactoring.core.RefactoringWorkflowController;
import org.erlide.wrangler.refactoring.selection.IErlMemberSelection;
import org.erlide.wrangler.refactoring.selection.IErlSelection;
import org.erlide.wrangler.refactoring.util.GlobalParameters;
import org.erlide.wrangler.refactoring.util.IErlRange;

import com.ericsson.otp.erlang.OtpErlangObject;

//TODO test
public class FoldAgainstMacro extends
		CostumWorkflowRefactoringWithPositionsSelection {
	protected OtpErlangObject syntaxTree;

	@Override
	public RefactoringWorkflowController getWorkflowController(Shell shell) {
		return new RefactoringWorkflowController(shell) {
			@Override
			public void doRefactoring() {
			}
		};
	}

	@Override
	public IRefactoringRpcMessage runAlternative(IErlSelection selection) {
		return null;
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		IErlSelection selection = GlobalParameters.getWranglerSelection();

		/*
		 * if (!((selection instanceof IErlMemberSelection) && (selection
		 * .getKind() == SelectionKind.FUNCTION || selection.getKind() ==
		 * SelectionKind.FUNCTION_CLAUSE))) return RefactoringStatus
		 * .createFatalErrorStatus("Please select an expression!");
		 */

		IErlMemberSelection sel = (IErlMemberSelection) selection;
		ExpressionPosRpcMessage m = new ExpressionPosRpcMessage();
		m = (ExpressionPosRpcMessage) WranglerBackendManager
				.getRefactoringBackend().callWithParser(m,
						"fold_against_macro_eclipse", "siixi",
						sel.getFilePath(), sel.getMemberRange().getStartLine(),
						sel.getMemberRange().getStartCol(),
						sel.getSearchPath(), GlobalParameters.getTabWidth());
		if (m.isSuccessful()) {
			syntaxTree = m.getSyntaxTree();
			positions = m.getPositionDefinitions(sel.getDocument());
			selectedPositions = new ArrayList<IErlRange>();
		} else {
			return RefactoringStatus.createFatalErrorStatus(m
					.getMessageString());
		}
		return new RefactoringStatus();
	}

	@Override
	public String getName() {
		return "Fold against macri definition";
	}

	@Override
	public IRefactoringRpcMessage run(IErlSelection selection) {
		IErlMemberSelection sel = (IErlMemberSelection) selection;
		return WranglerBackendManager.getRefactoringBackend().call(
				"fold_against_macro_1_eclipse", "sxxxi", sel.getFilePath(),
				getSelectedPos(), syntaxTree, sel.getSearchPath(),
				GlobalParameters.getTabWidth());
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		IErlSelection sel = GlobalParameters.getWranglerSelection();
		IRefactoringRpcMessage message = run(sel);
		if (message.isSuccessful()) {
			changedFiles = message.getRefactoringChangeset();
			return new RefactoringStatus();
		} else {
			return RefactoringStatus.createFatalErrorStatus(message
					.getMessageString());
		}
	}

}
