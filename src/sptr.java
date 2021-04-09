import init.InitMain;

public class sptr {

	public static void main(String[] args) {
		InitMain initobj = new InitMain();
		String fileName = "";
		boolean isUnitTest = false;
		boolean isMain = false;

		if (args.length > 2) {
			System.out.println("Usage: java sptr [filename]");
			return;
		}
		if (args.length == 1) {
			fileName = "../dat/" + args[0] + ".sptr";
		}
		else if (args.length == 2) {
			isMain = args[0].equals("main");
			if (args[1].equals("-u")) {
				fileName = "../dat/test/" + args[0] + ".test";
				isUnitTest = true;
			}
			else {
				System.out.println("Usage: java sptr filename [-u]");
				return;
			}
		}
		initobj.runInit(fileName, isUnitTest, isMain);
	}

}
