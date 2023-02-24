package runtm;

import iconst.IConst;
import iconst.RunConst;
import iconst.KeywordTyp;
import iconst.NodeCellTyp;
import iconst.TokenTyp;
import iconst.PageTyp;
import page.Node;
import page.AddrNode;
import page.Store;
import page.Page;
import scansrc.ScanSrc;
import synchk.SynChk;
import java.util.HashMap;
import java.util.ArrayList;

// Code Execution

public class RunTime implements IConst, RunConst {

	private Store store;
	private ScanSrc scanSrc;
	private SynChk synChk;
	private RunScanner rscan;
	private RunCall rc;
	private RunPushPop pp;
	private RunOperators runop;
	private int locBaseIdx;
	private int varCountIdx;
	private int stmtCount;
	private int popMultiFreeCount;
	private int currZstmt;
	private int currZexpr;
	private int locDepth;
	private boolean isTgtExpr;
	private boolean isCalcExpr;
	private boolean isExprLoop;
	private boolean isNegInt;
	private int lastErrCode;
	private int utKeyValIdx;
	private boolean isBadUtPair;
	private int runidx;
	private static final char SP = ' ';
	public HashMap<String, Integer> glbFunMap;
	public HashMap<String, Integer> glbLocVarMap;
	public ArrayList<Integer> glbFunList;
	public ArrayList<Integer> glbLocVarList;
	public ArrayList<String> utKeyValList;

	public RunTime(Store store, ScanSrc scanSrc, SynChk synChk) {
		this.store = store;
		this.scanSrc = scanSrc;
		this.synChk = synChk;
		pp = new RunPushPop(store, this);
		rc = new RunCall(store, this, pp);
		runop = new RunOperators(store, this, pp);
		locBaseIdx = 0;
		varCountIdx = 0;
		stmtCount = 0;
		locDepth = 0;
		lastErrCode = 0;
		popMultiFreeCount = 0;
		utKeyValIdx = 0;
		isTgtExpr = false;
		isCalcExpr = false;
		isExprLoop = false;
		glbFunMap = new HashMap<String, Integer>();
		glbLocVarMap = new HashMap<String, Integer>();
		glbFunList = new ArrayList<Integer>();
		glbLocVarList = new ArrayList<Integer>();
		utKeyValList = new ArrayList<String>();
	}
	
	public void setRscan(RunScanner rscan) {
		this.rscan = rscan;
	}

	public void out(String msg) {
		rscan.out(msg);
	}
	
	public void omsg(String msg) {  
		rscan.omsg(msg);
	}
	
	public void oprn(String msg) {  
		rscan.oprn(msg);
	}
	
	public void oerr(String msg) {  
		rscan.omsg(msg);
	}
	
	public boolean isBadUpFlag() {
		return isBadUtPair;
	}
	
	public int getLocBaseIdx() {
		return locBaseIdx;
	}
	
	public void setLocBaseIdx(int idx) {
		locBaseIdx = idx;
		pp.setLocBaseIdx(idx);
	}
	
	public void setNegInt(boolean flag) {
		isNegInt = flag;
	}
	
	public void setLastErrCode(int errCode) {
		lastErrCode = errCode;
	}
	
	public int getPopMultiFreeCount() {
		return popMultiFreeCount;
	}
	
	public void setPopMultiFreeCount(int count) {
		popMultiFreeCount = count;
	}
	
	public void incPopMultiFreeCount() {
		popMultiFreeCount++;
	}
	
	public boolean runTopBlock(int rightp, int runidx) {
		// process top-level stmts.:
		// do ( stmt-1; .. stmt-n; )
		Node node;
		int downp;
		KeywordTyp kwtyp;
		NodeCellTyp celltyp;
		int phaseNo = 0;
		boolean rtnval;

		this.runidx = runidx;
		isBadUtPair = false;
		while (rightp != 0) {
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			celltyp = node.getDownCellTyp();
			out("rightp = " + rightp +  
				", kwd = " + kwtyp + ", celtyp = " + celltyp);
			if (kwtyp != KeywordTyp.ZSTMT) {
				return false;
			}
			if (node.isOpenPar()) {
				out("Here is (");
				downp = node.getDownp();
				phaseNo = runTopStmt(downp, phaseNo);
				if (phaseNo < 0) {
					return false;
				}
				out("Here is )");
			}
			else {
				return false;
			}
			rightp = node.getRightp();
			if (phaseNo < 0) {
				return false;
			}
		}
		//rtnval = !isBadUtPair;
		rtnval = true;
		return rtnval;
	}
	
	private int runTopStmt(int rightp, int phaseNo) {
		// process top-level statement
		// return phase no. of current stmt.: 0=quest, 1=import,
		//   gdefun, functions, 4=classes
		// return -1 on error
		Node node;
		KeywordTyp kwtyp = null;
		NodeCellTyp celltyp;
		boolean first = true;
		int currPhaseNo = phaseNo;
		int rightq;

		while (rightp > 0) {
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			celltyp = node.getDownCellTyp();
			out("rightp = " + rightp +  
					", kwd = " + kwtyp + ", celtyp = " + celltyp);
			if (first) {
				// at keyword token, beginning of top-level stmt.
				out("Statement kwd = " + kwtyp);
				currPhaseNo = synChk.getPhaseNo(kwtyp);
				rightq = rightp;
				rightp = node.getRightp();
				if (rightp <= 0) {
					return -1;
				}
				// rightp > 0 inside following switch
				switch (currPhaseNo) {
				case 0:
					return -1;
				case 1:
				case 3:
				case 4:
					rightq = rightp;
					break;
				case 2: // only called once
					rightq = runGlbDefStmt(rightp);
					break;
				default:
					rightq = -1;
				}
				if (rightq > 0) {
					rightp = rightq;
				}
				else {
					return -1;
				}
			}
			rightp = node.getRightp();
			first = false;
		}
		return currPhaseNo;
	}
	
