/********************************************************************************/
/*										*/
/*		BseanFactory.java						*/
/*										*/
/*	Factory for setting up and interface semantic analysis with bubbles	*/
/*										*/
/********************************************************************************/
/*	Copyright 2011 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 * This program and the accompanying materials are made available under the	 *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at								 *
 *	http://www.eclipse.org/legal/epl-v10.html				 *
 *										 *
 ********************************************************************************/

/* SVN: $Id$ */



package edu.brown.cs.faitbb.bsean;

import edu.brown.cs.bubbles.bale.BaleFactory;
import edu.brown.cs.bubbles.bale.BaleConstants.BaleContextConfig;
import edu.brown.cs.bubbles.bale.BaleConstants.BaleContextListener;
import edu.brown.cs.bubbles.bale.BaleConstants.BaleContextType;
import edu.brown.cs.bubbles.bale.BaleConstants.BaleWindow;
import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.board.BoardPluginManager;
import edu.brown.cs.bubbles.board.BoardProperties;
import edu.brown.cs.bubbles.board.BoardSetup;
import edu.brown.cs.bubbles.board.BoardThreadPool;
import edu.brown.cs.bubbles.board.BoardConstants.BoardPluginFilter;
import edu.brown.cs.bubbles.board.BoardConstants.RunMode;
import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.bubbles.buda.BudaBubbleArea;
import edu.brown.cs.bubbles.buda.BudaConstants;
import edu.brown.cs.bubbles.buda.BudaErrorBubble;
import edu.brown.cs.bubbles.buda.BudaConstants.BubbleViewCallback;
import edu.brown.cs.bubbles.buda.BudaConstants.BudaBubblePosition;
import edu.brown.cs.bubbles.buda.BudaConstants.BudaWorkingSet;
import edu.brown.cs.bubbles.bump.BumpClient;
import edu.brown.cs.fredit.freditor.FreditorConstants;
import edu.brown.cs.fredit.freditor.FreditorRemote;
import edu.brown.cs.bubbles.buda.BudaRoot;
import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.ivy.exec.IvyExecQuery;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.mint.MintArguments;
import edu.brown.cs.ivy.mint.MintConstants;
import edu.brown.cs.ivy.mint.MintControl;
import edu.brown.cs.ivy.mint.MintDefaultReply;
import edu.brown.cs.ivy.mint.MintHandler;
import edu.brown.cs.ivy.mint.MintMessage;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

import org.w3c.dom.Element;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

public class BseanFactory implements BseanConstants, MintConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private boolean server_running;
private boolean server_started;
private BseanSession current_session;
private Set<File> open_files;
private boolean auto_start;

private static Collection<BseanFreditBubble> fredit_bubbles = new ConcurrentLinkedQueue<>();
private static Element last_analysis = null;

private static BseanFactory the_factory = new BseanFactory();



/********************************************************************************/
/*										*/
/*	Setup methods								*/
/*										*/
/********************************************************************************/

public static void setup()
{
   BoardPluginManager.installResources(BseanFactory.class,"fait",new ResourceFilter());
}



private static class ResourceFilter implements BoardPluginFilter {

   @Override public boolean accept(String nm) {
      if (nm.endsWith("karma.jar")) return true;
      return false;
    }

}	// end of inner class ResourceFilter




