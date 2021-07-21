import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
/**
 * 
 * @author rsingh
 * Updates check book's Date cashed and status i.e., FAPCHK table
 *
 */
public class UpdCheckBook extends ExtendM3Transaction {

	public UpdCheckBook(MIAPI mi, DatabaseAPI database, ProgramAPI program, MICallerAPI miCaller, LoggerAPI logger) {
		this.mi = mi;
		this.miCaller = miCaller;
		this.database = database;
		this.program = program;
		this.logger = logger;
	}
	/**
	 * 
	 * @param company
	 * @param division
	 * @param checkNo
	 * @param status
	 * @param dateCashed
	 */
	public void CheckAndUpdate(int company, String division, String checkNo, String status, int dateCashed) {
		DBAction FABCHK_Query = database.table("FAPCHK").index("10").selection("CKCONO", "CKDIVI", "CKCHKN", "CKSTTS", "CKCADA","CKCHID","CKCHNO","CKLMDT").build();
		DBContainer Container = FABCHK_Query.getContainer();
		Container.set("CKCONO", company);
		Container.set("CKDIVI", division);
		Container.set("CKCHKN", checkNo);
		if (FABCHK_Query.readAll(Container,3,readChecks)==0) {
			mi.error("Check no " + checkNo + " is invalid");
			return;
		}
		//check status
		int checkNoStatus = Integer.parseInt(status);
		if(checkNoStatus<1 || checkNoStatus>5){
			mi.error("Status " + checkNoStatus + " is invalid");
			return;
		}else{
			Container.set("CKSTTS", status);
		}
		//check date
		if(dateCashed != 0){
			try {
				LocalDate.parse(Integer.toString(dateCashed), DateTimeFormatter.ofPattern("yyyyMMdd"));
			}
			catch(DateTimeParseException e){
				mi.error("Date " + dateCashed + " is not in yyyyMMdd format");
				return;
			}
			Container.set("CKCADA", dateCashed);
		}
		FABCHK_Query.readAllLock(Container, 3, updateCallBack);
	}
	/**
	 * dummy
	 */
	Closure < ? > readChecks = {

	}
	/**
	 * call back function to update the FAPCHK record
	 */
	Closure < ? > updateCallBack = { LockedResult lockedResult ->
		lockedResult.set("CKSTTS", status);
		if (dateCashed != 0) {
			lockedResult.set("CKCADA", dateCashed);
		}
		lockedResult.set("CKCHID",program.getUser());
		lockedResult.set("CKCHNO",lockedResult.getInt("CKCHNO")+1);
		lockedResult.set("CKLMDT",LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInteger());
		lockedResult.update();
	}
	/**
	 * Main method
	 */
	public void main() {
		int company = mi.getIn().get("CONO")==null?(int) program.getLDAZD().get("CONO"):(int)mi.getIn().get("CONO");
		String division = mi.getIn().get("DIVI")==null?(String) program.getLDAZD().get("DIVI"):(String)mi.getIn().get("DIVI");
		String checkNo = (String) mi.getIn().get("CHKN");
		//append check No with 0
		int expectedLength = 15 - checkNo.trim().size();
		for(int i = 0; i<=expectedLength-1;i++)
		{
			checkNo = "0" + checkNo;
		}
		status = (int) mi.getIn().get("STTS");
		dateCashed = (int) mi.getIn().get("CADA");
		CheckAndUpdate(company, division, checkNo, status, dateCashed);
	}
	// Global variables
	private final MIAPI mi;
	private final LoggerAPI logger;
	private final DatabaseAPI database;
	private final ProgramAPI program;
	private final MICallerAPI miCaller;
	public String status;
	public int dateCashed;
}