	private int runGlbDefStmt(int rightp) {
		Node node;
		KeywordTyp kwtyp;
		int savep = rightp;

		omsg("Keyword gdefun detected again.");
		rightp = rscan.scopeGlbVarLists(rightp);
		if (rightp <= 0) {
			omsg("runGlbDefStmt: bad var list");
			return -1;
		}
		node = store.getNode(rightp);
		kwtyp = node.getKeywordTyp();
		if (kwtyp != KeywordTyp.DO) {
			omsg("Missing DO");
			return -1;
		}
		if (runGlbCall() < 0) {
			handleErrToken(STKOVERFLOW);
			return -1;
		}
		if (!pushInt(currZstmt)) {
			return STKOVERFLOW;
		}
		if (!pushInt(locDepth)) {
			return STKOVERFLOW;
		}
		// currently only called once, in body of gdefun stmt.
		rightp = handleDoBlock(node);
		omsg("Stmt count = " + stmtCount);
		if (rightp < EXIT) {
			handleErrToken(rightp);
			return -1;
		}
		if (rightp == 0) {
			omsg("handleDoBlock rtn = 0");  // done
		}
		omsg("runGlbDefStmt: popFreeCount = " + popMultiFreeCount);
		omsg("runGlbDefStmt: btm");
		return savep;
	}
	
	private void handleErrToken(int rightp) {
		oprn("Runtime Error: " + convertErrToken(rightp));
	}
	
	private String convertErrToken(int rightp) {
		switch (rightp) {
		case NEGADDR: return "Nonpositive pointer encountered";
		case STKOVERFLOW: return "Stack overflow";
		case STKUNDERFLOW: return "Stack underflow";
		case BADSTMT: return "Unsupported stmt. type";
		case BADOP: return "Unsupported operator";
		case BADCELLTYP: return "Unsupported var/const type";
		case BADALLOC: return "Memory allocation failure";
		case BADPOP: return "Error in pop operation";
		case ZERODIV: return "Divide by zero";
		case KWDPOPPED: return "Keyword popped unexpectedly";
		case BADINTVAL: return "Integer data expected after pop operation";
		case STMTINEXPR: return "Statement encountered in expression";
		case BADZSTMT: return "Expression encountered at stmt. level";
		case BADSETSTMT: return "Malformed SET statement";
		case BADPARMCT: return "Mismatched parameter count";
		case RTNISEMPTY: return "Return stmt. lacks value";
		case BADUTSTMT: return "Malformed unit test stmt.";
		case BADTYPE: return "Unexpected data type";
		case BADFREE: return "Memory free failure";
		case BADOPTYP: return "Invalid operand type";
		case GENERR: return "General runtime error";
		default: return "Error code = " + (-rightp);
		}
	}
	
	private int handleDoBlock(Node node) {	
		// currently only called once, in body of gdefun stmt.
		AddrNode zeroNode;
		KeywordTyp kwtyp;
		int rightp;

		// return 0 if done (anywhere in this fn)
		// Note that a rightp value of zero means points to nothing,
		//   everywhere in the coop project
		rightp = node.getDownp();
		if (rightp <= 0) {
			return 0;
		}
		pushOp(KeywordTyp.NULL);
		zeroNode = new AddrNode(0, 0);
		store.pushNode(zeroNode);
		while (rightp > 0) {
			// handle stmt.
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			omsg("doblock: kwtyp = " + kwtyp + ", rightp = " + rightp);
			if (kwtyp != KeywordTyp.ZSTMT) {
				return BADZSTMT;
			}
			stmtCount++;
			omsg("doblock: stmtCount = " + stmtCount);
			currZstmt = rightp;
			locDepth = 0;
			rightp = pushStmt(node);  // scan stmt., push it
			do {
				isExprLoop = false;
				// handle mult. exprs.
				rightp = handleExprToken(rightp, false);
				if (rightp >= 0) {
					return BADOP;
				}
				if (rightp == EXIT) {
					return 0;
				}
				if (rightp > NEGBASEVAL) {  // error
					return rightp;
				}
				// run stmt. using kwd. (encoded in -ve rightp)
				rightp = -(rightp - NEGBASEVAL);
				kwtyp = KeywordTyp.values[rightp];
				omsg("handleDoBlock: btm2, kwtyp = " + kwtyp);
				rightp = handleStmtKwd(kwtyp);
				omsg("handleDoBlock: btm2, rightp = " + rightp);
			} while (isExprLoop);  // keep going if return stmt...
			// unless depth counter is zero, ends up here:
			while (rightp == 0) {
				rightp = runRtnStmt(false);
				omsg("handleDoBlock: btm, rightp = " + rightp);
				if (rightp == EXIT) {
					return 0;
				}
			}
		} 
		return rightp;  // always -ve
	}
	