public static void initialize(BudaRoot br)
{
   switch (BoardSetup.getSetup().getLanguage()) {
      case JS :
      case PYTHON :
      case REBUS :
	 return;
      case JAVA :
	 break;
    }

   switch (BoardSetup.getSetup().getRunMode()) {
      case NORMAL :
      case CLIENT :
	 BudaRoot.addBubbleViewCallback(new ViewListener());
	 BudaRoot.registerMenuButton("Bubble.Security.Show Security Problems",new ProblemBubbleAction());
	 BudaRoot.registerMenuButton("Bubble.Security.Start Semantic Analysis",new StartAction());
	 BudaRoot.registerMenuButton("A#Bubble.Security.Edit Security Resource Files",new FreditAction());
	 BaleFactory.getFactory().addContextListener(new EditorListener());
	 BoardLog.logD("BSEAN","Added editor context listener");
	 break;
      case SERVER :
	 break;
    }

   BumpClient bc = BumpClient.getBump();
   Element xml = bc.getAllProjects(5000);
   if (xml != null) {
      boolean haveannot = false;
      for (Element pe : IvyXml.children(xml,"PROJECT")) {
	 String pnm = IvyXml.getAttrString(pe,"NAME");
	 Element opxml = bc.getProjectData(pnm,false,true,false,false,false);
	 if (opxml != null) {
	    Element cpe = IvyXml.getChild(opxml,"CLASSPATH");
	    for (Element rpe : IvyXml.children(cpe,"PATH")) {
	       String bn = null;
	       String ptyp = IvyXml.getAttrString(rpe,"TYPE");
	       if (ptyp != null && ptyp.equals("SOURCE")) {
		  bn = IvyXml.getTextElement(rpe,"OUTPUT");
		}
	       else {
		  bn = IvyXml.getTextElement(rpe,"BINARY");
		}
	       if (bn == null) continue;
	       if (bn.contains("annotations.jar") || bn.contains("karma.jar")) haveannot = true;
	     }
	  }
       }
      if (haveannot) {
	 getFactory().auto_start = true;
	 BseanStarter bs = new BseanStarter(br);
	 bs.start();
       }
    }
}



public static BseanFactory getFactory()
{
   return the_factory;
}



/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

private BseanFactory()
{
   server_running = false;
   server_started = false;
   auto_start = false;
   current_session = null;
   open_files = new HashSet<>();

   BoardSetup bs = BoardSetup.getSetup();
   MintControl mc = bs.getMintControl();
   mc.register("<FAITEXEC TYPE='_VAR_0' />",new UpdateHandler());

   switch (BoardSetup.getSetup().getRunMode()) {
      case NORMAL :
      case CLIENT :
	 break;
      case SERVER :
	 mc.register("<BSEAN TYPE='START' />",new StartHandler());
	 break;
    }
}


/********************************************************************************/
/*										*/
/*	Starting methods							*/
/*										*/
/********************************************************************************/

BseanSession getCurrentSession()
{
   if (server_running) {
      BoardSetup bs = BoardSetup.getSetup();
      MintControl mc = bs.getMintControl();
      MintDefaultReply rply = new MintDefaultReply();
      mc.send("<FAIT DO='PING' SID='*' />",rply,MINT_MSG_FIRST_NON_NULL);
      String rslt = rply.waitForString(5000);
      if (rslt == null) server_running = false;
    }
   if (!server_running) {
      if (current_session != null) current_session.remove();
      current_session = null;
      start();
    }

   return current_session;
}


private void start()
{
   if (!server_running) server_started = false; 		// for debug
   startFait();
   if (!server_running) return;

   try {
      if (current_session != null) current_session.remove();
      current_session = new BseanSession();
      current_session.begin();
      for (File f : open_files) {
	 current_session.handleEditorAdded(f);
       }
      current_session.startAnalysis();
    }
   catch (BseanException e) {
      if (current_session != null) current_session.remove();
      current_session = null;
    }
}



/********************************************************************************/
/*										*/
/*	Processing methods							*/
/*										*/
/********************************************************************************/

private void handleEditorAdded(BudaBubble bb)
{
   File f = bb.getContentFile();
   if (f == null) return;
   if (!open_files.add(f)) return;

   if (current_session != null) current_session.handleEditorAdded(f);
}


static void noteAnalysis(Element xml)
{
   for (BseanFreditBubble fbbl : fredit_bubbles) {
      fbbl.noteFaitAnalysis(xml);
    }

   last_analysis = xml;
}




