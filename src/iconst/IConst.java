package iconst;

public interface IConst {
	boolean debug = false;
	boolean isrtbug = false;
	int WRDPGLEN = 2048;
	int INTPGLEN = 1024;
	int DBLPGLEN = 512;
	int BYTPGLEN = 2048;
	int NODESTKLEN = 1024;
	int OPSTKLEN = 256;
	int NODESIZ = 5;
	int NODECOUNT = WRDPGLEN / NODESIZ;
	String DEFPROMPT = "> ";
	int RESERR = -1;
	int RESOK = 0;
	int RESFREE = 1;
}