	private int handleExprToken(int rightp, boolean isSingle) {	
		// handle single/mult. expr(s).
		KeywordTyp kwtyp;
		KeywordTyp kwtop;
		Node node;
		boolean found = false;
		
		// currently isSingle on single expr. end of push set/return
		omsg("exprtok: top, rightp = " + rightp);
		while (rightp >= 0) {
			while ((rightp <= 0) && !found) {  
				// rightp = 0, not found yet
				if (store.isOpStkEmpty()) {
					omsg("exprtok: top of while, empty op stk");
					return STKUNDERFLOW;
				}
				found = isSingle && (locDepth <= 0);
				kwtyp = popKwd();
				if (kwtyp == KeywordTyp.NULL && store.isOpStkEmpty()) {
					return EXIT;
				}
				omsg("exprtok: kwtyp popped = " + kwtyp);
				if (kwtyp == KeywordTyp.ZCALL && locDepth > 0) {  
					omsg("exprtok: function call, locDepth = " + locDepth);
				}
				rightp = handleExprKwd(kwtyp);
				omsg("exprtok: kwtyp = " + kwtyp + ", rightp = " + rightp);
				if (rightp < 0) {
					return rightp;  // err if > NEGBASEVAL,
					// else stmt. kwd. encoded in -ve rightp
				}
				locDepth--;
			}
			// rightp > 0 or found
			if (found) {  // isSingle and depth at bottom
				break;
			}
			// start here upon rightp > 0
			omsg("exprtok: node -> rightp = " + rightp);
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			omsg("exprtok: btm kwtyp = " + kwtyp + ", rightp = " + rightp);
			if (kwtyp == KeywordTyp.ZSTMT) {
				return STMTINEXPR;
			}
			// handling expr.
			kwtop = topKwd();
			omsg("exprtok: kwtop = " + kwtop);
			if (isLogicalKwd(kwtop)) {
				rightp = handleLogicalKwd(kwtop, rightp);
				omsg("exprtok: hlogkw-> rightp = " + rightp);
			}
			if (rightp > 0) {
				rightp = pushExprOrLeaf(node, rightp);
				found = isSingle && (locDepth <= 0);
			}
		} 
		return rightp;
	}
	
	private int pushExprOrLeaf(Node node, int rightp) {
		KeywordTyp kwtyp;
		kwtyp = node.getKeywordTyp();
		if (kwtyp == KeywordTyp.ZPAREN) {
			locDepth++;
			currZexpr = rightp;
			rightp = pushExpr(node);
		}
		else {
			rightp = handleLeafToken(node);
		}
		return rightp;
	}

	private int handleLogicalKwd(KeywordTyp kwtop, int rightp) {
		Node node;
		AddrNode addrNode;
		PageTyp pgtyp;
		int ival, jval;
		boolean isShortCircuit;
		
		isShortCircuit = false;
		addrNode = store.topNode();
		pgtyp = addrNode.getHdrPgTyp();
		if (pgtyp == PageTyp.KWD) {
			ival = topIntVal();  // = -1
			jval = 0;
			omsg("hlogkw: KWD ival = " + ival);
		}
		else if (kwtop != KeywordTyp.QUEST) {
			addrNode = store.popNode();
			if (addrNode == null) {
				return STKUNDERFLOW;
			}
			if (addrNode.getHdrPgTyp() != PageTyp.BOOLEAN) {
				return BADOPTYP; 
			}
			jval = popIntStkRtn(addrNode);  // WRONG!!!!!
			ival = topIntVal();  // = 0 or 1
			omsg("hlogkw: top ival = " + ival + ", jval = " + jval);
		}
		else {
			addrNode = store.popNode();
			if (addrNode == null) {
				return STKUNDERFLOW;
			}
			ival = topIntVal(); 
			if (ival < 0) {
				if (store.popNode() == null) {  // pop -1
					return STKUNDERFLOW;
				}
				else if (!pushKwdVal(0)) {
					return STKOVERFLOW;
				}
				// about to push boolean of QUEST
			}
			else if (ival == 0) {  // discard boolean on top
				if (addrNode.getHdrPgTyp() != PageTyp.BOOLEAN) {
					return BADOPTYP; 
				}
				if (store.popNode() == null) {  // pop 0 kwd
					return STKUNDERFLOW;
				}
				jval = addrNode.getAddr();  //!!!!!!! 0 or 1
				ival = jval + 1;
				if (!pushKwdVal(ival)) {  // push 1 or 2 kwd
					return STKOVERFLOW;
				}
				// about to push 1st expr
			}
			else if (ival == 1) {
				// skip 1st expr of QUEST (discard 1st expr)
				if (store.popNode() == null) {  // pop 1 kwd
					return STKUNDERFLOW;
				}
				node = store.getNode(rightp);
				rightp = node.getRightp();
				return rightp;
				// about to push 2nd expr
			}
			else {  // ival = 2
				if (store.popNode() == null) {  // pop 2 kwd
					return STKUNDERFLOW;
				}
				// push back 2nd expr
				if (!store.pushNode(addrNode)) {  
					return STKOVERFLOW;
				}
			}
			return rightp;
		}
		switch (kwtop) {
		case AND:
			omsg("hlogkw: AND");
			if (ival >= 0) {
				isShortCircuit = 
					((ival == 0) || (jval == 0));
			}
			else if (store.popNode() == null) {
				return STKUNDERFLOW;
			}
			else if (!pushKwdVal(1)) {
				return STKOVERFLOW;
			}
			break;
		case OR:
			omsg("hlogkw: OR, ival = " + ival);
			if (ival >= 0) {
				isShortCircuit = 
					((ival == 1) || (jval == 1));
			}
			else if (store.popNode() == null) {
				return STKUNDERFLOW;
			}
			else if (!pushKwdVal(0)) {
				return STKOVERFLOW;
			}
			break;
		default:
			break;
		}
		if (pgtyp != PageTyp.KWD) {
			if (!store.pushNode(addrNode)) {
				return STKOVERFLOW;
			}
		}
		if (isShortCircuit) {
			// skip over calling getRightp until zero:
			while (rightp > 0) {
				node = store.getNode(rightp);
				rightp = node.getRightp();
			}
		}
		return rightp;
	}		
	
	private int handleLeafToken(Node node) {
		return handleLeafTokenRtn(node, false);
	}
	
	private int handleLeafTokenQuote(Node node) {
		return handleLeafTokenRtn(node, true);
	}
	