/********************************************************************************/
/*										*/
/*	Fait Server communication						*/
/*										*/
/********************************************************************************/

Element sendFaitMessage(String id,String cmd,CommandArgs args,String cnts)
{
   if (!server_running) return null;

   BoardSetup bs = BoardSetup.getSetup();
   MintControl mc = bs.getMintControl();
   if (id == null && getCurrentSession() != null) id = getCurrentSession().getSessionId();

   MintDefaultReply rply = new MintDefaultReply();
   IvyXmlWriter xw = new IvyXmlWriter();
   xw.begin("FAIT");
   xw.field("DO",cmd);
   xw.field("SID",id);
   if (args != null) {
      for (Map.Entry<String,Object> ent : args.entrySet()) {
	 xw.field(ent.getKey(),ent.getValue());
       }
    }
   if (cnts != null) {
      xw.xmlText(cnts);
    }
   xw.end("FAIT");
   String msg = xw.toString();
   xw.close();

   BoardLog.logD("BSEAN","Send to FAIT: " + msg);

   mc.send(msg,rply,MINT_MSG_FIRST_NON_NULL);

   Element rslt = rply.waitForXml(60000);

   BoardLog.logD("BSEAN","Reply from FAIT: " + IvyXml.convertXmlToString(rslt));
   if (rslt == null && (cmd.equals("START") || cmd.equals("BEGIN"))) {
      MintDefaultReply prply = new MintDefaultReply();
      mc.send("<FAIT DO='PING' SID='*' />",rply,MINT_MSG_FIRST_NON_NULL);
      String prslt = prply.waitForString(3000);
      if (prslt == null) {
	 server_running = false;
	 server_started = false;
	 startFait();
	 rply = new MintDefaultReply();
	 mc.send(msg,rply,MINT_MSG_FIRST_NON_NULL);
	 rslt = rply.waitForXml(0);
       }
    }

   return rslt;
}




