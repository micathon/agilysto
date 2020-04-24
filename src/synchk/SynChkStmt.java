package synchk;

import iconst.KeywordTyp;
import iconst.NodeCellTyp;
import page.Node;
import page.Page;
import page.Store;
import scansrc.ScanSrc;
import synchk.SynChk;
import synchk.SynChkExpr;

public class SynChkStmt {

	private ScanSrc scan;
	private Store store;
	private SynChk synChk;
	private SynChkExpr synExpr;
	
	public SynChkStmt(SynChk synChk, ScanSrc scan, Store store) {
		this.synChk = synChk;
		this.scan = scan;
		this.store = store;
	}
	
	public void init() {
		this.synExpr = synChk.synExpr;
	}

	private void out(String msg) {
		scan.out(msg);
	}
	
	private void oerr(int nodep, String msg) {
		synChk.oerr(nodep, msg);
	}
	
	public int chkDo(int rightp) {
		Page page;
		int idx;
		Node node;
		int downp;

		while (rightp > 0) {
			page = store.getPage(rightp);
			idx = store.getElemIdx(rightp);
			node = page.getNode(idx);
			if (!node.isOpenPar()) {  // may never happen
				oerr(rightp, "Do block error (in chkDo): isOpenPar failure");
				return -1;
			}
			out("Here is (");
			downp = node.getDownp();
			if ((downp <= 0) || !doStmt(downp)) {
				return -1;
			}
			out("Here is )");
			rightp = node.getRightp();
		}
		return 0; // OK
	}
	
	private boolean doStmt(int rightp) {
		Page page;
		int idx;
		Node node;
		KeywordTyp kwtyp;
		NodeCellTyp celltyp;

		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		node = page.getNode(idx);
		kwtyp = node.getKeywordTyp();
		celltyp = node.getDownCellTyp();
		out("rightp = " + rightp + ", idx = " + idx + 
				", kwd = " + kwtyp + ", celtyp = " + celltyp);
		out("Statement kwd = " + kwtyp);
		switch (kwtyp) {
		case SET: return doSetStmt(rightp);
		case IF: return doIfStmt(rightp);
		case WHILE: return doWhileStmt(rightp);
		case FOR: return doForStmt(rightp);
		case TRY: return doTryStmt(rightp);
		case DEL: return doDelStmt(rightp);
		case PRINT: return doPrintStmt(rightp);
		case ECHO: return doEchoStmt(rightp);
		case CALL: return doCallStmt(rightp);
		case ZCALL: return doCallFunStmt(rightp);
		case DOT: return doDotStmt(rightp);
		case RAISE: return doRaiseStmt(rightp);
		case CONTINUE: return doContinueStmt(rightp);
		case BREAK: return doBreakStmt(rightp);
		case RETURN: return doReturnStmt(rightp);
		case ADDSET:
		case MINUSSET:
		case MPYSET:
		case DIVSET:
		case IDIVSET:
		case MODSET:
		case SHLSET:
		case SHRSET:
		case SHRUSET:
		case ANDBSET:
		case XORBSET:
		case ORBSET:
		case ANDSET:
		case XORSET:
		case ORSET:
			return doSetOpStmt(rightp);
		default:
			oerr(rightp, "Invalid keyword: " + kwtyp.toString() +
				" encountered at beginning of statement");
			return false;
		}
	}
	
	private boolean doIfStmt(int rightp) {
		Page page;
		int idx;
		Node node;
		boolean isValid;

		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		node = page.getNode(idx);
		rightp = node.getRightp();
		if (rightp <= 0) {
			return true;
		}
		isValid = synExpr.doExpr(rightp);
		//isValid = synExpr.doTargetExpr(rightp);
		if (!isValid) {
			oerr(rightp, "Error found in if stmt. expression");
		}
		return isValid;
	}
	
	private boolean doSetOpStmt(int rightp) {
		Page page;
		int idx;
		Node node;
		String msg = "Error in asst. stmt.: ";
		int savep = rightp;
		
		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		node = page.getNode(idx);
		rightp = node.getRightp();
		if (rightp <= 0) {
			oerr(savep, msg + "no args.");
			return false;
		}
		if (!synExpr.doTargetExpr(rightp)) {
			oerr(savep, msg + "invalid target expr.");
			return false;
		}
		return doSetStmtTail(rightp, savep, msg);
	}
	