	private int handleLeafTokenRtn(Node node, boolean isQuote) {
		NodeCellTyp celltyp;
		Page page;
		int idx, varidx;
		int downp;
		int rightp;
		int ival, rtnval;
		long longval;
		double dval;
		String sval;

		varidx = node.getDownp();
		celltyp = node.getDownCellTyp();
		rightp = node.getRightp();
		omsg("htok: celltyp = " + celltyp + ", downp = " + varidx +
			", rightp = " + rightp);
		if (isQuote) {
			switch (celltyp) {
			case ID:
			case LOCVAR:
				if (!pushVarQuote(varidx)) {
					return STKOVERFLOW;
				}
				return rightp;
			default:
				return BADCELLTYP;
			}
		}
		switch (celltyp) {
		case ID:
		case LOCVAR:
			if (!pushVar(varidx)) {
				return STKOVERFLOW;
			}
			break;
		case FUNC:
			if (!pushInt(varidx)) {
				return STKOVERFLOW;
			}
			break;
		case INT:
			ival = varidx;
			omsg("htok: push INT = " + ival);
			if (!pushIntStk(ival)) {
				return STKOVERFLOW;
			}
			break;
		case BOOLEAN:
			ival = varidx;
			omsg("htok: push BOOL = " + ival);
			if (!pushBoolStk(ival)) {
				return STKOVERFLOW;
			}
			break;
		case LONG:	
			downp = node.getDownp();
			page = store.getPage(downp);
			idx = store.getElemIdx(downp);
			longval = page.getLong(idx);
			rtnval = pushLong(longval);
			omsg("htok: long = " + longval);
			if (rtnval < 0) {
				return rtnval;
			}
			break;
		case FLOAT:	
			downp = node.getDownp();
			page = store.getPage(downp);
			idx = store.getElemIdx(downp);
			dval = page.getFloat(idx);
			rtnval = pushFloat(dval);
			omsg("htok: float = " + dval);
			if (rtnval < 0) {
				return rtnval;
			}
			break;
		case STRING:	
			downp = node.getDownp();
			page = store.getPage(downp);
			idx = store.getElemIdx(downp);
			sval = page.getString(idx);
			rtnval = pushString(sval);
			omsg("htok: string = " + sval);
			if (rtnval < 0) {
				return rtnval;
			}
			break;
		default: return BADCELLTYP;
		}
		rightp = node.getRightp();
		return rightp;
	}
	
	private int handleExprKwd(KeywordTyp kwtyp) {
		int rightp;
		AddrNode addrNode;
		
		rightp = runop.handleExprKwdRtn(kwtyp);
		if (rightp < 0) {
			return rightp;
		}
		if (!store.swapNodes()) {
			return STKUNDERFLOW;
		}
		addrNode = store.popNode();
		rightp = addrNode.getAddr();
		return rightp;
	}
	
	private int handleStmtKwd(KeywordTyp kwtyp) {
		int rightp;
		AddrNode addrNode;
		
		rightp = handleStmtKwdRtn(kwtyp);
		if ((rightp < 0) || isJumpKwd(kwtyp)) {
			return rightp;
		}
		addrNode = store.popNode();
		rightp = addrNode.getAddr();
		return rightp;
	}
	
	private int handleStmtKwdRtn(KeywordTyp kwtyp) {
		switch (kwtyp) {
		case ADDSET:
		case MINUSSET: 
		case MPYSET: 
		case DIVSET: 
		case SET: 
			return runop.runSetStmt(kwtyp);
		case PRINTLN: return runPrintlnStmt(kwtyp);
		case ZCALL: return runZcallStmt();
		case RETURN: return runRtnStmt(true);
		case UTPUSH: return runUtPushStmt();
		case UTSCAN: return runUtScanStmt();
		default:
			return BADOP;
		}
	}
	
	private boolean isJumpKwd(KeywordTyp kwtyp) {
		omsg("isJumpKwd: kwtyp = " + kwtyp);
		switch (kwtyp) {
		case ZCALL:
		case RETURN:
			return true;
		default:
			return false;
		}
	}
	
	private boolean isLogicalKwd(KeywordTyp kwtyp) {
		switch (kwtyp) {
		case AND:
		case OR:
		case QUEST:
			return true;
		default:
			return false;
		}
	}

	private int pushStmt(Node node) {
		// scan stmt.
		KeywordTyp kwtyp;
		int rightp, rightq;
		
		omsg(":::::::::: pushStmt: stkidx = " + 
			store.getStkIdx());
		rightq = node.getRightp();
		rightp = node.getDownp();
		if (rightp <= 0) {
			return NEGADDR;
		}
		node = store.getNode(rightp);
		kwtyp = node.getKeywordTyp();
		if (isJumpKwd(kwtyp)) { }
		else if (!pushAddr(rightq)) {
			return STKOVERFLOW;
		}
		switch (kwtyp) {
		case ADDSET:
		case MINUSSET: 
		case MPYSET: 
		case DIVSET: 
		case SET: 
			rightp = pushSetStmt(node, kwtyp);
			break;
		case PRINTLN: 
			rightp = pushPrintlnStmt(node);
			break;
		case ZCALL:
			rightp = pushZcallStmt(node);  // prepare to scan call
			break;
		case RETURN:
			rightp = pushRtnStmt(node);
			break;
		case UTPUSH:
		case UTSCAN:
			rightp = pushUtPushStmt(node, kwtyp);
			break;
		default: return BADSTMT;
		}
		return rightp;
	}
	