private boolean startFait()
{
   BoardSetup bs = BoardSetup.getSetup();
   MintControl mc = bs.getMintControl();

   if (BoardSetup.getSetup().getRunMode() == RunMode.CLIENT) {
      MintDefaultReply rply = new MintDefaultReply();
      mc.send("<BICEX TYPE='START' />",rply,MINT_MSG_FIRST_NON_NULL);
      Element xml = rply.waitForXml();
      boolean sts = IvyXml.getAttrBool(xml,"VALUE");
      return sts;
    }

   IvyExec exec = null;
   File wd = new File(bs.getDefaultWorkspace());
   File logf = new File(wd,"fait.log");

   if (server_running || server_started) return false;

   BoardProperties bp = BoardProperties.getProperties("Bsean");
   String dbgargs = bp.getProperty("Bsean.jvm.args");
   List<String> args = new ArrayList<>();
   args.add(IvyExecQuery.getJavaPath());

   if (dbgargs != null && dbgargs.contains("###")) {
      int port = (int)(Math.random() * 1000 + 3000);
      BoardLog.logI("BSEAN","Fait debugging port " + port);
      dbgargs = dbgargs.replace("###",Integer.toString(port));
    }

   if (dbgargs != null) {
      StringTokenizer tok = new StringTokenizer(dbgargs);
      while (tok.hasMoreTokens()) {
	 args.add(tok.nextToken());
       }
    }

   File jarfile = IvyFile.getJarFile(BseanFactory.class);

   args.add("-cp");
   String xcp = bp.getProperty("Bsean.fait.class.path");
   if (xcp == null) {
      xcp = System.getProperty("java.class.path");
      String ycp = bp.getProperty("Bsean.fait.add.path");
      if (ycp != null) xcp = ycp + File.pathSeparator + xcp;
    }
   else {
      BoardSetup setup = BoardSetup.getSetup();
      StringBuffer buf = new StringBuffer();
      StringTokenizer tok = new StringTokenizer(xcp,":;");
      while (tok.hasMoreTokens()) {
	 String elt = tok.nextToken();
	 if (!elt.startsWith("/") &&  !elt.startsWith("\\")) {
	    if (elt.equals("eclipsejar")) {
	       elt = setup.getEclipsePath();
	     }
	    else if (elt.equals("fait.jar") && jarfile != null) {
	       elt = jarfile.getPath();
	     }
	    else {
	       elt = setup.getLibraryPath(elt);
	     }
	  }
	 if (buf.length() > 0) buf.append(File.pathSeparator);
	 buf.append(elt);
       }
      xcp = buf.toString();
    }

   args.add(xcp);
   args.add("edu.brown.cs.fait.iface.FaitMain");
   args.add("-m");
   args.add(bs.getMintName());
   args.add("-L");
   args.add(logf.getPath());
   if (bp.getBoolean("Bsean.fait.debug")) {
      args.add("-D");
      if (bp.getBoolean("Bsean.fait.trace")) args.add("-T");
    }

   synchronized (this) {
      if (server_started || server_running) return false;
      server_started = true;
    }

   boolean isnew = false;
   for (int i = 0; i < 100; ++i) {
      MintDefaultReply rply = new MintDefaultReply();
      mc.send("<FAIT DO='PING' SID='*' />",rply,MINT_MSG_FIRST_NON_NULL);
      String rslt = rply.waitForString(1000);
      if (rslt != null) {
	 server_running = true;
	 break;
       }
      if (i == 0) {
	 try {
	    exec = new IvyExec(args,null,IvyExec.ERROR_OUTPUT);     // make IGNORE_OUTPUT to clean up otuput
	    isnew = true;
	    BoardLog.logD("BSEAN","Run " + exec.getCommand());
	  }
	 catch (IOException e) {
	    break;
	  }
       }
      else {
	 try {
	    if (exec != null) {
	       int sts = exec.exitValue();
	       BoardLog.logD("BSEAN","Fait server disappeared with status " + sts);
	       break;
	     }
	  }
	 catch (IllegalThreadStateException e) { }
       }

      try {
	 Thread.sleep(2000);
       }
      catch (InterruptedException e) { }
    }
   if (!server_running) {
      BoardLog.logE("BSEAN","Unable to start fait server: " + args);
      return true;
    }

   return isnew;
}



private class StartHandler implements MintHandler {

   @Override public void receive(MintMessage msg,MintArguments args) {
      boolean sts = startFait();
      if (sts) msg.replyTo("<RESULT VALUE='true'/>");
      else msg.replyTo("<RESULT VALUE='false' />");
    }

}	// end of inner class StartHandler												 <



/********************************************************************************/
/*										*/
/*	Message handling							*/
/*										*/
/********************************************************************************/

private class UpdateHandler implements MintHandler {

   @Override public void receive(MintMessage msg,MintArguments args) {
      String type = args.getArgument(0);
      Element xml = msg.getXml();
      String id = IvyXml.getAttrString(xml,"ID");
      if (current_session == null ||
	    !current_session.getSessionId().equals(id)) return;
      String rslt = null;
      try {
	 switch (type) {
	    case "ANALYSIS" :
	       if (current_session != null) {
		  current_session.handleAnalysis(xml);
		}
	       break;
	    default :
	       if (type.equals("ANALYSIS")) {
		  BoardLog.logE("BSEAN","WHY ARE WE HERE: `" + type + "'");
		}
	       BoardLog.logE("BSEAN","Unknown command " + type + " from Fait");
	       break;
	    case "ERROR" :
	       throw new BseanException("Bad command");
	  }
       }
      catch (Throwable e) {
	 BoardLog.logE("BSEAN","Error processing command",e);
       }
      msg.replyTo(rslt);
   }

}	// end of inner class UpdateHandler



