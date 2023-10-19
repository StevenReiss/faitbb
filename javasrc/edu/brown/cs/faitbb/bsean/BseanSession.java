/********************************************************************************/
/*										*/
/*		BseanSession.java						*/
/*										*/
/*	description of class							*/
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


package edu.brown.cs.faitbb.bsean;

import edu.brown.cs.bubbles.bale.BaleConstants;
import edu.brown.cs.bubbles.bale.BaleFactory;
import edu.brown.cs.bubbles.board.BoardImage;
import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.board.BoardMetrics;
import edu.brown.cs.bubbles.board.BoardProperties;
import edu.brown.cs.bubbles.board.BoardThreadPool;
import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.ivy.exec.IvyExecQuery;
import edu.brown.cs.ivy.swing.SwingEventListenerList;
import edu.brown.cs.ivy.xml.IvyXml;

import org.w3c.dom.Element;

import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.Icon;
import javax.swing.JPopupMenu;

class BseanSession implements BseanConstants, BaleConstants
{




/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private String		session_id;
private Set<File>	added_files;
private List<BseanError> error_set;
private List<SecurityAnnot> current_annots;

private SwingEventListenerList<BseanErrorHandler> error_handlers;


private static AtomicInteger id_counter = new AtomicInteger((int)(Math.random()*256000.0));



/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

BseanSession() throws BseanException
{
   session_id = "BSEAN_" + IvyExecQuery.getProcessId() + "_" + id_counter.incrementAndGet();
   added_files = new HashSet<>();
   error_set = new ArrayList<>();
   current_annots = new ArrayList<>();

   error_handlers = new SwingEventListenerList<>(BseanErrorHandler.class);

   Element rslt = sendFaitMessage("BEGIN",null,null);
   if (!IvyXml.isElement(rslt,"RESULT")) throw new BseanException("Failed to create session");
   Element sess = IvyXml.getChild(rslt,"SESSION");
   String sid = IvyXml.getAttrString(sess,"ID");
   if (sid != null) session_id = sid;
}




/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

String getSessionId()				{ return session_id; }

List<BseanError> getCurrentErrors()
{
   synchronized(error_set) {
      return new ArrayList<>(error_set);
    }
}



/********************************************************************************/
/*										*/
/*	Event handling								*/
/*										*/
/********************************************************************************/

void addErrorHandler(BseanErrorHandler eh)
{
   error_handlers.add(eh);
}

void removeErrorHandler(BseanErrorHandler eh)
{
   error_handlers.remove(eh);
}


void remove()
{
   if (current_annots != null) {
      for (SecurityAnnot an : current_annots) {
	 BaleFactory.getFactory().removeAnnotation(an);
       }
      current_annots.clear();
    }

   if (error_set != null) {
      synchronized (error_set) {
	 error_set.clear();
       }
      fireErrorsUpdated();
    }
}



private void fireErrorsUpdated()
{
   for (BseanErrorHandler eh : error_handlers) {
      eh.handleErrorsUpdated();
    }
}


/********************************************************************************/
/*										*/
/*	Maintenance methods							*/
/*										*/
/********************************************************************************/

void begin() throws BseanException
{
   Element rslt = sendFaitMessage("BEGIN",null,null);
   if (!IvyXml.isElement(rslt,"RESULT"))
      throw new BseanException("BEGIN for session failed");
}


void startAnalysis() throws BseanException
{
   updateAnnotations(null);

   CommandArgs args = new CommandArgs("REPORT","FULL");

   BoardProperties bp = BoardProperties.getProperties("Bsean");
   int nth = bp.getInt("Bsean.fait.threads");
   if (nth > 0) args.put("THREADS", nth);

   Element rslt = sendFaitMessage("ANALYZE",args,null);
   if (!IvyXml.isElement(rslt,"RESULT"))
      throw new BseanException("ANALYZE for session failed");
}



void handleEditorAdded(File f)
{
   if (f == null) return;
   if (!f.getPath().endsWith(".java")) return;

   StringBuffer buf = new StringBuffer();
   int ct = 0;
   if (added_files.add(f)) {
      buf.append("<FILE NAME='");
      buf.append(f.getAbsolutePath());
      buf.append("' />");
      ++ct;
    }
   if (ct > 0) {
      backgroundFaitMessage("ADDFILE",null,buf.toString());
    }
}



/********************************************************************************/
/*										*/
/*	Handle Anslysis returned						*/
/*										*/
/********************************************************************************/

synchronized void handleAnalysis(Element xml)
{
   BoardLog.logD("BSEAN","ANALYSIS: " +  IvyXml.convertXmlToString(xml));

   List<BseanError> errs = null;

   synchronized (error_set) {
      if (IvyXml.getAttrBool(xml,"ABORTED")) {
	 BoardMetrics.noteCommand("BSEAN","ExecReset",IvyXml.getAttrLong(xml,"COMPILETIME"),
	       IvyXml.getAttrLong(xml,"ANALYSISTIME"));
	 error_set.clear();
       }
      else if (IvyXml.getAttrBool(xml,"STARTED")) {
	 error_set.clear();
       }
      else {
	 error_set.clear();
	 BoardMetrics.noteCommand("BSEAN","ExecReturned",IvyXml.getAttrLong(xml,"COMPILETIME"),
	       IvyXml.getAttrLong(xml,"ANALYSISTIME"),IvyXml.getAttrInt(xml,"NTHREAD"),
	       IvyXml.getAttrBool(xml,"UPDATE"));
	 for (Element callelt : IvyXml.children(IvyXml.getChild(xml,"DATA"),"CALL")) {
	    String file = IvyXml.getAttrString(callelt,"FILE");
	    if (file != null) {
	       for (Element errelt : IvyXml.children(callelt,"ERROR")) {
		  ErrorLevel lvl = IvyXml.getAttrEnum(errelt,"LEVEL",ErrorLevel.NOTE);
		  if (lvl != ErrorLevel.ERROR) continue;
		  Element where = IvyXml.getChild(errelt,"POINT");
		  if (!IvyXml.getAttrPresent(where,"LINE")) continue;
		  BseanError err = new BseanError(callelt,errelt,where);
		  error_set.add(err);
		}
	     }
	  }
       }
      errs = new ArrayList<>(error_set);
    }

   updateAnnotations(errs);

   fireErrorsUpdated();

   BseanFactory.noteAnalysis(xml);
}

/********************************************************************************/
/*										*/
/*	Messaging methods							*/
/*										*/
/********************************************************************************/

Element sendFaitMessage(String cmd,CommandArgs args,String cnts)
{
   BseanFactory bf = BseanFactory.getFactory();
   return bf.sendFaitMessage(session_id,cmd,args,cnts);
}


BackgroundFait backgroundFaitMessage(String cmd,CommandArgs args,String cnts)
{
   BackgroundFait bgf = new BackgroundFait(cmd,args,cnts);
   BoardThreadPool.start(bgf);
   return bgf;
}


private final class BackgroundFait implements Runnable {

private String command_name;
private CommandArgs command_args;
private String command_contents;
private Element command_reply;
private boolean have_reply;

BackgroundFait(String cmd,CommandArgs args,String cnts) {
   command_name = cmd;
   command_args = args;
   command_contents = cnts;
   command_reply = null;
   have_reply = false;
}

@Override public void run() {
   BseanFactory bf = BseanFactory.getFactory();
   command_reply = bf.sendFaitMessage(session_id,command_name,command_args,command_contents);
   synchronized(this) {
      have_reply = true;
      notifyAll();
    }
}

@SuppressWarnings("unused")
synchronized Element getResponse() { 
   while (!have_reply) {
      try {
         wait(5000);
       }
      catch(InterruptedException e) { }
    }
   return command_reply;
}

}       // end of inner class BackgroundFait




/********************************************************************************/
/*										*/
/*	Annotation methods							*/
/*										*/
/********************************************************************************/

private synchronized void updateAnnotations(List<BseanError> errs)
{
   for (SecurityAnnot an : current_annots) {
       BaleFactory.getFactory().removeAnnotation(an);
    }

   current_annots.clear();

   if (errs == null) return;

   Map<String,List<SecurityAnnot>> annotmap = new HashMap<>();

   outer: for (BseanError be : errs) {
      String key = be.getLineKey();
      List<SecurityAnnot> ans = annotmap.get(key);
      if (ans != null) {
	 for (SecurityAnnot an1 : ans) {
	    if (an1.addError(be)) continue outer;
	  }
       }
      else {
	 ans = new ArrayList<>();
	 annotmap.put(key,ans);
       }
      SecurityAnnot an = new SecurityAnnot(be);
      if (an.getDocumentOffset() <= 0) continue;
      ans.add(an);
      BaleFactory.getFactory().addAnnotation(an);
      current_annots.add(an);
    }
}



/********************************************************************************/
/*										*/
/*	Annotation for security errors						*/
/*										*/
/********************************************************************************/

private static class SecurityAnnot implements BaleAnnotation {

   private List<BseanError> for_problem;

   SecurityAnnot(BseanError er) {
      for_problem = new ArrayList<>();
      for_problem.add(er);
    }

   boolean addError(BseanError er) {
      BseanError orig = for_problem.get(0);
      if (!orig.sameError(er)) return false;
      for_problem.add(er);
      return true;
    }

   @Override public File getFile() {
      return for_problem.get(0).getFile();
    }

   @Override public int getDocumentOffset() {
      return for_problem.get(0).getDocumentOffset();
    }

   @Override public Icon getIcon(BudaBubble bbl) {
      return BoardImage.getIcon("security_error");
    }

   @Override public String getToolTip() {
      return IvyXml.htmlSanitize(for_problem.get(0).getMessage());
    }

   @Override public Color getLineColor(BudaBubble bbl)		{ return null; }
   @Override public Color getBackgroundColor()			{ return null; }
   @Override public boolean getForceVisible(BudaBubble bb)	{ return false; }
   @Override public int getPriority()				{ return 10; }
   @Override public void addPopupButtons(Component c,JPopupMenu m) {
      m.add(new BseanFactory.ExplainAction(c,for_problem));
    }

}	// end of inner class ProblemAnnot

}	// end of class BseanSession




/* end of BseanSession.java */