	private int pushExpr(Node node) {
		KeywordTyp kwtyp;
		KeywordTyp nullkwd;
		int ival;
		int rightp;
		
		rightp = node.getRightp();
		if (!pushAddr(rightp)) {  
			return STKOVERFLOW;
		}
		rightp = node.getDownp();
		if (rightp <= 0) {
			return NEGADDR;
		}
		node = store.getNode(rightp);
		kwtyp = node.getKeywordTyp();
		omsg("pushExpr: kwtyp = " + kwtyp + ", rightp = " + rightp);
		switch (kwtyp) {
		case ADD:
		case MPY:
		case XOR:
			nullkwd = KeywordTyp.NULL; 
			if (!pushOp(kwtyp) || !pushOpAsNode(nullkwd)) {
				return STKOVERFLOW;
			}
			break;
		case MINUS:
		case DIV:
			if (!pushOp(kwtyp)) {
				return STKOVERFLOW;
			}
			break;
		case AND:
		case OR:
		case QUEST:
			//nullkwd = KeywordTyp.NULL; 
			//if (!pushOp(kwtyp) || !pushOpAsNode(nullkwd)) {
			//ival = (kwtyp == KeywordTyp.AND) ? 1 : 0;
			if (!pushOp(kwtyp) || !pushKwdVal(-1)) {  
				return STKOVERFLOW;
			}
			break;
		case ZCALL:
			if (popVal() < 0) {
				return STKUNDERFLOW;
			}
			if (!pushOp(kwtyp) || !pushOpAsNode(kwtyp) ||
				!pushInt(currZexpr)) 
			{
				return STKOVERFLOW;
			}
			rightp = handleLeafToken(node);
			return rightp;
		default: 
			return BADOP;
		}
		rightp = node.getRightp();
		return rightp;
	}
	
	private int pushSetStmt(Node node, KeywordTyp kwtyp) {
		int rightp;
		
		omsg("pushSetStmt: top");
		//kwtyp = KeywordTyp.SET;
		if (!pushOp(kwtyp)) {
			return STKOVERFLOW;
		}
		rightp = node.getRightp();
		if (rightp <= 0) {  // naked set kwd.
			return BADSETSTMT;
		}
		node = store.getNode(rightp);
		rightp = handleLeafTokenQuote(node);  // handle target expr.
		if (rightp <= 0) {
			return BADSETSTMT;
		}
		rightp = handleExprToken(rightp, true);  // handle expr.
		return rightp;
	}
	
	private int pushPrintlnStmt(Node node) {
		int rightp;
		KeywordTyp kwtyp;

		omsg("pushPrintlnStmt: top");
		kwtyp = KeywordTyp.PRINTLN;
		if (!pushOp(kwtyp) || !pushOpAsNode(kwtyp)) {
			return STKOVERFLOW;
		}
		rightp = node.getRightp();
		return rightp;
	}
	
	private int runPrintlnStmt(KeywordTyp kwtyp) {
		AddrNode addrNode;
		int val;
		int count;
		int rtnval;
		String msg = "";
		String s;
		PageTyp pgtyp;

		count = getCountOfSpares(kwtyp);
		omsg("runPrintlnStmt: count of spares = " + count);
		count = 0;
		while (true) {
			addrNode = store.fetchSpare();
			if (addrNode == null) {
				break;
			}
			s = popStrFromNode(addrNode);
			if (s.length() > 0) {
				msg = msg + s + SP;
			}
			omsg("runPrintlnStmt: msg = " + msg);
			count++;
		}
		if (count > 0) {
			oprn(msg);
		}
		rtnval = popUntilKwd(kwtyp);
		return rtnval;
	}
	
	private int runUtPushStmt() {
		AddrNode addrNode;
		String skey;
		String sval;
		int addr;
		KeywordTyp kwtyp = KeywordTyp.UTPUSH; 
		Page page;
		int idx;
		int len;
		int rtnval;

		omsg("runUtPushStmt: top");
		addrNode = store.popNode();
		if (addrNode == null) {
			return STKUNDERFLOW;
		}
		sval = popStrFromNode(addrNode);
		omsg("runUtPushStmt: sval = " + sval);
		len = sval.length();
		sval = sval.trim();
		if (sval.equals("") && (len > 0)) {
			sval = " ";
		}
		addrNode = store.popNode();
		if (addrNode == null) {
			return STKUNDERFLOW;
		}
		addr = addrNode.getAddr();
		page = store.getPage(addr);
		idx = store.getElemIdx(addr);
		skey = page.getString(idx);
		skey = skey.trim();
		utKeyValList.add(skey);
		utKeyValList.add(sval);
		omsg("runUtPushStmt: skey = " + skey + ", sval = " + sval);
		//oprn(ditto...); 
		rtnval = popUntilKwd(kwtyp);
		omsg("runUtPushStmt: btm, rtnval = " + rtnval);
		return rtnval;
	}
	
	private int runUtScanStmt() {
		AddrNode addrNode;
		String skey;
		String sval;
		String lstkey;
		String lstval;
		int addr;
		KeywordTyp kwtyp = KeywordTyp.UTSCAN; 
		Page page;
		int idx;
		int len;
		int rtnval;
		boolean isBadPair;
		boolean isBadUtKey;
		boolean isBadUtVal;
		int pairIdx;

		addrNode = store.popNode();
		if (addrNode == null) {
			return STKUNDERFLOW;
		}
		addr = addrNode.getAddr();
		page = store.getPage(addr);
		idx = store.getElemIdx(addr);
		sval = page.getString(idx);
		len = sval.length();
		sval = sval.trim();
		if (sval.equals("") && (len > 0)) {
			sval = " ";
		}
		addrNode = store.popNode();
		if (addrNode == null) {
			return STKUNDERFLOW;
		}
		addr = addrNode.getAddr();
		page = store.getPage(addr);
		idx = store.getElemIdx(addr);
		skey = page.getString(idx);
		skey = skey.trim();
		pairIdx = (utKeyValIdx / 2) + 1;
		lstkey = utKeyValList.get(utKeyValIdx);
		isBadUtKey = !skey.equals(lstkey);
		utKeyValIdx++;
		lstval = utKeyValList.get(utKeyValIdx);
		isBadUtVal = !sval.equals(lstval);
		utKeyValIdx++;
		isBadPair = (isBadUtKey || isBadUtVal);
		if (isBadPair) {
			oprn("UT Error: run index = " + runidx);
			oprn("UT Error: pair index = " + pairIdx);
			//oprn("UT Error: pair utKeyValIdx = " + utKeyValIdx);
		}
		if (isBadUtKey) {
			oprn("UT Error: bad key = " + lstkey);
			oprn("UT Error: correct key = " + skey);
			if (!isBadUtVal) {
				oprn("UT Error: value = " + sval);
			}
		}
		if (isBadUtVal) {
			if (!isBadUtKey) {
				oprn("UT Error: key = " + skey);
				//oprn("UT Error: lstkey = " + lstkey);
			}
			oprn("UT Error: bad value = " + lstval);
			oprn("UT Error: correct value = " + sval);
		}
		isBadUtPair = isBadUtPair || isBadPair;
		rtnval = popUntilKwd(kwtyp);
		return rtnval;
	}
	