/********************************************************************************/
/*										*/
/*	Monitor the current display						*/
/*										*/
/********************************************************************************/


private static class ViewListener implements BubbleViewCallback {

   @Override public void doneConfiguration()				{ }
   @Override public void focusChanged(BudaBubble bb,boolean set)	{ }
   @Override public void bubbleRemoved(BudaBubble bb)			{ }
   @Override public boolean bubbleActionDone(BudaBubble bb)		{ return false; }
   @Override public void workingSetAdded(BudaWorkingSet ws)		{ }
   @Override public void workingSetRemoved(BudaWorkingSet ws)		{ }
   @Override public void copyFromTo(BudaBubble f,BudaBubble t)		{ }

   @Override public void bubbleAdded(BudaBubble bb) {
      File f = bb.getContentFile();
      if (f == null) return;
      BseanFactory.getFactory().handleEditorAdded(bb);
    }

}



/********************************************************************************/
/*										*/
/*	Monitor the current editor						*/
/*										*/
/********************************************************************************/

private static class EditorListener implements BaleContextListener {

   @Override public BudaBubble getHoverBubble(BaleContextConfig cfg) {
      return null;
    }

   @Override public String getToolTipHtml(BaleContextConfig cfg) {
      // BoardLog.logD("BSEAN","Get editor tool tip");
      return null;
    }

   @Override public void noteEditorAdded(BaleWindow win)		{ }
   @Override public void noteEditorRemoved(BaleWindow win)		{ }

   @Override public void addPopupMenuItems(BaleContextConfig cfg,JPopupMenu menu) {
      BaleContextType ttyp = cfg.getTokenType();
      if (last_analysis == null) return;
      switch (ttyp) {
	 default :
	 case NONE :
	    return;
	 case LOCAL_ID :
	 case FIELD_ID :
	 case CALL_ID :
	 case STATIC_FIELD_ID :
	 case STATIC_CALL_ID :
	 case LOCAL_DECL_ID :
	    String method = cfg.getMethodName();
	    if (method == null) {
	       BoardLog.logE("BSEAN","Can't find method name for " + cfg.getDocument());
	       break;
	     }
	    menu.add(new ValueAction(cfg));
	    break;
       }
    }

}	// end of inner class EditorListener



private static class ValueAction extends AbstractAction implements Runnable {

   private BaleContextConfig start_context;

   private static final long serialVersionUID = 1;

   ValueAction(BaleContextConfig cfg) {
      super("Provide Flow Data for " + cfg.getToken());
      start_context = cfg;
    }

   @Override public void actionPerformed(ActionEvent e) {
      BoardThreadPool.start(this);
    }

   @Override public void run() {
      int pos = start_context.getOffset();
      int apos = start_context.getDocument().mapOffsetToEclipse(pos);
      CommandArgs args = new CommandArgs("FILE",start_context.getEditor().getContentFile(),
	    "START",apos,
	    "LINE",start_context.getLineNumber(),
	    "TOKEN",start_context.getToken(),
	    "METHOD",start_context.getMethodName());
      BseanFactory fac = getFactory();
      Element rslt =  fac.sendFaitMessage(null,"VARQUERY",args,null);
      Element qrslt = IvyXml.getChild(rslt,"VALUESET");
      if (qrslt == null) return;
      try {
	 BseanVarBubble vbbl = new BseanVarBubble(start_context,qrslt);
	 SwingUtilities.invokeLater(new CreateBubble(start_context.getEditor(),vbbl));
       }
      catch (BseanException e) {
	 BudaErrorBubble ebbl = new BudaErrorBubble("No flow was found to this point");
	 SwingUtilities.invokeLater(new CreateBubble(start_context.getEditor(),ebbl));
       }
    }

}	// end of inner class ValueAction



/********************************************************************************/
/*										*/
/*	Handle starting 						       */
/*										*/
/********************************************************************************/

private static class StartAction implements BudaConstants.ButtonListener, Runnable {

