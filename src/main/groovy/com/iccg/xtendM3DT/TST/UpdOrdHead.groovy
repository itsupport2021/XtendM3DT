import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 *
 * @author SSAADI Updates OOHEAD's TEPY, AGNT and OOHEAC AGN2, AGN3 fields
 */
public class UpdOrdHead extends ExtendM3Transaction {
  // Global variables
	private final MIAPI mi;
	private final LoggerAPI logger;
	private final DatabaseAPI database;
	private final ProgramAPI program;
	private final MICallerAPI miCaller;
	private String agent;
	private String paymentTerms;
	private String agent2;
	private String agent3;
  
	/**
	 * Constructor
	 *
	 * @param mi
	 * @param database
	 * @param program
	 * @param miCaller
	 */
	UpdOrdHead(MIAPI mi, DatabaseAPI database, ProgramAPI program, MICallerAPI miCaller, LoggerAPI logger) {
		this.mi = mi;
		this.database = database;
		this.program = program;
		this.miCaller = miCaller;
		this.logger = logger;
	}

	/**
	 *main method from here execution starts
	 */
	public void main() {
		int company = (int) mi.getIn().get("CONO");
		if (company == 0) {
			company = (int) program.getLDAZD().get("CONO"); // if no CONO is provided take default company from user context
		}
		String orderNumber = (String) mi.getIn().get("ORNO");
		agent = (String) mi.getIn().get("AGNT");
		paymentTerms = (String) mi.getIn().get("TEPY");
		agent2 = (String) mi.getIn().get("AGN2");
		agent3 = (String) mi.getIn().get("AGN3");
		if ((agent == null || agent.equals("")) && (paymentTerms == null || paymentTerms.equals(""))
				&&(agent2 == null || agent2.equals(""))&&(agent3 == null || agent3.equals(""))) {
			// if no update fields are entered then exit transaction
			mi.error("Any one of the fields to be updated must be entered");
			return;
		}
		// Check order
		if(validateOrderNumber(company, orderNumber)) {
			// Update order head
			if((agent != null && !agent.equals("")) || (paymentTerms != null && !paymentTerms.equals(""))){
				checkAndUpdateHeader(company, orderNumber, agent, paymentTerms);
			}
			if((agent2 != null && !agent2.equals(""))||(agent3 != null && !agent3.equals(""))) {
				// update bonus commission
				checkAndUpdateAgents(company,orderNumber, agent2, agent3);
			}
		}
	}

	/**
	 * Validates order number from OOHEAD
	 *
	 * @param company
	 * @param orderNumber
	 * @return
	 */
	public boolean validateOrderNumber(int company, String orderNumber) {
		DBAction orderQuery = database.table("OOHEAD").index("00").selection("OACONO", "OAORNO", "OAAGNT", "OATEPY")
				.build();
		DBContainer container = orderQuery.getContainer();
		container.set("OAORNO", orderNumber);
		container.set("OACONO", company);
		// Check if order number is valid or not
		if (!orderQuery.read(container)) {
			mi.error("OrderNumber " + orderNumber + " doesn't exists", "ORNO", "");
			return false;
		}
		return true;
	}