	private int runAltZcallStmt(int parmCount, int lbidx) {
		AddrNode addrNode;
		PageTyp pgtyp;
		Integer val;
		int rtnval;
		int i;

		for (i = 0; i < parmCount; i++) {
			addrNode = store.fetchSpare();
			if (addrNode == null) {
				return STKOVERFLOW;
			}
			pgtyp = addrNode.getHdrPgTyp();
			val = popIObjFromNode(addrNode, lbidx);
			if (val == null) {
				omsg("runAltZcallStmt: rtn = " + lastErrCode + 
					", i = " + i);
				return lastErrCode;
			}
			omsg("runAltZcallStmt: i/pc = " + i + "/" + parmCount +
				", val = " + val +
				", pgtyp = " + pgtyp);
			rtnval = storeLocGlbInt(i, val, pgtyp, false);
			if (rtnval < 0) {
				return rtnval;
			}
		}
		return 0;
	}
	
	private int getCountOfSpares(KeywordTyp kwtyp) {
		AddrNode addrNode;
		PageTyp pgtyp;
		int addr;
		int count = 0;
		
		store.initSpareStkIdx();
		while (true) {
			addrNode = store.popSpare();
			if (addrNode == null) {
				return STKUNDERFLOW;
			}
			addr = addrNode.getAddr();
			pgtyp = addrNode.getHdrPgTyp();
			omsg("getCountOfSpares: addr = " + addr + ", pgtyp = " + pgtyp);
			if ((addr == kwtyp.ordinal()) && (pgtyp == PageTyp.KWD)) {
				break;
			}
			count++;
		}
		addrNode = store.fetchSpare();  // pop the PRINTLN/ZCALL
		if (addrNode == null) { 
			return STKOVERFLOW; 
		}
		return count;
	}
	
	private int getReturnpSpares() {
		AddrNode addrNode;
		int addr;
	
		addrNode = store.fetchSpare();  // pop the returnp
		if (addrNode == null) {
			return STKOVERFLOW; 
		}
		addr = addrNode.getAddr();
		omsg("getReturnpSpares: rtn addr = " + addr);
		return addr;
	}
			
	private int getFuncIdxSpares() {
		AddrNode addrNode;
		int addr;
	
		addrNode = store.fetchSpare();  // pop the func var
		if (addrNode == null) {
			return STKOVERFLOW; 
		}
		addr = addrNode.getAddr();
		omsg("getFuncIdxSpares: rtn addr = " + addr);
		return addr;
	}
			
	private int pushZcallStmt(Node node) {
		KeywordTyp kwtyp;
		int rightp;
		
		omsg("pushZcallStmt: top");
		kwtyp = KeywordTyp.ZCALL;
		if (!pushOp(kwtyp) || !pushOpAsNode(kwtyp) || !pushInt(currZstmt)) {
			return STKOVERFLOW;
		}
		rightp = handleLeafToken(node);
		return rightp;
	}
	
	private int runZcallStmt() {
		// - popSpare returnp (go back here)
		// - popSpare func ref. (idx in func list)
		// - get ptr to func def parm list
		// - set locBaseIdx
		// - push parms, loc vars
		// - push varCount
		// - push old locBaseIdx
		// - push returnp
		// - push old local depth
		// - go to func body: return ptr to first zstmt
		int downp;
		int funcidx;
		int upNodep;
		int funcp;
		int firstp;
		int returnp;
		int currLocBase;
		Node node = null;
		Node upNode;
		KeywordTyp kwtyp = KeywordTyp.ZCALL;
		String funcName;
		String varName;
		int varCount = 0;  // includes parms, loc vars
		int parmCount;
		int parmFixedCount;
		int i, j;
		int rtnval;
		
		omsg("runZcallStmt: top");
		currLocBase = locBaseIdx;
		parmCount = getCountOfSpares(kwtyp) - 2;
		returnp = getReturnpSpares();
		if (returnp < 0) {
			return STKUNDERFLOW;
		}
		funcidx = getFuncIdxSpares();
		if (funcidx < 0) {
			return STKUNDERFLOW;
		}
		omsg("Zcall: funcidx = " + funcidx);
		upNodep = glbFunList.get(funcidx);
		upNode = store.getNode(upNodep);
		funcp = upNode.getDownp();
		firstp = upNode.getRightp();
		node = store.getNode(firstp);
		firstp = node.getDownp();  // return firstp
		node = store.getNode(funcp);
		downp = node.getDownp();  // (_f_ x y)
		node = store.getNode(downp);
		downp = node.getDownp();
		funcName = store.getVarName(downp);
		varName = rscan.getFunVar(funcName);
		parmFixedCount = glbLocVarMap.get(varName);  
		if (parmFixedCount != parmCount) {
			return BADPARMCT;
		}
		i = glbLocVarMap.get(funcName);
		setLocBaseIdx(store.getStkIdx() - parmCount);
		omsg("runZcallStmt: lbidx = " + locBaseIdx + 
			", parmCount = " + parmCount);
		while (true) {
			if (varCount < parmCount) {
				varCount++;
				continue;
			}
			j = glbLocVarList.get(i + varCount);
			if (j < 0) {
				break;
			}
			omsg("runZcallStmt: stkidx = " + store.getStkIdx());
			if (!pushVal(varCount, PageTyp.INTVAL, LOCVAR)) {
				return STKOVERFLOW;
			}
			varCount++;
		}
		rtnval = runAltZcallStmt(parmCount, currLocBase);
		if (rtnval < 0) {
			omsg("Zcall: rtnval = " + rtnval);
			return rtnval;
		}
		if (!pushInt(varCount)) {
			return STKOVERFLOW;
		}
		if (!pushInt(currLocBase)) {
			return STKOVERFLOW;
		}
		if (!pushInt(returnp)) {
			return STKOVERFLOW;
		}
		omsg("Zcall: locDepth = " + locDepth);
		if (!pushInt(locDepth)) {
			return STKOVERFLOW;
		}
		omsg("Zcall: btm, firstp = " + firstp);
		return firstp;
	}
	