   @Override public void buttonActivated(BudaBubbleArea bba,String id,Point pt) {
      BoardThreadPool.start(this);
    }

   @Override public void run() {
      BseanFactory fac = getFactory();
      fac.start();
    }

}	// end of inner class StartAction



/********************************************************************************/
/*										*/
/*	Handle problem bubble display						*/
/*										*/
/********************************************************************************/

private static class ProblemBubbleAction implements BudaConstants.ButtonListener {

   @Override public void buttonActivated(BudaBubbleArea bba,String id,Point pt) {
      if (bba == null) return;

      BudaBubble bbl = null;
      try {
	 bbl = new BseanProblemBubble();
       }
      catch (BseanException e) {
	 BoardLog.logE("BSEAN","Problem creating problem bubble",e);
	 return;
       }
      BudaBubblePosition pos = BudaBubblePosition.MOVABLE;
      BoardProperties bp = BoardProperties.getProperties("Bsean");
      if (bp.getBoolean("Bsean.problem.fixed")) pos = BudaBubblePosition.FLOAT;
      int place = BudaConstants.PLACEMENT_LOGICAL | BudaConstants.PLACEMENT_USER;
      bba.addBubble(bbl,null,pt,place,pos);
    }

}	// end of inner class ProblemBubbleAction



/********************************************************************************/
/*										*/
/*	Explain actions 							*/
/*										*/
/********************************************************************************/

static class ExplainAction extends AbstractAction implements Runnable {

   private Component for_window;
   private List<BseanError> for_errors;

   private static final long serialVersionUID = 1;

   ExplainAction(Component c,List<BseanError> errs) {
      super("Explain: " + errs.get(0).getMessage());
      for_window = c;
      for_errors = new ArrayList<>(errs);
    }

   @Override public void actionPerformed(ActionEvent e) {
      if (for_errors.size() == 0) return;
      BoardThreadPool.start(this);
    }

   @Override public void run() {
      BseanError er0 = for_errors.get(0);
      StringBuffer errids = new StringBuffer();
      errids.append(er0.getId());
      for (int i = 1; i < for_errors.size(); ++i) {
	 errids.append(" ");
	 errids.append(for_errors.get(i).getId());
       }
      CommandArgs args = new CommandArgs("FILE",er0.getFile(),
	    "QTYPE","ERROR",
	    "LINE",er0.getLine(),
	    "ERROR",errids.toString(),
	    "METHOD",er0.getMethod(),
	    "START",er0.getEclipseOffset());
      BseanFactory bfac = getFactory();
      Element rslt = bfac.sendFaitMessage(null,"QUERY",args,null);
      if (rslt == null) return;
      Element rset = IvyXml.getChild(rslt,"RESULTSET");
      for (Element qelt : IvyXml.children(rset,"QUERY")) {
	 try {
	    BudaBubble nbbl = new BseanExplainBubble(qelt,null);
	    SwingUtilities.invokeLater(new CreateBubble(for_window,nbbl));
	  }
	 catch (BseanException e) { }
       }
    }

}	// end of inner class ExplainAction


static class CreateBubble implements Runnable {

   private BudaBubble new_bubble;
   private Component for_window;

   CreateBubble(Component w,BudaBubble bbl) {
      for_window = w;
      new_bubble = bbl;
    }

   @Override public void run() {
      if (new_bubble == null) return;
      BudaBubbleArea bba = BudaRoot.findBudaBubbleArea(for_window);
      bba.addBubble(new_bubble,for_window,null,
	    BudaConstants.PLACEMENT_LOGICAL|BudaConstants.PLACEMENT_NEW|
	    BudaConstants.PLACEMENT_MOVETO|BudaConstants.PLACEMENT_USER);
    }

}	// end of inner class CreateBubble



/********************************************************************************/
/*										*/
/*	Configurator								*/
/*										*/
/********************************************************************************/

