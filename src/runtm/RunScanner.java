package runtm;

import iconst.IConst;
import iconst.KeywordTyp;
import iconst.NodeCellTyp;
import page.Node;
import page.AddrNode;
import page.Store;
import page.Page;
import scansrc.ScanSrc;
import synchk.SynChk;

// Setup Code Execution

public class RunScanner implements IConst {

	private Store store;
	private ScanSrc scanSrc;
	private SynChk synChk;
	private static final boolean isSilent = false;
	private int rootNodep;
	private RunTime rt;
	private String scopeFuncName;
	private int defunCount;
	private int count;

	public RunScanner(Store store, ScanSrc scanSrc, SynChk synChk, int rootNodep) {
		this.store = store;
		this.scanSrc = scanSrc;
		this.synChk = synChk;
		this.rootNodep = rootNodep;
		rt = new RunTime(store, scanSrc, synChk);
		defunCount = 0;
		count = 0;
	}

	public boolean run() {
		boolean rtnval;
		
		omsg("RunTime.run: rootNodep = " + rootNodep);
		rt.setRscan(this);
		rtnval = runRoot(rootNodep);
		if (!rtnval) {
			oprn("Runtime error detected!");
		}
		return rtnval;
	}
	
	public void out(String msg) {
		if (debug) {
			System.out.println(msg);
		}
	}
	
	public void omsg(String msg) {  
		if (isrtbug) {
			System.out.println(msg);
		}
	}
	
	public void oprn(String msg) {  
		System.out.println(msg);
	}
	
	private boolean runRoot(int rightp) {
		int downp;
		Node node;
		boolean rtnval;
		
		node = store.getNode(rightp);
		rightp = node.getRightp();
		node = store.getNode(rightp);
		downp = node.getDownp();
		// scan decls. of glb/loc vars., 
		// scan names of func decls., including gdefun
		rtnval = scanTopBlock(downp);
		if (!rtnval) {
			return false;
		}
		// replace downp of glb/loc var refs. w/ var idx nos.
		// must scan all do-blocks
		rtnval = scopeTopBlock(downp);
		if (!rtnval) {
			return false;
		}
		// run prog. using do-block of gdefun stmt.
		rtnval = rt.runTopBlock(downp);
		return rtnval;
	}
	
	private boolean scanTopBlock(int rightp) {
		// process top-level stmts.:
		// do ( stmt-1; .. stmt-n; )
		Node node;
		int downp;
		KeywordTyp kwtyp;
		NodeCellTyp celltyp;
		int phaseNo = 0;

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
				phaseNo = scanTopStmt(downp, phaseNo);
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
		return true;
	}
	
	private boolean scopeTopBlock(int rightp) {
		// process top-level stmts.
		// scope = scan + code
		Node node;
		int downp;
		KeywordTyp kwtyp;
		NodeCellTyp celltyp;
		int phaseNo = 0;

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
				phaseNo = scopeTopStmt(downp, phaseNo);
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
		return true;
	}
	