	private int runRtnStmt(boolean isExpr) {
		// - pop return value if any
		// - pop local depth
		// - pop returnp (go back here)
		// - if returnp=0 then done
		// - pop calling locBaseIdx
		// - pop varCount
		// - pop loc vars, parms
		// - pop until ZCALL, inclusive
		// - push return value if any
		// - set local depth
		// - return getRightp of returnp
		int rightp;
		int currLocBase;
		int varCount;
		int funcAddr = 0;
		int i;
		int rtnval;
		Integer val;
		boolean isDelayPops = false;
		AddrNode funcReturns = null;
		Node node;
		
		omsg("runRtnStmt: top");
		if (isExpr) {
			funcReturns = store.popNode(); 
			if (funcReturns == null) {
				return STKUNDERFLOW;
			}
			val = popIObjFromNode(funcReturns, locBaseIdx);
			if (val == null) {
				omsg("runRtnStmt: isExpr, rtn = " + lastErrCode); 
				return lastErrCode;
			}
			funcAddr = val;
			isExprLoop = true;
			omsg("runRtnStmt: funcAddr = " + funcAddr);
		}
		locDepth = popVal();
		omsg("runRtnStmt: locDepth = " + locDepth);
		if (locDepth == NEGBASEVAL) {
			return STKUNDERFLOW;
		}
		rightp = popIdxVal(); // currZstmt/Zexpr
		omsg("runRtnStmt: returnp = " + rightp);
		if (rightp < 0) {
			return STKUNDERFLOW;
		}
		if (rightp == 0) {
			omsg("runRtnStmt: done");
			return EXIT; // done
		}
		omsg("runRtnStmt: top2");
		currLocBase = popVal(); // locBaseIdx
		if (currLocBase < 0) {
			return STKUNDERFLOW;
		}
		omsg("runRtnStmt: top3");
		varCount = popVal(); // varCount
		if (varCount < 0) {
			return STKUNDERFLOW;
		}
		omsg("runRtnStmt: varCount = " + varCount);
		isDelayPops = isNonImmedLocVar(funcReturns);
		if (!isDelayPops) {
			omsg("runRtnStmt: popMulti");
			rtnval = popMulti(varCount);
			if (rtnval < 0) {
				return rtnval;
			}
		}
		// push func rtnval if locDepth > 0:
		if (locDepth < 0) {
			return GENERR;  // not needed (just be safe)
		}
		if (locDepth == 0) { 
			omsg("runRtnStmt: locDepth = zero");
			isExprLoop = false;
			if (isDelayPops) {
				omsg("runRtnStmt: zero LocD, popMulti");
				rtnval = popMulti(varCount);
				if (rtnval < 0) {
					return rtnval;
				}
			}
			/*if (isExpr) {
				store.popNode();
			}*/
		}
		else if (funcReturns == null) { 
			return GENERR;
		}
		else {  
			rtnval = pushFuncRtnVal(funcAddr, funcReturns, isDelayPops,
				varCount);
			if (rtnval < 0) {
				return rtnval;
			}
			locDepth--;
		}
		setLocBaseIdx(currLocBase);
		omsg("runRtnStmt: btm, locDepth = " + locDepth);
		omsg("runRtnStmt: btm, rightp = " + rightp);
		node = store.getNode(rightp);
		rightp = node.getRightp();
		omsg("runRtnStmt: rtnval = " + rightp);
		return rightp;
	}
	
	private int pushRtnStmt(Node node) {
		int rightp;
		KeywordTyp kwtyp;
		
		omsg("pushRtnStmt: top");
		kwtyp = KeywordTyp.RETURN;
		if (!pushOp(kwtyp)) {
			return STKOVERFLOW;
		}
		rightp = node.getRightp();
		if (rightp <= 0) {
			return RTNISEMPTY;  
		}
		node = store.getNode(rightp);
		rightp = handleExprToken(rightp, true);
		return rightp;
	}
	
	private int runGlbCall() {
		int i, j;
		int len;
		
		i = 0;
		len = glbLocVarList.size();
		setLocBaseIdx(store.getStkIdx());
		omsg("runGlbCall: locBaseIdx = " + locBaseIdx);
		while (len > 0) {
			j = glbLocVarList.get(i);
			if (j < 0) {
				break;
			}
			if (!pushVal(i, PageTyp.INTVAL, GLBVAR)) {
				return STKOVERFLOW;
			}
			i++;
		}
		varCountIdx = store.getStkIdx();
		if (!pushInt(i)) {
			return STKOVERFLOW;
		}
		if (!pushInt(0)) {
			return STKOVERFLOW;
		}
		return 0;
	}
	