private static class BseanStarter extends Thread {

   private BudaRoot buda_root;

   BseanStarter(BudaRoot br) {
      super("BseanStarter");
      buda_root = br;
    }

   @Override public void run() {
      buda_root.waitForSetup();
      BseanFactory bf = getFactory();
      if (bf.auto_start){
	 bf.start();
	 bf.auto_start = false;
       }
   }

}	// end of inner class BseanConfigurator



/********************************************************************************/
/*										*/
/*	Resource editing action 						*/
/*										*/
/********************************************************************************/

static class FreditAction implements BudaConstants.ButtonListener, Runnable {

   private BudaBubbleArea bubble_area;
   private Point at_point;
   private BudaBubble result_bubble;

   FreditAction()			{ }

   @Override public void buttonActivated(BudaBubbleArea bba,String id,Point pt) {
      if (bba == null) return;
      bubble_area = bba;
      at_point = pt;
      result_bubble = null;

      BoardThreadPool.start(this);
    }

   @Override public void run() {
      if (result_bubble != null) {
	 BudaBubblePosition pos = BudaBubblePosition.MOVABLE;
	 int place = BudaConstants.PLACEMENT_LOGICAL | BudaConstants.PLACEMENT_USER;
	 bubble_area.addBubble(result_bubble,null,at_point,place,pos);
       }
      else {
	 try {
	    result_bubble = new BseanFreditBubble();
	  }
	 catch (BseanException e) {
	    BoardLog.logE("BSEAN","Problem creating problem bubble",e);
	    return;
	  }
	 if (result_bubble != null) SwingUtilities.invokeLater(this);
       }
    }



}	// end of inner class ExplainAction


private static class BseanFreditBubble extends BudaBubble {

   private FreditorRemote freditor_remote;

   private static final long serialVersionUID = 1;

   BseanFreditBubble() throws BseanException {
      BseanSession ss = BseanFactory.getFactory().getCurrentSession();
      if (ss == null) throw new BseanException("Can't start fait");
      String mintid = BoardSetup.getSetup().getMintName();
      freditor_remote = new FreditorRemote(new BseanAuxCreator(this),mintid,ss.getSessionId());
      fredit_bubbles.add(this);
      if (last_analysis != null) noteFaitAnalysis(last_analysis);
      JComponent pane = freditor_remote.getEditor();
      setContentPane(pane,null);
    }

   @Override public void disposeBubble() {
      fredit_bubbles.remove(this);
   }

   void noteFaitAnalysis(Element xml) {
      freditor_remote.noteFaitAnalysis(xml);
    }

}	// end of inner class BseanFreditBubble




private static class BseanAuxCreator implements FreditorConstants.WindowCreator {

   private BudaBubble base_bubble;

   BseanAuxCreator(BudaBubble bbl) {
      base_bubble = bbl;
    }

   @Override public void createWindow(String ttl,JComponent comp) {
      BudaBubbleArea bba = BudaRoot.findBudaBubbleArea(base_bubble);
      BseanAuxBubble bbl = new BseanAuxBubble(comp);
      BudaBubblePosition pos = BudaBubblePosition.MOVABLE;
      BoardProperties bp = BoardProperties.getProperties("Bsean");
      if (bp.getBoolean("Bsean.problem.fixed")) pos = BudaBubblePosition.FLOAT;
      int place = BudaConstants.PLACEMENT_LOGICAL | BudaConstants.PLACEMENT_USER;
      bba.addBubble(bbl,base_bubble,null,place,pos);
    }

}	// end of inner class BseanAuxCreator



private static class BseanAuxBubble extends BudaBubble {

   private static final long serialVersionUID = 1;

   BseanAuxBubble(JComponent cmp) {
      setContentPane(cmp,null);
    }

}	// end of inner class BseanAuxBubble


}	// end of class BseanFactory




/* end of BseanFactory.java */