	/**
	 * if order is valid then updates the order header (OOHEAD) fields AGNT and TEPY
	 * @param company
	 * @param orderNumber
	 * @param agent
	 * @param paymentTerms
	 */
	public void checkAndUpdateHeader(int company, String orderNumber, String agent, String paymentTerms) {
		// Check if Agent is valid or not
		if (agent != null && !agent.equals("")) {
			DBAction agentQuery = database.table("OCUSMA").index("00").selection("OKCONO", "OKCUNO").build();
			DBContainer agentContainer = agentQuery.getContainer();
			agentContainer.set("OKCONO", company);
			agentContainer.set("OKCUNO", agent);
			if (!agentQuery.read(agentContainer)) {
				mi.error("Agent " + agent + " is invalid","AGNT","");
				return;
			}
		}
		DBAction orderQuery = database.table("OOHEAD").index("00").selection("OACONO", "OAORNO", "OAAGNT", "OATEPY", "OACHID", "OACHNO", "OALMDT")
				.build();
		DBContainer orderContainer = orderQuery.getContainer();
		orderContainer.set("OAORNO", orderNumber);
		orderContainer.set("OACONO", company);
		// Check if Payment terms are valid or not
		if (paymentTerms != null && !paymentTerms.equals("")) {
			DBAction paymentTermsQuery = database.table("CSYTAB").index("00").selection("CTCONO", "CTDIVI", "CTSTCO", "CTSTKY", "CTLNCD").build();
			DBContainer paymentTermsContainer = paymentTermsQuery.getContainer();
			paymentTermsContainer.set("CTDIVI", "");
			paymentTermsContainer.set("CTCONO", company);
			paymentTermsContainer.set("CTSTCO", "TEPY");
			paymentTermsContainer.set("CTSTKY", paymentTerms.toString());
			paymentTermsContainer.set("CTLNCD", (String) program.getLDAZD().get("LANC"));
			if(!paymentTermsQuery.read(paymentTermsContainer)) {
				mi.error("Payment terms " + paymentTerms.toString() + " is invalid","TEPY","");
				return;
			}
		}
		orderQuery.readLock(orderContainer, updateCallBack);
	}

	/**
	 * call back function to update the OOHEAD record
	 */
	Closure < ? > updateCallBack = { LockedResult lockedResult->
		if(agent!=null&&!agent.equals(""))
		{
			lockedResult.set("OAAGNT", agent);
		}
		if(paymentTerms!=null&&!paymentTerms.equals(""))
		{
			lockedResult.set("OATEPY", paymentTerms);
		}
		lockedResult.set("OACHID",program.getUser());
		lockedResult.set("OACHNO",lockedResult.getInt("OACHNO")+1);
		lockedResult.set("OALMDT",LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInteger());
		lockedResult.update();
	}

	/**
	 * Checks and updates agent 2 and agent 3
	 * @param company
	 * @param AGN2
	 * @param AGN3
	 */
	public void checkAndUpdateAgents(int company, String orderNumber, String AGN2, String AGN3) {
		// Check if Agent 2 is valid or not
		if (agent2 != null && !agent2.equals("")) {
			DBAction agentQuery = database.table("OCUSMA").index("00").selection("OKCONO", "OKCUNO").build();
			DBContainer agentContainer = agentQuery.getContainer();
			agentContainer.set("OKCONO", company);
			agentContainer.set("OKCUNO", agent2);
			if (!agentQuery.read(agentContainer)) {
				mi.error("Agent2 " + agent2 + " is invalid","AGN2","");
				return;
			}
		}
		// Check if Agent 3 is valid or not
		if (agent3 != null && !agent3.equals("")) {
			DBAction agentQuery = database.table("OCUSMA").index("00").selection("OKCONO", "OKCUNO").build();
			DBContainer agentContainer = agentQuery.getContainer();
			agentContainer.set("OKCONO", company);
			agentContainer.set("OKCUNO", agent3);
			if (!agentQuery.read(agentContainer)) {
				mi.error("Agent3 " + agent3 + " is invalid","AGN3","");
				return;
			}
		}
		// update agent2 and agent3 fields
		DBAction queryOOHEAC = database.table("OOHEAC").index("00").selection("BECONO","BEORNO","BEAGN2", "BEAGN3", "BECHID","BELMDT","BECHNO").build();
		DBContainer commissionContainer = queryOOHEAC.getContainer();
		commissionContainer.set("BECONO", company);
		commissionContainer.set("BEORNO", orderNumber);
		queryOOHEAC.readLock(commissionContainer, callbackToUpdateOOHEAC);
	}

	/**
	 * call back function to update the OOHEAD record
	 */
	Closure < ? > callbackToUpdateOOHEAC = { LockedResult lockedResult->
		if(agent2!=null&&!agent2.equals(""))
		{
			lockedResult.set("BEAGN2", agent2);
		}
		if(agent3!=null&&!agent3.equals(""))
		{
			lockedResult.set("BEAGN3", agent3);
		}
		lockedResult.set("BECHID",program.getUser());
		lockedResult.set("BECHNO",lockedResult.getInt("BECHNO")+1);
		lockedResult.set("BELMDT",LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInteger());
		lockedResult.update();
	}
}