	private int scanTopStmt(int rightp, int phaseNo) {
		// scan process saves local var. names/idx nos., and node ptrs.
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
				case 1:  // stub
					rightq = scanImportStmt(rightp, kwtyp);
					break;
				case 2:
					rightq = scanGlbDefStmt(rightp);
					break;
				case 3:
					rightq = scanDefunStmt(rightp);
					break;
				case 4:  // stub
					rightq = scanClassStmt(rightp, kwtyp);
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
	
	private int scopeTopStmt(int rightp, int phaseNo) {
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
				case 1:  // stub
					rightq = scopeImportStmt(rightp, kwtyp);
					break;
				case 2:
					rightq = scopeGlbDefStmt(rightp);
					break;
				case 3:
					rightq = scopeDefunStmt(rightp);
					break;
				case 4:  // stub
					rightq = scopeClassStmt(rightp, kwtyp);
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
	
	private int scanImportStmt(int rightp, KeywordTyp kwtyp) {
		return rightp;
	}
	
	private int scopeImportStmt(int rightp, KeywordTyp kwtyp) {
		return rightp;
	}
	
	private int scanGlbDefStmt(int rightp) {
		Node node;
		Node firstNode;
		KeywordTyp kwtyp;
		NodeCellTyp celltyp;
		int downp;
		int savep = rightp;
		String varName;
		int varidx = 0;
		int idx;
		Page page;

		omsg("Keyword gdefun detected.");
		rt.glbFunMap.put(getGdefunWord(), defunCount);
		rt.glbFunList.add(0);
		defunCount++;
		node = store.getNode(rightp);
		firstNode = node;
		kwtyp = node.getKeywordTyp();
		if (kwtyp == KeywordTyp.ZPAREN) {
			rightp = node.getDownp();
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			if (kwtyp != KeywordTyp.VAR) {
				return -1;
			}
			rightp = node.getRightp();
			while (rightp > 0) {
				// scan decls. of local vars.
				// save dict. w/ var names and var nos.
				// save list w/ var decl. node ptrs.
				page = store.getPage(rightp);
				idx = store.getElemIdx(rightp);
				node = page.getNode(idx);
				celltyp = node.getDownCellTyp();
				if (celltyp != NodeCellTyp.ID) {
					return -1;
				}
				downp = node.getDownp();
				varName = getGdefunWord() + ' ';
				varName += store.getVarName(downp);
				rt.glbLocVarMap.put(varName, varidx);
				rt.glbLocVarList.add(rightp);
				varidx++;
				rightp = node.getRightp();
				node.setRightp(downp);
				node.setDownp(0);
				node.setRightCell(true);
				page.setNode(idx, node);
			}
			rt.glbLocVarList.add(-1);
			rightp = firstNode.getRightp();
			omsg("Global public var count = " + varidx);
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			if (kwtyp == KeywordTyp.ZPAREN) {  // (ivar ...)
				rightp = node.getRightp();
				if (rightp <= 0) {
					return -1;
				}
				node = store.getNode(rightp);
			}
			kwtyp = node.getKeywordTyp();
		}
		if (kwtyp != KeywordTyp.DO) {
			omsg("Missing DO");
			return -1;
		}
		return savep;
	}
	
	private int scopeGlbDefStmt(int rightp) {
		// scope operation:
		// replace downp of glb/loc var refs. w/ var idx nos.
		// (they were pointing to var names)
		Node node;
		Node firstNode;
		KeywordTyp kwtyp;
		int downp;
		int savep = rightp;
		int stmtCount = 0;
		boolean rtnval;

		omsg("Keyword gdefun detected.");
		scopeFuncName = "";
		node = store.getNode(rightp);
		firstNode = node;
		kwtyp = node.getKeywordTyp();
		if (kwtyp == KeywordTyp.ZPAREN) {
			rightp = node.getDownp();
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			if (kwtyp != KeywordTyp.VAR) {
				return -1;
			}
			rightp = firstNode.getRightp();
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			if (kwtyp == KeywordTyp.ZPAREN) {  // (ivar ...)
				rightp = node.getRightp();
				if (rightp <= 0) {
					return -1;
				}
				node = store.getNode(rightp);
			}
			kwtyp = node.getKeywordTyp();
		}
		if (kwtyp != KeywordTyp.DO) {
			omsg("Missing DO");
			return -1;
		}
		rightp = node.getDownp();
		while (rightp > 0) {
			// for all stmts. in do-block, perform scope oper.
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			if (kwtyp != KeywordTyp.ZSTMT) {
				return -1;
			}
			omsg("Stmt count = " + stmtCount);
			downp = node.getDownp();
			rtnval = scopeStmt(downp);
			if (!rtnval) {
				return -1;
			}
			stmtCount++;
			rightp = node.getRightp();
		} 
		omsg("Stmt count = " + stmtCount + ", set count = " + count);
		return savep;
	}
	
	private boolean scopeStmt(int rightp) {
		Node node;
		KeywordTyp kwtyp;
		
		node = store.getNode(rightp);
		kwtyp = node.getKeywordTyp();
		switch (kwtyp) {
		case SET: return scopeSetStmt(node);
		case PRINTLN: return scopePrintlnStmt(node);
		case ZCALL: return scopeZcallStmt(rightp, false);
		case RETURN: return scopeRtnStmt(node);
		default: return false;
		}
	}
	
	private boolean scopeSetStmt(Node node) {
		int rightp;
		
		count++;
		rightp = node.getRightp();
		if (rightp <= 0) {
			return false;
		}
		node = store.getNode(rightp);
		// perform scope oper. on single var. ref.
		if (!scopeLocVar(rightp)) {
			return false;
		}
		rightp = node.getRightp();
		if (rightp <= 0) {
			return false;
		}
		// perform scope oper. on single expr.
		return scopeExpr(rightp);
	}
	
	private boolean scopePrintlnStmt(Node node) {
		// perform scope oper. on multiple exprs.
		int rightp;
		boolean rtnval;

		rightp = node.getRightp();
		while (rightp > 0) {
			node = store.getNode(rightp);
			rtnval = scopeExpr(rightp);
			if (!rtnval) {
				return false;
			}
			rightp = node.getRightp();
		}
		return true;
	}
	
	private boolean scopeRtnStmt(Node node) {
		// perform scope oper. on single expr.
		// fails if no expr. found
		int rightp;
		
		rightp = node.getRightp();
		if (rightp <= 0) {
			return false;
		}
		return scopeExpr(rightp);
	}
	
	private boolean scopeZcallStmt(int rightp, boolean isExpr) {
		// scope a func. call
		// perform scope oper. on zero or more args.
		int downp;
		Node node;
		Page page;
		int idx;
		NodeCellTyp celltyp;
		String varName;
		Integer value;
		int varidx;
		boolean rtnval;
		
		node = store.getNode(rightp);
		celltyp = node.getDownCellTyp();
		if (celltyp != NodeCellTyp.FUNC) {
			return false;
		}
		downp = node.getDownp();
		varName = store.getVarName(downp);
		value = rt.glbFunMap.get(varName);
		if (value == null) {
			return false;
		}
		varidx = (int)value;
		// replace downp of func. ref. w/ func. idx no.
		node.setDownp(varidx);
		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		page.setNode(idx, node);
		omsg("FunVar = " + varidx);
		if (isExpr) {
			return true;
		}
		rtnval = scopePrintlnStmt(node);
		return rtnval;
	}
	
	private boolean scopeLocVar(int rightp) {
		// replace downp of glb/loc var ref. w/ var idx no.
		// (it was pointing to var name)
		// glb var idx nos. are -ve
		int downp;
		Page page;
		int idx;
		Node node;
		NodeCellTyp celltyp;
		boolean isGlb;
		String varName, name;
		Integer value;
		int varidx;
		
		node = store.getNode(rightp);
		celltyp = node.getDownCellTyp();
		if (celltyp != NodeCellTyp.ID) {
			return false;
		}
		downp = node.getDownp();
		isGlb = scopeFuncName.equals("");
		if (isGlb) {
			name = getGdefunWord();
		}
		else {
			name = scopeFuncName;
		}
		varName = store.getVarName(downp);
		name = name + ' ' + varName;
		value = rt.glbLocVarMap.get(name);
		if (value != null) { }
		else if (isGlb) {
			return false;
		}
		else {
			isGlb = true;
			name = getGdefunWord() + ' ' + varName;
			value = rt.glbLocVarMap.get(name);
			if (value == null) {
				return false;
			}
		}
		varidx = (int)value;
		if (isGlb) {
			varidx = -1 - varidx;
		}
		node.setDownCellTyp(NodeCellTyp.LOCVAR.ordinal());
		node.setDownp(varidx);
		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		page.setNode(idx, node);
		omsg("LocVar = " + varidx);
		return true;
	}
	
	private boolean scopeExpr(int rightp) {
		// call scopeLocVar for all lower level IDs
		Node node;
		NodeCellTyp celltyp;
		boolean rtnval = true;

		node = store.getNode(rightp);
		celltyp = node.getDownCellTyp();
		if (celltyp == NodeCellTyp.ID) {  // scope var. ref.
			return scopeLocVar(rightp);
		}
		if (celltyp == NodeCellTyp.FUNC) {  // scope fun. call
			return scopeZcallStmt(rightp, true);
		}
		if (celltyp != NodeCellTyp.PTR) {
			return true;
		}
		rightp = node.getDownp();
		while (rtnval && (rightp > 0)) {
			// handle lower level exprs. recursively
			rtnval = rtnval && scopeExpr(rightp);
			node = store.getNode(rightp);
			rightp = node.getRightp();
		}
		return rtnval;
	}
	
	private int scanDefunStmt(int rightp) {
		Node node;
		Node upNode, funcNameNode;
		KeywordTyp kwtyp;
		NodeCellTyp celltyp;
		int downp;
		int savep = rightp;
		int funcp;
		String funcName;
		String varName;
		int varidx = 0;
		int idx;
		int glbLocIdx;
		Page page;

		omsg("Keyword defun detected.");
		node = store.getNode(rightp);
		upNode = node;
		kwtyp = node.getKeywordTyp();
		if (kwtyp != KeywordTyp.ZPAREN) {
			return -1;
		}
		funcp = rightp;
		rightp = node.getDownp();
		node = store.getNode(rightp);
		celltyp = node.getDownCellTyp();
		if (celltyp != NodeCellTyp.FUNC) {
			return -1;
		}
		funcNameNode = node;
		downp = node.getDownp();
		funcName = store.getVarName(downp);
		rt.glbFunMap.put(funcName, defunCount);
		glbLocIdx = rt.glbLocVarList.size();
		rt.glbLocVarMap.put(funcName, glbLocIdx);
		rightp = node.getRightp();
		while (rightp > 0) {
			// scan var decls. of parm. list
			rightp = scanParmVarList(rightp, varidx, funcName);
			varidx++;
		}
		if (rightp < 0) {
			return rightp;
		}
		// call to put used if parm count value needed later:
		varName = getFunVar(funcName);
		rt.glbLocVarMap.put(varName, varidx);
		node = upNode;
		rightp = node.getRightp();
		node = store.getNode(rightp);
		kwtyp = node.getKeywordTyp();
		if (kwtyp == KeywordTyp.ZPAREN) {
			upNode = node;
			rightp = node.getDownp();
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			if (kwtyp != KeywordTyp.VAR) {
				return -1;
			}
			rightp = node.getRightp();
			while (rightp > 0) {
				// scan var decls. of local vars.
				rightp = scanParmVarList(rightp, varidx, funcName);
				varidx++;
			}
			if (rightp < 0) {
				return rightp;
			}
			node = upNode;
			rightp = node.getRightp();
			omsg("Global/local var count = " + varidx);
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			if (kwtyp == KeywordTyp.ZPAREN) {  // (gvar ...)
				rightp = node.getRightp();
				if (rightp <= 0) {
					return -1;
				}
				node = store.getNode(rightp);
			}
			kwtyp = node.getKeywordTyp();
		}
		rt.glbLocVarList.add(-1);
		if (kwtyp != KeywordTyp.DO) {
			omsg("Missing DO");
			return -1;
		}
		if (node.getRightp() > 0) {
			omsg("Post DO: unexpected rightp found");
			return -1;
		}
		// rightp points to do-block
		// funcp points to zparen of (f x y)
		downp = funcNameNode.getDownp();
		varName = store.getVarName(downp);
		omsg("Post DO: varName = " + varName);
		upNode = new Node(0, funcp, rightp);
		rightp = store.allocNode(upNode);
		rt.glbFunList.add(rightp);
		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		celltyp = NodeCellTyp.PTR;
		upNode.setDownCellTyp(celltyp.ordinal());
		upNode.setRightCell(true);
		page.setNode(idx, upNode);
		defunCount++;
		return savep;
	}
	
	private int scanParmVarList(int rightp, int varidx, String funcName) {
		// scan decl. of local var
		// save dict. w/ var name and var no.
		// save list w/ var decl. node ptr.
		Node node;
		NodeCellTyp celltyp;
		int downp;
		String varName;
		int idx;
		Page page;

		page = store.getPage(rightp);
		idx = store.getElemIdx(rightp);
		node = page.getNode(idx);
		celltyp = node.getDownCellTyp();
		if (celltyp != NodeCellTyp.ID) {
			return -1;
		}
		downp = node.getDownp();
		varName = funcName + ' ';
		varName += store.getVarName(downp);
		rt.glbLocVarMap.put(varName, varidx);
		rt.glbLocVarList.add(rightp);
		rightp = node.getRightp();
		node.setRightp(downp);
		node.setDownp(0);
		node.setRightCell(true);
		page.setNode(idx, node);
		return rightp;
	}
	
	private int scopeDefunStmt(int rightp) {
		// scope operation:
		// replace downp of glb/loc var refs. w/ var idx nos.
		// (they were pointing to var names)
		Node node;
		Node upNode;
		KeywordTyp kwtyp;
		NodeCellTyp celltyp;
		int downp;
		int savep = rightp;
		int stmtCount = 0;
		boolean rtnval;

		omsg("Keyword (scope) defun detected.");
		node = store.getNode(rightp);
		upNode = node;
		kwtyp = node.getKeywordTyp();
		if (kwtyp != KeywordTyp.ZPAREN) {
			return -1;
		}
		rightp = node.getDownp();
		node = store.getNode(rightp);
		celltyp = node.getDownCellTyp();
		if (celltyp != NodeCellTyp.FUNC) {
			return -1;
		}
		downp = node.getDownp();
		scopeFuncName = store.getVarName(downp);
		node = upNode;
		rightp = node.getRightp();
		node = store.getNode(rightp);
		kwtyp = node.getKeywordTyp();
		if (kwtyp == KeywordTyp.ZPAREN) {
			upNode = node;
			rightp = node.getDownp();
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			if (kwtyp != KeywordTyp.VAR) {
				return -1;
			}
			node = upNode;
			rightp = node.getRightp();
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			if (kwtyp == KeywordTyp.ZPAREN) {  // (gvar ...)
				rightp = node.getRightp();
				if (rightp <= 0) {
					return -1;
				}
				node = store.getNode(rightp);
			}
			kwtyp = node.getKeywordTyp();
		}
		if (kwtyp != KeywordTyp.DO) {
			omsg("Missing DO");
			return -1;
		}
		rightp = node.getDownp();
		while (rightp > 0) {
			// for all stmts. in do-block, perform scope oper.
			node = store.getNode(rightp);
			kwtyp = node.getKeywordTyp();
			if (kwtyp != KeywordTyp.ZSTMT) {
				return -1;
			}
			omsg("Stmt count = " + stmtCount);
			downp = node.getDownp();
			rtnval = scopeStmt(downp);
			if (!rtnval) {
				return -1;
			}
			stmtCount++;
			rightp = node.getRightp();
		} 
		omsg("Stmt count = " + stmtCount + ", set count = " + count);
		return savep;
	}
	
	private int scanClassStmt(int rightp, KeywordTyp kwtyp) {
		return rightp;
	}
	
	private int scopeClassStmt(int rightp, KeywordTyp kwtyp) {
		return rightp;
	}
	
	private String getGdefunWord() {
		return "gdefun";
	}
	
	private boolean isGdefun(String s) {
		return s.equals("gdefun");
	}
	
	public String getFunVar(String funcName) {
		String varName;
		varName = funcName + " var";
		return varName;
	}
	
}