	private boolean doSetStmtTail(int rightp, int savep, String msg) {
		Page page;
		int idx;
		Node node;
		
		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		node = page.getNode(idx);
		rightp = node.getRightp();
		if (rightp <= 0) {
			oerr(savep, msg + "missing result expr.");
			return false;
		}
		if (!synExpr.doExpr(rightp)) {
			oerr(savep, msg + "invalid result expr.");
			return false;
		}
		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		node = page.getNode(idx);
		rightp = node.getRightp();
		if (rightp > 0) {
			oerr(savep, msg + "too many args.");
			return false;
		}
		return true;
	}
	
	private boolean doSetStmt(int rightp) {
		Page page;
		int idx;
		Node node;
		KeywordTyp kwtyp;
		NodeCellTyp celltyp;
		String msg = "Error in SET stmt.: ";
		int savep = rightp;
		
		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		node = page.getNode(idx);
		rightp = node.getRightp();
		if (rightp <= 0) {
			oerr(savep, msg + "no args.");
			return false;
		}
		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		node = page.getNode(idx);
		celltyp = node.getDownCellTyp();
		if (celltyp == NodeCellTyp.ID) {
			return doSetOpStmt(savep);
		}
		rightp = synExpr.parenExprRtn(rightp, node); 
		if (rightp <= 0) {
			oerr(savep, msg + "invalid parenthetical arg.");
			return false;
		}
		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		node = page.getNode(idx);
		kwtyp = node.getKeywordTyp();
		if (kwtyp == KeywordTyp.TUPLE) {
			return doSetTuple(savep);
		}
		return doSetOpStmt(savep);
	}
	
	private boolean doSetTuple(int rightp) {
		Page page;
		int idx;
		Node node;
		KeywordTyp kwtyp;
		String msg = "Error in tuple asst. stmt.: ";
		int savep = rightp;
		int rightq;
		int count = 0;
		
		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		node = page.getNode(idx);
		rightp = node.getRightp();
		if (rightp <= 0) {
			oerr(savep, msg + "no args.");
			return false;
		}
		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		node = page.getNode(idx);
		rightq = rightp;
		rightp = synExpr.parenExprRtn(rightp, node); 
		if (rightp <= 0) {
			oerr(savep, msg + "no tuple arg. in parentheses");
			return false;
		}
		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		node = page.getNode(idx);
		kwtyp = node.getKeywordTyp();
		if (kwtyp != KeywordTyp.TUPLE) {
			oerr(savep, msg + "expecting TUPLE keyword, " + kwtyp + " found");
			return false;
		}
		rightp = node.getRightp();
		while (rightp > 0) {
			count++;
			if (!synExpr.doTargetExpr(rightp)) {
				oerr(savep, msg + "invalid target expr.");
				return false;
			}
			page = store.getPage(rightp);
			idx = store.getElemIdx(rightp);
			node = page.getNode(idx);
			rightp = node.getRightp();
		}
		if (count == 0) {
			oerr(savep, msg + "no target exprs.");
			return false;
		}
		return doSetStmtTail(rightq, savep, msg);
	}
	
	private boolean doWhileStmt(int rightp) {
		return true;
	}
	
	private boolean doForStmt(int rightp) {
		return true;
	}
	
	private boolean doTryStmt(int rightp) {
		return true;
	}
	
	private boolean doDelStmt(int rightp) {
		return true;
	}
	
	private boolean doPrintStmt(int rightp) {
		return true;
	}
	
	private boolean doEchoStmt(int rightp) {
		return true;
	}
	
	private boolean doCallStmt(int rightp) {
		return true;
	}
	
	private boolean doCallFunStmt(int rightp) {
		return true;
	}
	
	private boolean doDotStmt(int rightp) {
		return true;
	}
	
	private boolean doRaiseStmt(int rightp) {
		return true;
	}
	
	private boolean doContinueStmt(int rightp) {
		return true;
	}
	
	private boolean doBreakStmt(int rightp) {
		return true;
	}
	
	private boolean doReturnStmt(int rightp) {
		return true;
	}
	
}