	private int pushUtPushStmt(Node node, KeywordTyp kwtyp) {
		int rightp;
		
		omsg("pushUtPushStmt: top, kwtyp = " + kwtyp);
		if (!pushOp(kwtyp) || !pushOpAsNode(kwtyp)) {
			return STKOVERFLOW;
		}
		rightp = node.getRightp();
		if (rightp <= 0) {
			return BADUTSTMT;  
		}
		node = store.getNode(rightp);
		rightp = handleLeafToken(node);  // handle 1st expr.
		if (rightp <= 0) {
			return BADUTSTMT;
		}
		rightp = handleExprToken(rightp, true);  // handle 2nd expr.
		omsg("pushUtPushStmt: btm, rightp = " + rightp);
		return rightp;
	}
	
	public AddrNode getVarNode(AddrNode node) {
		return pp.getVarNode(node);
	}
		
	private int popUntilKwd(KeywordTyp kwtyp) {
		return pp.popUntilKwd(kwtyp);
	}
	
	public int popMulti(int varCount) {
		return pp.popMulti(varCount);
	}
	
	private KeywordTyp popKwd() {
		return pp.popKwd();
	}
	
	private KeywordTyp topKwd() {
		return pp.topKwd();
	}
	
	public int stripIntSign(int val) {
		return pp.stripIntSign(val);
	}
	
	public int packIntSign(boolean isNeg, int val) {
		return pp.packIntSign(isNeg, val);
	}
	
	private int popIdxVal() {
		return pp.popIdxVal();
	}
	
	private int getIntOffStk(int stkidx) {
		return pp.getIntOffStk(stkidx);
	}
	
	private int topIntVal() {
		return pp.topIntVal();
	}
	
	private int popIntStk() {
		return pp.popIntStk();
	}

	public int popIntStkRtn(AddrNode addrNode) {
		return pp.popIntStkRtn(addrNode);
	}
		
	private boolean isNullKwd(AddrNode addrNode) {
		return pp.isNullKwd(addrNode);
	}
	
	private int popInt(boolean isKwd) {
		return pp.popInt(isKwd);
	}
	
	private int popIntFromNode(AddrNode addrNode) {
		return pp.popIntFromNode(addrNode);
	}
	
	private int popIntRtn(AddrNode addrNode, boolean isKwd) {
		return pp.popIntRtn(addrNode, isKwd);
	}
	
	private Integer popIObjFromNode(AddrNode addrNode, int lbidx) {
		return pp.popIObjFromNode(addrNode, lbidx);
	}
	
	private Integer setErrCode(int errCode) {
		lastErrCode = errCode;
		return null;
	}
	
	private String popStrFromNode(AddrNode addrNode) {
		return pp.popStrFromNode(addrNode);
	}
	
	private int popVal() {
		return pp.popVal();
	}
	
	private int storeLocGlbInt(int varidx, int val, PageTyp pgtyp,
		boolean isGlb) 
	{
		return pp.storeLocGlbInt(varidx, val, pgtyp, isGlb);
	}
	
	private boolean pushVal(int val, PageTyp pgtyp, int locVarTyp) {
		return pp.pushVal(val, pgtyp, locVarTyp);
	}
	
	public boolean pushKwdVal(int ival) {
		return pp.pushKwdVal(ival);
	}
	
	private boolean pushAddr(int rightp) {
		return pp.pushAddr(rightp);
	}
	
	private int pushLong(long val) {
		return pp.pushLong(val);
	}
	
	private int pushFloat(double val) {
		return pp.pushFloat(val);
	}
	
	private int pushString(String val) {
		return pp.pushString(val);
	}
	
	private boolean pushInt(int val) {
		return pp.pushInt(val);
	}
	
	private boolean pushIntStk(int val) {
		return pp.pushIntStk(val);
	}
	
	private boolean pushIntVar(int val, int locVarTyp, boolean ptrFlag) {
		return pp.pushIntVar(val, locVarTyp, ptrFlag);
	}
	
	private boolean pushBoolStk(int val) {
		return pp.pushBoolStk(val);
	}
	
	private int pushIntMulti(int val, int varCount) {
		return pp.pushIntMulti(val, varCount);
	}
	
	private int pushFuncRtnVal(int val, AddrNode srcNode,
		boolean isDelayPops, int varCount) 
	{
		return pp.pushFuncRtnVal(val, srcNode, isDelayPops, varCount);
	}
	
	private int pushNonImmed(int addr, PageTyp pgtyp, int varCount) {
		return pp.pushNonImmed(addr, pgtyp, varCount);
	}
	
	private boolean isNonImmedLocVar(AddrNode addrNode) {
		return pp.isNonImmedLocVar(addrNode);
	}
	
	private boolean pushPtrVar(int val, int locVarTyp, PageTyp pgtyp) {
		return pp.pushPtrVar(val, locVarTyp, pgtyp);
	}
	
	private boolean isImmedTyp(PageTyp pgtyp) {
		return (pgtyp == PageTyp.INTVAL);
	}
	
	private boolean pushOp(KeywordTyp kwtyp) {
		return pp.pushOp(kwtyp);
	}
	
	private boolean pushOpAsNode(KeywordTyp kwtyp) {
		return pp.pushOpAsNode(kwtyp);
	}
	
	private boolean pushVar(int varidx) {
		return pp.pushVar(varidx);
	}
	
	private boolean pushVarQuote(int varidx) {
		return pp.pushVarQuote(varidx);
	}
	
	private boolean freeTarget(AddrNode node, 
		boolean isChkTgt, int addr) 
	{
		return pp.freeTarget(node, isChkTgt, addr);
	}
}

class RunCall implements IConst {
	
	private Store store;
	private RunTime rt;
	private RunPushPop pp;
	
	public RunCall(Store store, RunTime rt, RunPushPop pp) {
		this.store = store;
		this.rt = rt;
		this.pp = pp;
	}
}

